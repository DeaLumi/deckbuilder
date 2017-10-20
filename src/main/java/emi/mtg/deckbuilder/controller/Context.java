package emi.mtg.deckbuilder.controller;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import emi.lib.Service;
import emi.lib.mtg.Card;
import emi.lib.mtg.DataSource;
import emi.lib.mtg.game.Format;
import emi.lib.mtg.scryfall.ScryfallDataSource;
import emi.mtg.deckbuilder.model.DeckList;
import emi.mtg.deckbuilder.model.EmptyDataSource;
import emi.mtg.deckbuilder.model.Preferences;
import emi.mtg.deckbuilder.view.Images;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Collections;
import java.util.Map;
import java.util.stream.Collectors;

public class Context {
	public static final Map<String, Format> FORMATS = Service.Loader.load(Format.class).stream()
			.collect(Collectors.toMap(s -> s.string("name"), s -> s.uncheckedInstance()));

	private static final Path PREFERENCES = Paths.get("preferences.json");
	private static final Path TAGS = Paths.get("tags.json");

	public final Gson gson;

	public final DataSource data;
	public final Images images;
	public final Tags tags;

	public DeckList deck;

	public final Preferences preferences;

	public Context() throws IOException {
		DataSource data;
		try {
			data = new ScryfallDataSource();
		} catch (IOException ioe) {
			data = new EmptyDataSource();
		}
		this.data = data;

		this.gson = new GsonBuilder()
				.setPrettyPrinting()
				.registerTypeAdapter(Card.Printing.class, TypeAdapters.createCardPrintingAdapter(this.data))
				.registerTypeAdapter(Format.class, TypeAdapters.createFormatAdapter(FORMATS))
				.registerTypeAdapter(Path.class, TypeAdapters.createPathTypeAdapter())
				.registerTypeAdapterFactory(TypeAdapters.createPropertyTypeAdapterFactory())
				.registerTypeAdapterFactory(TypeAdapters.createObservableListTypeAdapterFactory())
				.create();

		if (Files.exists(PREFERENCES)) {
			Reader reader = Files.newBufferedReader(PREFERENCES);
			this.preferences = gson.fromJson(reader, Preferences.class);
			reader.close();
		} else {
			this.preferences = new Preferences();
		}

		this.images = new Images();
		this.tags = new Tags(this);
		this.deck = new DeckList("", "", preferences.defaultFormat, "", Collections.emptyMap());

		loadTags();
	}

	public void savePreferences() throws IOException {
		Writer writer = Files.newBufferedWriter(PREFERENCES);
		gson.toJson(this.preferences, writer);
		writer.close();
	}

	public void loadTags() throws IOException {
		try {
			this.tags.load(TAGS);
		} catch (FileNotFoundException fnfe) {
			// do nothing
		}
	}

	public void saveTags() throws IOException {
		this.tags.save(TAGS);
	}
}
