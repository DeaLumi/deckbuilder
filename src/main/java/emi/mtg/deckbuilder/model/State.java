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
import java.time.Instant;

public class State {
	private static final Path PATH = MainApplication.JAR_DIR.resolve("state.json");
	private static State instance = null;

	private static final Instant MAIN_JAR_MODIFIED_DATE;

	static {
		Instant modifiedDate;
		try {
			modifiedDate = Files.getLastModifiedTime(MainApplication.JAR_PATH).toInstant();
		} catch (IOException ioe) {
			modifiedDate = Instant.now();
		}
		MAIN_JAR_MODIFIED_DATE = modifiedDate;
	}

	public static synchronized State instantiate() throws IOException {
		if (instance == null) {
			if (Files.exists(PATH)) {
				Reader reader = Files.newBufferedReader(PATH);
				instance = Serialization.GSON.fromJson(reader, State.class);
				reader.close();
			}

			if (instance == null) {
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

	public Instant buildTime = Instant.EPOCH;

	public Path lastLoadDirectory = null;
	public Path lastSaveDirectory = null;
	public ObservableList<Path> recentDecks = FXCollections.observableArrayList();

	public static int MRU_LIMIT = 5;

	public void addRecentDeck(Path path) {
		recentDecks.remove(path);
		recentDecks.add(0, path);

		if (recentDecks.size() > MRU_LIMIT) {
			recentDecks.remove(MRU_LIMIT, recentDecks.size());
		}
	}

	public boolean checkUpdated() {
		if (MAIN_JAR_MODIFIED_DATE.isAfter(buildTime)) {
			buildTime = MAIN_JAR_MODIFIED_DATE;
			return true;
		}

		return false;
	}
}
