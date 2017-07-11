package emi.mtg.deckbuilder.view;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.sun.javafx.collections.ObservableListWrapper;
import emi.lib.Service;
import emi.lib.mtg.card.Card;
import emi.lib.mtg.characteristic.CardRarity;
import emi.lib.mtg.data.CardSource;
import emi.lib.mtg.data.ImageSource;
import emi.lib.mtg.game.Format;
import emi.lib.mtg.game.Zone;
import emi.lib.mtg.scryfall.ScryfallCardSource;
import emi.lib.mtg.scryfall.ScryfallImageSource;
import emi.mtg.deckbuilder.controller.DeckImportExport;
import emi.mtg.deckbuilder.model.CardInstance;
import emi.mtg.deckbuilder.model.DeckList;
import javafx.application.Application;
import javafx.collections.ObservableList;
import javafx.geometry.Orientation;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.input.TransferMode;
import javafx.scene.layout.BorderPane;
import javafx.stage.FileChooser;
import javafx.stage.Stage;

import java.io.File;
import java.io.IOException;
import java.util.*;
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

	private Format format;
	private Map<Zone, CardPane> deckPanes;
	private CardPane sideboard;
	private SplitPane zoneSplitter;

	private void setFormat(Format format) {
		this.format = format;
		zoneSplitter.getItems().clear();
		for (Zone zone : format.deckZones()) {
			zoneSplitter.getItems().add(deckPanes.get(zone));
		}
		zoneSplitter.getItems().add(sideboard);
	}

	@Override
	public void start(Stage stage) throws Exception {
		ImageSource is = new UnifiedImageSource();

		Gson gson = new GsonBuilder()
				.registerTypeHierarchyAdapter(Card.class, CardInstance.createCardAdapter(cs))
				.setPrettyPrinting()
				.create();

		stage.setTitle("MTG Deckbuilder - Main Window");

		// Menu bar

		MenuBar menu = new MenuBar();

		Menu newDeck = new Menu("Set Format");
		for (Map.Entry<String, Format> format : formats.entrySet()) {
			MenuItem newDeckOfFormat = new MenuItem(format.getKey());
			newDeckOfFormat.setOnAction(ae -> setFormat(format.getValue()));
			newDeck.getItems().add(newDeckOfFormat);
		}

		Map<Zone, ObservableList<CardInstance>> deckModel = new EnumMap<>(Zone.class);
		ObservableList<CardInstance> sideboardModel = new ObservableListWrapper<>(new ArrayList<>());

		MenuItem validateDeck = new MenuItem("Validate Deck");
		validateDeck.setOnAction(ae -> {
			DeckList tmp = new DeckList();

			for (Zone zone : Zone.values()) {
				tmp.cards.put(zone, deckModel.get(zone));
			}

			Set<String> messages = this.format.validate(tmp);
			String joined = messages.isEmpty() ? "Deck is valid." : messages.stream().collect(Collectors.joining("\n"));

			new Alert(Alert.AlertType.INFORMATION, joined).show();
		});

		MenuItem loadDeck = new MenuItem("Load deck...");
		MenuItem saveDeck = new MenuItem("Save deck...");
		Menu file = new Menu("File");
		file.getItems().addAll(newDeck, loadDeck, saveDeck, validateDeck);
		menu.getMenus().add(file);

		// Deck editing view
		ObservableList<CardInstance> collectionModel = collectionModel(cs);

		CardPane collectionView = new CardPane("Collection", is, collectionModel, "Flow Grid");
		collectionView.view().dragModes(TransferMode.COPY);
		collectionView.view().dropModes();
		collectionView.view().doubleClick(deckModel.computeIfAbsent(Zone.Library, z -> new ObservableListWrapper<>(new ArrayList<>()))::add);

		deckPanes = new EnumMap<>(Zone.class);
		for (Zone zone : Zone.values()) {
			ObservableList<CardInstance> zoneCards = deckModel.computeIfAbsent(zone, z -> new ObservableListWrapper<>(new ArrayList<>()));
			CardPane zoneEdit = new CardPane(zone.name(), is, zoneCards, "Piles");
			zoneEdit.view().doubleClick(zoneCards::remove);
			zoneEdit.view().dragModes(TransferMode.MOVE);
			zoneEdit.view().dropModes(TransferMode.COPY_OR_MOVE);
			deckPanes.put(zone, zoneEdit);
		}

		zoneSplitter = new SplitPane();
		zoneSplitter.setOrientation(Orientation.HORIZONTAL);
		sideboard = new CardPane("Sideboard", is, sideboardModel, "Piles");
		sideboard.view().doubleClick(sideboardModel::remove);
		sideboard.view().dragModes(TransferMode.MOVE);
		sideboard.view().dropModes(TransferMode.COPY_OR_MOVE);
		setFormat(formats.values().iterator().next());

		SplitPane splitter = new SplitPane();
		splitter.getItems().addAll(collectionView, zoneSplitter);
		splitter.setOrientation(Orientation.VERTICAL);

		// Assembly
		BorderPane root = new BorderPane();
		root.setTop(menu);
		root.setCenter(splitter);

		FileChooser chooser = new FileChooser();
		chooser.getExtensionFilters().setAll(importExports.keySet());
		loadDeck.setOnAction(ae -> {
			File f = chooser.showOpenDialog(stage);

			DeckImportExport die = importExports.get(chooser.getSelectedExtensionFilter());

			if (f != null) {
				try {
					DeckList list = die.importDeck(f);

					for (Zone zone : Zone.values()) {
						if (list.cards.containsKey(zone)) {
							deckModel.computeIfAbsent(zone, z -> new ObservableListWrapper<>(new ArrayList<>())).setAll(list.cards.get(zone));
						}
					}

					sideboardModel.setAll(list.sideboard);
				} catch (IOException e) {
					e.printStackTrace();
				}
			}
		});

		saveDeck.setOnAction(ae -> {
			File f = chooser.showSaveDialog(stage);

			DeckImportExport die = importExports.get(chooser.getSelectedExtensionFilter());

			if (f != null) {
				try {
					DeckList deckList = new DeckList();

					for (Zone zone : Zone.values()) {
						if (deckModel.containsKey(zone) && !deckModel.get(zone).isEmpty()) {
							deckList.cards.computeIfAbsent(zone, z -> new ArrayList<>()).addAll(deckModel.get(zone));
						}
					}

					deckList.sideboard.addAll(sideboardModel);

					die.exportDeck(deckList, f);
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
