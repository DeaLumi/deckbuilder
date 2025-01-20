package emi.mtg.deckbuilder.controller;

import com.google.gson.Gson;
import emi.lib.mtg.Card;
import emi.lib.mtg.DataSource;
import emi.mtg.deckbuilder.model.Preferences;
import emi.mtg.deckbuilder.view.Images;

import java.io.IOException;
import java.util.function.DoubleConsumer;

public class Context {
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
				.registerTypeAdapter(Card.Print.Reference.class, Serialization.createCardPrintReferenceAdapter())
				.registerTypeAdapterFactory(Serialization.createCardPrintAdapterFactory())
				.create();

		this.images = new Images(Preferences.get().imagesPath);
		this.tags = new Tags();
	}

	public boolean loadData(DoubleConsumer progress) throws IOException {
		if (this.data.loadData(Preferences.get().dataPath, progress)) {
			loadTags(progress);
			return true;
		} else {
			return false;
		}
	}

	public void loadTags(DoubleConsumer progress) throws IOException {
		this.tags.load(this.data, Preferences.get().dataPath, progress);
	}

	public void saveTags(DoubleConsumer progress) throws IOException {
		this.tags.save(Preferences.get().dataPath, progress);
	}
}
