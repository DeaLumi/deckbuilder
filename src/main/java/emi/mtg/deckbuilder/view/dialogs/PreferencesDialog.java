package emi.mtg.deckbuilder.view.dialogs;

import emi.lib.mtg.game.Format;
import emi.lib.mtg.game.Zone;
import emi.mtg.deckbuilder.model.Preferences;
import emi.mtg.deckbuilder.view.MainApplication;
import emi.mtg.deckbuilder.view.components.CardView;
import emi.mtg.deckbuilder.view.util.AlertBuilder;
import javafx.event.ActionEvent;
import javafx.geometry.HPos;
import javafx.geometry.Orientation;
import javafx.geometry.Pos;
import javafx.geometry.VPos;
import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
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
import java.util.Arrays;
import java.util.List;
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

	private static abstract class Preference<T> extends PrefEntry {
		public final String label;
		private final Label labelNode;
		private final Supplier<T> fromPrefs;
		private final Predicate<T> validate;
		private final Consumer<T> toPrefs;

		public Preference(String label, Function<Preferences, T> fromPrefs, Predicate<T> validate, BiConsumer<Preferences, T> toPrefs) {
			this.label = label;
			this.labelNode = new Label(label + ":");
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

	private static class PreferAgePreference extends Preference<Preferences.PreferAge> {
		private final ToggleGroup toggleGroup;
		private final ToggleButton oldest, newest;
		private final HBox box;

		public PreferAgePreference(String label, Function<Preferences, Preferences.PreferAge> fromPrefs, Predicate<Preferences.PreferAge> validate, BiConsumer<Preferences, Preferences.PreferAge> toPrefs) {
			super(label, fromPrefs, validate, toPrefs);

			this.toggleGroup = new ToggleGroup();
			this.oldest = new ToggleButton("Oldest");
			this.oldest.setToggleGroup(this.toggleGroup);
			this.oldest.setMaxWidth(Double.MAX_VALUE);
			this.newest = new ToggleButton("Newest");
			this.newest.setToggleGroup(this.toggleGroup);
			this.newest.setMaxWidth(Double.MAX_VALUE);
			this.box = new HBox(8.0, this.oldest, this.newest);
			this.box.setAlignment(Pos.BASELINE_CENTER);
			HBox.setHgrow(this.oldest, Priority.ALWAYS);
			HBox.setHgrow(this.newest, Priority.ALWAYS);
		}

		@Override
		Node gui() {
			return box;
		}

		@Override
		void toGui(Preferences.PreferAge value) {
			oldest.setSelected(value == Preferences.PreferAge.Oldest);
			newest.setSelected(value == Preferences.PreferAge.Newest);
		}

		@Override
		Preferences.PreferAge fromGui() {
			if (oldest.isSelected()) {
				return Preferences.PreferAge.Oldest;
			} else if (newest.isSelected()) {
				return Preferences.PreferAge.Newest;
			} else {
				return Preferences.PreferAge.Any;
			}
		}
	}

	private static abstract class OneControlPreference<T, C extends Node> extends Preference<T> {
		private final C gui;
		private final BiConsumer<C, T> set;
		private final Function<C, T> get;

		public OneControlPreference(Supplier<C> guiFactory, Function<C, T> get, BiConsumer<C, T> set, String label, Function<Preferences, T> fromPrefs, Predicate<T> validate, BiConsumer<Preferences, T> toPrefs) {
			super(label, fromPrefs, validate, toPrefs);

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
		public BooleanPreference(String label, Function<Preferences, Boolean> fromPrefs, Predicate<Boolean> validate, BiConsumer<Preferences, Boolean> toPrefs) {
			super(CheckBox::new, CheckBox::isSelected, CheckBox::setSelected, label, fromPrefs, validate, toPrefs);
		}
	}

	private static class StringLikePreference<T> extends OneControlPreference<T, TextField> {
		public StringLikePreference(Consumer<TextField> modifier, Function<String, T> fromString, Function<T, String> toString, String label, Function<Preferences, T> fromPrefs, Predicate<T> validate, BiConsumer<Preferences, T> toPrefs) {
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
					fromPrefs,
					validate,
					toPrefs
			);
		}
	}

	private static class StringPreference extends StringLikePreference<String> {
		public StringPreference(String label, Function<Preferences, String> fromPrefs, Predicate<String> validate, BiConsumer<Preferences, String> toPrefs) {
			super(null, s -> s, s -> s, label, fromPrefs, validate, toPrefs);
		}
	}

	private static class URIPreference extends StringLikePreference<URI> {
		public URIPreference(String label, Function<Preferences, URI> fromPrefs, Predicate<URI> validate, BiConsumer<Preferences, URI> toPrefs) {
			super(f -> f.setFont(Font.font("Courier New")), URI::create, URI::toString, label, fromPrefs, validate, toPrefs);
		}
	}

	private static class PathPreference extends Preference<Path> {
		private final TextField textField;
		private final Button chooseBtn;
		private final HBox box;

		public PathPreference(String label, Function<Preferences, Path> fromPrefs, Predicate<Path> validate, BiConsumer<Preferences, Path> toPrefs) {
			super(label, fromPrefs, validate, toPrefs);

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
		private ComboBoxPreference(Consumer<ComboBox<T>> setAll, String label, Function<Preferences, T> fromPrefs, Predicate<T> validate, BiConsumer<Preferences, T> toPrefs) {
			super(
					() -> {
						ComboBox<T> box = new ComboBox<>();
						setAll.accept(box);
						return box;
					},
					c -> c.getSelectionModel().getSelectedItem(), (c, v) -> c.getSelectionModel().select(v),
					label,
					fromPrefs,
					validate,
					toPrefs);
		}

		public ComboBoxPreference(List<T> options, String label, Function<Preferences, T> fromPrefs, Predicate<T> validate, BiConsumer<Preferences, T> toPrefs) {
			this(c -> c.getItems().setAll(options), label, fromPrefs, validate, toPrefs);
		}

		public ComboBoxPreference(T[] options, String label, Function<Preferences, T> fromPrefs, Predicate<T> validate, BiConsumer<Preferences, T> toPrefs) {
			this(c -> c.getItems().setAll(options), label, fromPrefs, validate, toPrefs);
		}
	}

	private static class GroupingPreference extends ComboBoxPreference<CardView.Grouping> {
		public GroupingPreference(String label, Function<Preferences, CardView.Grouping> fromPrefs, Predicate<CardView.Grouping> validate, BiConsumer<Preferences, CardView.Grouping> toPrefs) {
			super(CardView.GROUPINGS, label, fromPrefs, validate, toPrefs);
		}
	}

	private static class FormatPreference extends ComboBoxPreference<Format> {
		public FormatPreference(String label, Function<Preferences, Format> fromPrefs, Predicate<Format> validate, BiConsumer<Preferences, Format> toPrefs) {
			super(Format.values(), label, fromPrefs, validate, toPrefs);
		}
	}

	private static class ThemePreference extends ComboBoxPreference<Preferences.Theme> {
		public ThemePreference(String label, Function<Preferences, Preferences.Theme> fromPrefs, Predicate<Preferences.Theme> validate, BiConsumer<Preferences, Preferences.Theme> toPrefs) {
			super(Preferences.Theme.values(), label, fromPrefs, validate, toPrefs);
		}
	}

	private static class SortingPreference extends OneControlPreference<List<CardView.ActiveSorting>, Button> {
		public SortingPreference(String label, Function<Preferences, List<CardView.ActiveSorting>> fromPrefs, Predicate<List<CardView.ActiveSorting>> validate, BiConsumer<Preferences, List<CardView.ActiveSorting>> toPrefs) {
			super(
					() -> {
						Button btn = new Button("Configure");
						btn.setOnAction(ae -> {
							SortDialog dlg = new SortDialog((List<CardView.ActiveSorting>) btn.getUserData());
							dlg.initModality(Modality.APPLICATION_MODAL);
							dlg.showAndWait().ifPresent(btn::setUserData);
						});
						return btn;
					},
					c -> (List<CardView.ActiveSorting>) c.getUserData(),
					Button::setUserData,
					label,
					fromPrefs,
					validate,
					toPrefs
			);
		}
	}

	@FunctionalInterface
	private interface TypicalConstructor<T, P extends Preference<T>> {
		P create(String label, Function<Preferences, T> fromPrefs, Predicate<T> validate, BiConsumer<Preferences, T> toPrefs);
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

	private static GroupingPreference zoneGroupingPreference(Zone zone) {
		return new GroupingPreference(
				String.format("%s Grouping", zone.name()),
				prefs -> prefs.zoneGroupings.get(zone),
				x -> true,
				(prefs, g) -> prefs.zoneGroupings.put(zone, g)
		);
	}

	private final Predicate<Path> PATH_WRITABLE_VALIDATOR = path -> {
		try {
			Path tmp = Files.createTempFile(path, "probe-", ".tmp");
			Files.delete(tmp);
			return true;
		} catch (IOException ioe) {
			AlertBuilder.notify(null)
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
			return AlertBuilder.query(getOwner())
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

	private final PrefEntry[] PREFERENCE_FIELDS = {
			reflectField(ThemePreference::new, "Theme", "theme", x -> true),
			new PrefSeparator(),
			reflectField(PathPreference::new, "Data Path", "dataPath", PATH_WRITABLE_VALIDATOR),
			reflectField(PathPreference::new, "Images Path", "imagesPath", IMAGES_PATH_VALIDATOR),
			reflectField(PreferAgePreference::new, "Default Printing", "preferAge", x -> true),
			reflectField(BooleanPreference::new, "Prefer Non-Promo Versions","preferNotPromo", x -> true),
			reflectField(BooleanPreference::new, "Prefer Physical Versions","preferPhysical", x -> true),
			new PrefSeparator(),
			reflectField(BooleanPreference::new, "The Future is Now", "theFutureIsNow", x -> true),
			reflectField(BooleanPreference::new, "Collapse Duplicates", "collapseDuplicates", x -> true),
			reflectField(GroupingPreference::new, "Collection Grouping", "collectionGrouping", x -> true),
			reflectField(SortingPreference::new, "Collection Sorting", "collectionSorting", x -> true),
			zoneGroupingPreference(Zone.Library),
			zoneGroupingPreference(Zone.Sideboard),
			zoneGroupingPreference(Zone.Command),
			new PrefSeparator(),
			reflectField(StringPreference::new, "Default Author", "authorName", x -> true),
			reflectField(FormatPreference::new, "Default Format", "defaultFormat", x -> true),
			new PrefSeparator(),
			reflectField(BooleanPreference::new, "Auto-Update Data", "autoUpdateData", x -> true),
			reflectField(BooleanPreference::new, "Auto-Update Deckbuilder", "autoUpdateProgram", x -> true),
			reflectField(URIPreference::new, "Update URL", "updateUri", x -> true)
	};

	public PreferencesDialog(Window owner) {
		super(AlertType.CONFIRMATION);
		initOwner(owner);
		setTitle("Deck Builder Preferences");
		setHeaderText("Update Preferences");
		getDialogPane().setStyle("-fx-base: " + Preferences.get().theme.baseHex());

		GridPane grid = new GridPane();
		grid.setHgap(10.0);
		grid.setVgap(10.0);

		int i = -1;
		for (PrefEntry entry : PREFERENCE_FIELDS) {
			entry.installGui(grid, ++i);

			if (entry instanceof Preference) {
				((Preference<?>) entry).init();
			}
		}

		getDialogPane().setContent(grid);

		getDialogPane().lookupButton(ButtonType.OK).addEventFilter(ActionEvent.ACTION, ae -> {
			List<String> invalidPrefs = Arrays.stream(PREFERENCE_FIELDS)
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
				Arrays.stream(PREFERENCE_FIELDS)
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
}
