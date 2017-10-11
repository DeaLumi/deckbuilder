package emi.mtg.deckbuilder.controller.serdes;

import emi.mtg.deckbuilder.controller.Context;
import emi.mtg.deckbuilder.controller.DeckImportExport;
import emi.mtg.deckbuilder.model.DeckList;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.lang.reflect.Field;
import java.util.EnumSet;
import java.util.Set;

// N.B. MainWindow uses this class directly, so we don't actually provide the DeckImportExport service.
public class Json implements DeckImportExport {
	private final Context context;

	public Json(Context context) {
		this.context = context;
	}

	/**
	 * This is a nasty hack to update the parent pointer of all variants
	 * in a newly-loaded decklist, via reflection. Note that this MUST
	 * happen before the decklist is made available to any other thread!
	 * @param deck The freshly-deserialized decklist.
	 */
	private static void updateVariantsParent(DeckList deck) {
		try {
			Field f = DeckList.Variant.class.getDeclaredField("this$0");
			f.setAccessible(true);

			for (DeckList.Variant var : deck.variants()) {
				f.set(var, deck);
			}
		} catch (NoSuchFieldException | IllegalAccessException e) {
			e.printStackTrace();
		}
	}

	@Override
	public DeckList importDeck(File from) throws IOException {
		FileReader reader = new FileReader(from);

		DeckList out = context.gson.fromJson(reader, DeckList.class);
		updateVariantsParent(out);

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
