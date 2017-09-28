package emi.mtg.deckbuilder.controller.serdes;

import emi.mtg.deckbuilder.controller.Context;
import emi.mtg.deckbuilder.controller.DeckImportExport;
import emi.mtg.deckbuilder.model.DeckList;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.util.EnumSet;
import java.util.Set;

// N.B. MainWindow uses this class directly, so we don't actually provide the DeckImportExport service.
public class Json implements DeckImportExport {
	private final Context context;

	public Json(Context context) {
		this.context = context;
	}

	@Override
	public DeckList importDeck(File from) throws IOException {
		FileReader reader = new FileReader(from);

		DeckList out = context.gson.fromJson(reader, DeckList.class);

		reader.close();

		return out;
	}

	@Override
	public void exportDeck(DeckList deck, File to) throws IOException {
		FileWriter writer = new FileWriter(to);

		context.gson.toJson(deck, DeckList.class, writer);

		writer.close();
	}

	@Override
	public Set<Features> unsupportedFeatures() {
		return EnumSet.noneOf(Features.class);
	}
}
