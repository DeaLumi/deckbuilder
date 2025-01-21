package emi.mtg.deckbuilder.model;

import com.google.gson.GsonBuilder;
import com.google.gson.InstanceCreator;
import com.google.gson.annotations.SerializedName;
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
import java.lang.reflect.Method;
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

			/**
			 * @return Minimum value, for numeric preferences.
			 */
			double min() default Double.NaN;

			/**
			 * @return Maximum value, for numeric preferences.
			 */
			double max() default Double.NaN;
		}

		@Target(ElementType.METHOD)
		@Retention(RetentionPolicy.RUNTIME)
		@interface Operation {
			/**
			 * @return A label for the operation in the preferences dialog.
			 */
			String value();

			/**
			 * @return The text of the button on the preferences dialog.
			 */
			String text() default "Perform";

			/**
			 * @return Tooltip to display to the user when hovering over the operation button, to explain what it does.
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
					.collect(Collectors.toMap(f -> f, f -> f.getAnnotation(Preference.class), (a, b) -> b, LinkedHashMap::new));
		}

		static Map<Method, Operation> operationMethods(Plugin plugin) {
			return Arrays.stream(plugin.getClass().getDeclaredMethods())
					.filter(m -> (m.getAnnotation(Operation.class) != null))
					.collect(Collectors.toMap(m -> m, m -> m.getAnnotation(Operation.class), (a, b) -> b, LinkedHashMap::new));
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

	public static HashSet<UUID> deferredPreferredPrints = new HashSet<>();

	/**
	 * Preference value types
	 */

	public static class PreferredPrint {
		public final String setCode;
		public final String collectorNumber;

		public PreferredPrint(String setCode, String collectorNumber) {
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

	public static class DefaultPrints {
		public final List<String> preferredSets;
		public final Set<String> ignoredSets;

		protected transient Map<String, Integer> preferredMap;

		public DefaultPrints() {
			this(new ArrayList<>(), new HashSet<>());
		}

		public DefaultPrints(List<String> preferredSets, Set<String> ignoredSets) {
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
			return String.format("-fx-base: %s; -fx-accent: %s; -fx-focus-color: %s; -fx-faint-focus-color: %s;",
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
	public DeckImportExport.CopyPaste copyPasteFormat = TextFile.Arena.INSTANCE;
	public boolean showDebugOptions = false;

	/**
	 * Card pane preferences
	 */

	public SearchProvider searchProvider = SearchProvider.SEARCH_PROVIDERS.get(Omnifilter.NAME);
	public String defaultQuery = "";

	public CardView.Grouping collectionGrouping = CardView.GROUPINGS.get(Rarity.class);
	public List<CardView.ActiveSorting> collectionSorting = CardView.DEFAULT_COLLECTION_SORTING;
	public Map<Zone, CardView.Grouping> zoneGroupings = new HashMap<>();
	public SimpleObjectProperty<CardView.Grouping> cutboardGrouping = new SimpleObjectProperty<>(CardView.GROUPINGS.get(None.class));

	public boolean theFutureIsNow = true;

	public boolean cardInfoTooltips = false;
	public boolean cardTagsTooltips = true;

	/**
	 * Printing selection preferences
	 */

	// N.B. Preferences get loaded *before* card data, so we can't reference Card.Printings here.
	@Deprecated
	public HashMap<String, PreferredPrint> preferredPrintings = new HashMap<>();
	public HashMap<String, Card.Print.Reference> preferredPrints = new HashMap<>();
	@SerializedName(value="defaultPrints", alternate={ "defaultPrintings" })
	public DefaultPrints defaultPrints = new DefaultPrints();

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

	/**
	 * Given two prints A and B, returns which the user would rather see, if either. If it's a toss-up, returns null!
	 * @param a The first option
	 * @param b The second option
	 * @return Which print the user prefers, if either. Null if their preferences don't help decide between the two.
	 * @param <T> Kludge because I think this should be able to work with any card print including CardInstances but it won't.
	 */
	public <T extends Card.Print> T preferPrint(T a, T b) {
		Objects.requireNonNull(a);
		Objects.requireNonNull(b);
		if (a.card() != b.card()) throw new IllegalArgumentException(String.format("Can't compare %s with %s; they're not the same card???", a, b));

		Card.Print.Reference prefRef = preferredPrints.get(a.card().name());
		if (prefRef != null) {
			if (a.reference().equals(prefRef)) return a;
			if (b.reference().equals(prefRef)) return b;
		}

		if (!defaultPrints.preferredSets.isEmpty()) {
			int ia = defaultPrints.prefPriority(a.set().code()), ib = defaultPrints.prefPriority(b.set().code());
			if (ia < Integer.MAX_VALUE || ib < Integer.MAX_VALUE) return ia < ib ? a : b;
		}

		if (!defaultPrints.ignoredSets.isEmpty()) {
			boolean ignoreA = defaultPrints.ignoredSets.contains(a.set().code()), ignoreB = defaultPrints.ignoredSets.contains(b.set().code());
			if (ignoreA ^ ignoreB) return ignoreA ? b : a;
		}

		if (preferNotPromo && a.promo() ^ b.promo()) return a.promo() ? b : a;

		if (preferAge != PreferAge.Any) {
			if (a.releaseDate().isAfter(b.releaseDate())) return preferAge == PreferAge.Newest ? a : b;
			if (b.releaseDate().isAfter(a.releaseDate())) return preferAge == PreferAge.Newest ? b : a;
		}

		if (preferVariation != PreferVariation.Any) {
			// TODO: Collector number preference?
			if (a.variation() < b.variation()) return preferVariation == PreferVariation.Primary ? a : b;
			if (b.variation() < a.variation()) return preferVariation == PreferVariation.Primary ? b : a;
		}

		return null;
	}

	public final transient Comparator<Card.Print> PREFER_PRINT_COMPARATOR = (a, b) -> {
		Card.Print pref = preferPrint(a, b);
		if (pref == a) {
			return -1;
		} else if (pref == b) {
			return 1;
		} else {
			return 0;
		}
	};

	public Card.Print preferredPrint(Card card) {
		Card.Print.Reference prefRef = preferredPrints.get(card.fullName());
		Card.Print pref = prefRef == null ? null : card.print(prefRef);
		if (pref != null) return pref;

		Stream<? extends Card.Print> stream = card.prints().stream();

		if (preferNotPromo) stream = stream.filter(pr -> !pr.promo());
		if (!defaultPrints.ignoredSets.isEmpty()) stream = stream.filter(pr -> !defaultPrints.ignoredSets.contains(pr.set().code()));

		if (preferAge != PreferAge.Any) stream = stream.sorted(preferAge == PreferAge.Newest ?
				(a1, a2) -> a2.releaseDate().compareTo(a1.releaseDate()) :
				(a1, a2) -> a1.releaseDate().compareTo(a2.releaseDate()));
		if (preferVariation != PreferVariation.Any) stream = stream.sorted(preferVariation == PreferVariation.Primary ?
				(a1, a2) -> a1.variation() - a2.variation() :
				(a1, a2) -> a2.variation() - a1.variation());
		if (!defaultPrints.preferredSets.isEmpty()) stream = stream.sorted(Comparator.comparingInt(a -> defaultPrints.prefPriority(a.set().code())));

		return stream.findFirst().orElse(null);
	}

	public Card.Print anyPrint(Card card) {
		Card.Print preferred = preferredPrint(card);
		if (preferred == null) preferred = card.prints().iterator().next();

		return preferred;
	}

	public void convertOldPreferredPrints() {
		for (UUID old : deferredPreferredPrints) {
			Card.Print pr = Context.get().data.print(old);
			preferredPrintings.put(pr.card().fullName(), new PreferredPrint(pr.set().code(), pr.collectorNumber()));
		}

		deferredPreferredPrints.clear();

		for (Map.Entry<String, PreferredPrint> badref : preferredPrintings.entrySet()) {
			preferredPrints.put(badref.getKey(), Card.Print.Reference.to(badref.getKey(), badref.getValue().setCode, badref.getValue().collectorNumber));
		}

		preferredPrintings.clear();
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
