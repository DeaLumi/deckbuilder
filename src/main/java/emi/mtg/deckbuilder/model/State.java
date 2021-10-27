package emi.mtg.deckbuilder.model;

import emi.mtg.deckbuilder.controller.Serialization;
import emi.mtg.deckbuilder.view.MainApplication;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;

public class State {
	private static final Path PATH = MainApplication.JAR_DIR.resolve("state.json");
	private static State instance = null;

	public static synchronized State instantiate() throws IOException {
		if (instance == null) {
			if (Files.exists(PATH)) {
				Reader reader = Files.newBufferedReader(PATH);
				instance = Serialization.GSON.fromJson(reader, State.class);
				reader.close();
			} else {
				instance = new State();
			}
		} else {
			throw new IllegalStateException("State has already been initialized!");
		}

		return instance;
	}

	public static synchronized State get() {
		if (instance == null) {
			throw new IllegalStateException("State hasn't been loaded yet!");
		}

		return instance;
	}

	public static void save() throws IOException {
		Writer writer = Files.newBufferedWriter(PATH);
		Serialization.GSON.toJson(get(), writer);
		writer.close();
	}

	public String lastBuildTime = "";

	public Path lastDeckDirectory = null;
	public ObservableList<Path> recentDecks = FXCollections.observableArrayList();

	public static int MRU_LIMIT = 5;

	public void addRecentDeck(Path path) {
		recentDecks.remove(path);
		recentDecks.add(0, path);

		if (recentDecks.size() > MRU_LIMIT) {
			recentDecks.remove(MRU_LIMIT, recentDecks.size());
		}
	}
}
