package emi.mtg.deckbuilder.view.dialogs;

import emi.lib.mtg.DataSource;
import emi.lib.mtg.game.Format;
import emi.lib.mtg.game.Zone;
import emi.mtg.deckbuilder.controller.serdes.DeckImportExport;
import emi.mtg.deckbuilder.model.Preferences;
import emi.mtg.deckbuilder.view.MainApplication;
import emi.mtg.deckbuilder.view.components.CardView;
import emi.mtg.deckbuilder.view.search.SearchProvider;
import emi.mtg.deckbuilder.view.util.AlertBuilder;
import javafx.beans.property.Property;
import javafx.event.ActionEvent;
import javafx.geometry.*;
import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.scene.text.Font;
import javafx.stage.DirectoryChooser;
import javafx.stage.Modality;
import javafx.stage.Window;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.Field;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.function.*;
import java.util.stream.Collectors;

public class PreferencesDialog extends Alert {
	private static abstract class PrefEntry {
		abstract void installGui(GridPane grid, int row);
	}

	private static class PrefSeparator extends PrefEntry {
		private final Separator separator;

		public PrefSeparator() {
			this.separator = new Separator(Orientation.HORIZONTAL);
		}

		void installGui(GridPane grid, int row) {
			grid.add(separator,0,row,2,1);
		}
	}

	private static class LabeledSeparator extends PrefEntry {
		private final HBox box;

		public LabeledSeparator(String text) {
			Separator left = new Separator(), right = new Separator();
			Label label = new Label(text);

			this.box = new HBox(8, left, label, right);
			box.setAlignment(Pos.CENTER);
			HBox.setHgrow(left, Priority.ALWAYS);
			HBox.setHgrow(right, Priority.ALWAYS);
			HBox.setHgrow(label, Priority.NEVER);
		}

		@Override
		void installGui(GridPane grid, int row) {
			grid.add(box, 0, row, 2, 1);
		}
	}

	private interface FromPrefs<T> extends Function<Preferences, T> {
		@Override
		default T apply(Preferences preferences) {
			try {
				return throwingApply(preferences);
			} catch (ReflectiveOperationException roe) {
				throw new RuntimeException(roe);
			}
		}

		T throwingApply(Preferences preferences) throws ReflectiveOperationException;
	}

	private interface ToPrefs<T> extends BiConsumer<Preferences, T> {
		@Override
		default void accept(Preferences preferences, T value) {
			try {
				throwingAccept(preferences, value);
			} catch (ReflectiveOperationException roe) {
				throw new RuntimeException(roe);
			}
		}

		void throwingAccept(Preferences preferences, T value) throws ReflectiveOperationException;
	}

	private static abstract class Preference<T> extends PrefEntry {
		public final String label;
		private final Label labelNode;
		private final Supplier<T> fromPrefs;
		private final Predicate<T> validate;
		private final Consumer<T> toPrefs;

		public Preference(String label, Tooltip tooltip, Function<Preferences, T> fromPrefs, Predicate<T> validate, BiConsumer<Preferences, T> toPrefs) {
			this.label = label;
			this.labelNode = new Label(label + ":");
			this.labelNode.setTooltip(tooltip);
			this.fromPrefs = () -> fromPrefs.apply(Preferences.get());
			this.validate = validate;
			this.toPrefs = v -> toPrefs.accept(Preferences.get(), v);
		}

		void installGui(GridPane grid, int row) {
			final Node gui = gui();
			grid.addRow(row, labelNode, gui);
			GridPane.setHalignment(labelNode, HPos.RIGHT);
			GridPane.setValignment(labelNode, VPos.BASELINE);
			GridPane.setHalignment(gui, HPos.LEFT);
			GridPane.setValignment(gui, VPos.BASELINE);
		}

		abstract Node gui();

		abstract void toGui(T value);

		abstract T fromGui();

		public void init() {
			toGui(fromPrefs.get());
		}

		public boolean validate() {
			return validate.test(fromGui());
		}

		public void save() {
			toPrefs.accept(fromGui());
		}
	}

	private static class ButtonBarPref<E> extends Preference<E> {
		private final E none;
		private final E[] options;
		private final ToggleGroup toggleGroup;
		private final ToggleButton[] buttons;
		private final HBox box;

		public ButtonBarPref(String label, Tooltip tooltip, Function<Preferences, E> fromPrefs, Predicate<E> validate, BiConsumer<Preferences, E> toPrefs, E[] options, E none) {
			super(label, tooltip, fromPrefs, validate, toPrefs);

			this.none = none;
			int noneIdx;
			this.toggleGroup = new ToggleGroup();
			if (none == null) {
				this.toggleGroup.selectedToggleProperty().addListener((obs, old, newval) -> {
					if (newval == null) old.setSelected(true);
				});
				noneIdx = -1;
			} else {
				noneIdx = Arrays.asList(options).indexOf(none);
			}

			this.options = options;
			this.buttons = new ToggleButton[this.options.length - (none != null ? 1 : 0)];
			for (int i = 0; i < this.options.length; ++i) {
				E e = this.options[i];
				if (e == none) continue;
				int b = i - ((none != null && noneIdx >= 0 && i > noneIdx) ? 1 : 0);
				int leftRadius = b > 0 ? 0 : 4;
				int rightRadius = b < this.buttons.length - 1 ? 0 : 4;

				ToggleButton btn = new ToggleButton(e.toString());
				btn.setUserData(e);
				btn.setToggleGroup(this.toggleGroup);
				btn.setMaxWidth(Double.MAX_VALUE);
				btn.setStyle(String.format("-fx-background-radius: %1$d %2$d %2$d %1$d;", leftRadius, rightRadius));
				HBox.setHgrow(btn, Priority.ALWAYS);
				this.buttons[b] = btn;
			}

			this.box = new HBox(0.0);
			this.box.getChildren().setAll(buttons);
			this.box.setAlignment(Pos.BASELINE_CENTER);
		}

		public ButtonBarPref(String label, Tooltip tooltip, Function<Preferences, E> fromPrefs, Predicate<E> validate, BiConsumer<Preferences, E> toPrefs, E[] options) {
			this(label, tooltip, fromPrefs, validate, toPrefs, options, null);
		}

		@Override
		Node gui() {
			return box;
		}

		@Override
		void toGui(E value) {
			for (ToggleButton button : this.buttons) {
				button.setSelected(button.getUserData().equals(value));
			}
		}

		@Override
		E fromGui() {
			if (this.toggleGroup.getSelectedToggle() != null) {
				return (E) this.toggleGroup.getSelectedToggle().getUserData();
			} else {
				return none;
			}
		}
	}

	private static class EnumeratedPreference<E extends Enum<E>> extends ButtonBarPref<E> {
		public EnumeratedPreference(String label, Tooltip tooltip, Function<Preferences, E> fromPrefs, Predicate<E> validate, BiConsumer<Preferences, E> toPrefs, E[] options, E none) {
			super(label, tooltip, fromPrefs, validate, toPrefs, options, none);
		}

		public EnumeratedPreference(String label, Tooltip tooltip, Function<Preferences, E> fromPrefs, Predicate<E> validate, BiConsumer<Preferences, E> toPrefs, E none) {
			super(label, tooltip, fromPrefs, validate, toPrefs, none.getDeclaringClass().getEnumConstants(), none);
		}

		public EnumeratedPreference(String label, Tooltip tooltip, Function<Preferences, E> fromPrefs, Predicate<E> validate, BiConsumer<Preferences, E> toPrefs, E[] options) {
			super(label, tooltip, fromPrefs, validate, toPrefs, options);
		}
	}

	private static class PreferAgePreference extends EnumeratedPreference<Preferences.PreferAge> {
		public PreferAgePreference(String label, Tooltip tooltip, Function<Preferences, Preferences.PreferAge> fromPrefs, Predicate<Preferences.PreferAge> validate, BiConsumer<Preferences, Preferences.PreferAge> toPrefs) {
			super(label, tooltip, fromPrefs, validate, toPrefs, Preferences.PreferAge.Any);
		}
	}

	private static class PreferVariationPreference extends EnumeratedPreference<Preferences.PreferVariation> {
		public PreferVariationPreference(String label, Tooltip tooltip, Function<Preferences, Preferences.PreferVariation> fromPrefs, Predicate<Preferences.PreferVariation> validate, BiConsumer<Preferences, Preferences.PreferVariation> toPrefs) {
			super(label, tooltip, fromPrefs, validate, toPrefs, Preferences.PreferVariation.Any);
		}
	}

	private static class WindowBehaviorPreference extends EnumeratedPreference<Preferences.WindowBehavior> {
		public WindowBehaviorPreference(String label, Tooltip tooltip, Function<Preferences, Preferences.WindowBehavior> fromPrefs, Predicate<Preferences.WindowBehavior> validate, BiConsumer<Preferences, Preferences.WindowBehavior> toPrefs) {
			super(label, tooltip, fromPrefs, validate, toPrefs, Preferences.WindowBehavior.AlwaysAsk);
		}
	}

	private static abstract class OneControlPreference<T, C extends Node> extends Preference<T> {
		private final C gui;
		private final BiConsumer<C, T> set;
		private final Function<C, T> get;

		public OneControlPreference(Supplier<C> guiFactory, Function<C, T> get, BiConsumer<C, T> set, String label, Tooltip tooltip, Function<Preferences, T> fromPrefs, Predicate<T> validate, BiConsumer<Preferences, T> toPrefs) {
			super(label, tooltip, fromPrefs, validate, toPrefs);

			this.gui = guiFactory.get();
			this.set = set;
			this.get = get;
		}

		@Override
		public C gui() {
			return gui;
		}

		@Override
		void toGui(T value) {
			set.accept(gui, value);
		}

		@Override
		T fromGui() {
			return get.apply(gui);
		}
	}

	private static class BooleanPreference extends OneControlPreference<Boolean, CheckBox> {
		public BooleanPreference(String label, Tooltip tooltip, Function<Preferences, Boolean> fromPrefs, Predicate<Boolean> validate, BiConsumer<Preferences, Boolean> toPrefs) {
			super(CheckBox::new, CheckBox::isSelected, CheckBox::setSelected, label, tooltip, fromPrefs, validate, toPrefs);
		}
	}

	private static class StringLikePreference<T> extends OneControlPreference<T, TextField> {
		public StringLikePreference(Consumer<TextField> modifier, Function<String, T> fromString, Function<T, String> toString, String label, Tooltip tooltip, Function<Preferences, T> fromPrefs, Predicate<T> validate, BiConsumer<Preferences, T> toPrefs) {
			super(
					() -> {
						TextField f = new TextField();
						f.setPrefColumnCount(30);
						if (modifier != null) modifier.accept(f);
						return f;
					},
					tf -> fromString.apply(tf.getText()),
					(tf, v) -> tf.setText(toString.apply(v)),
					label,
					tooltip,
					fromPrefs,
					validate,
					toPrefs
			);
		}
	}

	private static class StringPreference extends StringLikePreference<String> {
		public StringPreference(String label, Tooltip tooltip, Function<Preferences, String> fromPrefs, Predicate<String> validate, BiConsumer<Preferences, String> toPrefs) {
			super(null, s -> s, s -> s, label, tooltip, fromPrefs, validate, toPrefs);
		}
	}

	private static class URIPreference extends StringLikePreference<URI> {
		public URIPreference(String label, Tooltip tooltip, Function<Preferences, URI> fromPrefs, Predicate<URI> validate, BiConsumer<Preferences, URI> toPrefs) {
			super(f -> f.setFont(Font.font("Courier New")), URI::create, URI::toString, label, tooltip, fromPrefs, validate, toPrefs);
		}
	}

	private static class PathPreference extends Preference<Path> {
		private final TextField textField;
		private final Button chooseBtn;
		private final HBox box;

		public PathPreference(String label, Tooltip tooltip, Function<Preferences, Path> fromPrefs, Predicate<Path> validate, BiConsumer<Preferences, Path> toPrefs) {
			super(label, tooltip, fromPrefs, validate, toPrefs);

			this.textField = new TextField();
			this.textField.setFont(Font.font("Courier New"));
			this.textField.setMaxWidth(Double.MAX_VALUE);
			this.textField.setPrefColumnCount(30);
			this.chooseBtn = new Button("Choose");
			this.box = new HBox(8.0, textField, chooseBtn);
			this.box.setAlignment(Pos.BASELINE_LEFT);
			HBox.setHgrow(textField,Priority.ALWAYS);
			HBox.setHgrow(chooseBtn, Priority.SOMETIMES);

			chooseBtn.setOnAction(ae -> {
				DirectoryChooser chooser = new DirectoryChooser();
				Path path = fromGui();
				if (!Files.exists(path)) path = MainApplication.JAR_DIR;
				chooser.setInitialDirectory(path.toFile());

				File dir = chooser.showDialog(box.getScene().getWindow());
				if (dir != null) {
					toGui(dir.toPath());
				}
			});
		}

		@Override
		Node gui() {
			return box;
		}

		@Override
		void toGui(Path value) {
			textField.setText(value.toString());
		}

		@Override
		Path fromGui() {
			return Paths.get(textField.getText());
		}
	}

	private static class ComboBoxPreference<T> extends OneControlPreference<T, ComboBox<T>> {
		private ComboBoxPreference(Consumer<ComboBox<T>> setAll, String label, Tooltip tooltip, Function<Preferences, T> fromPrefs, Predicate<T> validate, BiConsumer<Preferences, T> toPrefs) {
			super(
					() -> {
						ComboBox<T> box = new ComboBox<>();
						setAll.accept(box);
						return box;
					},
					c -> c.getSelectionModel().getSelectedItem(), (c, v) -> c.getSelectionModel().select(v),
					label,
					tooltip,
					fromPrefs,
					validate,
					toPrefs);
		}

		public ComboBoxPreference(List<T> options, String label, Tooltip tooltip, Function<Preferences, T> fromPrefs, Predicate<T> validate, BiConsumer<Preferences, T> toPrefs) {
			this(c -> c.getItems().setAll(options), label, tooltip, fromPrefs, validate, toPrefs);
		}

		public ComboBoxPreference(T[] options, String label, Tooltip tooltip, Function<Preferences, T> fromPrefs, Predicate<T> validate, BiConsumer<Preferences, T> toPrefs) {
			this(c -> c.getItems().setAll(options), label, tooltip, fromPrefs, validate, toPrefs);
		}
	}

	private static class GroupingPreference extends ComboBoxPreference<CardView.Grouping> {
		public GroupingPreference(String label, Tooltip tooltip, Function<Preferences, CardView.Grouping> fromPrefs, Predicate<CardView.Grouping> validate, BiConsumer<Preferences, CardView.Grouping> toPrefs) {
			super(CardView.GROUPINGS, label, tooltip, fromPrefs, validate, toPrefs);
		}
	}

	private static class SearchProviderPreference extends ComboBoxPreference<SearchProvider> {
		public SearchProviderPreference(String label, Tooltip tooltip, Function<Preferences, SearchProvider> fromPrefs, Predicate<SearchProvider> validate, BiConsumer<Preferences, SearchProvider> toPrefs) {
			super(b -> b.getItems().setAll(SearchProvider.SEARCH_PROVIDERS.values()), label, tooltip, fromPrefs, validate, toPrefs);
		}
	}

	private static class FormatPreference extends ComboBoxPreference<Format> {
		public FormatPreference(String label, Tooltip tooltip, Function<Preferences, Format> fromPrefs, Predicate<Format> validate, BiConsumer<Preferences, Format> toPrefs) {
			super(Format.values(), label, tooltip, fromPrefs, validate, toPrefs);
		}
	}

	private static class CutboardBehaviorPreference extends EnumeratedPreference<Preferences.CutboardBehavior> {
		public CutboardBehaviorPreference(String label, Tooltip tooltip, Function<Preferences, Preferences.CutboardBehavior> fromPrefs, Predicate<Preferences.CutboardBehavior> validate, BiConsumer<Preferences, Preferences.CutboardBehavior> toPrefs) {
			super(label, tooltip, fromPrefs, validate, toPrefs, Preferences.CutboardBehavior.values());
		}
	}

	private static class ThemePreference extends ComboBoxPreference<Preferences.Theme> {
		public ThemePreference(String label, Tooltip tooltip, Function<Preferences, Preferences.Theme> fromPrefs, Predicate<Preferences.Theme> validate, BiConsumer<Preferences, Preferences.Theme> toPrefs) {
			super(Preferences.Theme.values(), label, tooltip, fromPrefs, validate, toPrefs);
		}
	}

	private static class CopyPastePreference extends ComboBoxPreference<DeckImportExport.Textual> {
		public CopyPastePreference(String label, Tooltip tooltip, Function<Preferences, DeckImportExport.Textual> fromPrefs, Predicate<DeckImportExport.Textual> validate, BiConsumer<Preferences, DeckImportExport.Textual> toPrefs) {
			super(DeckImportExport.TEXTUAL_PROVIDERS, label, tooltip, fromPrefs, validate, toPrefs);
		}
	}

	private static class DataSourcePreference extends ComboBoxPreference<DataSource> {
		public DataSourcePreference(String label, Tooltip tooltip, Function<Preferences, DataSource> fromPrefs, Predicate<DataSource> validate, BiConsumer<Preferences, DataSource> toPrefs) {
			super(MainApplication.DATA_SOURCES, label, tooltip, fromPrefs, validate, toPrefs);
		}
	}

	private static class SortingPreference extends OneControlPreference<List<CardView.ActiveSorting>, Button> {
		public SortingPreference(String label, Tooltip tooltip, Function<Preferences, List<CardView.ActiveSorting>> fromPrefs, Predicate<List<CardView.ActiveSorting>> validate, BiConsumer<Preferences, List<CardView.ActiveSorting>> toPrefs) {
			super(
					() -> {
						Button btn = new Button("Configure");
						btn.setOnAction(ae -> {
							SortDialog dlg = new SortDialog(btn.getScene().getWindow(), (List<CardView.ActiveSorting>) btn.getUserData());
							dlg.initModality(Modality.APPLICATION_MODAL);
							dlg.showAndWait().ifPresent(btn::setUserData);
						});
						return btn;
					},
					c -> (List<CardView.ActiveSorting>) c.getUserData(),
					Button::setUserData,
					label,
					tooltip,
					fromPrefs,
					validate,
					toPrefs
			);
		}
	}

	private static class DefaultPrintingsPreference extends OneControlPreference<Preferences.DefaultPrintings, Button> {
		public DefaultPrintingsPreference(String label, Tooltip tooltip, Function<Preferences, Preferences.DefaultPrintings> fromPrefs, Predicate<Preferences.DefaultPrintings> validate, BiConsumer<Preferences, Preferences.DefaultPrintings> toPrefs) {
			super(
					() -> {
						Button btn = new Button("Configure");
						btn.setOnAction(ae -> {
							DefaultPrintingsDialog dlg = new DefaultPrintingsDialog(btn.getScene().getWindow(), (Preferences.DefaultPrintings) btn.getUserData());
							dlg.initModality(Modality.APPLICATION_MODAL);
							dlg.showAndWait().ifPresent(btn::setUserData);
						});
						return btn;
					},
					c -> (Preferences.DefaultPrintings) c.getUserData(),
					Button::setUserData,
					label,
					tooltip,
					fromPrefs,
					validate,
					toPrefs
			);
		}
	}

	@FunctionalInterface
	private interface TypicalConstructor<T, P extends Preference<T>> {
		P create(String label, Tooltip tooltip, Function<Preferences, T> fromPrefs, Predicate<T> validate, BiConsumer<Preferences, T> toPrefs);
	}

	private static <T, P extends Preference<T>> P reflectField(TypicalConstructor<T, P> constructor, String label, String fieldName, Predicate<T> validate) {
		Field f;
		try {
			f = Preferences.class.getDeclaredField(fieldName);
		} catch (NoSuchFieldException nsfe) {
			throw new AssertionError(nsfe);
		}

		f.setAccessible(true);
		return constructor.create(
				label,
				null, // TODO tooltips
				prefs -> {
					try {
						return (T) f.get(prefs);
					} catch (IllegalAccessException iae) {
						throw new AssertionError(iae);
					}
				},
				validate,
				(prefs, val) -> {
					try {
						f.set(prefs, val);
					} catch (IllegalAccessException iae) {
						throw new AssertionError(iae);
					}
				});
	}

	private static <T, P extends Preference<T>> P propertyField(TypicalConstructor<T, P> constructor, String label, Function<Preferences, Property<T>> get, Predicate<T> validate) {
		return constructor.create(
				label,
				null, // TODO tooltips
				prefs -> get.apply(prefs).getValue(),
				validate,
				(prefs, val) -> get.apply(prefs).setValue(val)
		);
	}

	private static GroupingPreference zoneGroupingPreference(Zone zone) {
		return new GroupingPreference(
				String.format("%s Grouping", zone.name()),
				null, // TODO tooltips
				prefs -> prefs.zoneGroupings.get(zone),
				x -> true,
				(prefs, g) -> prefs.zoneGroupings.put(zone, g)
		);
	}

	private static GroupingPreference cutboardGroupingPreference() {
		return new GroupingPreference(
				"Cutboard Grouping",
				null, // TODO tooltips
				prefs -> prefs.cutboardGrouping.get(),
				x -> true,
				(prefs, g) -> prefs.cutboardGrouping.set(g)
		);
	}

	private final Predicate<Path> PATH_WRITABLE_VALIDATOR = path -> {
		try {
			Path tmp = Files.createTempFile(path, "probe-", ".tmp");
			Files.delete(tmp);
			return true;
		} catch (IOException ioe) {
			AlertBuilder.notify(getWindow())
					.type(AlertType.ERROR)
					.title("Invalid Directory")
					.headerText("Unable to write to chosen directory.")
					.contentText("Please make sure you select a path you have permissions to write.")
					.showAndWait();
			return false;
		}
	};

	private final Predicate<Path> IMAGES_PATH_VALIDATOR = path -> {
		PATH_WRITABLE_VALIDATOR.test(path);

		if (!path.equals(Preferences.get().imagesPath)) {
			return AlertBuilder.query(getWindow())
					.type(AlertType.WARNING)
					.buttons(ButtonType.OK, ButtonType.CANCEL)
					.title("Change Image Path")
					.headerText("Restart required. Old images will remain.")
					.contentText(String.join(" ",
							"Changing your image path preference does not move existing images!",
							"Old images will remain, and images for cards will be redownloaded as you see them.",
							"You may wish to copy some or all of the old images to the new directory, then",
							"consider deleting the old directory to save hard drive space!") + "\n\n" +
							String.join(" ",
									"Finally, please note that this change will not take effect until you",
									"restart the deckbuilder. I recommend doing so immediately!"))
					.showAndWait().orElse(ButtonType.CANCEL) == ButtonType.OK;
		}

		return true;
	};

	private final Predicate<DeckImportExport.Textual> COPY_PASTE_VALIDATOR = serdes -> {
		if (serdes.importExtensions().isEmpty()) {
			return AlertBuilder.query(getWindow())
					.type(AlertType.WARNING)
					.buttons(ButtonType.OK, ButtonType.CANCEL)
					.title("Copy Paste Format Warning")
					.headerText("This format can't support pasted decks.")
					.contentText(String.join(" ",
							"The selected copy/paste format only supports exporting decks to your clipboard.",
							"If you copy a deck this way, you won't be able to paste it back into the deckbuilder."))
					.showAndWait().orElse(ButtonType.CANCEL) == ButtonType.OK;
		} else if (serdes.exportExtensions().isEmpty()) {
			return AlertBuilder.query(getWindow())
					.type(AlertType.WARNING)
					.buttons(ButtonType.OK, ButtonType.CANCEL)
					.title("Copy Paste Format Warning")
					.headerText("This format can't copy decks to clipboard.")
					.contentText(String.join(" ",
							"The selected copy/paste format only supports importing decks from your clipboard.",
							"If you paste a deck this way, you won't be able to copy it back to your clipboard."))
					.showAndWait().orElse(ButtonType.CANCEL) == ButtonType.OK;
		} else {
			return true;
		}
	};

	private final Map<String, PrefEntry[]> PREFERENCE_FIELDS = preferenceFields();

	private Map<String, PrefEntry[]> preferenceFields() {
		Map<String, PrefEntry[]> map = new LinkedHashMap<>();

		map.put("Interface", new PrefEntry[] {
				reflectField(ThemePreference::new, "Theme", "theme", x -> true),
				reflectField(WindowBehaviorPreference::new, "Window Behavior", "windowBehavior", x -> true),
				reflectField(CopyPastePreference::new, "Copy/Paste Format", "copyPasteFormat", COPY_PASTE_VALIDATOR),
				new PrefSeparator(),
				reflectField(StringPreference::new, "Default Author", "authorName", x -> true),
				reflectField(FormatPreference::new, "Default Format", "defaultFormat", x -> true),
				propertyField(CutboardBehaviorPreference::new, "Remove Cards to Cutboard", p -> p.cutboardBehavior, x -> true),
				new PrefSeparator(),
				reflectField(BooleanPreference::new, "Show Debug Options", "showDebugOptions", x -> true),
		});

		map.put("Printing Selection", new PrefEntry[] {
				reflectField(DefaultPrintingsPreference::new, "Ignored/Preferred Sets", "defaultPrintings", x -> true),
				reflectField(PreferAgePreference::new, "Default Printing", "preferAge", x -> true),
				reflectField(PreferVariationPreference::new, "Prefer Variation", "preferVariation", x -> true),
				reflectField(BooleanPreference::new, "Prefer Non-Promo Versions","preferNotPromo", x -> true),
		});

		map.put("Collection & Zones", new PrefEntry[] {
				reflectField(SearchProviderPreference::new, "Search Provider", "searchProvider", x -> true),
				reflectField(StringPreference::new, "New Window Search", "defaultQuery", x -> true),
				new PrefSeparator(),
				reflectField(BooleanPreference::new, "The Future is Now", "theFutureIsNow", x -> true),
				reflectField(BooleanPreference::new, "Collapse Duplicates", "collapseDuplicates", x -> true),
				reflectField(GroupingPreference::new, "Collection Grouping", "collectionGrouping", x -> true),
				reflectField(SortingPreference::new, "Collection Sorting", "collectionSorting", x -> true),
				new PrefSeparator(),
				zoneGroupingPreference(Zone.Library),
				zoneGroupingPreference(Zone.Sideboard),
				zoneGroupingPreference(Zone.Command),
				cutboardGroupingPreference(),
				new PrefSeparator(),
				reflectField(BooleanPreference::new, "Card Info Tooltips", "cardInfoTooltips", x -> true),
				reflectField(BooleanPreference::new, "Card Tags on Tooltips", "cardTagsTooltips", x -> true),
		});

		map.put("Paths & Updates", new PrefEntry[] {
				reflectField(DataSourcePreference::new, "Data Source (Requires Restart)", "dataSource", x -> true),
				reflectField(BooleanPreference::new, "Automatically Load", "autoLoadData", x -> true),
				new PrefSeparator(),
				reflectField(PathPreference::new, "Data Path", "dataPath", PATH_WRITABLE_VALIDATOR),
				reflectField(PathPreference::new, "Images Path", "imagesPath", IMAGES_PATH_VALIDATOR),
				new PrefSeparator(),
				reflectField(BooleanPreference::new, "Auto-Update Data", "autoUpdateData", x -> true),
				reflectField(BooleanPreference::new, "Auto-Update Deckbuilder", "autoUpdateProgram", x -> true),
				reflectField(URIPreference::new, "Update URL", "updateUri", x -> true)
		});

		return Collections.unmodifiableMap(map);
	}

	private final List<PrefEntry> prefEntries;

	public PreferencesDialog(Window owner) {
		super(AlertType.CONFIRMATION);
		initOwner(owner);
		setTitle("Deck Builder Preferences");
		setHeaderText("Update Preferences");
		getDialogPane().setStyle(Preferences.get().theme.style());

		this.prefEntries = new ArrayList<>();

		TabPane tabs = new TabPane();
		tabs.setTabClosingPolicy(TabPane.TabClosingPolicy.UNAVAILABLE);
		tabs.getStyleClass().add(TabPane.STYLE_CLASS_FLOATING);

		for (Map.Entry<String, PrefEntry[]> tab : PREFERENCE_FIELDS.entrySet()) {
			GridPane grid = new GridPane();
			grid.setHgap(10.0);
			grid.setVgap(10.0);
			grid.setMaxWidth(GridPane.USE_PREF_SIZE);
			grid.setMaxHeight(GridPane.USE_PREF_SIZE);

			int i = -1;
			for (PrefEntry entry : tab.getValue()) {
				entry.installGui(grid, ++i);

				if (entry instanceof Preference) {
					((Preference<?>) entry).init();
				}

				this.prefEntries.add(entry);
			}

			StackPane pane = new StackPane(grid);
			pane.setPadding(new Insets(10.0));
			StackPane.setAlignment(grid, Pos.CENTER);

			tabs.getTabs().add(new Tab(tab.getKey(), pane));
		}

		TabPane preferencesTabs = new TabPane();
		preferencesTabs.setTabClosingPolicy(TabPane.TabClosingPolicy.UNAVAILABLE);
		preferencesTabs.getStyleClass().add(TabPane.STYLE_CLASS_FLOATING);

		// Plugin preferences
		for (Preferences.Plugin plugin : Preferences.get().pluginPreferences.values()) {
			GridPane grid = new GridPane();
			grid.setHgap(10.0);
			grid.setVgap(10.0);
			grid.setMaxWidth(GridPane.USE_PREF_SIZE);
			grid.setMaxHeight(GridPane.USE_PREF_SIZE);

			int i = -1;
			for (Map.Entry<Field, Preferences.Plugin.Preference> preference : Preferences.Plugin.preferenceFields(plugin).entrySet()) {
				Field field = preference.getKey();
				field.setAccessible(true);
				Preferences.Plugin.Preference info = preference.getValue();

				Tooltip tooltip = info.tooltip().isEmpty() ? null : new Tooltip(info.tooltip());
				if (tooltip != null) tooltip.setFont(Font.font(12.0));

				Preference<?> prefEntry;
				if (field.getType().isEnum() || info.options().length > 0) {
					Object[] options = field.getType().isEnum() ? field.getType().getEnumConstants() : info.options();
					Object none = null;

					if (!info.noneOption().isEmpty()) {
						if (field.getType().isEnum()) {
							for (Object obj : field.getType().getEnumConstants()) {
								if (info.noneOption().equals(((Enum<?>) obj).name())) {
									none = obj;
									break;
								}
							}
						} else {
							for (Object option : options) {
								if (info.noneOption().equals(option)) {
									none = option;
									break;
								}
							}
						}

						if (none == null) {
							System.err.printf(
									"Plugin %s declares an enumerated preference %s (%s.%s) with a noneOption %s that doesn't match any of its options (%s)!%n",
									plugin.name(),
									info.value(),
									field.getDeclaringClass().getCanonicalName(), field.getName(),
									info.noneOption(),
									Arrays.toString(info.options())
							);
							continue;
						}
					}

					if (info.buttonBar()) {
						prefEntry = new ButtonBarPref<>(
								info.value(),
								tooltip,
								(FromPrefs<Object>) prefs -> field.get(plugin),
								x -> true,
								(ToPrefs<Object>) (prefs, val) -> field.set(plugin, val),
								options,
								none
						);
					} else {
						prefEntry = new ComboBoxPreference<>(
								options,
								info.value(),
								tooltip,
								(FromPrefs<Object>) prefs -> field.get(plugin),
								x -> true,
								(ToPrefs<Object>) (prefs, val) -> field.set(plugin, val)
						);
					}
				} else if (field.getType() == String.class) {
					prefEntry = new StringPreference(
							info.value(),
							tooltip,
							(FromPrefs<String>) prefs -> (String) field.get(plugin),
							x -> true,
							(ToPrefs<String>) (prefs, val) -> field.set(plugin, val)
					);
				} else if (field.getType() == boolean.class) {
					prefEntry = new BooleanPreference(
							info.value(),
							tooltip,
							(FromPrefs<Boolean>) prefs -> field.getBoolean(plugin),
							x -> true,
							(ToPrefs<Boolean>) (prefs, val) -> field.set(plugin, val)
					);
				} else {
					System.err.printf(
							"Plugin %s declares a preference %s (%s.%s) with an unhandled field type %s!%n",
							plugin.name(),
							info.value(),
							field.getDeclaringClass().getCanonicalName(), field.getName(),
							field.getType()
					);
					continue;
				}

				prefEntry.installGui(grid, ++i);
				prefEntry.init();
				this.prefEntries.add(prefEntry);
			}

			StackPane pane = new StackPane(grid);
			pane.setPadding(new Insets(10.0));
			StackPane.setAlignment(grid, Pos.CENTER);

			preferencesTabs.getTabs().add(new Tab(plugin.name(), pane));
		}

		tabs.getTabs().add(new Tab("Plugins", preferencesTabs));

		getDialogPane().setContent(tabs);

		getDialogPane().lookupButton(ButtonType.OK).addEventFilter(ActionEvent.ACTION, ae -> {
			List<String> invalidPrefs = prefEntries.stream()
					.filter(f -> f instanceof Preference)
					.map(f -> (Preference<?>) f)
					.filter(f -> !f.validate())
					.map(f -> f.label)
					.collect(Collectors.toList());

			if (!invalidPrefs.isEmpty()) {
				ae.consume();
			}
		});

		setResultConverter(bt -> {
			if (bt.equals(ButtonType.OK)) {
				prefEntries.stream()
						.filter(f -> f instanceof Preference)
						.map(f -> (Preference<?>) f)
						.forEach(Preference::save);

				try {
					Preferences.save();
				} catch (IOException ioe) {
					throw new RuntimeException(ioe);
				}
			}

			return bt;
		});
	}

	protected Window getWindow() {
		return getDialogPane().getScene().getWindow();
	}
}
