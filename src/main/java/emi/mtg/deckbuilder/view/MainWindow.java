package emi.mtg.deckbuilder.view;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.sun.javafx.collections.ObservableListWrapper;
import emi.lib.mtg.card.CardFace;
import emi.lib.mtg.characteristic.CardRarity;
import emi.lib.mtg.data.CardSource;
import emi.lib.mtg.data.ImageSource;
import emi.lib.mtg.data.mtgjson.MtgJsonCardSource;
import emi.mtg.deckbuilder.controller.SerdesControl;
import emi.mtg.deckbuilder.model.CardInstance;
import emi.mtg.deckbuilder.model.DeckList;
import javafx.application.Application;
import javafx.collections.ObservableList;
import javafx.geometry.Orientation;
import javafx.scene.Scene;
import javafx.scene.control.Menu;
import javafx.scene.control.MenuBar;
import javafx.scene.control.MenuItem;
import javafx.scene.control.SplitPane;
import javafx.scene.layout.BorderPane;
import javafx.stage.FileChooser;
import javafx.stage.Stage;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public class MainWindow extends Application {
	public static void main(String[] args) {
		launch(args);
	}

	private ObservableList<CardInstance> collectionModel(CardSource cs) {
		List<CardInstance> cards = new ArrayList<>();
		cs.sets().stream()
				.flatMap(s -> s.cards().stream())
//				.filter(c -> "Storm Crow".equals(c.name()))
//				.filter(c -> "AVR".equals(c.set().code()) || "ALL".equals(c.set().code()))
//				.filter(c -> c.manaCost() != null && c.manaCost().convertedCost() <= 4)
//				.filter(c -> CardRarity.Rare.equals(c.rarity()) || CardRarity.MythicRare.equals(c.rarity()))
//				.filter(c -> CardRarity.MythicRare.equals(c.rarity()))
//				.filter(c -> c.color().size() > 1)
				.map(CardInstance::new)
				.forEach(cards::add);
		return new ObservableListWrapper<>(cards);
	}

	private ObservableList<CardInstance> deckModel(CardSource cs) {
		List<CardInstance> cards = new ArrayList<>();
		cs.sets().stream()
				.flatMap(s -> s.cards().stream())
//				.filter(c -> "Storm Crow".equals(c.name()))
				.filter(c -> "AVR".equals(c.set().code()))
//				.filter(c -> c.manaCost() != null && c.manaCost().convertedCost() <= 4)
//				.filter(c -> CardRarity.Rare.equals(c.rarity()) || CardRarity.MythicRare.equals(c.rarity()))
				.filter(c -> CardRarity.Common.equals(c.rarity()) || CardRarity.Uncommon.equals(c.rarity()))
//				.filter(c -> CardRarity.MythicRare.equals(c.rarity()))
//				.filter(c -> c.color().size() > 1)
				.map(CardInstance::new)
				.forEach(cards::add);
		return new ObservableListWrapper<>(cards);
	}

	@Override
	public void start(Stage stage) throws Exception {
		CardSource cs = new MtgJsonCardSource();
		ImageSource is = new UnifiedImageSource(); // new XlhqImageSource(); // new RenderedImageSource();

		Gson gson = new GsonBuilder()
				.registerTypeHierarchyAdapter(CardFace.class, CardInstance.createCardAdapter(cs))
				.setPrettyPrinting()
				.create();

		stage.setTitle("MTG Deckbuilder - Main Window");

		// Menu bar

		MenuBar menu = new MenuBar();
		MenuItem loadDeck = new MenuItem("Load deck...");
		MenuItem saveDeck = new MenuItem("Save deck...");
		Menu file = new Menu("File");
		file.getItems().addAll(loadDeck, saveDeck);
		menu.getMenus().add(file);

		// Deck editing view
		ObservableList<CardInstance> collectionModel = collectionModel(cs);
		ObservableList<CardInstance> deckModel = deckModel(cs);

		CardPane collectionView = new CardPane("Collection", is, collectionModel, "Flow Grid");
		collectionView.view().dropModes();
		collectionView.view().doubleClick(deckModel::add);

		CardPane deckEdit = new CardPane("Mainboard", is, deckModel, "Piles");
		deckEdit.view().doubleClick(deckModel::remove);

		SplitPane splitter = new SplitPane();
		splitter.getItems().addAll(collectionView, deckEdit);
		splitter.setOrientation(Orientation.VERTICAL);

		// Assembly
		BorderPane root = new BorderPane();
		root.setTop(menu);
		root.setCenter(splitter);

		SerdesControl serdesControl = new SerdesControl(gson);
		FileChooser chooser = new FileChooser();
		chooser.getExtensionFilters().addAll(new FileChooser.ExtensionFilter("JSON file (*.json)", "*.json"), new FileChooser.ExtensionFilter("All Files (*.*)", "*.*"));
		loadDeck.setOnAction(ae -> {
			File f = chooser.showOpenDialog(stage);

			if (f != null) {
				try {
					DeckList deckList = serdesControl.loadDeck(f);
					deckModel.setAll(deckList.cards);
				} catch (IOException e) {
					e.printStackTrace();
				}
			}
		});

		saveDeck.setOnAction(ae -> {
			File f = chooser.showSaveDialog(stage);

			if (f != null) {
				try {
					DeckList deckList = new DeckList("Bleh");
					deckList.cards.addAll(deckModel);
					serdesControl.saveDeck(deckList, f);
				} catch (IOException ioe) {
					ioe.printStackTrace();
				}
			}
		});

		stage.setScene(new Scene(root, 1024, 1024));
		stage.setMaximized(true);
		stage.show();
	}
}
