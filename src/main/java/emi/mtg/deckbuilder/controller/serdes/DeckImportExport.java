package emi.mtg.deckbuilder.controller.serdes;

import emi.mtg.deckbuilder.model.DeckList;

import java.io.File;
import java.io.IOException;
import java.util.EnumSet;

public interface DeckImportExport {
	String extension();

	DeckList importDeck(File from) throws IOException;

	void exportDeck(DeckList deck, File to) throws IOException;

	EnumSet<Features> supportedFeatures();
}
