package emi.mtg.deckbuilder.controller;

import com.google.gson.Gson;
import emi.lib.mtg.Card;
import emi.lib.mtg.DataSource;
import emi.mtg.deckbuilder.model.Preferences;
import emi.mtg.deckbuilder.view.Images;
import emi.mtg.deckbuilder.view.MainApplication;

import java.io.IOException;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.util.concurrent.ForkJoinPool;
import java.util.function.DoubleConsumer;

public class Context {
	private static final Path TAGS = MainApplication.JAR_DIR.resolve("tags.json");

	private static Context instance;

	public static void instantiate(DataSource data) throws IOException {
		synchronized (Context.class) {
			if (instance != null) {
				throw new IllegalStateException("Attempt to reinitialize context!");
			}

			instance = new Context(data);
		}
	}

	public static Context get() {
		synchronized (Context.class) {
			if (instance == null) {
				throw new IllegalStateException("Context hasn't been initialized!");
			}

			return instance;
		}
	}

	public static boolean instantiated() {
		return instance != null;
	}

	public final Gson gson;

	public final DataSource data;
	public final Images images;
	public final Tags tags;

	public Context(DataSource data) throws IOException {
		this.data = data;

		this.gson = Serialization.GSON.newBuilder()
				.registerTypeAdapter(Card.class, Serialization.createCardAdapter())
				.registerTypeAdapterFactory(Serialization.createCardPrintingAdapterFactory())
				.create();

		this.images = new Images(Preferences.get().imagesPath);
		this.tags = new Tags(this);
	}

	public boolean loadData(DoubleConsumer progress) throws IOException {
		if (this.data.loadData(Preferences.get().dataPath, progress)) {
			loadTags();
			return true;
		} else {
			return false;
		}
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
}
