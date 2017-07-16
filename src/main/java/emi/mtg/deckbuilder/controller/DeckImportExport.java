package emi.mtg.deckbuilder.controller;

import emi.lib.Service;
import emi.lib.mtg.data.CardSource;
import emi.mtg.deckbuilder.model.DeckList;

import java.io.File;
import java.io.IOException;
import java.util.Set;

@Service(CardSource.class)
@Service.Property.String(name="name")
@Service.Property.String(name="extension")
public interface DeckImportExport {
	enum Features {
		DeckName ("Deck Name"),
		Author ("Author"),
		Description ("Description"),
		Format ("Intended Format"),
		CardArt ("Unique Card Art");

		private final String description;

		Features(String description) {
			this.description = description;
		}

		@Override
		public String toString() {
			return description;
		}
	}

	DeckList importDeck(File from) throws IOException;

	void exportDeck(DeckList deck, File to) throws IOException;

	Set<Features> unsupportedFeatures();
}
