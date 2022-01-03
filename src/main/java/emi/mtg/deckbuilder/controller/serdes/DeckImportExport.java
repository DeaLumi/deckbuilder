package emi.mtg.deckbuilder.controller.serdes;

import emi.mtg.deckbuilder.model.DeckList;
import emi.mtg.deckbuilder.util.PluginUtils;

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

	interface Textual extends DeckImportExport {
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
	}

	static void checkLinkage(DeckImportExport serdes) {
		serdes.importExtensions();
		serdes.exportExtensions();
	}

	List<DeckImportExport> DECK_FORMAT_PROVIDERS = PluginUtils.providers(DeckImportExport.class, DeckImportExport::checkLinkage);

	List<DeckImportExport.Textual> TEXTUAL_PROVIDERS = Collections.unmodifiableList(DECK_FORMAT_PROVIDERS.stream()
			.filter(s -> s instanceof DeckImportExport.Textual)
			.map(s -> (DeckImportExport.Textual) s)
			.collect(Collectors.toList()));
}
