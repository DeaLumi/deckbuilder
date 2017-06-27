package emi.mtg.deckbuilder.view;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.sun.javafx.collections.ObservableListWrapper;
import emi.lib.mtg.card.Card;
import emi.lib.mtg.characteristic.CardRarity;
import emi.lib.mtg.data.CardSource;
import emi.lib.mtg.data.ImageSource;
import emi.lib.mtg.data.mtgjson.MtgJsonCardSource;
import emi.lib.mtg.data.xlhq.XlhqImageSource;
import emi.mtg.deckbuilder.controller.SerdesControl;
import emi.mtg.deckbuilder.model.CardInstance;
import emi.mtg.deckbuilder.model.DeckList;
import javafx.application.Application;
import javafx.collections.ObservableList;
import javafx.geometry.Orientation;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.stage.FileChooser;
import javafx.stage.Stage;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * Created by Emi on 5/20/2017.
 */
public class MainWindow extends Application {
	public static void main(String[] args) {
		launch(args);
	}

	private ObservableList<CardInstance> collectionModel(CardSource cs) {
		List<CardInstance> cards = new ArrayList<>();
		cs.cards().stream()
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
		cs.cards().stream()
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
		ImageSource is = new RenderedImageSource(); // new XlhqImageSource();

		Gson gson = new GsonBuilder()
				.registerTypeHierarchyAdapter(Card.class, CardInstance.createCardAdapter(cs))
				.setPrettyPrinting()
				.create();

		stage.setTitle("MTG Deckbuilder - Main Window");

		// Menu bar

		MenuBar menu = new MenuBar();
		MenuItem loadDeck = new MenuItem("Load deck...");
		MenuItem saveDeck = new MenuItem("Save deck...");
		MenuItem unloadAll = new MenuItem("Unload All Images");
		MenuItem loadAll = new MenuItem("Load All Images");
		MenuItem gc = new MenuItem("Collect Garbage");
		Menu file = new Menu("File");
		file.getItems().addAll(loadDeck, saveDeck, new SeparatorMenuItem(), unloadAll, loadAll, gc);
		menu.getMenus().add(file);

		// Deck editing view

		TextField collectionFilter = new TextField();
		collectionFilter.setPromptText("Filter by name or text...");
		ScrollPane collectionScroll = new ScrollPane();
		collectionScroll.setFitToWidth(true);
		ObservableList<CardInstance> collectionModel = collectionModel(cs);
		CardFlowView collection = new CardFlowView(is, gson, collectionModel);
		collectionScroll.setContent(collection);

		collectionFilter.setOnAction(ae -> collection.manager().reconfigure(ci -> {
			String searchText = collectionFilter.getText();

			return ci.card().name().contains(searchText) || ci.card().text().contains(searchText);
		}, null, null, null));

		BorderPane collectionPane = new BorderPane();
		collectionPane.setTop(collectionFilter);
		collectionPane.setCenter(collectionScroll);

		ScrollPane deckScroll = new ScrollPane();
		deckScroll.setFitToWidth(true);
		ObservableList<CardInstance> deckModel = deckModel(cs);
		CardView deckView = new CardView(deckModel, NewPilesView.CMC_GROUP, NewPilesView.COLOR_SORT, is, gson);
		deckView.setMinWidth(800.0);
		deckScroll.setContent(deckView);

		SplitPane deckEdit = new SplitPane();
		deckEdit.getItems().addAll(collectionPane, deckScroll);
		deckEdit.setOrientation(Orientation.VERTICAL);

		// Assembly

		BorderPane root = new BorderPane();
		root.setTop(menu);
		root.setCenter(deckEdit);

/*
		unloadAll.setOnAction(ae -> cardsView.manager().unloadAll());
		loadAll.setOnAction(ae -> cardsView.manager().loadAll());
		gc.setOnAction(ae -> {
			System.gc();
			System.gc();
		});
*/

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
