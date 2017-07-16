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

	private Stage stage;

	@Override
	public void start(Stage stage) throws Exception {
		this.stage = stage;

		stage.setTitle("MTG Deckbuilder - Main Window");

		stage.setScene(new Scene(root, 1024, 1024));
		stage.setMaximized(true);
		stage.show();
	}

	@FXML
	private BorderPane root;

	@FXML
	private Menu newDeckMenu;

	@FXML
	private Menu importDeckMenu;

	@FXML
	private Menu exportDeckMenu;

	@FXML
	private SplitPane collectionSplitter;

	@FXML
	private SplitPane zoneSplitter;

	public MainWindow() {
		this.model = new DeckList();

		this.deckPanes = new EnumMap<>(Zone.class);
	}

	private Format format;
	private DeckList model;
	private final Map<Zone, CardPane> deckPanes;
	private CardPane sideboard;

	private FileChooser mainFileChooser, exportFileChooser;

	private void setFormat(Format format) {
		zoneSplitter.getItems().clear();
		for (Zone zone : format.deckZones()) {
			zoneSplitter.getItems().add(deckPanes.get(zone));
		}
		zoneSplitter.getItems().add(sideboard);
	}

	private void newDeck(Format format) {
		setFormat(format);

		this.model = new DeckList();

		this.model.name = "<No Name>";
		this.model.author = System.getProperty("user.name", "<No Author>");
		this.model.description = "<No Description>";
		this.model.format = format;

		for (Zone zone : Zone.values()) {
			deckPanes.get(zone).view().model(this.model.cards.get(zone));
		}
		sideboard.view().model(this.model.sideboard);
	}

	@Override
	public void init() throws Exception {
		super.init();

		FXMLLoader loader = new FXMLLoader(getClass().getResource("MainWindow.fxml"));
		loader.setController(this);
		loader.load();

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

		this.mainFileChooser = new FileChooser();
		this.mainFileChooser.getExtensionFilters().setAll(
				new FileChooser.ExtensionFilter("JSON (*.json)", "*.json"),
				new FileChooser.ExtensionFilter("All Files (*.*)", "*.*")
		);

		this.exportFileChooser = new FileChooser();
		this.exportFileChooser.getExtensionFilters().setAll(importExports.keySet());

		for (Format format : formats.values()) {
			MenuItem item = new MenuItem(format.name());
			item.setOnAction(ae -> newDeck(format));
			this.newDeckMenu.getItems().add(item);
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
