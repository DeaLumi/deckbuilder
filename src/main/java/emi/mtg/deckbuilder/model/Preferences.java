package emi.mtg.deckbuilder.model;

import emi.lib.mtg.Card;
import emi.lib.mtg.game.Format;
import emi.lib.mtg.game.Zone;
import emi.mtg.deckbuilder.controller.Context;
import emi.mtg.deckbuilder.controller.Serialization;
import emi.mtg.deckbuilder.controller.serdes.DeckImportExport;
import emi.mtg.deckbuilder.controller.serdes.impl.TextFile;
import emi.mtg.deckbuilder.view.MainApplication;
import emi.mtg.deckbuilder.view.components.CardView;
import emi.mtg.deckbuilder.view.groupings.None;
import emi.mtg.deckbuilder.view.groupings.Rarity;
import emi.mtg.deckbuilder.view.search.omnifilter.Omnifilter;
import emi.mtg.deckbuilder.view.search.SearchProvider;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.property.SimpleObjectProperty;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.lang.ref.WeakReference;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.function.Consumer;
import java.util.stream.Stream;

public class Preferences {
	/**
	 * Static utilities
	 */

	private static final Path PATH = MainApplication.JAR_DIR.resolve("preferences.json");
	private static Preferences instance = null;
	private static final Set<WeakReference<Consumer<Preferences>>> LISTENERS = new HashSet<>();

	public static synchronized Preferences instantiate() throws IOException {
		if (instance == null) {
			if (Files.exists(PATH)) {
				Reader reader = Files.newBufferedReader(PATH);
				instance = Serialization.GSON.fromJson(reader, Preferences.class);
				reader.close();
			}

			if (instance == null) {
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

	public static void listen(Consumer<Preferences> listener) {
		LISTENERS.add(new WeakReference<>(listener));
	}

	public void changed() {
		Iterator<WeakReference<Consumer<Preferences>>> iter = LISTENERS.iterator();
		while (iter.hasNext()) {
			WeakReference<Consumer<Preferences>> ref = iter.next();
			final Consumer<Preferences> listener = ref.get();
			if (listener == null) {
				iter.remove();
				continue;
			}

			listener.accept(this);
		}
	}

	public static HashSet<UUID> deferredPreferredPrintings = new HashSet<>();

	/**
	 * Preference value types
	 */

	public static class PreferredPrinting {
		public final String setCode;
		public final String collectorNumber;

		public PreferredPrinting(String setCode, String collectorNumber) {
			this.setCode = setCode;
			this.collectorNumber = collectorNumber;
		}
	}

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

	public static class DefaultPrintings {
		public final List<String> preferredSets;
		public final Set<String> ignoredSets;

		protected transient Map<String, Integer> preferredMap;

		public DefaultPrintings() {
			this(new ArrayList<>(), new HashSet<>());
		}

		public DefaultPrintings(List<String> preferredSets, Set<String> ignoredSets) {
			this.preferredSets = preferredSets;
			this.ignoredSets = ignoredSets;
			this.preferredMap = null;
		}

		public int prefPriority(String setCode) {
			if (preferredMap == null) {
				preferredMap = new HashMap<>();
				for (int i = 0; i < preferredSets.size(); ++i) preferredMap.put(preferredSets.get(i).toLowerCase(), i);
			}

			return preferredMap.getOrDefault(setCode.toLowerCase(), Integer.MAX_VALUE);
		}
	}

	public enum Theme {
		Light (javafx.scene.paint.Color.gray(0.925)),
		Dark (javafx.scene.paint.Color.gray(0.15));

		public final javafx.scene.paint.Color base;

		Theme(javafx.scene.paint.Color base) {
			this.base = base;
		}

		public String baseHex() {
			return hex(base);
		}

		public String style() {
			return String.format("-fx-base: %s;", baseHex());
		}

		public static String hex(javafx.scene.paint.Color color) {
			return String.format("#%02x%02x%02x", (int) (color.getRed() * 255.0), (int) (color.getBlue() * 255.0), (int) (color.getBlue() * 255.0));
		}
	}

	public enum WindowBehavior {
		AlwaysAsk ("Always Ask"),
		ReplaceCurrent("Replace Current"),
		NewTab ("New Tab"),
		NewWindow ("New Window");

		public final String text;

		WindowBehavior(String text) {
			this.text = text;
		}

		@Override
		public String toString() {
			return text;
		}
	}

	/**
	 * Critical operation preferences
	 */

	public final Path dataPath = MainApplication.JAR_DIR.resolve("data/").toAbsolutePath();
	public final Path imagesPath = MainApplication.JAR_DIR.resolve("images/").toAbsolutePath();

	public URI updateUri = URI.create("https://cloudpost.app:8443/deckbuilder-nodata.zip");
	public boolean autoUpdateData = true;
	public boolean autoUpdateProgram = true;

	/**
	 * Overall user interface preferences
	 */

	public WindowBehavior windowBehavior = WindowBehavior.AlwaysAsk;
	public Theme theme = Theme.Dark;
	public DeckImportExport.Textual copyPasteFormat = TextFile.Arena.INSTANCE;
	public boolean showDebugOptions = false;

	/**
	 * Card pane preferences
	 */

	public SearchProvider searchProvider = SearchProvider.SEARCH_PROVIDERS.get(Omnifilter.NAME);
	public String defaultQuery = "";

	public CardView.Grouping collectionGrouping = Rarity.INSTANCE;
	public List<CardView.ActiveSorting> collectionSorting = CardView.DEFAULT_COLLECTION_SORTING;
	public Map<Zone, CardView.Grouping> zoneGroupings = new HashMap<>();
	public SimpleObjectProperty<CardView.Grouping> cutboardGrouping = new SimpleObjectProperty<>(None.INSTANCE);

	public boolean collapseDuplicates = true;
	public boolean theFutureIsNow = true;

	public boolean cardInfoTooltips = false;
	public boolean cardTagsTooltips = true;

	/**
	 * Printing selection preferences
	 */

	// N.B. Preferences get loaded *before* card data, so we can't reference Card.Printings here.
	public HashMap<String, PreferredPrinting> preferredPrintings = new HashMap<>();
	public DefaultPrintings defaultPrintings = new DefaultPrintings();

	public PreferAge preferAge = PreferAge.Any;
	public PreferVariation preferVariation = PreferVariation.Any;
	public boolean preferNotPromo = true;

	/**
	 * Preferences related to deck construction
	 */

	public Format defaultFormat = Format.Standard;
	public String authorName = "";
	public SimpleBooleanProperty removeToCutboard = new SimpleBooleanProperty(false);

	/**
	 * Instance utilities (e.g. simplifying accessors)
	 */

	public Card.Printing preferredPrinting(Card card) {
		PreferredPrinting pref = preferredPrintings.get(card.fullName());
		if (pref != null) return card.printing(pref.setCode, pref.collectorNumber);

		Stream<? extends Card.Printing> stream = card.printings().stream();

		if (preferNotPromo) stream = stream.filter(pr -> !pr.promo());
		if (!defaultPrintings.ignoredSets.isEmpty()) stream = stream.filter(pr -> !defaultPrintings.ignoredSets.contains(pr.set().code()));

		if (preferAge != PreferAge.Any) stream = stream.sorted(preferAge == PreferAge.Newest ?
				(a1, a2) -> a2.releaseDate().compareTo(a1.releaseDate()) :
				(a1, a2) -> a1.releaseDate().compareTo(a2.releaseDate()));
		if (preferVariation != PreferVariation.Any) stream = stream.sorted(preferVariation == PreferVariation.Primary ?
				(a1, a2) -> a1.variation() - a2.variation() :
				(a1, a2) -> a2.variation() - a1.variation());
		if (!defaultPrintings.preferredSets.isEmpty()) stream = stream.sorted(Comparator.comparingInt(a -> defaultPrintings.prefPriority(a.set().code())));

		return stream.findFirst().orElse(null);
	}

	public Card.Printing anyPrinting(Card card) {
		Card.Printing preferred = preferredPrinting(card);
		if (preferred == null) preferred = card.printings().iterator().next();

		return preferred;
	}

	public void convertOldPreferredPrintings() {
		for (UUID old : deferredPreferredPrintings) {
			Card.Printing pr = Context.get().data.printing(old);
			preferredPrintings.put(pr.card().fullName(), new PreferredPrinting(pr.set().code(), pr.collectorNumber()));
		}

		deferredPreferredPrintings.clear();
	}
}
