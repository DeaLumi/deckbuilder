package emi.mtg.deckbuilder.controller;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import emi.lib.mtg.Card;
import emi.lib.mtg.DataSource;
import emi.lib.mtg.game.Format;
import emi.lib.mtg.scryfall.ScryfallDataSource;
import emi.mtg.deckbuilder.model.EmptyDataSource;
import emi.mtg.deckbuilder.model.Preferences;
import emi.mtg.deckbuilder.model.State;
import emi.mtg.deckbuilder.view.Images;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.concurrent.ForkJoinPool;

public class Context {
	private static final Path PREFERENCES = Paths.get("preferences.json");
	private static final Path STATE = Paths.get("state.json");
	private static final Path TAGS = Paths.get("tags.json");

	private static Context instance;

	public static Context get() {
		synchronized (Context.class) {
			if (instance == null) {
				try {
					instance = new Context();
				} catch (IOException e) {
					throw new Error(e);
				}
			}
		}

		return instance;
	}

	public final Gson gson;

	public final DataSource data;
	public final Images images;
	public final Tags tags;

	public final Preferences preferences;
	public final State state;

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
				.registerTypeAdapter(Format.class, TypeAdapters.createFormatAdapter())
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

		if (Files.exists(STATE)) {
			Reader reader = Files.newBufferedReader(STATE);
			this.state = gson.fromJson(reader, State.class);
			reader.close();
		} else {
			this.state = new State();
		}

		this.images = new Images();
		this.tags = new Tags(this);

		loadTags();
	}

	public void savePreferences() throws IOException {
		Writer writer = Files.newBufferedWriter(PREFERENCES);
		gson.toJson(this.preferences, writer);
		writer.close();
	}

	public void loadTags() throws IOException {
		ForkJoinPool.commonPool().submit(() -> {
			try {
				this.tags.load(TAGS);
			} catch (NoSuchFileException fnfe) {
				// do nothing
			} catch (IOException e) {
				throw new RuntimeException(e); // TODO do this better
			}
		});
	}

	public void saveTags() throws IOException {
		this.tags.save(TAGS);
	}

	public void saveState() throws IOException {
		OutputStream fos = Files.newOutputStream(STATE);
		OutputStreamWriter writer = new OutputStreamWriter(fos);
		gson.toJson(this.state, writer);
		writer.close();
	}

	public void saveAll() throws IOException {
		savePreferences();
		saveTags();
		saveState();
	}
}
