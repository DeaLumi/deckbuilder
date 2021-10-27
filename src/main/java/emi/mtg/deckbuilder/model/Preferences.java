package emi.mtg.deckbuilder.model;

import emi.lib.mtg.Card;
import emi.lib.mtg.game.Format;
import emi.lib.mtg.game.Zone;
import emi.mtg.deckbuilder.controller.Serialization;
import emi.mtg.deckbuilder.view.MainApplication;
import emi.mtg.deckbuilder.view.components.CardView;
import emi.mtg.deckbuilder.view.groupings.Rarity;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Stream;

public class Preferences {
	private static final Path PATH = MainApplication.JAR_DIR.resolve("preferences.json");
	private static Preferences instance = null;

	public static synchronized Preferences instantiate() throws IOException {
		if (instance == null) {
			if (Files.exists(PATH)) {
				Reader reader = Files.newBufferedReader(PATH);
				instance = Serialization.GSON.fromJson(reader, Preferences.class);
				reader.close();
			} else {
				instance = new Preferences();
			}
		} else {
			throw new IllegalStateException("Preferences have already been initialized!");
		}

		return instance;
	}

	public static synchronized Preferences get() {
		if (instance == null) {
			throw new IllegalStateException("Preferences haven't been loaded yet!");
		}

		return instance;
	}

	public static void save() throws IOException {
		Writer writer = Files.newBufferedWriter(PATH);
		Serialization.GSON.toJson(get(), writer);
		writer.close();
	}

	public Format defaultFormat = Format.Standard;
	public URI updateUri = URI.create("http://emi.sly.io/deckbuilder-nodata.zip");
	public final Path dataPath = MainApplication.JAR_DIR.resolve("data/").toAbsolutePath();
	public final Path imagesPath = MainApplication.JAR_DIR.resolve("images/").toAbsolutePath();

	public boolean autoUpdateData = true;
	public boolean autoUpdateProgram = true;

	public boolean theFutureIsNow = true;

	public String authorName = "";

	public CardView.Grouping collectionGrouping = Rarity.INSTANCE;
	public List<CardView.ActiveSorting> collectionSorting = CardView.DEFAULT_COLLECTION_SORTING;
	public Map<Zone, CardView.Grouping> zoneGroupings = new HashMap<>();

	public boolean collapseDuplicates = true;

	// N.B. Preferences get loaded *before* card data, so we can't reference Card.Printings here.
	public HashMap<String, UUID> preferredPrintings = new HashMap<>();

	public enum PreferAge {
		Any,
		Newest,
		Oldest
	}

	public enum PreferVariation {
		Any,
		Primary,
		Variant
	}

	public PreferAge preferAge = PreferAge.Any;
	public PreferVariation preferVariation = PreferVariation.Any;
	public boolean preferNotPromo = true;
	public boolean preferPhysical = true;

	public Card.Printing preferredPrinting(Card card) {
		Card.Printing preferred = card.printing(preferredPrintings.get(card.fullName()));

		if (preferred != null) return preferred;

		Stream<? extends Card.Printing> stream = card.printings().stream();

		if (preferNotPromo) stream = stream.filter(pr -> !pr.promo());
		if (preferPhysical) stream = stream.filter(pr -> !pr.set().digital());

		if (preferAge != PreferAge.Any) stream = stream.sorted(preferAge == PreferAge.Newest ?
				(a1, a2) -> a2.releaseDate().compareTo(a1.releaseDate()) :
				(a1, a2) -> a1.releaseDate().compareTo(a2.releaseDate()));
		if (preferVariation != PreferVariation.Any) stream = stream.sorted(preferVariation == PreferVariation.Primary ?
				(a1, a2) -> a1.variation() - a2.variation() :
				(a1, a2) -> a2.variation() - a1.variation());

		return stream.findFirst().orElse(null);
	}

	public Card.Printing anyPrinting(Card card) {
		Card.Printing preferred = preferredPrinting(card);
		if (preferred == null) preferred = card.printings().iterator().next();

		return preferred;
	}

	public enum Theme {
		Light (javafx.scene.paint.Color.gray(0.925)),
		Dark (javafx.scene.paint.Color.gray(0.15));

		public final javafx.scene.paint.Color base;

		Theme(javafx.scene.paint.Color base) {
			this.base = base;
		}

		public String baseHex() {
			return String.format("#%02x%02x%02x", (int) (base.getRed() * 255.0), (int) (base.getBlue() * 255.0), (int) (base.getBlue() * 255.0));
		}
	}

	public Theme theme = Theme.Dark;
}
