package emi.mtg.deckbuilder.view;

import com.sun.javafx.collections.ObservableListWrapper;
import emi.lib.Service;
import emi.lib.mtg.DataSource;
import emi.lib.mtg.game.Format;
import emi.lib.mtg.game.Zone;
import emi.mtg.deckbuilder.controller.Context;
import emi.mtg.deckbuilder.controller.Updater;
import emi.mtg.deckbuilder.controller.serdes.DeckImportExport;
import emi.mtg.deckbuilder.controller.serdes.Features;
import emi.mtg.deckbuilder.controller.serdes.VariantImportExport;
import emi.mtg.deckbuilder.controller.serdes.full.Json;
import emi.mtg.deckbuilder.model.CardInstance;
import emi.mtg.deckbuilder.model.DeckList;
import emi.mtg.deckbuilder.view.components.CardPane;
import emi.mtg.deckbuilder.view.components.CardView;
import emi.mtg.deckbuilder.view.components.VariantPane;
import emi.mtg.deckbuilder.view.dialogs.DeckInfoDialog;
import emi.mtg.deckbuilder.view.dialogs.TagManagementDialog;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.beans.property.ReadOnlyListWrapper;
import javafx.collections.ListChangeListener;
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
import java.util.List;
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
	private Menu newDeckMenu;

	@FXML
	private TabPane deckVariantTabs;

	@FXML
	private Tab deckVariantTabNew;

	@FXML
	private Tab deckVariantTabImport;

	private Context context;

	private CardPane collection;

	private FileChooser primaryFileChooser;
	private DeckImportExport primarySerdes;
	private File currentDeckFile;

	private Map<FileChooser.ExtensionFilter, VariantImportExport> variantSerdes;
	private FileChooser variantFileChooser;

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
		setDeck(context.deck); // TODO: This looks tautological...

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
		collection.view().doubleClick(ci -> ((VariantPane) deckVariantTabs.getSelectionModel().getSelectedItem()).deckPanes.get(Zone.Library).model().add(new CardInstance(ci.printing())));
		collection.showIllegalCards.set(false);
		collection.showVersionsSeparately.set(false);

		this.collectionSplitter.getItems().add(0, collection);

		deckVariantTabs.getSelectionModel().selectedItemProperty().addListener((obs, old, newTab) -> {
			if ((newTab != deckVariantTabNew && newTab != deckVariantTabImport) || deckVariantTabs.getTabs().size() <= 2) {
				return;
			}

			if (newTab == deckVariantTabNew) {
				newVariant();
			} else if (newTab == deckVariantTabImport) {
				importVariant();
			}

			if (obs.getValue() == newTab) {
				if (old != deckVariantTabNew && old != deckVariantTabImport) {
					deckVariantTabs.getSelectionModel().select(old);
				}
			}
		});
	}

	private void setupImportExport() {
		this.primarySerdes = new Json(context);

		this.primaryFileChooser = new FileChooser();
		this.primaryFileChooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("JSON Files (*.json)", "*.json"));

		for (Format format : Context.FORMATS.values()) {
			MenuItem item = new MenuItem(format.name());
			item.setOnAction(ae -> newDeck(format));
			this.newDeckMenu.getItems().add(item);
		}

		this.variantSerdes = Service.Loader.load(VariantImportExport.class).stream()
				.collect(Collectors.toMap(
						vies -> new FileChooser.ExtensionFilter(String.format("%s (*.%s)", vies.string("name"), vies.string("extension")), String.format("*.%s", vies.string("extension"))),
						vies -> vies.uncheckedInstance(context)
				));

		this.variantFileChooser = new FileChooser();
		this.variantFileChooser.getExtensionFilters().setAll(this.variantSerdes.keySet());
	}

	private final ListChangeListener<? super DeckList.Variant> deckVariantsChangedListener = e -> {
		while (e.next()) {
			if (e.wasRemoved()) {
				deckVariantTabs.getTabs().removeIf(t -> t != deckVariantTabNew && t != deckVariantTabImport && e.getRemoved().contains(((VariantPane) t).variant));
			}

			if (e.wasAdded()) {
				int lastIndex = deckVariantTabs.getTabs().indexOf(deckVariantTabNew);

				List<VariantPane> newPanes = e.getAddedSubList().stream()
						.map(v -> new VariantPane(this, context, v))
						.collect(Collectors.toList());

				deckVariantTabs.getTabs().addAll(lastIndex, newPanes);
				System.err.println("Added " + newPanes.size() + " tabs; selecting " + (lastIndex + newPanes.size() - 1));
				deckVariantTabs.getSelectionModel().select(lastIndex + newPanes.size() - 1);
			}

			if (e.wasPermutated()) {
				throw new UnsupportedOperationException("Please don't permute variants yet...");
			}
		}
	};

	private void setDeck(DeckList deck) {
		context.deck = deck;

		collection.updateFilter();

		deck.variants().removeListener(deckVariantsChangedListener);
		deck.variants().addListener(deckVariantsChangedListener);

		deckVariantTabs.getTabs().removeIf(t -> t != deckVariantTabNew && t != deckVariantTabImport);
		deckVariantTabs.getTabs().addAll(0, deck.variants().stream()
			.map(v -> new VariantPane(this, context, v))
			.collect(Collectors.toList()));
		deckVariantTabs.getSelectionModel().select(deckVariantTabs.getTabs().indexOf(deckVariantTabNew) - 1);
	}

	private void newDeck(Format format) {
		DeckList newDeck = new DeckList("", "", format, "", Collections.emptyMap());
		setDeck(newDeck);

		currentDeckFile = null;
	}

	@FXML
	protected void newDeck() {
		newDeck(context.preferences.defaultFormat);
		currentDeckFile = null;
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
		} catch (IOException ioe) {
			Alert alert = new Alert(Alert.AlertType.ERROR, ioe.getMessage(), ButtonType.OK);
			alert.setHeaderText("An error occurred while opening:");
			alert.initOwner(this.stage);
			alert.showAndWait();
		}
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
		File to = primaryFileChooser.showSaveDialog(this.stage);

		if (to == null) {
			return false;
		}

		return saveDeck(to);
	}

	private boolean saveDeck(File to) {
		try {
			primarySerdes.exportDeck(context.deck, to);
			currentDeckFile = to;
			return true;
		} catch (IOException ioe) {
			Alert alert = new Alert(Alert.AlertType.ERROR, ioe.getMessage(), ButtonType.OK);
			alert.setHeaderText("An error occurred while saving:");
			alert.initOwner(this.stage);
			alert.showAndWait();
			return false;
		}
	}

	private boolean warnAboutSerdes(Set<Features> unsupportedFeatures) {
		StringBuilder builder = new StringBuilder();

		builder.append("The file format you selected doesn't support the following features:\n");

		for (Features feature : unsupportedFeatures) {
			builder.append(" \u2022 ").append(feature.toString()).append('\n');
		}

		builder.append('\n').append("Is this okay?");

		Alert alert = new Alert(Alert.AlertType.WARNING, builder.toString(), ButtonType.YES, ButtonType.NO);
		alert.initOwner(this.stage);

		return alert.showAndWait().orElse(null) == ButtonType.YES;
	}

	@FXML
	protected void showDeckInfoDialog() {
		try {
			DeckInfoDialog did = new DeckInfoDialog(Context.FORMATS.values(), context.deck);
			did.initOwner(this.stage);

			if(did.showAndWait().orElse(false)) {
				collection.updateFilter();
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
	protected void showTipsAndTricks() {
		Alert alert = new Alert(Alert.AlertType.INFORMATION,
				"The UI of this program is really dense! Here are some bits on some subtle\n"
				+ "but powerful features!\n"
				+ "\n"
				+ "Deck variants:\n"
				+ "\u2022 Tabs on the bottom show different versions of your deck.\n"
				+ "\u2022 All versions are saved to one file when you use Deck -> Save/Save As.\n"
				+ "\u2022 To rename a variant, double-click the tab, or right click -> Info...\n"
				+ "\u2022 You can export a variant to a single decklist as plain text or MTGO v4 .dek\n"
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
				+ "I never claimed to be good at UI design! :^)", ButtonType.OK);

		alert.setTitle("Tips and Tricks");
		alert.setHeaderText("Tips and Tricks");
		alert.getDialogPane().setPrefWidth(550);
		alert.initOwner(stage);
		alert.showAndWait();
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
	protected void savePreferences() throws IOException {
		context.savePreferences();
	}

	@FXML
	protected void updateDeckbuilder() throws IOException {
		TextInputDialog uriInput = new TextInputDialog(context.preferences.updateUri == null ? "" : context.preferences.updateUri.toString());
		uriInput.setTitle("Update Source");
		uriInput.setHeaderText("Update Server URL");
		uriInput.setContentText("URL:");
		uriInput.getDialogPane().setExpanded(true);

		Updater updater = new Updater(context);

		ProgressBar progress = new ProgressBar(0.0);
		progress.progressProperty().bind(updater.progress);
		uriInput.getDialogPane().setExpandableContent(progress);
		uriInput.getDialogPane().setPrefWidth(512.0);

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

		uriInput.initOwner(stage);
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
		progressDialog.getDialogPane().lookupButton(ButtonType.CLOSE).setDisable(true);

		ProgressBar pbar = new ProgressBar(0.0);
		progressDialog.getDialogPane().setContent(pbar);
		progressDialog.getDialogPane().setPrefWidth(256.0);

		progressDialog.initOwner(stage);
		progressDialog.show();

		ForkJoinPool.commonPool().submit(() -> {
			try {
				if (context.data.update(d -> Platform.runLater(() -> pbar.setProgress(d)))) {
					Platform.runLater(() -> {
						progressDialog.close();

						collection.view().model(new ReadOnlyListWrapper<>(collectionModel(context.data)));

						Alert alert = new Alert(Alert.AlertType.INFORMATION, "Live updates are a new feature; if anything acts hinky, please restart the program!", ButtonType.OK);
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

	@FXML
	protected void newVariant() {
		TextInputDialog nameDialog = new TextInputDialog();
		nameDialog.setTitle("New Deck Variant");
		nameDialog.setHeaderText("New Deck Variant");
		nameDialog.setContentText("Name:");
		nameDialog.initOwner(stage);
		nameDialog.showAndWait().ifPresent(s -> context.deck.new Variant(s, "", Collections.emptyMap()));
	}

	@FXML
	protected void importVariant() {
		File f = variantFileChooser.showOpenDialog(stage);

		if (f == null) {
			return;
		}

		VariantImportExport importer = variantSerdes.get(variantFileChooser.getSelectedExtensionFilter());

		if (!importer.unsupportedFeatures().isEmpty()) {
			if (!warnAboutSerdes(importer.unsupportedFeatures())) {
				return;
			}
		}

		try {
			importer.importVariant(context.deck, f);
		} catch (IOException ioe) {
			Alert alert = new Alert(Alert.AlertType.ERROR, ioe.getMessage(), ButtonType.OK);
			alert.setHeaderText("An error occurred while importing:");
			alert.initOwner(stage);
			alert.showAndWait();
		}
	}

	public void duplicateVariant(DeckList.Variant variant) {
		variant.deck().new Variant(variant.name(), variant.description(), variant.cards());
	}

	public void showVariantInfo(DeckList.Variant variant) {
		try {
			FXMLLoader loader = new FXMLLoader(getClass().getResource("dialogs/VariantInfoDialog.fxml"));
			Dialog<Void> dlg = loader.load();

			TextField name = (TextField) dlg.getDialogPane().lookup("#variantName");
			name.setText(variant.name());

			TextArea description = (TextArea) dlg.getDialogPane().lookup("#variantDescription");
			description.setText(variant.description());

			dlg.setResultConverter(bt -> {
				if (bt == ButtonType.OK) {
					variant.nameProperty().setValue(name.getText());
					variant.descriptionProperty().setValue(description.getText());
				}

				return null;
			});

			dlg.showAndWait();
		} catch (IOException ioe) {
			throw new RuntimeException(ioe);
		}
	}

	public void exportVariant(DeckList.Variant variant) {
		File f = variantFileChooser.showSaveDialog(stage);

		if (f == null) {
			return;
		}

		VariantImportExport exporter = variantSerdes.get(variantFileChooser.getSelectedExtensionFilter());

		if (!exporter.unsupportedFeatures().isEmpty()) {
			if (!warnAboutSerdes(exporter.unsupportedFeatures())) {
				return;
			}
		}

		try {
			exporter.exportVariant(variant, f);
		} catch (IOException ioe) {
			Alert alert = new Alert(Alert.AlertType.ERROR, ioe.getMessage(), ButtonType.OK);
			alert.setHeaderText("An error occurred while exporting:");
			alert.initOwner(stage);
			alert.showAndWait();
		}
	}
}
