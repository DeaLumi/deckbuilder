package emi.mtg.deckbuilder.controller.serdes;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import emi.lib.mtg.Card;
import emi.lib.mtg.DataSource;
import emi.lib.mtg.game.Format;
import emi.mtg.deckbuilder.controller.DeckImportExport;
import emi.mtg.deckbuilder.controller.TypeAdapters;
import emi.mtg.deckbuilder.model.DeckList;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

// N.B. MainWindow uses this class directly, so we don't actually provide the DeckImportExport service.
public class Json implements DeckImportExport {
	private final DataSource cs;
	private final Gson gson;
	private final Map<String, Format> formats;

	public Json(DataSource cs, Map<String, Format> formats) {
		this.cs = cs;

		this.formats = formats;

		this.gson = new GsonBuilder()
				.registerTypeAdapter(Card.Printing.class, TypeAdapters.createCardPrintingAdapter(cs))
				.registerTypeAdapter(Format.class, TypeAdapters.createFormatAdapter(formats))
				.registerTypeAdapterFactory(TypeAdapters.createPropertyTypeAdapterFactory())
				.registerTypeAdapterFactory(TypeAdapters.createObservableListTypeAdapterFactory())
				.setPrettyPrinting()
				.create();
	}

	@Override
	public DeckList importDeck(File from) throws IOException {
		FileReader reader = new FileReader(from);

		DeckList out = gson.fromJson(reader, DeckList.class);

		reader.close();

		return out;
	}

	@Override
	public void exportDeck(DeckList deck, File to) throws IOException {
		FileWriter writer = new FileWriter(to);

		gson.toJson(deck, DeckList.class, writer);

		writer.close();
	}

	@Override
	public Set<Features> unsupportedFeatures() {
		return EnumSet.noneOf(Features.class);
	}
}
