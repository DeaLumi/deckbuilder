package emi.mtg.deckbuilder.controller.serdes;

import emi.lib.Service;
import emi.lib.mtg.Card;
import emi.lib.mtg.DataSource;
import emi.lib.mtg.game.Format;
import emi.lib.mtg.game.Zone;
import emi.mtg.deckbuilder.controller.Context;
import emi.mtg.deckbuilder.controller.DeckImportExport;
import emi.mtg.deckbuilder.model.CardInstance;
import emi.mtg.deckbuilder.model.DeckList;
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

@Service.Provider(DeckImportExport.class)
@Service.Property.String(name="name", value="MTGO")
@Service.Property.String(name="extension", value="dek")
public class MTGO implements DeckImportExport {
	private final DataSource cs;

	public MTGO(DataSource cs, Map<String, Format> formats) {
		this.cs = cs;
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
		Set<String> missingCards = new HashSet<>();

		NodeList cardss = xml.getDocumentElement().getElementsByTagName("Cards");
		for (int i = 0; i < cardss.getLength(); ++i) {
			Node element = cardss.item(i);
			Integer catId = Integer.parseInt(element.getAttributes().getNamedItem("CatID").getNodeValue());
			Integer qty = Integer.parseInt(element.getAttributes().getNamedItem("Quantity").getNodeValue());
			Boolean sb = Boolean.parseBoolean(element.getAttributes().getNamedItem("Sideboard").getNodeValue());
			String name = element.getAttributes().getNamedItem("Name").getNodeValue();

			Card.Printing printing = printingsCache.computeIfAbsent(catId, id -> {
				for (Card.Printing pr : cs.printings()) {
					if (catId.equals(pr.mtgoCatalogId())) {
						return pr;
					} else {
						printingsCache.put(pr.mtgoCatalogId(), pr);
					}
				}

				return null;
			});

			if (printing == null) {
				Card card = cs.card(name);

				if (card == null) {
					throw new IOException("Couldn't find card " + name + " / " + catId);
				}

				printing = card.printings().iterator().next();
				System.err.println("Warning: Couldn't find card " + name + " by catId " + catId + "; found by name, using first printing.");
				missingCards.add(name);
			}

			for (int n = 0; n < qty; ++n) {
				(sb ? sideboard : library).add(new CardInstance(printing));
			}
		}

		Map<Zone, List<CardInstance>> cards = new HashMap<>();
		cards.put(Zone.Library, library);
		cards.put(Zone.Sideboard, sideboard);
		return new DeckList(from.getName().substring(0, from.getName().lastIndexOf('.')), "", Context.FORMATS.get("Standard"), "", cards);
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

		Map<Card.Printing, Integer> multiset = new LinkedHashMap<>();
		for (Card.Printing pr : deck.primaryVariant.get().cards(Zone.Library)) {
			multiset.compute(pr, (p, v) -> v == null ? 1 : v + 1);
		}

		for (Map.Entry<Card.Printing, Integer> e : multiset.entrySet()) {
			if (e.getKey().mtgoCatalogId() == null) {
				throw new IOException(String.format("The card \"%s\" from set \"%s\" isn't available on MTGO.\nPlease select a different version.", e.getKey().card().name(), e.getKey().set().name()));
			}

			deckEl.appendChild(xml.createTextNode("\n  "));

			Element cardEl = xml.createElement("Cards");
			cardEl.setAttribute("CatID", Integer.toString(e.getKey().mtgoCatalogId()));
			cardEl.setAttribute("Quantity", Integer.toString(e.getValue()));
			cardEl.setAttribute("Sideboard", "false");
			cardEl.setAttribute("Name", e.getKey().card().name());
			deckEl.appendChild(cardEl);
		}

		Map<Card.Printing, Integer> sbMultiset = new LinkedHashMap<>();
		for (Card.Printing pr : deck.primaryVariant.get().cards(Zone.Sideboard)) {
			sbMultiset.compute(pr, (p, v) -> v == null ? 1 : v + 1);
		}

		for (Map.Entry<Card.Printing, Integer> e : sbMultiset.entrySet()) {
			if (e.getKey().mtgoCatalogId() == null) {
				throw new IOException(String.format("The card \"%s\" from set \"%s\" isn't available on MTGO.\nPlease select a different version.", e.getKey().card().name(), e.getKey().set().name()));
			}

			deckEl.appendChild(xml.createTextNode("\n  "));

			Element cardEl = xml.createElement("Cards");
			cardEl.setAttribute("CatID", Integer.toString(e.getKey().mtgoCatalogId()));
			cardEl.setAttribute("Quantity", Integer.toString(e.getValue()));
			cardEl.setAttribute("Sideboard", "true");
			cardEl.setAttribute("Name", e.getKey().card().name());
			deckEl.appendChild(cardEl);
		}

		deckEl.appendChild(xml.createTextNode("\n"));

		try {
			TransformerFactory factory = TransformerFactory.newInstance();
			Transformer xform = factory.newTransformer();
			xform.transform(new DOMSource(xml), new StreamResult(to));
		} catch (TransformerException e) {
			throw new IOException(e);
		}
	}

	@Override
	public Set<Features> unsupportedFeatures() {
		return EnumSet.complementOf(EnumSet.of(Features.CardArt));
	}
}
