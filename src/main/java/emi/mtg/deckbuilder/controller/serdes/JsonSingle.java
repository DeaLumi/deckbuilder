package emi.mtg.deckbuilder.controller.serdes;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import emi.lib.Service;
import emi.lib.mtg.Card;
import emi.lib.mtg.DataSource;
import emi.lib.mtg.game.Format;
import emi.lib.mtg.game.Zone;
import emi.mtg.deckbuilder.controller.DeckImportExport;
import emi.mtg.deckbuilder.controller.TypeAdapters;
import emi.mtg.deckbuilder.model.CardInstance;
import emi.mtg.deckbuilder.model.DeckList;

import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.util.*;

@Service.Provider(DeckImportExport.class)
@Service.Property.String(name="name", value="Json (Old)")
@Service.Property.String(name="extension", value="json")
public class JsonSingle implements DeckImportExport {
	private static class OldDeckList {
		String name = "";
		String description = "";
		Format format = null;
		String author = "";

		Map<Zone, List<CardInstance>> cards = Collections.emptyMap();
		List<CardInstance> sideboard = Collections.emptyList();
	}

	private final DataSource cs;
	private final Gson gson;
	private final Map<String, Format> formats;

	public JsonSingle(DataSource cs, Map<String, Format> formats) {
		this.cs = cs;

		this.formats = formats;

		this.gson = new GsonBuilder()
				.registerTypeAdapter(Card.Printing.class, TypeAdapters.createCardPrintingAdapter(cs))
				.registerTypeAdapter(Format.class, TypeAdapters.createFormatAdapter(formats))
				.setPrettyPrinting()
				.create();
	}

	@Override
	public DeckList importDeck(File from) throws IOException {
		FileReader reader = new FileReader(from);

		OldDeckList out = gson.fromJson(reader, OldDeckList.class);
		out.cards.put(Zone.Sideboard, out.sideboard);

		reader.close();

		DeckList newOut = new DeckList(out.name, out.author, out.format, out.description, out.cards);

		return newOut;
	}

	@Override
	public void exportDeck(DeckList deck, File to) throws IOException {
		throw new UnsupportedOperationException("You can't export these versions of decks any more -- please just save the file normally!");
	}

	@Override
	public Set<Features> unsupportedFeatures() {
		return EnumSet.of(Features.Variants);
	}
}
