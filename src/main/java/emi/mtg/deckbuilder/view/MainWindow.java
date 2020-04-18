package emi.mtg.deckbuilder.view;

import com.sun.javafx.collections.ObservableListWrapper;
import emi.lib.Service;
import emi.lib.mtg.Card;
import emi.lib.mtg.DataSource;
import emi.lib.mtg.game.Format;
import emi.lib.mtg.game.Zone;
import emi.mtg.deckbuilder.controller.Context;
import emi.mtg.deckbuilder.controller.serdes.DeckImportExport;
import emi.mtg.deckbuilder.controller.serdes.Features;
import emi.mtg.deckbuilder.controller.serdes.impl.Json;
import emi.mtg.deckbuilder.model.CardInstance;
import emi.mtg.deckbuilder.model.DeckList;
import emi.mtg.deckbuilder.view.components.CardPane;
import emi.mtg.deckbuilder.view.components.CardView;
import emi.mtg.deckbuilder.view.dialogs.DeckInfoDialog;
import emi.mtg.deckbuilder.view.dialogs.PreferencesDialog;
import emi.mtg.deckbuilder.view.dialogs.TagManagementDialog;
import javafx.beans.binding.Bindings;
import javafx.beans.property.ReadOnlyListWrapper;
import javafx.collections.ListChangeListener;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.BorderPane;
import javafx.scene.text.Text;
import javafx.stage.FileChooser;
import javafx.stage.Stage;

import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.*;
import java.util.stream.Collectors;

public class MainWindow extends Stage {
	private BorderPane root;

	@FXML
	private SplitPane collectionSplitter;

	@FXML
	private Menu newDeckMenu;

	@FXML
	private SplitPane deckSplitter;

	private DeckList deck;
	private boolean deckModified;

	private CardPane collection;
	private CardView.ContextMenu collectionContextMenu;

	private FileChooser primaryFileChooser;
	private DeckImportExport primarySerdes;
	private File currentDeckFile;

	private Map<FileChooser.ExtensionFilter, DeckImportExport> deckSerdes;
	private FileChooser serdesFileChooser;
	private final MainApplication owner;

	public MainWindow(MainApplication owner, DeckList deck) {
		super();

		this.owner = owner;
		this.owner.registerMainWindow(this);

		root = new BorderPane();
		FXMLLoader loader = new FXMLLoader();
		loader.setRoot(root);
		loader.setControllerFactory(cls -> this);
		try {
			loader.load(getClass().getResourceAsStream(getClass().getSimpleName() + ".fxml"));
		} catch (IOException ioe) {
			throw new AssertionError(ioe);
		}

		setTitle("Deck Builder v0.0.0");
		setScene(new Scene(root, 1024, 1024));

		Thread.setDefaultUncaughtExceptionHandler((x, e) -> {
			boolean deckSaved = true;

			try {
				primarySerdes.exportDeck(deck, new File("emergency-dump.json"));
			} catch (Throwable t) {
				e.addSuppressed(t);
				deckSaved = false;
			}

			e.printStackTrace();
			e.printStackTrace(new PrintWriter(System.out));

			StringWriter stackTrace = new StringWriter();
			e.printStackTrace(new PrintWriter(stackTrace));

			Alert alert = new Alert(Alert.AlertType.ERROR);
			alert.initOwner(this);

			alert.setTitle("Uncaught Exception");
			alert.setHeaderText("An internal error has occurred.");

			alert.setContentText(
					"Something went wrong!\n" +
							"\n" +
							"I have no idea if the application will be able to keep running.\n" +
							(deckSaved ?
									"Your deck has been emergency-saved to 'emergency-dump.json' in the deckbuilder directory.\n" :
									"Something went even more wrong while we tried to save your deck. Sorry. D:\n"
							) +
							"If this keeps happening, message me on Twitter @DeaLuminis!\n" +
							"If you're the nerdy type, tech details follow.\n" +
							"\n" +
							"Thread: " + x.getName() + " / " + x.getId() + "\n" +
							"Exception: " + e.getClass().getSimpleName() + ": " + e.getMessage() + "\n" +
							"\n" +
							"Full stack trace written to standard out and standard error (usually err.txt)."
			);

			alert.getDialogPane().setExpandableContent(new ScrollPane(new Text(stackTrace.toString())));

			alert.showAndWait();
		});

		setOnCloseRequest(we -> {
			if (!offerSaveIfModified()) {
				we.consume();
				return;
			}

			try {
				Context.get().saveAll();
			} catch (IOException e) {
				throw new RuntimeException(e);
			}

			owner.deregisterMainWindow(this);
		});

		setupUI();
		setupImportExport();
		setDeck(deck);

		Alert loading = information("Loading", "Loading Card Data...", "Please wait a moment!");
		loading.getButtonTypes().clear();
		loading.show();
		collection.model().setAll(new ReadOnlyListWrapper<>(collectionModel(Context.get().data)));
		loading.getButtonTypes().add(ButtonType.OK);
		loading.hide();
	}

	private CardPane deckPane(Zone zone) {
		return deckSplitter.getItems().stream()
				.map(pane -> (CardPane) pane)
				.filter(pane -> pane.title().equals(zone.name()))
				.findAny()
				.orElseThrow(() -> new AssertionError("No zone " + zone.name() + " in deck!"));
	}

	private void flagCardLegality(CardInstance ci) {
		switch (ci.card().legality(deck.format())) {
			case Legal:
			case Restricted:
				break;
			default:
				if (Context.get().preferences.theFutureIsNow && ci.card().legality(Format.Future) == Card.Legality.Legal) {
					break;
				}
				ci.flags.add(CardInstance.Flags.Invalid);
				break;
		}
	}

	private void updateCollectionValidity() {
		collection.model().stream()
				.forEach(ci -> {
					ci.flags.remove(CardInstance.Flags.Invalid);
					flagCardLegality(ci);
				});
	}

	private ObservableList<CardInstance> collectionModel(DataSource cs) {
		return new ObservableListWrapper<>(cs.printings().stream()
				.map(CardInstance::new)
				.peek(ci -> ci.flags.add(CardInstance.Flags.Unlimited))
				.peek(this::flagCardLegality)
				.collect(Collectors.toList()));
	}

	private void createCollectionContextMenu() {
		collectionContextMenu = new CardView.ContextMenu();

		Menu fillMenu = new Menu("Fill");
		fillMenu.visibleProperty().bind(collectionContextMenu.cards.emptyProperty().not());

		for (Zone zone : Zone.values()) {
			MenuItem fillZoneMenuItem = new MenuItem(zone.name());
			fillZoneMenuItem.visibleProperty().bind(Bindings.createBooleanBinding(() -> deck != null && deck.format().deckZones().contains(zone), collectionContextMenu.showingProperty()));
			fillZoneMenuItem.setOnAction(ae -> {
				for (CardInstance source : collectionContextMenu.cards) {
					ObservableList<CardInstance> zoneModel = deckPane(zone).model();

					long count = deck.format().maxCopies - zoneModel.parallelStream().filter(ci -> ci.card().equals(source.card())).count();
					for (int i = 0; i < count; ++i) {
						zoneModel.add(new CardInstance(source.printing()));
					}
				}
			});
			fillMenu.getItems().add(fillZoneMenuItem);
		}

		collectionContextMenu.getItems().addAll(fillMenu);
	}

	private void setupUI() {
		createCollectionContextMenu();

		collection = new CardPane("Collection", new ObservableListWrapper<>(new ArrayList<>()), "Flow Grid", CardView.DEFAULT_COLLECTION_SORTING);
		collection.view().immutableModelProperty().set(true);
		collection.view().doubleClick(ci -> deckPane(Zone.Library).model().add(new CardInstance(ci.printing())));
		collection.view().contextMenu(collectionContextMenu);
		collection.showIllegalCards.set(false);
		collection.showVersionsSeparately.set(false);

		this.collectionSplitter.getItems().add(0, collection);
	}

	public CardPane collection() {
		return collection;
	}

	private void setupImportExport() {
		this.primarySerdes = new Json();

		this.primaryFileChooser = new FileChooser();
		this.primaryFileChooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("JSON Files (*.json)", "*.json"));

		for (Format format : Format.values()) {
			MenuItem item = new MenuItem(format.name());
			item.setOnAction(ae -> newDeck(format));
			this.newDeckMenu.getItems().add(item);
		}

		this.deckSerdes = Service.Loader.load(DeckImportExport.class, MainApplication.PLUGIN_CLASS_LOADER).stream()
				.collect(Collectors.toMap(
						vies -> new FileChooser.ExtensionFilter(String.format("%s (*.%s)", vies.string("name"), vies.string("extension")), String.format("*.%s", vies.string("extension"))),
						vies -> vies.uncheckedInstance()
				));

		this.serdesFileChooser = new FileChooser();
		this.serdesFileChooser.getExtensionFilters().setAll(this.deckSerdes.keySet());
	}

	private final ListChangeListener<Object> deckListChangedListener = e -> {
		updateDeckValidity();
		deckModified = true;
		for (Zone zone : deck.format().deckZones()) {
			deckPane(zone).view().scheduleRender();
		}
	};

	private void setDeck(DeckList deck) {
		this.deck = deck;
		titleProperty().unbind();
		titleProperty().bind(Bindings.createStringBinding(() -> {
			if (deck.name() != null && !deck.name().isEmpty()) {
				return String.format("Deck Builder v0.0.0 - %s", deck.name());
			} else {
				return "Deck Builder v0.0.0";
			}
		}, deck.nameProperty()));

		deckModified = false;
		updateCollectionValidity();
		updateDeckValidity();

		collection.updateFilter();

		deckSplitter.getItems().clear();
		deckSplitter.getItems().addAll(deck.format().deckZones().stream()
				.map(z -> new CardPane(z.name(), deck.cards(z)))
				.peek(pane -> pane.model().addListener(deckListChangedListener))
				.peek(pane -> pane.view().doubleClick(ci -> {
					pane.model().remove(ci);
					collection.view().scheduleRender();
				}))
				.collect(Collectors.toList()));
	}

	private void newDeck(Format format) {
		DeckList newDeck = new DeckList("", Context.get().preferences.authorName, format, "", Collections.emptyMap());
		MainWindow window = new MainWindow(this.owner, newDeck);
		window.show();
	}

	@FXML
	protected void openDeck() throws IOException {
		if (!offerSaveIfModified()) {
			return;
		}

		File from = primaryFileChooser.showOpenDialog(this);

		if (from == null) {
			return;
		}

		try {
			if(checkDeckForVariants(from)) {
				return;
			}

			DeckList list = primarySerdes.importDeck(from);
			if (currentDeckFile == null && !deckModified) {
				setDeck(list);
			} else {
				MainWindow window = new MainWindow(this.owner, primarySerdes.importDeck(from));
				window.currentDeckFile = from;
				window.show();
			}
		} catch (IOException ioe) {
			ioe.printStackTrace();
			error("Open Error", "An error occurred while opening:", ioe.getMessage()).showAndWait();
		}
	}

	private static class DeckListWithVariants {
		private static class Variant {
			public String name;
			public String description;
			public Map<Zone, List<CardInstance>> cards;
		}

		public String name;
		public Format format;
		public String author;
		public String description;
		public List<Variant> variants;

		public DeckList toDeckList(Variant var) {
			return new DeckList(this.name + (var != null && var.name != null && !var.name.isEmpty() ? var.name : ""),
					this.author,
					this.format,
					var != null && var.description != null && !var.description.isEmpty() ? var.description : this.description,
					var != null && var.cards != null ? var.cards : Collections.emptyMap());
		}
	}

	private boolean checkDeckForVariants(File f) throws IOException {
		java.io.FileReader reader = new java.io.FileReader(f);
		DeckListWithVariants lwv = Context.get().gson.getAdapter(DeckListWithVariants.class).fromJson(reader);

		if (lwv == null || lwv.variants == null) {
			return false;
		}

		if (lwv.variants.size() == 1 && currentDeckFile == null && !deckModified) {
			DeckListWithVariants.Variant var = lwv.variants.iterator().next();
			setDeck(lwv.toDeckList(var));
			currentDeckFile = f;
			return true;
		}

		int unnamed = 0;
		for (DeckListWithVariants.Variant var : lwv.variants) {
			MainWindow window = new MainWindow(this.owner, lwv.toDeckList(var));
			String name = var.name == null || var.name.isEmpty() ? Integer.toString(++unnamed) : var.name;
			window.currentDeckFile = new File(f.getParent(), String.format("%s-%s.json", f.getName().replace(".json", ""), name));
			window.saveDeck();
			window.show();
		}

		if (lwv.variants.size() > 1) {
			information("Information",
					"Variants",
					"A deck with variants has been opened.\n" +
							"This feature has been deprecated.\n" +
							"All variants have been opened as separate decks.\n" +
							"They've been saved to the deck's directory.").show();
		}

		return true;
	}

	protected boolean offerSaveIfModified() {
		if (deckModified) {
			Alert alert = alert(Alert.AlertType.CONFIRMATION, "Deck Modified", "Deck has been modified.", "Would you like to save this deck?", ButtonType.YES, ButtonType.NO, ButtonType.CANCEL);
			ButtonType type = alert.showAndWait().orElse(ButtonType.CANCEL);

			if (type == ButtonType.CANCEL) {
				return false;
			}

			if (type == ButtonType.YES) {
				return doSaveDeck();
			}
		}

		return true;
	}

	@FXML
	protected void saveDeck() {
		doSaveDeck();
	}

	protected boolean doSaveDeck() {
		if (currentDeckFile == null) {
			return doSaveDeckAs();
		} else {
			return saveDeck(currentDeckFile);
		}
	}

	@FXML
	protected void saveDeckAs() {
		doSaveDeckAs();
	}

	protected boolean doSaveDeckAs() {
		File to = primaryFileChooser.showSaveDialog(this);

		if (to == null) {
			return false;
		}

		return saveDeck(to);
	}

	private boolean saveDeck(File to) {
		try {
			primarySerdes.exportDeck(deck, to);
			deckModified = false;
			currentDeckFile = to;
			return true;
		} catch (IOException ioe) {
			ioe.printStackTrace();
			error("Save Error", "An error occurred while saving:", ioe.getMessage()).showAndWait();
			return false;
		}
	}

	private boolean warnAboutSerdes(Set<Features> unsupportedFeatures) {
		StringBuilder builder = new StringBuilder();

		builder.append("The file format you selected doesn't support the following features:\n");

		for (Features feature : unsupportedFeatures) {
			if (feature == Features.Import || feature == Features.Export) {
				continue;
			}

			builder.append(" \u2022 ").append(feature.toString()).append('\n');
		}

		if (unsupportedFeatures.contains(Features.Import)) {
			builder.append('\n').append("Additionally, you will not be able to re-import from this file.");
		} else if (unsupportedFeatures.contains(Features.Export)) {
			builder.append('\n').append("Additionally, you will not be able to re-export to this file.");
		}

		builder.append('\n').append("Is this okay?");

		return confirmation("Warning", "Some information may be lost:", builder.toString())
				.showAndWait()
				.orElse(ButtonType.NO) == ButtonType.YES;
	}

	@FXML
	protected void showDeckInfoDialog() {
		try {
			DeckInfoDialog did = new DeckInfoDialog(deck);
			did.initOwner(this);

			if(did.showAndWait().orElse(false)) {
				setDeck(deck); // Reset the view.
			}
		} catch (IOException e) {
			e.printStackTrace();
		}
	}

	@FXML
	protected void actionQuit() {
		owner.closeAllWindows();
	}

	@FXML
	protected void showTipsAndTricks() {
		Alert alert = information("Program Usage", "Tips and Tricks",
				"The UI of this program is really dense! Here are some bits on some subtle\n"
				+ "but powerful features!\n"
				+ "\n"
				+ "Card versions:\n"
				+ "\u2022 Alt+Click on cards to show all printings.\n"
				+ "\u2022 Double-click a printing to change the version of the card you clicked on.\n"
				+ "\u2022 Application -> Save Preferences to remember chosen versions in the Collection.\n"
				+ "\n"
				+ "Tags:\n"
				+ "\u2022 Application -> Manage Tags to define categories for cards.\n"
				+ "\u2022 Change any view to Grouping -> Tags to group cards by their tags.\n"
				+ "\u2022 While grouped by tags, drag cards to their tag groups to assign tags!\n"
				+ "\u2022 You can even Control+Drag to assign multiple tags to a card!\n"
				+ "\u2022 Search for cards by tag with the 'tag' filter: \"tag:wrath\"\n"
				+ "\n"
				+ "I never claimed to be good at UI design! :^)");
		alert.getDialogPane().setPrefWidth(550.0);
		alert.showAndWait();
	}

	@FXML
	protected void showFilterSyntax() {
		Alert alert = information("Syntax Help", "Omnifilter Syntax",
				"General:\n"
				+ "\u2022 Separate search terms with a space.\n"
				+ "\u2022 Search terms that don't start with a key and operator search card names.\n"
				+ "\n"
				+ "Operators:\n"
				+ "\u2022 ':' \u2014 Meaning varies.\n"
				+ "\u2022 '=' \u2014 Must match the value exactly.\n"
				+ "\u2022 '!=' \u2014 Must not exactly match the value.\n"
				+ "\u2022 '>=' \u2014 Must contain the value.\n"
				+ "\u2022 '>' \u2014 Must contain the value and more.\n"
				+ "\u2022 '<=' \u2014 Value must completely contain the characteristic.\n"
				+ "\u2022 '<' \u2014 Value must contain the characteristic and more.\n"
				+ "\n"
				+ "Search keys:\n"
				+ "\u2022 'type' or 't' \u2014 Supertype/type/subtype. (Use ':' or '>='.)\n"
				+ "\u2022 'text' or 'o' \u2014 Rules text. (Use ':' or '>='.)\n"
				+ "\u2022 'identity' or 'ci' \u2014 Color identity. (':' means '<='.)\n"
				+ "\u2022 'color' or 'c' \u2014 Color. (':' means '<=')\n"
				+ "\u2022 'cmc' \u2014 Converted mana cost. (':' means '==').\n"
				+ "\n"
				+ "Examples:\n"
				+ "\u2022 'color=rug t:legendary' \u2014 Finds all RUG commanders.\n"
				+ "\u2022 't:sorcery cmc>=8' \u2014 Finds good cards for Spellweaver Helix.\n"
				+ "\u2022 'o:when o:\"enters the battlefield\" t:creature' \u2014 Finds creatures with ETB effects.\n"
				+ "\n"
				+ "Upcoming features:\n"
				+ "\u2022 Logic \u2014 And, or, not, and parenthetical grouping.\n"
				+ "\u2022 More keys \u2014 e.g. Mana cost.");
		alert.getDialogPane().setPrefWidth(550.0);
		alert.showAndWait();
	}

	@FXML
	protected void showAboutDialog() {
		information("About Deck Builder", "Deck Builder v0.0.0",
				"Developer: Emi (@DeaLumi)\n" +
				"Data & Images: Scryfall (@Scryfall)\n" +
				"\n" +
				"Source code will be available at some point probably. Feel free to DM me with feedback/issues on Twitter!\n" +
				"\n" +
				"Special thanks to MagnetMan, for generously indulging my madness time and time again.\n")
				.showAndWait();
	}

	@FXML
	protected void showTagManagementDialog() {
		try {
			TagManagementDialog dlg = new TagManagementDialog();
			dlg.initOwner(this);
			dlg.showAndWait();
		} catch (IOException ioe) {
			ioe.printStackTrace(); // TODO: Handle gracefully.
		}
	}

	@FXML
	protected void saveTags() {
		try {
			Context.get().saveTags(); // TODO: Move this to controller?
		} catch (IOException ioe) {
			ioe.printStackTrace(); // TODO: Handle gracefully
		}
	}

	private Format.ValidationResult updateDeckValidity() {
		Format.ValidationResult result = deck.validate();

		deck.cards().values().stream()
				.flatMap(ObservableList::stream)
				.forEach(ci -> {
					if (result.cardErrors.containsKey(ci)) {
						ci.flags.add(CardInstance.Flags.Invalid);
					} else {
						ci.flags.remove(CardInstance.Flags.Invalid);
					}
				});

		return result;
	}

	@FXML
	protected void validateDeck() {
		Format.ValidationResult result = updateDeckValidity();

		if (result.deckErrors.isEmpty() && result.zoneErrors.values().stream().allMatch(Set::isEmpty) && result.cardErrors.values().stream().allMatch(Set::isEmpty)) {
			information("Deck Validation", "Deck is valid.", "No validation errors were found!").showAndWait();
		} else {
			StringBuilder msg = new StringBuilder();
			for (String err : result.deckErrors) {
				msg.append("\u2022 ").append(err).append("\n");
			}

			for (Map.Entry<Zone, Set<String>> zone : result.zoneErrors.entrySet()) {
				if (zone.getValue().isEmpty()) continue;

				msg.append("\n").append(zone.getKey().name()).append(":\n");
				for (String err : zone.getValue()) {
					msg.append("\u2022 ").append(err).append("\n");
				}
			}

			Set<String> cardErrors = result.cardErrors.values().stream().flatMap(Set::stream).collect(Collectors.toSet());
			if (!cardErrors.isEmpty()) {
				msg.append("\nCard errors:\n");
				for (String err : cardErrors) {
					msg.append("\u2022 ").append(err).append("\n");
				}
			}

			error("Deck Validation", "Deck has errors:", msg.toString().trim()).showAndWait();
		}
	}

	@FXML
	protected void showPreferencesDialog() throws IOException {
		try {
			PreferencesDialog pd = new PreferencesDialog(Context.get().preferences);
			pd.initOwner(this);

			if(pd.showAndWait().orElse(false)) {
				Context.get().savePreferences();
			}
		} catch (IOException e) {
			e.printStackTrace();
		}
	}

	@FXML
	protected void updateDeckbuilder() throws IOException {
		owner.update();
	}

	@FXML
	protected void updateData() throws IOException {
		owner.updateData();
	}

	@FXML
	protected void remodel() {
		collection.view().model(new ReadOnlyListWrapper<>(collectionModel(Context.get().data)));
		updateCollectionValidity();
		updateDeckValidity();
		// TODO We actually need to rebuild the deck, here. Update all card instances to point to printings from the new data source.
	}

	@FXML
	protected void importDeck() {
		if (!offerSaveIfModified()) {
			return;
		}

		File f = serdesFileChooser.showOpenDialog(this);

		if (f == null) {
			return;
		}

		DeckImportExport importer = deckSerdes.get(serdesFileChooser.getSelectedExtensionFilter());

		if (!importer.unsupportedFeatures().isEmpty()) {
			if (!warnAboutSerdes(importer.unsupportedFeatures())) {
				return;
			}
		}

		try {
			setDeck(importer.importDeck(f));
		} catch (IOException ioe) {
			ioe.printStackTrace();
			error("Import Error", "An error occurred while importing:", ioe.getMessage()).showAndWait();
		}
	}

	@FXML
	protected void exportDeck() {
		File f = serdesFileChooser.showSaveDialog(this);

		if (f == null) {
			return;
		}

		DeckImportExport exporter = deckSerdes.get(serdesFileChooser.getSelectedExtensionFilter());

		if (!exporter.unsupportedFeatures().isEmpty()) {
			if (!warnAboutSerdes(exporter.unsupportedFeatures())) {
				return;
			}
		}

		try {
			exporter.exportDeck(deck, f);
		} catch (IOException ioe) {
			ioe.printStackTrace();
			error("Export Error", "An error occurred while exporting:", ioe.getMessage()).showAndWait();
		}
	}

	private Alert confirmation(String title, String headerText, String text) {
		Alert confirm = new Alert(Alert.AlertType.CONFIRMATION, text, ButtonType.YES, ButtonType.NO);
		confirm.setTitle(title);
		confirm.setHeaderText(headerText);
		confirm.initOwner(this);
		return confirm;
	}

	private Alert information(String title, String headerText, String text) {
		return notification(Alert.AlertType.INFORMATION, title, headerText, text);
	}

	private Alert error(String title, String headerText, String text) {
		return notification(Alert.AlertType.ERROR, title, headerText, text);
	}

	private Alert warning(String title, String headerText, String text) {
		return notification(Alert.AlertType.WARNING, title, headerText, text);
	}

	private Alert notification(Alert.AlertType type, String title, String headerText, String text) {
		Alert notification = new Alert(type, text, ButtonType.OK);
		notification.setTitle(title);
		notification.setHeaderText(headerText);
		notification.initOwner(this);
		return notification;
	}

	private Alert alert(Alert.AlertType type, String title, String headerText, String text, ButtonType... buttons) {
		Alert alert = new Alert(type, text, buttons);
		alert.setTitle(title);
		alert.setHeaderText(headerText);
		alert.initOwner(this);
		return alert;
	}
}
