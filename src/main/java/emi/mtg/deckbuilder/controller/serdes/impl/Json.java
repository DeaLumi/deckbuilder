package emi.mtg.deckbuilder.controller.serdes.impl;

import emi.mtg.deckbuilder.controller.Context;
import emi.mtg.deckbuilder.controller.serdes.DeckImportExport;
import emi.mtg.deckbuilder.model.DeckList;
import javafx.scene.input.DataFormat;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.EnumSet;

public class Json implements DeckImportExport.Textual, DeckImportExport.Monotype {
	private static final DataFormat JSON = DataFormat.singleton(
			"Deck Builder JSON",
			"json",
			"application/json",
			"Deck Builder deck list file, a variant of JSON.",
			javafx.scene.input.DataFormat.PLAIN_TEXT,
			String.class
	);

	@Override
	public DataFormat format() {
		return JSON;
	}

	@Override
	public String toString() {
		return "Deck Builder JSON";
	}

	@Override
	public DeckList importDeck(Reader from) throws IOException {
		return Context.get().gson.getAdapter(DeckList.class).fromJson(from);
	}

	@Override
	public void exportDeck(DeckList deck, Writer to) throws IOException {
		Context.get().gson.toJson(deck, DeckList.class, to);
	}

	@Override
	public EnumSet<Feature> supportedFeatures() {
		return EnumSet.allOf(Feature.class);
	}
}
