package emi.mtg.deckbuilder.controller.serdes;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import emi.lib.Service;
import emi.lib.mtg.card.Card;
import emi.lib.mtg.data.CardSource;
import emi.mtg.deckbuilder.controller.DeckImportExport;
import emi.mtg.deckbuilder.model.CardInstance;
import emi.mtg.deckbuilder.model.DeckList;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;

@Service.Provider(DeckImportExport.class)
@Service.Property.String(name="name", value="JSON")
@Service.Property.String(name="extension", value="json")
public class Json implements DeckImportExport {
	private final CardSource cs;
	private final Gson gson;

	public Json(CardSource cs) {
		this.cs = cs;

		this.gson = new GsonBuilder()
				.registerTypeAdapter(Card.class, CardInstance.createCardAdapter(cs))
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
}
