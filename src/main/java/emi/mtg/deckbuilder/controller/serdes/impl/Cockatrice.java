package emi.mtg.deckbuilder.controller.serdes.impl;

import emi.lib.mtg.Card;
import emi.lib.mtg.game.Zone;
import emi.mtg.deckbuilder.controller.serdes.DeckImportExport;
import emi.mtg.deckbuilder.model.DeckList;
import emi.mtg.deckbuilder.model.Preferences;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.SAXException;

import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.transform.OutputKeys;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerException;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

public class Cockatrice extends NameOnlyImporter implements DeckImportExport.Monotype {

	private static final Map<String, Zone> COCKATRICE_ZONE_MAP = cockatriceZoneMap();
	private static Map<String, Zone> cockatriceZoneMap() {
		HashMap<String, Zone> tmp = new HashMap<>();
		tmp.put("main", Zone.Library);
		tmp.put("side", Zone.Sideboard);
		return Collections.unmodifiableMap(tmp);
	}

	private static final Map<Zone, String> COCKATRICE_ZONE_MAP_REVERSE = cockatriceZoneMapReverse();
	private static Map<Zone, String> cockatriceZoneMapReverse() {
		EnumMap<Zone, String> tmp = new EnumMap<>(Zone.class);
		tmp.put(Zone.Library, "main");
		tmp.put(Zone.Sideboard, "side");
		tmp.put(Zone.Command, "side");
		return Collections.unmodifiableMap(tmp);
	}

	private static final DataFormat COCKATRICE = DataFormat.singleton(
			"Cockatrice",
			"cod",
			"application/xml",
			"Cockatrice XML deck file.",
			javafx.scene.input.DataFormat.PLAIN_TEXT,
			String.class
	);

	@Override
	public DataFormat format() {
		return COCKATRICE;
	}

	@Override
	public String toString() {
		return "Cockatrice";
	}

	@Override
	public DeckList importDeck(Path from) throws IOException {
		Document xml;

		beginImport();

		try (InputStream in = Files.newInputStream(from)) {
			xml = DocumentBuilderFactory.newInstance()
					.newDocumentBuilder()
					.parse(in);
		} catch (ParserConfigurationException | SAXException e) {
			throw new IOException(e);
		}

		Node deckNameEl = xml.getDocumentElement().getElementsByTagName("deckname").item(0);
		if (deckNameEl != null && !deckNameEl.getTextContent().isEmpty()) {
			name(deckNameEl.getTextContent());
		} else {
			name(from.getFileName().toString().replace(".cod", ""));
		}

		String deckComments = "";
		Node deckCommentsEl = xml.getDocumentElement().getElementsByTagName("comments").item(0);
		if (deckCommentsEl != null && !deckCommentsEl.getTextContent().isEmpty()) {
			deckComments = deckCommentsEl.getTextContent();
		}

		String deckAuthor;
		if (deckComments.startsWith("Author: ")) {
			String[] parts = deckComments.split("\n\n", 2);
			deckAuthor = parts[0].substring("Author: ".length());
			deckComments = parts.length > 1 ? parts[1] : "";
		} else {
			deckAuthor = Preferences.get().authorName;
		}

		description(deckComments);
		author(deckAuthor);

		NodeList zones = xml.getDocumentElement().getElementsByTagName("zone");
		for (int i = 0; i < zones.getLength(); ++i) {
			Element zone = (Element) zones.item(i);

			beginZone(COCKATRICE_ZONE_MAP.get(zone.getAttribute("name")));

			NodeList cards = zone.getElementsByTagName("card");
			for (int j = 0; j < cards.getLength(); ++j) {
				Element cardEl = (Element) cards.item(j);
				addCard(cardEl.getAttribute("name"), Integer.parseInt(cardEl.getAttribute("number")));
			}

			endZone();
		}

		return completeImport();
	}

	@Override
	public void exportDeck(DeckList deck, Path to) throws IOException {
		Document xml;
		try {
			xml = DocumentBuilderFactory.newInstance()
					.newDocumentBuilder()
					.newDocument();
		} catch (ParserConfigurationException pce) {
			throw new IOException(pce);
		}

		Element rootEl = xml.createElement("cockatrice_deck");
		rootEl.setAttribute("version", "1");

		Element deckNameEl = xml.createElement("deckname");
		deckNameEl.setTextContent(deck.name());
		rootEl.appendChild(deckNameEl);

		Element commentsEl = xml.createElement("comments");
		StringBuilder comments = new StringBuilder();
		if (!deck.author().isEmpty()) comments.append("Author: ").append(deck.author());
		if (!deck.author().isEmpty() && !deck.description().isEmpty()) comments.append('\n').append('\n');
		if (!deck.description().isEmpty()) comments.append(deck.description());
		commentsEl.setTextContent(comments.toString());
		rootEl.appendChild(commentsEl);

		for (Map.Entry<Zone, ? extends List<? extends Card.Printing>> zone : deck.cards().entrySet()) {
			if (zone.getValue().isEmpty()) continue;

			Element zoneEl = xml.createElement("zone");
			zoneEl.setAttribute("name", COCKATRICE_ZONE_MAP_REVERSE.get(zone.getKey()));

			Map<Card, Element> cardsElMap = new HashMap<>();
			for (Card.Printing pr : zone.getValue()) {
				Card c = pr.card();
				Element cardEl = cardsElMap.computeIfAbsent(c, x -> {
					Element el = xml.createElement("card");
					el.setAttribute("number", "0");
					el.setAttribute("name", x.front() != null ? x.front().name() : c.name()); // TODO: Cockatrice uses some weird naming conventions.
					zoneEl.appendChild(el);
					return el;
				});

				cardEl.setAttribute("number", Integer.toString(Integer.parseInt(cardEl.getAttribute("number")) + 1));
			}

			rootEl.appendChild(zoneEl);
		}

		rootEl.appendChild(xml.createTextNode("\n"));
		xml.appendChild(rootEl);

		try (OutputStream out = Files.newOutputStream(to)) {
			Transformer xform = TransformerFactory.newInstance().newTransformer();
			xform.setOutputProperty(OutputKeys.ENCODING, StandardCharsets.UTF_8.name());
			xform.transform(new DOMSource(xml), new StreamResult(out));
		} catch (TransformerException te) {
			throw new IOException(te);
		}
	}

	@Override
	public EnumSet<Feature> supportedFeatures() {
		return EnumSet.of(Feature.DeckName, Feature.Description, Feature.Author);
	}
}
