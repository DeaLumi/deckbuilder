package emi.mtg.deckbuilder.view;

import com.sun.javafx.collections.ObservableListWrapper;
import emi.lib.Service;
import emi.lib.mtg.data.CardSource;
import emi.lib.mtg.data.ImageSource;
import emi.lib.mtg.game.Format;
import emi.lib.mtg.game.Zone;
import emi.lib.mtg.scryfall.ScryfallCardSource;
import emi.mtg.deckbuilder.controller.DeckImportExport;
import emi.mtg.deckbuilder.model.CardInstance;
import emi.mtg.deckbuilder.model.DeckList;
import javafx.application.Application;
import javafx.beans.property.ReadOnlyListWrapper;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.input.TransferMode;
import javafx.scene.layout.BorderPane;
import javafx.stage.FileChooser;
import javafx.stage.Stage;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public class MainWindow extends Application {
	public static void main(String[] args) {
		launch(args);
	}

	private static final CardSource cs;

	static {
		CardSource csTmp;
		try {
			csTmp = new ScryfallCardSource();
		} catch (IOException e) {
			throw new Error("Couldn't create ScryfallCardSource.");
		}
		cs = csTmp;
	}

	private static final Map<String, Format> formats = Service.Loader.load(Format.class).stream()
			.collect(Collectors.toMap(s -> s.string("name"), s -> s.uncheckedInstance()));

	private static final Map<FileChooser.ExtensionFilter, DeckImportExport> importExports = Service.Loader.load(DeckImportExport.class).stream()
			.collect(Collectors.toMap(dies -> new FileChooser.ExtensionFilter(String.format("%s (*.%s)", dies.string("name"), dies.string("extension")), String.format("*.%s", dies.string("extension"))),
					dies -> dies.uncheckedInstance(cs)));

	private ObservableList<CardInstance> collectionModel(CardSource cs) {
		List<CardInstance> cards = new ArrayList<>();
		cs.sets().stream()
				.flatMap(s -> s.cards().stream())
				.map(CardInstance::new)
				.forEach(cards::add);
		return new ObservableListWrapper<>(cards);
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

	private DeckList model;
	private final Map<Zone, CardPane> deckPanes;
	private CardPane sideboard;

	private FileChooser fileChooser;
	private DeckImportExport reexportSerdes;
	private File reexportFile;

	public MainWindow() {
		this.model = new DeckList();
		this.model.format = formats.get("Standard");

		this.deckPanes = new EnumMap<>(Zone.class);
	}

	private Stage stage;

	@Override
	public void start(Stage stage) throws Exception {
		this.stage = stage;

		stage.setTitle("MTG Deckbuilder - Main Window");

		stage.setScene(new Scene(root, 1024, 1024));
		stage.setMaximized(true);
		stage.show();
	}

	@Override
	public void init() throws Exception {
		super.init();

		FXMLLoader loader = new FXMLLoader(getClass().getResource("MainWindow.fxml"));
		loader.setController(this);
		loader.load();

		setupUI();
		setupImportExport();
	}

	private void setupUI() {
		ImageSource images = new UnifiedImageSource();
		CardSource cards = cs;

		CardPane collection = new CardPane("Collection", images, new ReadOnlyListWrapper<>(collectionModel(cards)), "Flow Grid", CardView.DEFAULT_COLLECTION_SORTING);
		collection.view().dragModes(TransferMode.COPY);
		collection.view().dropModes();
		collection.view().doubleClick(model.cards.get(Zone.Library)::add);

		collectionSplitter.getItems().add(0, collection);

		for (Zone zone : Zone.values()) {
			CardPane deckZone = new CardPane(zone.name(), images, model.cards.get(zone), "Piles", CardView.DEFAULT_SORTING);
			deckZone.view().dragModes(TransferMode.MOVE);
			deckZone.view().dropModes(TransferMode.COPY_OR_MOVE);
			deckZone.view().doubleClick(model.cards.get(zone)::remove);
			deckPanes.put(zone, deckZone);
		}

		this.sideboard = new CardPane("Sideboard", images, model.sideboard, "Piles", CardView.DEFAULT_SORTING);

		setFormat(formats.get("Standard"));
	}

	private void setupImportExport() {
		this.fileChooser = new FileChooser();
		this.fileChooser.getExtensionFilters().setAll(importExports.keySet());

		for (Format format : formats.values()) {
			MenuItem item = new MenuItem(format.name());
			item.setOnAction(ae -> newDeck(format));
			this.newDeckMenu.getItems().add(item);
		}

		for (Map.Entry<FileChooser.ExtensionFilter, DeckImportExport> dies : importExports.entrySet()) {
			MenuItem importItem = new MenuItem(dies.getKey().getDescription());
			importItem.setOnAction(ae -> {
				fileChooser.setSelectedExtensionFilter(dies.getKey());
				importDeck();
			});

			this.importDeckMenu.getItems().add(importItem);

			MenuItem exportItem = new MenuItem(dies.getKey().getDescription());
			exportItem.setOnAction(ae -> {
				fileChooser.setSelectedExtensionFilter(dies.getKey());
				exportDeck();
			});

			this.exportDeckMenu.getItems().add(exportItem);
		}

		this.reexportMenuItem.setDisable(true);
	}

	private void setFormat(Format format) {
		zoneSplitter.getItems().clear();
		for (Zone zone : format.deckZones()) {
			zoneSplitter.getItems().add(deckPanes.get(zone));
		}
		zoneSplitter.getItems().add(sideboard);
	}

	private void setDeck(DeckList deck) {
		this.model = deck;

		for (Zone zone : Zone.values()) {
			deckPanes.get(zone).view().model(this.model.cards.get(zone));
		}
		sideboard.view().model(this.model.sideboard);
	}

	private void newDeck(Format format) {
		setFormat(format);

		DeckList newDeck = new DeckList();
		newDeck.format = format;
		setDeck(newDeck);

		reexportFile = null;
		reexportSerdes = null;
		reexportMenuItem.setDisable(true);
	}

	private boolean warnAboutSerdes(DeckImportExport die) {
		StringBuilder builder = new StringBuilder();

		builder.append("The file format you selected doesn't support the following features:\n");

		for (DeckImportExport.Features feature : die.unsupportedFeatures()) {
			builder.append(" \u2022 ").append(feature.toString()).append('\n');
		}

		builder.append('\n').append("Is this okay?");

		return new Alert(Alert.AlertType.WARNING, builder.toString(), ButtonType.YES, ButtonType.NO)
				.showAndWait()
				.orElse(null) == ButtonType.YES;
	}

	protected void importDeck() {
		File f = this.fileChooser.showOpenDialog(this.stage);
		DeckImportExport die = importExports.get(this.fileChooser.getSelectedExtensionFilter());

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
			ioe.printStackTrace();
		}
	}

	protected void exportDeck() {
		File f = this.fileChooser.showSaveDialog(this.stage);
		DeckImportExport die = importExports.get(this.fileChooser.getSelectedExtensionFilter());

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
			serdes.exportDeck(this.model, to);
		} catch (IOException ioe) {
			ioe.printStackTrace();
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
			new DeckInfoDialog(formats.values(), this.model)
					.showAndWait();
		} catch (IOException e) {
			e.printStackTrace();
		}
	}

	@FXML
	protected void actionQuit() {
		stage.close();
	}

	@FXML
	protected void showAboutDialog() {
		new Alert(Alert.AlertType.NONE,
				"Deck Builder v0.0.0\n" +
				"\n" +
				"Developer: Emi (@DeaLumi)\n" +
				"Data & Images: Scryfall (@Scryfall)\n" +
				"\n" +
				"Source code will be available at some point probably.\n" +
				"Feel free to DM me with feedback/issues on Twitter!", ButtonType.OK).showAndWait();
	}
}
