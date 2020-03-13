package emi.mtg.deckbuilder.controller;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import emi.lib.Service;
import emi.lib.mtg.Card;
import emi.lib.mtg.DataSource;
import emi.lib.mtg.characteristic.Supertype;
import emi.lib.mtg.game.Format;
import emi.lib.mtg.game.impl.formats.AbstractFormat;
import emi.lib.mtg.scryfall.ScryfallDataSource;
import emi.mtg.deckbuilder.model.*;
import emi.mtg.deckbuilder.util.Profiling;
import emi.mtg.deckbuilder.view.Images;
import emi.mtg.deckbuilder.view.components.CardView;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Collection;
import java.util.Collections;
import java.util.EnumSet;
import java.util.Map;
import java.util.concurrent.ForkJoinPool;
import java.util.stream.Collectors;

public class Context {
	public static final Map<String, Format> FORMATS = Service.Loader.load(Format.class).stream()
			.collect(Collectors.toMap(s -> s.string("name"), s -> s.uncheckedInstance()));

	private static final Path PREFERENCES = Paths.get("preferences.json");
	private static final Path STATE = Paths.get("state.json");
	private static final Path TAGS = Paths.get("tags.json");

	public final Gson gson;

	public final DataSource data;
	public final Images images;
	public final Tags tags;

	public DeckList deck;

	public final Preferences preferences;
	public final State state;

	public Context() throws IOException {
		Profiling.start();
		DataSource data;
		try {
			data = new ScryfallDataSource();
		} catch (IOException ioe) {
			data = new EmptyDataSource();
		}
		this.data = data;
		Profiling.stop("DataSource");

		Profiling.start();
		this.gson = new GsonBuilder()
				.setPrettyPrinting()
				.registerTypeAdapter(Card.Printing.class, TypeAdapters.createCardPrintingAdapter(this.data))
				.registerTypeAdapter(Format.class, TypeAdapters.createFormatAdapter(FORMATS))
				.registerTypeAdapter(Path.class, TypeAdapters.createPathTypeAdapter())
				.registerTypeAdapterFactory(TypeAdapters.createPropertyTypeAdapterFactory())
				.registerTypeAdapterFactory(TypeAdapters.createObservableListTypeAdapterFactory())
				.create();
		Profiling.stop("gson");

		Profiling.start();
		if (Files.exists(PREFERENCES)) {
			Reader reader = Files.newBufferedReader(PREFERENCES);
			this.preferences = gson.fromJson(reader, Preferences.class);
			reader.close();
		} else {
			this.preferences = new Preferences();
		}
		Profiling.stop("preferences");

		Profiling.start();
		if (Files.exists(STATE)) {
			Reader reader = Files.newBufferedReader(STATE);
			this.state = gson.fromJson(reader, State.class);
			reader.close();
		} else {
			this.state = new State();
		}
		Profiling.stop("state");

		Profiling.start();
		this.images = new Images();
		Profiling.stop("new Images()");
		Profiling.start();
		this.tags = new Tags(this);
		Profiling.stop("new Tags()");
		Profiling.start();
		this.deck = new DeckList("", "", preferences.defaultFormat, "", Collections.emptyMap());
		Profiling.stop("new DeckList()");

		Profiling.start();
		loadTags();
		Profiling.stop("loadTags()");
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

	public EnumSet<CardView.CardState> flagCard(CardInstance ci, boolean countIsInfinite) {
		EnumSet<CardView.CardState> states = EnumSet.noneOf(CardView.CardState.class);

		if (deck != null && deck.format() != null) {
			if (!deck.format().cardIsLegal(ci.card())) {
				states.add(CardView.CardState.Flagged);
			}

			// TODO: libmtg should really have, like, card.quantityRule() or something.
			Card.Face front = ci.card().face(Card.Face.Kind.Front);
			if (front == null || (!front.type().supertypes().contains(Supertype.Basic) && !front.rules().contains("A deck can have any number of cards named"))) {
				if (deck.format() instanceof AbstractFormat) {
					AbstractFormat fmt = (AbstractFormat) deck.format();
					long zonedCount = deck.cards().values().stream()
							.flatMap(Collection::stream)
							.filter(inZone -> inZone.card().equals(ci.card()))
							.count();

					if (countIsInfinite) {
						if (zonedCount >= fmt.maxCardCopies()) {
							states.add(CardView.CardState.Full);
						}
					} else {
						if (zonedCount > fmt.maxCardCopies()) {
							states.add(CardView.CardState.Flagged);
						}
					}
				}
			}
		}

		return states;
	}

	public void saveAll() throws IOException {
		savePreferences();
		saveTags();
		saveState();
	}
}
