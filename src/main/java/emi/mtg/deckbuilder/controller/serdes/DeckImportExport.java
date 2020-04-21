package emi.mtg.deckbuilder.controller.serdes;

import emi.mtg.deckbuilder.model.DeckList;

import java.io.File;
import java.io.IOException;
import java.util.EnumSet;

public interface DeckImportExport {
	enum Feature {
		DeckName("Deck Name"),
		Author("Author"),
		Description("Description"),
		Format("Intended deck format"),
		CardArt("Specific card printings"),
		OtherZones("Zones (beyond library & sideboard)"),
		Export("Exporting"),
		Import("Importing");

		private final String description;

		Feature(String description) {
			this.description = description;
		}

		@Override
		public String toString() {
			return description;
		}
	}

	String extension();

	DeckList importDeck(File from) throws IOException;

	void exportDeck(DeckList deck, File to) throws IOException;

	EnumSet<Feature> supportedFeatures();
}
