package emi.mtg.deckbuilder.controller.serdes;

import emi.mtg.deckbuilder.model.DeckList;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Collections;
import java.util.EnumSet;
import java.util.List;

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
}
