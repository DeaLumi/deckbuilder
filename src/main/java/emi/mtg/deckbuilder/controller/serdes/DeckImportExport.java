package emi.mtg.deckbuilder.controller.serdes;

import emi.mtg.deckbuilder.model.DeckList;
import emi.mtg.deckbuilder.util.PluginUtils;
import emi.mtg.deckbuilder.view.MainApplication;
import javafx.scene.input.Clipboard;
import javafx.scene.input.ClipboardContent;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;

public interface DeckImportExport {
	enum Feature {
		DeckName("Deck Name"),
		Author("Author"),
		Description("Description"),
		Format("Intended deck format"),
		CardArt("Specific card printings"),
		OtherZones("Zones (beyond library & sideboard)"),
		Tags("Deck-specific card tags");

		private final String description;

		Feature(String description) {
			this.description = description;
		}

		@Override
		public String toString() {
			return description;
		}
	}

	List<String> importExtensions();

	List<String> exportExtensions();

	DeckList importDeck(Path from) throws IOException;

	void exportDeck(DeckList deck, Path to) throws IOException;

	EnumSet<Feature> supportedFeatures();

	interface Monotype extends DeckImportExport {
		String extension();

		default List<String> importExtensions() {
			return Collections.singletonList(extension());
		}

		default List<String> exportExtensions() {
			return Collections.singletonList(extension());
		}
	}

	interface CopyPaste extends DeckImportExport {
		DeckList importDeck(Clipboard from) throws IOException;

		void exportDeck(DeckList deck, ClipboardContent clipboard) throws IOException;
	}

	interface Textual extends DeckImportExport, CopyPaste {
		DeckList importDeck(Reader from) throws IOException;
		void exportDeck(DeckList deck, Writer to) throws IOException;

		default DeckList importDeck(Path from) throws IOException {
			try (Reader source = Files.newBufferedReader(from)) {
				return importDeck(source);
			}
		}

		default void exportDeck(DeckList deck, Path to) throws IOException {
			try (Writer sink = Files.newBufferedWriter(to, StandardCharsets.UTF_8)) {
				exportDeck(deck, sink);
			}
		}

		default DeckList deserializeDeck(String from) throws IOException {
			try (Reader source = new StringReader(from)) {
				DeckList deck = importDeck(source);
				if (!supportedFeatures().contains(Feature.DeckName)) deck.nameProperty().setValue("Imported Deck");
				return deck;
			}
		}

		default String serializeDeck(DeckList deck) throws IOException {
			try (Writer sink = new StringWriter()) {
				exportDeck(deck, sink);
				sink.flush();
				return sink.toString();
			}
		}

		default DeckList importDeck(Clipboard from) throws IOException {
			if (!from.hasContent(javafx.scene.input.DataFormat.PLAIN_TEXT)) throw new IOException("Clipboard does not contain a deck!");
			return deserializeDeck((String) from.getContent(javafx.scene.input.DataFormat.PLAIN_TEXT));
		}

		default void exportDeck(DeckList deck, ClipboardContent to) throws IOException {
			to.put(javafx.scene.input.DataFormat.PLAIN_TEXT, serializeDeck(deck));
		}
	}

	static void checkLinkage(DeckImportExport serdes) {
		MainApplication.LOG.log("Checking linkage for %s: Input format = %s, output format = %s", serdes, serdes.importExtensions(), serdes.exportExtensions());
	}

	List<DeckImportExport> DECK_FORMAT_PROVIDERS = PluginUtils.providers(DeckImportExport.class, DeckImportExport::checkLinkage);

	List<DeckImportExport.CopyPaste> COPYPASTE_PROVIDERS = Collections.unmodifiableList(DECK_FORMAT_PROVIDERS.stream()
			.filter(s -> s instanceof DeckImportExport.CopyPaste)
			.map(s -> (DeckImportExport.CopyPaste) s)
			.collect(Collectors.toList()));
}
