package emi.mtg.deckbuilder.controller.serdes.impl;

import emi.mtg.deckbuilder.controller.Context;
import emi.mtg.deckbuilder.controller.serdes.DeckImportExport;
import emi.mtg.deckbuilder.model.DeckList;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.EnumSet;

public class Json implements DeckImportExport.Monotype {
	@Override
	public String extension() {
		return "json";
	}

	@Override
	public String toString() {
		return "Deck Builder JSON";
	}

	@Override
	public DeckList importDeck(Path from) throws IOException {
		BufferedReader reader = Files.newBufferedReader(from);

		DeckList out = Context.get().gson.getAdapter(DeckList.class).fromJson(reader);
		reader.close();

		return out;
	}

	@Override
	public void exportDeck(DeckList deck, Path to) throws IOException {
		BufferedWriter writer = Files.newBufferedWriter(to);

		Context.get().gson.toJson(deck, DeckList.class, writer);

		writer.close();
	}

	@Override
	public EnumSet<Feature> supportedFeatures() {
		return EnumSet.allOf(Feature.class);
	}
}
