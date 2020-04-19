package emi.mtg.deckbuilder.view;

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
import emi.mtg.deckbuilder.view.layouts.FlowGrid;
import emi.mtg.deckbuilder.view.util.AlertBuilder;
import javafx.beans.binding.Bindings;
import javafx.beans.property.ReadOnlyListWrapper;
import javafx.collections.FXCollections;
import javafx.collections.ListChangeListener;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.BorderPane;
import javafx.stage.FileChooser;
import javafx.stage.Stage;

import java.io.File;
import java.io.IOException;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;

public class MainWindow extends Stage {
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

	private final ListChangeListener<Object> deckListChangedListener = e -> {
		updateCardStates();
		deckModified = true;
	};

	public MainWindow(MainApplication owner, DeckList deck) {
		super();

		this.owner = owner;
		this.owner.registerMainWindow(this);

		BorderPane root = new BorderPane();
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

			owner.deregisterMainWindow(this);
		});

		Alert alert = AlertBuilder.create()
				.owner(this)
				.title("Initializing UI")
				.headerText("Getting things ready...")
				.contentText("Please wait a moment!")
				.show();

		setupUI();
		setupImportExport();
		setDeck(deck);

		collection.model().setAll(new ReadOnlyListWrapper<>(collectionModel(Context.get().data)));

		alert.getButtonTypes().setAll(ButtonType.CLOSE);
		alert.hide();
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

	private ObservableList<CardInstance> collectionModel(DataSource cs) {
		return FXCollections.observableList(cs.printings().stream()
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

		collection = new CardPane("Collection", FXCollections.observableArrayList(), FlowGrid.Factory.INSTANCE, CardView.DEFAULT_COLLECTION_SORTING);
		collection.view().immutableModelProperty().set(true);
		collection.view().doubleClick(ci -> deckPane(Zone.Library).model().add(new CardInstance(ci.printing())));
		collection.view().contextMenu(collectionContextMenu);
		collection.showIllegalCards.set(false);
		collection.showVersionsSeparately.set(false);

		this.collectionSplitter.getItems().add(0, collection);
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

		this.deckSerdes = StreamSupport.stream(ServiceLoader.load(DeckImportExport.class, MainApplication.PLUGIN_CLASS_LOADER).spliterator(), false)
				.collect(Collectors.toMap(
						vies -> new FileChooser.ExtensionFilter(String.format("%s (*.%s)", vies.toString(), vies.extension()), String.format("*.%s", vies.extension())),
						vies -> vies
				));

		this.serdesFileChooser = new FileChooser();
		this.serdesFileChooser.getExtensionFilters().setAll(this.deckSerdes.keySet());
	}

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
		updateCardStates();

		collection.updateFilter();

		deckSplitter.getItems().setAll(deck.format().deckZones().stream()
				.map(z -> new CardPane(z.name(), deck.cards(z)))
				.peek(pane -> pane.model().addListener(deckListChangedListener))
				.peek(pane -> pane.view().doubleClick(ci -> pane.model().remove(ci)))
				.collect(Collectors.toList()));
	}

	private void newDeck(Format format) {
		DeckList newDeck = new DeckList("", Context.get().preferences.authorName, format, "", Collections.emptyMap());

		if (currentDeckFile == null && deckIsEmpty()) {
			setDeck(newDeck);
		} else {
			MainWindow window = new MainWindow(this.owner, newDeck);
			window.show();
		}
	}

	@FXML
	protected void openDeck() {
		File from = primaryFileChooser.showOpenDialog(this);

		if (from == null) {
			return;
		}

		try {
			if(checkDeckForVariants(from)) {
				return;
			}

			DeckList list = primarySerdes.importDeck(from);
			if (currentDeckFile == null && deckIsEmpty()) {
				currentDeckFile = from;
				setDeck(list);
			} else {
				MainWindow window = new MainWindow(this.owner, list);
				window.currentDeckFile = from;
				window.show();
			}
		} catch (IOException ioe) {
			ioe.printStackTrace();
			AlertBuilder.notify(this)
					.type(Alert.AlertType.ERROR)
					.title("Open Error")
					.headerText("An error occurred while opening:")
					.contentText(ioe.getMessage())
					.showAndWait();
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

	private static final String VARIANTS_QUERY = String.join("\n",
			"This feature has been deprecated.",
			"Open all variants separately?");

	private static final String VARIANTS_OPENED = String.join("\n",
			"You should save each of them separately!");

	private boolean checkDeckForVariants(File f) throws IOException {
		java.io.FileReader reader = new java.io.FileReader(f);
		DeckListWithVariants lwv = Context.get().gson.getAdapter(DeckListWithVariants.class).fromJson(reader);

		if (lwv == null || lwv.variants == null) {
			return false;
		}

		if (lwv.variants.size() == 1 && currentDeckFile == null && deckIsEmpty()) {
			DeckListWithVariants.Variant var = lwv.variants.iterator().next();
			setDeck(lwv.toDeckList(var));
			currentDeckFile = f;
			return true;
		}

		if (lwv.variants.size() > 1) {
			ButtonType result = AlertBuilder.query(this)
					.title("Variants")
					.headerText("A deck with variants was opened.")
					.contentText(VARIANTS_QUERY)
					.showAndWait().orElse(ButtonType.NO);

			if (result != ButtonType.YES) {
				return true;
			}
		}

		for (DeckListWithVariants.Variant var : lwv.variants) {
			MainWindow window = new MainWindow(this.owner, lwv.toDeckList(var));
			window.deckModified = true;
			window.show();
		}

		if (lwv.variants.size() > 1) {
			AlertBuilder.notify(this)
					.title("Variants Opened")
					.headerText("All variants have been opened.")
					.contentText(VARIANTS_OPENED)
					.show();
		}

		return true;
	}

	protected boolean offerSaveIfModified() {
		if (deckModified) {
			ButtonType type = AlertBuilder.query(this)
					.type(Alert.AlertType.WARNING)
					.title("Deck Modified")
					.headerText("Deck has been modified.")
					.contentText("Would you like to save this deck?")
					.buttons(ButtonType.YES, ButtonType.NO, ButtonType.CANCEL)
					.showAndWait().orElse(ButtonType.CANCEL);

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
			AlertBuilder.notify(this)
					.type(Alert.AlertType.ERROR)
					.title("Save Error")
					.headerText("An error occurred while saving:")
					.contentText(ioe.getMessage())
					.showAndWait();
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

		return AlertBuilder.query(this)
				.title("Warning")
				.headerText("Some information may be lost:")
				.contentText(builder.toString())
				.showAndWait()
				.orElse(ButtonType.NO) != ButtonType.YES;
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

	private static final String TIPS_AND_TRICKS = String.join("\n",
			"The UI of this program is really dense! Here are some bits on some subtle",
			"but powerful features!",
			"",
			"Card versions:",
			"\u2022 Alt+Click on cards to show all printings.",
			"\u2022 Double-click a printing to change the version of the card you clicked on.",
			"\u2022 Application -> Save Preferences to remember chosen versions in the Collection.",
			"",
			"Tags:",
			"\u2022 Application -> Manage Tags to define categories for cards.",
			"\u2022 Change any view to Grouping -> Tags to group cards by their tags.",
			"\u2022 While grouped by tags, drag cards to their tag groups to assign tags!",
			"\u2022 You can even Control+Drag to assign multiple tags to a card!",
			"\u2022 Search for cards by tag with the 'tag' filter: \"tag:wrath\"",
			"",
			"I never claimed to be good at UI design! :^)");

	@FXML
	protected void showTipsAndTricks() {
		AlertBuilder.notify(this)
				.title("Program Usage")
				.headerText("Tips and Tricks")
				.contentText(TIPS_AND_TRICKS)
				.showAndWait();
	}

	private static final String FILTER_SYNTAX = String.join("\n",
			"General:",
			"\u2022 Separate search terms with a space.",
			"\u2022 Search terms that don't start with a key and operator search card names.",
			"",
			"Operators:",
			"\u2022 ':' \u2014 Meaning varies.",
			"\u2022 '=' \u2014 Must match the value exactly.",
			"\u2022 '!=' \u2014 Must not exactly match the value.",
			"\u2022 '>=' \u2014 Must contain the value.",
			"\u2022 '>' \u2014 Must contain the value and more.",
			"\u2022 '<=' \u2014 Value must completely contain the characteristic.",
			"\u2022 '<' \u2014 Value must contain the characteristic and more.",
			"",
			"Search keys:",
			"\u2022 'type' or 't' \u2014 Supertype/type/subtype. (Use ':' or '>='.)",
			"\u2022 'text' or 'o' \u2014 Rules text. (Use ':' or '>='.)",
			"\u2022 'identity' or 'ci' \u2014 Color identity. (':' means '<='.)",
			"\u2022 'color' or 'c' \u2014 Color. (':' means '<=')",
			"\u2022 'cmc' \u2014 Converted mana cost. (':' means '==').",
			"",
			"Examples:",
			"\u2022 'color=rug t:legendary' \u2014 Finds all RUG commanders.",
			"\u2022 't:sorcery cmc>=8' \u2014 Finds good cards for Spellweaver Helix.",
			"\u2022 'o:when o:\"enters the battlefield\" t:creature' \u2014 Finds creatures with ETB effects.",
			"",
			"Upcoming features:",
			"\u2022 Logic \u2014 And, or, not, and parenthetical grouping.",
			"\u2022 More keys \u2014 e.g. Mana cost.");

	@FXML
	protected void showFilterSyntax() {
		AlertBuilder.notify(this)
				.title("Syntax Help")
				.headerText("Omnifilter Syntax")
				.contentText(FILTER_SYNTAX)
				.showAndWait();
	}

	private static final String ABOUT_TEXT = String.join("\n",
			"Developer: Emi (@DeaLumi)",
			"Data & Images: Scryfall (@Scryfall)",
			"",
			"Source code will be available at some point probably.",
			"Feel free to DM me with feedback/issues on Twitter!",
			"",
			"Special thanks to MagnetMan, for generously indulging my madness time and time again.");

	@FXML
	protected void showAboutDialog() {
		AlertBuilder.notify(this)
				.title("About Deck Builder")
				.headerText("Deck Builder v0.0.0")
				.contentText(ABOUT_TEXT)
				.showAndWait();
	}

	@FXML
	protected void showTagManagementDialog() {
		owner.showTagManagementDialog();

		collection.view().regroup();
		for (Zone zone : deck.format().deckZones()) {
			deckPane(zone).view().regroup();
		}
	}

	@FXML
	protected void saveTags() {
		owner.saveTags();
	}

	private Format.ValidationResult updateCardStates() {
		Format.ValidationResult result = deck.validate();
		Map<Card, AtomicInteger> histogram = new HashMap<>();

		deck.cards().values().stream()
				.flatMap(ObservableList::stream)
				.peek(ci -> {
					if (result.cardErrors.containsKey(ci)) {
						ci.flags.add(CardInstance.Flags.Invalid);
					} else {
						ci.flags.remove(CardInstance.Flags.Invalid);
					}
				})
				.map(CardInstance::card)
				.forEach(c -> histogram.computeIfAbsent(c, x -> new AtomicInteger(0)).incrementAndGet());
		for (Node zonePane : deckSplitter.getItems()) {
			if (zonePane instanceof CardPane) {
				((CardPane) zonePane).view().scheduleRender();
			}
		}

		histogram.entrySet().removeIf(e -> e.getValue().get() < deck.format().maxCopies);

		collection.model()
				.forEach(ci -> {
					ci.flags.remove(CardInstance.Flags.Invalid);
					ci.flags.remove(CardInstance.Flags.Full);
					flagCardLegality(ci);
					if (histogram.containsKey(ci.card())) {
						ci.flags.add(CardInstance.Flags.Full);
					} else {
						ci.flags.remove(CardInstance.Flags.Full);
					}
				});
		collection.view().scheduleRender();

		return result;
	}

	@FXML
	protected void validateDeck() {
		Format.ValidationResult result = updateCardStates();

		if (result.deckErrors.isEmpty() && result.zoneErrors.values().stream().allMatch(Set::isEmpty) && result.cardErrors.values().stream().allMatch(Set::isEmpty)) {
			AlertBuilder.notify(this)
					.title("Deck Validation")
					.headerText("Deck is valid.")
					.contentText("No validation errors were found!")
					.showAndWait();
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

			AlertBuilder.notify(this)
					.type(Alert.AlertType.ERROR)
					.title("Deck Validation")
					.headerText("Deck has errors:")
					.contentText(msg.toString().trim())
					.showAndWait();
		}
	}

	@FXML
	protected void showPreferencesDialog() {
		owner.showPreferences();
	}

	@FXML
	protected void updateDeckbuilder() {
		owner.update();
	}

	@FXML
	protected void updateData() {
		owner.updateData();
	}

	@FXML
	protected void remodel() {
		collection.model().setAll(new ReadOnlyListWrapper<>(collectionModel(Context.get().data)));

		// We need to fix all the card instances in the current deck. They're hooked to old objects.
		deck.cards().values().stream()
				.flatMap(ObservableList::stream)
				.forEach(CardInstance::refreshInstance);

		updateCardStates();
	}

	private boolean deckIsEmpty() {
		return deck.cards().values().stream().allMatch(List::isEmpty);
	}

	@FXML
	protected void importDeck() {
		File f = serdesFileChooser.showOpenDialog(this);

		if (f == null) {
			return;
		}

		DeckImportExport importer = deckSerdes.get(serdesFileChooser.getSelectedExtensionFilter());

		if (!importer.unsupportedFeatures().isEmpty()) {
			if (warnAboutSerdes(importer.unsupportedFeatures())) {
				return;
			}
		}

		try {
			DeckList list = importer.importDeck(f);
			if (currentDeckFile == null && deckIsEmpty()) {
				setDeck(list);
			} else {
				new MainWindow(this.owner, list).show();
			}
		} catch (IOException ioe) {
			ioe.printStackTrace();
			AlertBuilder.notify(this)
					.type(Alert.AlertType.ERROR)
					.title("Import Error")
					.headerText("An error occurred while importing:")
					.contentText(ioe.getMessage())
					.showAndWait();
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
			if (warnAboutSerdes(exporter.unsupportedFeatures())) {
				return;
			}
		}

		try {
			exporter.exportDeck(deck, f);
		} catch (IOException ioe) {
			ioe.printStackTrace();
			AlertBuilder.notify(this)
					.type(Alert.AlertType.ERROR)
					.title("Export Error")
					.headerText("An error occurred while exporting:")
					.contentText(ioe.getMessage())
					.showAndWait();
		}
	}
}
