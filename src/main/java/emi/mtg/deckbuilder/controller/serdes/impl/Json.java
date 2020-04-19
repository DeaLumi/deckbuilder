package emi.mtg.deckbuilder.controller.serdes.impl;

import emi.mtg.deckbuilder.controller.Context;
import emi.mtg.deckbuilder.controller.serdes.DeckImportExport;
import emi.mtg.deckbuilder.controller.serdes.Features;
import emi.mtg.deckbuilder.model.DeckList;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.util.EnumSet;
import java.util.Set;

public class Json implements DeckImportExport {
	@Override
	public String extension() {
		return "json";
	}

	@Override
	public String toString() {
		return "Deck Builder JSON";
	}

	@Override
	public DeckList importDeck(File from) throws IOException {
		FileReader reader = new FileReader(from);

		DeckList out = Context.get().gson.getAdapter(DeckList.class).fromJson(reader);
		reader.close();

		return out;
	}

	@Override
	public void exportDeck(DeckList deck, File to) throws IOException {
		FileWriter writer = new FileWriter(to);

		Context.get().gson.toJson(deck, DeckList.class, writer);

		writer.close();
	}

	@Override
	public Set<Features> unsupportedFeatures() {
		return EnumSet.noneOf(Features.class);
	}
}
