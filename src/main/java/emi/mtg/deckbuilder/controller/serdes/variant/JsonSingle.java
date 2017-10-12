package emi.mtg.deckbuilder.controller.serdes.variant;

import emi.lib.Service;
import emi.lib.mtg.game.Format;
import emi.lib.mtg.game.Zone;
import emi.mtg.deckbuilder.controller.Context;
import emi.mtg.deckbuilder.controller.serdes.Features;
import emi.mtg.deckbuilder.controller.serdes.VariantImportExport;
import emi.mtg.deckbuilder.model.CardInstance;
import emi.mtg.deckbuilder.model.DeckList;

import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.util.*;

@Service.Provider(VariantImportExport.class)
@Service.Property.String(name="name", value="Json (Old)")
@Service.Property.String(name="extension", value="json")
public class JsonSingle implements VariantImportExport {
	private static class OldDeckList {
		String name = "";
		String description = "";
		Format format = null;
		String author = "";

		Map<Zone, List<CardInstance>> cards = Collections.emptyMap();
		List<CardInstance> sideboard = Collections.emptyList();
	}

	private final Context context;

	public JsonSingle(Context context) {
		this.context = context;
	}

	@Override
	public DeckList.Variant importVariant(DeckList decklist, File from) throws IOException {
		FileReader reader = new FileReader(from);

		OldDeckList out = context.gson.fromJson(reader, OldDeckList.class);
		out.cards.put(Zone.Sideboard, out.sideboard);

		reader.close();

		return decklist.new Variant(out.name, out.description, out.cards);
	}

	@Override
	public void exportVariant(DeckList.Variant variant, File to) throws IOException {
		throw new UnsupportedOperationException("You can't export these versions of decks any more -- please just save the file normally!");
	}

	@Override
	public Set<Features> unsupportedFeatures() {
		return EnumSet.of(Features.Variants, Features.Author);
	}
}
