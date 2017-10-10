package emi.mtg.deckbuilder.view;

import com.sun.javafx.collections.ObservableListWrapper;
import emi.lib.Service;
import emi.lib.mtg.DataSource;
import emi.lib.mtg.game.Format;
import emi.lib.mtg.game.Zone;
import emi.mtg.deckbuilder.controller.Context;
import emi.mtg.deckbuilder.controller.DeckImportExport;
import emi.mtg.deckbuilder.controller.Updater;
import emi.mtg.deckbuilder.controller.serdes.Json;
import emi.mtg.deckbuilder.model.CardInstance;
import emi.mtg.deckbuilder.model.DeckList;
import emi.mtg.deckbuilder.view.components.CardPane;
import emi.mtg.deckbuilder.view.components.CardView;
import emi.mtg.deckbuilder.view.dialogs.DeckInfoDialog;
import emi.mtg.deckbuilder.view.dialogs.DeckStatsDialog;
import emi.mtg.deckbuilder.view.dialogs.TagManagementDialog;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.beans.property.ReadOnlyListWrapper;
import javafx.collections.ObservableList;
import javafx.event.ActionEvent;
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
import java.net.URI;
import java.net.URISyntaxException;
import java.util.Collections;
import java.util.EnumMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ForkJoinPool;
import java.util.stream.Collectors;

public class MainWindow extends Application {
	public static void main(String[] args) {
		launch(args);
	}

	private ObservableList<CardInstance> collectionModel(DataSource cs) {
		return new ObservableListWrapper<>(cs.printings().stream()
				.map(CardInstance::new)
				.collect(Collectors.toList()));
	}

	@FXML
	private BorderPane root;

	@FXML
	private SplitPane collectionSplitter;

	@FXML
	private SplitPane zoneSplitter;

	@FXML
	private Menu newDeckMenu;

	@FXML
	private Menu importDeckMenu;

	@FXML
	private Menu exportDeckMenu;

	@FXML
	private MenuItem reexportMenuItem;

	private Context context;

	private CardPane collection;
	private Map<Zone, CardPane> deckPanes;

	private FileChooser primaryFileChooser;
	private DeckImportExport primarySerdes;
	private File currentDeckFile;

	private Map<FileChooser.ExtensionFilter, DeckImportExport> importExports;
	private FileChooser importFileChooser;
	private DeckImportExport reexportSerdes;
	private File reexportFile;

	public MainWindow() {
		Thread.setDefaultUncaughtExceptionHandler((x, e) -> {
			boolean deckSaved = true;

			try {
				primarySerdes.exportDeck(context.deck, new File("emergency-dump.json"));
			} catch (IOException ioe) {
				deckSaved = false;
			}

			e.printStackTrace();
			e.printStackTrace(new PrintWriter(System.out));

			StringWriter stackTrace = new StringWriter();
			e.printStackTrace(new PrintWriter(stackTrace));

			Alert alert = new Alert(Alert.AlertType.ERROR);
			alert.initOwner(stage);

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
	}

	private Stage stage;

	@Override
	public void init() throws Exception {
		this.context = new Context();
	}

	@Override
	public void start(Stage stage) throws Exception {
		this.stage = stage;

		this.stage.setOnCloseRequest(we -> {
			try {
				context.savePreferences();
			} catch (IOException e) {
				throw new RuntimeException(e);
			}
		});

		FXMLLoader loader = new FXMLLoader(getClass().getResource("MainWindow.fxml"));
		loader.setController(this);
		loader.load();

		setupUI();
		setupImportExport();

		stage.setTitle("Deck Builder v0.0.0");

		stage.setScene(new Scene(root, 1024, 1024));
		stage.setMaximized(true);
		stage.show();

		if (context.data.needsUpdate()) {
			Alert confirm = new Alert(Alert.AlertType.CONFIRMATION, "Data source seems stale -- update?", ButtonType.YES, ButtonType.NO);
			confirm.initOwner(stage);
			if (confirm.showAndWait().orElse(ButtonType.NO) == ButtonType.YES) {
				doGraphicalDataUpdate();
			}
		}
	}

	private void setupUI() {
		collection = new CardPane("Collection", context, new ReadOnlyListWrapper<>(collectionModel(context.data)), "Flow Grid", CardView.DEFAULT_COLLECTION_SORTING);
		collection.view().immutableModelProperty().set(true);
		collection.view().doubleClick(ci -> this.deckPanes.get(Zone.Library).model().add(new CardInstance(ci.printing())));
		collection.showIllegalCards.set(false);
		collection.showVersionsSeparately.set(false);

		this.collectionSplitter.getItems().add(0, collection);

		this.deckPanes = new EnumMap<>(Zone.class);

		for (Zone zone : Zone.values()) {
			CardPane deckZone = new CardPane(zone.name(), context, context.deck.primaryVariant.get().cards(zone), "Piles", CardView.DEFAULT_SORTING);
			deckZone.view().doubleClick(ci -> deckZone.model().remove(ci));
			deckPanes.put(zone, deckZone);
		}

		setFormat(Context.FORMATS.get("Standard"));
	}

	private void setupImportExport() {
		this.primarySerdes = new Json(context);

		this.primaryFileChooser = new FileChooser();
		this.primaryFileChooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("JSON Files (*.json)", "*.json"));

		this.importExports = Service.Loader.load(DeckImportExport.class).stream()
				.collect(Collectors.toMap(
						dies -> new FileChooser.ExtensionFilter(String.format("%s (*.%s)", dies.string("name"), dies.string("extension")), String.format("*.%s", dies.string("extension"))),
						dies -> dies.uncheckedInstance(context)));

		this.importFileChooser = new FileChooser();
		this.importFileChooser.getExtensionFilters().setAll(importExports.keySet());

		for (Format format : Context.FORMATS.values()) {
			MenuItem item = new MenuItem(format.name());
			item.setOnAction(ae -> newDeck(format));
			this.newDeckMenu.getItems().add(item);
		}

		for (Map.Entry<FileChooser.ExtensionFilter, DeckImportExport> dies : importExports.entrySet()) {
			MenuItem importItem = new MenuItem(dies.getKey().getDescription());
			importItem.setOnAction(ae -> {
				importFileChooser.setSelectedExtensionFilter(dies.getKey());
				importDeck();
			});

			this.importDeckMenu.getItems().add(importItem);

			MenuItem exportItem = new MenuItem(dies.getKey().getDescription());
			exportItem.setOnAction(ae -> {
				importFileChooser.setSelectedExtensionFilter(dies.getKey());
				exportDeck();
			});

			this.exportDeckMenu.getItems().add(exportItem);
		}

		this.reexportMenuItem.setDisable(true);
	}

	private void setFormat(Format format) {
		context.deck.formatProperty().setValue(format);

		collection.updateFilter();

		int i = 0;
		for (Zone zone : Zone.values()) {
			if (zoneSplitter.getItems().contains(deckPanes.get(zone))) {
				if (!format.deckZones().contains(zone)) {
					zoneSplitter.getItems().remove(deckPanes.get(zone));
				} else {
					deckPanes.get(zone).updateFilter();
					++i;
				}
			} else if (format.deckZones().contains(zone)) {
				zoneSplitter.getItems().add(i, deckPanes.get(zone));
				deckPanes.get(zone).updateFilter();
				++i;
			}
		}
	}

	private void setDeck(DeckList deck) {
		context.deck = deck;

		for (Zone zone : Zone.values()) {
			deckPanes.get(zone).view().model(context.deck.primaryVariant.get().cards(zone));
		}

		if (deck.formatProperty().getValue() != null) {
			setFormat(deck.formatProperty().getValue());
		}
	}

	private void newDeck(Format format) {
		setFormat(format);

		DeckList newDeck = new DeckList("", "", format, "", Collections.emptyMap());
		setDeck(newDeck);

		reexportFile = null;
		reexportSerdes = null;
		reexportMenuItem.setDisable(true);
	}

	@FXML
	protected void newDeck() {
		newDeck(context.preferences.defaultFormat);
		currentDeckFile = null;

		reexportSerdes = null;
		reexportFile = null;
	}

	@FXML
	protected void openDeck() throws IOException {
		File from = primaryFileChooser.showOpenDialog(this.stage);

		if (from == null) {
			return;
		}

		try {
			setDeck(primarySerdes.importDeck(from));
			currentDeckFile = from;

			reexportSerdes = null;
			reexportFile = null;
		} catch (IOException ioe) {
			Alert alert = new Alert(Alert.AlertType.ERROR, ioe.getMessage(), ButtonType.OK);
			alert.setHeaderText("An error occurred while opening:");
			alert.initOwner(this.stage);
			alert.showAndWait();
		}
	}

	@FXML
	protected void saveDeck() {
		if (currentDeckFile == null) {
			saveDeckAs();
		} else {
			saveDeck(currentDeckFile);
		}
	}

	@FXML
	protected void saveDeckAs() {
		File to = primaryFileChooser.showSaveDialog(this.stage);

		if (to == null) {
			return;
		}

		saveDeck(to);
	}

	private void saveDeck(File to) {
		try {
			primarySerdes.exportDeck(context.deck, to);
			currentDeckFile = to;
		} catch (IOException ioe) {
			Alert alert = new Alert(Alert.AlertType.ERROR, ioe.getMessage(), ButtonType.OK);
			alert.setHeaderText("An error occurred while saving:");
			alert.initOwner(this.stage);
			alert.showAndWait();
		}
	}

	private boolean warnAboutSerdes(DeckImportExport die) {
		StringBuilder builder = new StringBuilder();

		builder.append("The file format you selected doesn't support the following features:\n");

		for (DeckImportExport.Features feature : die.unsupportedFeatures()) {
			builder.append(" \u2022 ").append(feature.toString()).append('\n');
		}

		builder.append('\n').append("Is this okay?");

		Alert alert = new Alert(Alert.AlertType.WARNING, builder.toString(), ButtonType.YES, ButtonType.NO);
		alert.initOwner(this.stage);

		return alert.showAndWait().orElse(null) == ButtonType.YES;
	}

	protected void importDeck() {
		File f = this.importFileChooser.showOpenDialog(this.stage);
		DeckImportExport die = importExports.get(this.importFileChooser.getSelectedExtensionFilter());

		if (f == null) {
			return;
		}

		reexportFile = f;
		reexportSerdes = die;
		reexportMenuItem.setDisable(false);

		importDeck(f, die);
	}

	protected void importDeck(File from, DeckImportExport serdes) {
		if (!serdes.unsupportedFeatures().isEmpty()) {
			if (!warnAboutSerdes(serdes)) {
				return;
			}
		}

		try {
			setDeck(serdes.importDeck(from));
		} catch (IOException ioe) {
			Alert alert = new Alert(Alert.AlertType.ERROR, ioe.getMessage(), ButtonType.OK);
			alert.setHeaderText("An error occurred while importing:");
			alert.initOwner(this.stage);
			alert.showAndWait();
		}
	}

	protected void exportDeck() {
		File f = this.importFileChooser.showSaveDialog(this.stage);
		DeckImportExport die = importExports.get(this.importFileChooser.getSelectedExtensionFilter());

		if (f == null) {
			return;
		}

		reexportFile = f;
		reexportSerdes = die;
		reexportMenuItem.setDisable(false);

		exportDeck(f, die);
	}

	protected void exportDeck(File to, DeckImportExport serdes) {
		if (!serdes.unsupportedFeatures().isEmpty()) {
			if (!warnAboutSerdes(serdes)) {
				return;
			}
		}

		try {
			serdes.exportDeck(context.deck, to);
		} catch (IOException ioe) {
			Alert alert = new Alert(Alert.AlertType.ERROR, ioe.getMessage(), ButtonType.OK);
			alert.setHeaderText("An error occurred while exporting:");
			alert.initOwner(this.stage);
			alert.showAndWait();
		}
	}

	@FXML
	protected void reexportDeck() {
		if (reexportSerdes == null || reexportFile == null) {
			return;
		}

		exportDeck(reexportFile, reexportSerdes);
	}

	@FXML
	protected void showDeckInfoDialog() {
		try {
			DeckInfoDialog did = new DeckInfoDialog(Context.FORMATS.values(), context.deck);
			did.initOwner(this.stage);

			if(did.showAndWait().orElse(false)) {
				setFormat(context.deck.formatProperty().getValue());
			}
		} catch (IOException e) {
			e.printStackTrace();
		}
	}

	@FXML
	protected void actionQuit() {
		stage.close();
	}

	@FXML
	protected void showFilterSyntax() {
		Alert alert = new Alert(Alert.AlertType.INFORMATION,
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
				+ "\u2022 Keys \u2014 Set, mana, power, toughness, loyalty, etc.",
				ButtonType.OK
		);

		alert.setTitle("Syntax Help");
		alert.setHeaderText("Omnifilter Syntax");
		alert.initOwner(this.stage);

		alert.showAndWait();
	}

	@FXML
	protected void showAboutDialog() {
		Alert alert = new Alert(Alert.AlertType.NONE,
				"Developer: Emi (@DeaLumi)\n" +
				"Data & Images: Scryfall (@Scryfall)\n" +
				"\n" +
				"Source code will be available at some point probably. Feel free to DM me with feedback/issues on Twitter!\n" +
				"\n" +
				"Special thanks to MagnetMan, for generously indulging my madness time and time again.\n", ButtonType.OK);

		alert.setTitle("About Deck Builder");
		alert.setHeaderText("Deck Builder v0.0.0");
		alert.initOwner(this.stage);

		alert.showAndWait();
	}

	@FXML
	protected void showTagManagementDialog() {
		try {
			TagManagementDialog dlg = new TagManagementDialog(context.tags);
			dlg.initOwner(this.stage);
			dlg.showAndWait();
		} catch (IOException ioe) {
			ioe.printStackTrace(); // TODO: Handle gracefully.
		}
	}

	@FXML
	protected void saveTags() {
		try {
			context.tags.save(new File("tags.json")); // TODO: Move this to controller?
		} catch (IOException ioe) {
			ioe.printStackTrace(); // TODO: Handle gracefully
		}
	}

	@FXML
	protected void validateDeck() {
		Set<String> warnings = context.deck.formatProperty().getValue().validate(context.deck);

		Alert alert;
		if (warnings.isEmpty()) {
			alert = new Alert(Alert.AlertType.INFORMATION, "Deck is valid.", ButtonType.OK);
		} else {
			alert = new Alert(Alert.AlertType.ERROR, "", ButtonType.OK);
			alert.setHeaderText("Deck has errors:");
			alert.setContentText(warnings.stream().map(s -> "\u2022 " + s).collect(Collectors.joining("\n")));
		}
		alert.setTitle("Deck Validation");
		alert.initOwner(this.stage);

		alert.showAndWait();
	}

	@FXML
	protected void showDeckStatisticsDialog() {
		try {
			new DeckStatsDialog(context.deck.primaryVariant.get().cards(Zone.Library)).showAndWait();
		} catch (IOException ioe) {
			ioe.printStackTrace(); // TODO: Handle gracefully
		}
	}

	@FXML
	protected void savePreferences() throws IOException {
		context.savePreferences();
	}

	@FXML
	protected void updateDeckbuilder() throws IOException {
		TextInputDialog uriInput = new TextInputDialog(context.preferences.updateUri.toString());
		uriInput.setTitle("Update Source");
		uriInput.setHeaderText("Update Server URL");
		uriInput.setContentText("URL:");
		uriInput.setWidth(256);
		uriInput.getDialogPane().setExpanded(true);

		Updater updater = new Updater(context);

		ProgressBar progress = new ProgressBar(0.0);
		progress.progressProperty().bind(updater.progress);
		uriInput.getDialogPane().setExpandableContent(progress);

		Button btnOk = (Button) uriInput.getDialogPane().lookupButton(ButtonType.OK);
		btnOk.addEventFilter(ActionEvent.ACTION, ae -> {
			String newUri = uriInput.getEditor().getText();

			try {
				URI uri = new URI(newUri);
				context.preferences.updateUri = uri;
				savePreferences();

				ForkJoinPool.commonPool().submit(() -> {
					try {
						updater.update(uri);
					} catch (IOException e) {
						throw new RuntimeException(e);
					}
				});
			} catch (IOException | URISyntaxException e) {
				throw new RuntimeException(e);
			}

			btnOk.setDisable(true);
			ae.consume();
		});

		uriInput.showAndWait();
	}

	@FXML
	protected void updateData() throws IOException {
		if (!context.data.needsUpdate()) {
			Alert confirm = new Alert(Alert.AlertType.CONFIRMATION, "Data source seems fresh. Update anyway?", ButtonType.YES, ButtonType.NO);
			confirm.initOwner(stage);
			if (confirm.showAndWait().orElse(ButtonType.NO) != ButtonType.YES) {
				return;
			}
		}

		doGraphicalDataUpdate();
	}

	private void doGraphicalDataUpdate() {
		Alert progressDialog = new Alert(Alert.AlertType.NONE, "Updating...", ButtonType.CLOSE);
		progressDialog.setHeaderText("Updating...");
		progressDialog.setWidth(256);
		progressDialog.initOwner(stage);
		progressDialog.getDialogPane().lookupButton(ButtonType.CLOSE).setDisable(true);

		ProgressBar pbar = new ProgressBar(0.0);
		progressDialog.getDialogPane().setContent(pbar);

		progressDialog.show();

		ForkJoinPool.commonPool().submit(() -> {
			try {
				if (context.data.update(d -> Platform.runLater(() -> pbar.setProgress(d)))) {
					Platform.runLater(() -> {
						progressDialog.close();
						Alert alert = new Alert(Alert.AlertType.INFORMATION, "Please restart the program. (I'm working on obviating this step.)", ButtonType.OK);
						alert.setHeaderText("Update completed.");
						alert.setTitle("Update Complete");
						alert.initOwner(stage);
						alert.showAndWait();
					});
				}
			} catch (IOException e) {
				e.printStackTrace();
				Platform.runLater(() -> {
					progressDialog.close();
					Alert alert = new Alert(Alert.AlertType.ERROR, "You may need to re-try the update.", ButtonType.OK);
					alert.setTitle("Update Error");
					alert.setHeaderText("An error occurred.");
					alert.initOwner(stage);
					alert.showAndWait();
				});
			}
		});
	}
}
