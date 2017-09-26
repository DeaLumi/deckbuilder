package emi.mtg.deckbuilder.controller;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import emi.lib.Service;
import emi.lib.mtg.Card;
import emi.lib.mtg.DataSource;
import emi.lib.mtg.game.Format;
import emi.lib.mtg.scryfall.ScryfallDataSource;
import emi.mtg.deckbuilder.model.DeckList;
import emi.mtg.deckbuilder.model.Preferences;
import emi.mtg.deckbuilder.view.Images;

import java.io.*;
import java.nio.file.Path;
import java.util.Map;
import java.util.stream.Collectors;

public class Context {
	public static final Map<String, Format> FORMATS = Service.Loader.load(Format.class).stream()
			.collect(Collectors.toMap(s -> s.string("name"), s -> s.uncheckedInstance()));

	private static final File PREFERENCES = new File("preferences.json");
	private static final File TAGS = new File("tags.json");

	public final Gson gson;

	public final DataSource data;
	public final Images images;
	public final Tags tags;

	public DeckList deck;

	public final Preferences preferences;

	public Context() throws IOException {
		this.data = new ScryfallDataSource();

		this.gson = new GsonBuilder()
				.setPrettyPrinting()
				.registerTypeAdapter(Card.Printing.class, TypeAdapters.createCardPrintingAdapter(this.data))
				.registerTypeAdapter(Format.class, TypeAdapters.createFormatAdapter(FORMATS))
				.registerTypeAdapter(Path.class, TypeAdapters.createPathTypeAdapter())
				.create();

		if (PREFERENCES.exists()) {
			FileInputStream fis = new FileInputStream(PREFERENCES);
			InputStreamReader reader = new InputStreamReader(fis);
			this.preferences = gson.fromJson(reader, Preferences.class);
			reader.close();
		} else {
			this.preferences = new Preferences();
		}

		this.images = new Images();
		this.tags = new Tags(this);
		this.deck = new DeckList();
		this.deck.formatProperty().setValue(preferences.defaultFormat);

		try {
			this.tags.load(TAGS);
		} catch (FileNotFoundException fnfe) {
			// do nothing
		}
	}

	public void savePreferences() throws IOException {
		FileOutputStream fos = new FileOutputStream(new File("preferences.json"));
		OutputStreamWriter writer = new OutputStreamWriter(fos);
		gson.toJson(this.preferences, writer);
		writer.close();
	}
}
