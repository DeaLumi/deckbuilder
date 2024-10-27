package emi.mtg.deckbuilder.model;

import com.google.gson.GsonBuilder;
import com.google.gson.InstanceCreator;
import emi.lib.mtg.Card;
import emi.lib.mtg.DataSource;
import emi.lib.mtg.game.Format;
import emi.lib.mtg.game.Zone;
import emi.mtg.deckbuilder.controller.Context;
import emi.mtg.deckbuilder.controller.Serialization;
import emi.mtg.deckbuilder.controller.serdes.DeckImportExport;
import emi.mtg.deckbuilder.controller.serdes.impl.TextFile;
import emi.mtg.deckbuilder.util.InstanceMap;
import emi.mtg.deckbuilder.util.PluginUtils;
import emi.mtg.deckbuilder.view.MainApplication;
import emi.mtg.deckbuilder.view.components.CardView;
import emi.mtg.deckbuilder.view.groupings.None;
import emi.mtg.deckbuilder.view.groupings.Rarity;
import emi.mtg.deckbuilder.view.search.omnifilter.Omnifilter;
import emi.mtg.deckbuilder.view.search.SearchProvider;
import javafx.beans.property.Property;
import javafx.beans.property.SimpleObjectProperty;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.lang.ref.WeakReference;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.function.Consumer;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class Preferences {
	/**
	 * Preferences plugin for plugin preferences.
	 */
	public interface Plugin {
		@Target(ElementType.FIELD)
		@Retention(RetentionPolicy.RUNTIME)
		@interface Preference {
			/**
			 * @return A label for the preference in the preferences dialog.
			 */
			String value();

			/**
			 * @return A list of options. Only applies if the field type is String. Turns control into combo box or button bar.
			 */
			String[] options() default {};

			/**
			 * @return Option if no other option is selected. Only applies if buttonBar applies and is true.
			 */
			String noneOption() default "";

			/**
			 * @return True to use a button bar instead of a combo box. Only applies if field type is String or enum.
			 */
			boolean buttonBar() default false;

			/**
			 * @return Tooltip to display to the user when hovering over the label, to explain what the preference does.
			 */
			String tooltip() default "";
		}

		/**
		 * The name for this preferences plugin. Should really be unique. Please be unique.
		 * @return A unique name for this preferences plugin.
		 */
		String name();

		static Map<Field, Preference> preferenceFields(Plugin plugin) {
			return Arrays.stream(plugin.getClass().getDeclaredFields())
					.filter(f -> (f.getModifiers() & Modifier.TRANSIENT) == 0)
					.filter(f -> f.getAnnotation(Preference.class) != null)
					.collect(Collectors.toMap(f -> f, f -> f.getAnnotation(Preference.class)));
		}
	}

	public static void registerPluginTypeAdapters(GsonBuilder builder) {
		for (Plugin plugin : PLUGINS) {
			builder.registerTypeAdapter(plugin.getClass(), (InstanceCreator) type -> plugin);
		}
	}

	/**
	 * Static utilities
	 */

	private static final Path PATH = MainApplication.JAR_DIR.resolve("preferences.json");
	private static Preferences instance = null;
	private static final Set<WeakReference<Consumer<Preferences>>> LISTENERS = new HashSet<>();

	private static final List<Plugin> PLUGINS = PluginUtils.providers(Plugin.class);

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

			// Ensure all plugins have a presence in the preferences.
			for (Plugin plugin : PLUGINS) {
				if (!instance.pluginPreferences.containsKey(plugin.getClass())) {
					instance.pluginPreferences.put(plugin.getClass(), plugin);
				}
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
		// Ensure all plugins have entries.
		for (Plugin plugin : PLUGINS) get().plugin(plugin.getClass());

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
		Light (javafx.scene.paint.Color.gray(0.925), javafx.scene.paint.Color.color(0, 0.588, 0.788)),
		Dark (javafx.scene.paint.Color.gray(0.15), javafx.scene.paint.Color.color(0.500, 0, 0.700));

		public final javafx.scene.paint.Color base;
		public final javafx.scene.paint.Color accent;

		Theme(javafx.scene.paint.Color base, javafx.scene.paint.Color accent) {
			this.base = base;
			this.accent = accent;
		}

		public String baseHex() {
			return hex(base);
		}

		public String accentHex() {
			return hex(accent);
		}

		public String style() {
			return String.format("-fx-base: %s; -fx-accent: %s; -fx-focus-color: %s; -fx-faint-focus-color: %s",
					baseHex(),
					accentHex(),
					hex(accent.deriveColor(0.0, 1.0, 1.25, 1.0)),
					hex(accent.deriveColor(0.0, 1.0, 1.25, 0.13)));
		}

		public static String hex(javafx.scene.paint.Color color) {
			return String.format("#%02x%02x%02x%02x", (int) (color.getRed() * 255.0), (int) (color.getGreen() * 255.0), (int) (color.getBlue() * 255.0), (int) (color.getOpacity() * 255.0));
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

	public enum CutboardBehavior {
		Never ("Never"),
		WhenOpen ("When Open"),
		Always ("Always");

		public final String text;

		CutboardBehavior(String text) {
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

	public final DataSource dataSource = null;
	public boolean autoLoadData = true;

	public final Path dataPath = MainApplication.JAR_DIR.resolve("data/").toAbsolutePath();
	public final Path imagesPath = MainApplication.JAR_DIR.resolve("images/").toAbsolutePath();

	public URI updateUri = URI.create("https://cloudpost.app:8443/deckbuilder-nodata.zip");
	public boolean autoUpdateData = true;
	public boolean autoUpdateProgram = true;

	/**
	 * Overall user interface preferences
	 */

	public boolean startMaximized = false;
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
	public Property<CutboardBehavior> cutboardBehavior = new SimpleObjectProperty<>(CutboardBehavior.WhenOpen);

	/**
	 * Plugins' preferences
	 */

	public final InstanceMap<Plugin> pluginPreferences = PLUGINS.stream().collect(Collectors.toMap(Plugin::getClass, p -> p, (a, b) -> b, InstanceMap::new));

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

	public <T extends Plugin> T plugin(Class<T> type) {
		if (!pluginPreferences.containsKey(type)) {
			// See if the known plugins list has an element -- if so, insist upon it.
			for (Plugin plugin : PLUGINS) {
				if (plugin.getClass() == type) {
					pluginPreferences.put(type, plugin);
					return (T) plugin;
				}
			}

			throw new NoSuchElementException("No such plugin type: " + type);
		}

		return (T) pluginPreferences.get(type);
	}
}
