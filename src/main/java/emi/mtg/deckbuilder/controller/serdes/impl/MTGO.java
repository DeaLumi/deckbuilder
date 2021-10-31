package emi.mtg.deckbuilder.controller.serdes.impl;

import emi.lib.mtg.Card;
import emi.lib.mtg.game.Format;
import emi.lib.mtg.game.Zone;
import emi.mtg.deckbuilder.controller.Context;
import emi.mtg.deckbuilder.controller.serdes.DeckImportExport;
import emi.mtg.deckbuilder.model.CardInstance;
import emi.mtg.deckbuilder.model.DeckList;
import emi.mtg.deckbuilder.model.Preferences;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.SAXException;

import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerException;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;
import java.io.File;
import java.io.IOException;
import java.util.*;
import java.util.stream.Collectors;

public class MTGO implements DeckImportExport.Monotype {
	@Override
	public String extension() {
		return "dek";
	}

	@Override
	public String toString() {
		return "Magic: the Gathering Online";
	}

	@Override
	public DeckList importDeck(File from) throws IOException {
		Document xml;

		List<CardInstance> library = new ArrayList<>(),
				sideboard = new ArrayList<>();

		try {
			xml = DocumentBuilderFactory.newInstance()
					.newDocumentBuilder()
					.parse(from);
		} catch (ParserConfigurationException | SAXException e) {
			throw new IOException(e);
		}

		Map<Integer, Card.Printing> printingsCache = new HashMap<>();

		NodeList cardss = xml.getDocumentElement().getElementsByTagName("Cards");
		for (int i = 0; i < cardss.getLength(); ++i) {
			Node element = cardss.item(i);
			Integer catId = Integer.parseInt(element.getAttributes().getNamedItem("CatID").getNodeValue());
			Integer qty = Integer.parseInt(element.getAttributes().getNamedItem("Quantity").getNodeValue());
			Boolean sb = Boolean.parseBoolean(element.getAttributes().getNamedItem("Sideboard").getNodeValue());
			String name = element.getAttributes().getNamedItem("Name").getNodeValue();

			Card.Printing printing = printingsCache.get(catId);
			if (printing == null) {
				for (Card.Printing pr : Context.get().data.printings()) {
					if (catId.equals(pr.mtgoCatalogId())) {
						printing = pr;
						break;
					} else {
						printingsCache.put(pr.mtgoCatalogId(), pr);
					}
				}
			};

			if (printing == null) {
				Card card = Context.get().data.cards().stream().filter(c -> c.name().equals(name)).findAny().orElse(null);

				if (card == null) {
					throw new IOException("Couldn't find card " + name + " / " + catId);
				}

				printing = Preferences.get().preferredPrinting(card);
				if (printing == null) printing = card.printings().iterator().next();
				System.err.println("Warning: Couldn't find card " + name + " by catId " + catId + "; found by name; using preferred printing. This won't export back to MTGO.");
			}

			for (int n = 0; n < qty; ++n) {
				(sb ? sideboard : library).add(new CardInstance(printing));
			}
		}

		Map<Zone, List<CardInstance>> cards = new HashMap<>();
		cards.put(Zone.Library, library);
		cards.put(Zone.Sideboard, sideboard);

		return new DeckList(from.getName().substring(0, from.getName().lastIndexOf('.')), "", Format.Freeform, "Imported from MTGO.", cards);
	}

	private static void appendZone(Document xml, Element deckEl, Collection<? extends Card.Printing> printings, boolean sideboard, Set<String> missingCards, Set<String> subbedCards) {
		Map<Card.Printing, Integer> multiset = new LinkedHashMap<>();
		for (Card.Printing pr : printings) {
			multiset.compute(pr, (p, v) -> v == null ? 1 : v + 1);
		}

		for (Map.Entry<Card.Printing, Integer> e : multiset.entrySet()) {
			Card.Printing pr = e.getKey();
			if (pr.mtgoCatalogId() == null) {
				// Try to find another version.

				for (Card.Printing otherPrint : e.getKey().card().printings()) {
					if (otherPrint.mtgoCatalogId() != null) {
						subbedCards.add(String.format("%s: used %s version", otherPrint.card().name(), otherPrint.set().name()));
						pr = otherPrint;
						break;
					}
				}

				if (pr.mtgoCatalogId() == null) {
					missingCards.add(pr.card().name());
					continue;
				}
			}

			deckEl.appendChild(xml.createTextNode("\n  "));

			Element cardEl = xml.createElement("Cards");
			cardEl.setAttribute("CatID", Integer.toString(pr.mtgoCatalogId()));
			cardEl.setAttribute("Quantity", Integer.toString(e.getValue()));
			cardEl.setAttribute("Sideboard", Boolean.toString(sideboard));
			cardEl.setAttribute("Name", e.getKey().card().name());
			deckEl.appendChild(cardEl);
		}
	}

	@Override
	public void exportDeck(DeckList deck, File to) throws IOException {
		Document xml;
		try {
			xml = DocumentBuilderFactory.newInstance()
					.newDocumentBuilder()
					.newDocument();
		} catch (ParserConfigurationException e) {
			throw new IOException(e);
		}

		Set<String> missingCards = new HashSet<>(), subbedCards = new HashSet<>();

		Element deckEl = xml.createElement("Deck");
		deckEl.setAttribute("xmlns:xsd", "http://www.w3.org/2001/XMLSchema");
		deckEl.setAttribute("xmlns:xsi", "http://www.w3.org/2001/XMLSchema-instance");
		deckEl.appendChild(xml.createTextNode("\n  "));
		xml.appendChild(deckEl);

		Element netDeckIdEl = xml.createElement("NetDeckID");
		netDeckIdEl.setTextContent("0");
		deckEl.appendChild(netDeckIdEl);
		deckEl.appendChild(xml.createTextNode("\n  "));

		Element preconDeckIdEl = xml.createElement("PreconstructedDeckID");
		preconDeckIdEl.setTextContent("0");
		deckEl.appendChild(preconDeckIdEl);

		appendZone(xml, deckEl, deck.cards(Zone.Library), false, missingCards, subbedCards);
		appendZone(xml, deckEl, deck.cards(Zone.Sideboard), true, missingCards, subbedCards);

		if (deck.format() == Format.Commander || deck.format() == Format.Brawl) {
			appendZone(xml, deckEl, deck.cards(Zone.Command), true, missingCards, subbedCards);
		}

		if (!missingCards.isEmpty()) {
			throw new IOException("The following cards are not available on MTGO:\n\u2022 " + missingCards.stream().collect(Collectors.joining("\n\u2022 ")));
		}

		deckEl.appendChild(xml.createTextNode("\n"));

		try {
			TransformerFactory factory = TransformerFactory.newInstance();
			Transformer xform = factory.newTransformer();
			xform.transform(new DOMSource(xml), new StreamResult(to));
		} catch (TransformerException e) {
			throw new IOException(e);
		}

		if (!subbedCards.isEmpty()) {
			throw new IOException("Some of your chosen versions were not available on MTGO.\nThe following substitutions have been made:\n\u2022 " + subbedCards.stream().collect(Collectors.joining("\n\u2022 ")) + "\n\nYour deck was exported successfully with these printings.");
		}
	}

	@Override
	public EnumSet<Feature> supportedFeatures() {
		return EnumSet.of(Feature.CardArt);
	}
}
