package emi.mtg.deckbuilder.view;

import com.sun.javafx.collections.ObservableListWrapper;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.collections.ObservableList;
import javafx.scene.CacheHint;
import javafx.scene.Group;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.StackPane;
import javafx.scene.transform.Scale;
import javafx.stage.Stage;
import emi.lib.mtg.characteristic.CardRarity;
import emi.lib.mtg.data.CardSource;
import emi.lib.mtg.data.ImageSource;
import emi.lib.mtg.data.mtgjson.MtgJsonCardSource;
import emi.lib.mtg.data.xlhq.XlhqImageSource;
import emi.mtg.deckbuilder.model.CardInstance;

import java.util.ArrayList;
import java.util.List;

/**
 * Created by Emi on 5/20/2017.
 */
public class MainWindow extends Application {
	public static void main(String[] args) {
		launch(args);
	}

	@Override
	public void start(Stage stage) throws Exception {
		CardSource cs = new MtgJsonCardSource();
		ImageSource is = new XlhqImageSource();

		stage.setTitle("MTG Deckbuilder - Main Window");

		StackPane root = new StackPane();

		MenuBar menu = new MenuBar();

		Menu file = new Menu("File");
		MenuItem unloadAll = new MenuItem("Unload All Images");
		MenuItem loadAll = new MenuItem("Load All Images");
		MenuItem gc = new MenuItem("Collect Garbage");
		file.getItems().addAll(unloadAll, loadAll, gc);

		menu.getMenus().addAll(file);

		BorderPane layout = new BorderPane();

		Slider zoom = new Slider(0.25, 1.5, 0.5);
		layout.setTop(menu);

		BorderPane pilesPane = new BorderPane();

		ScrollPane scroll = new ScrollPane();
		scroll.setPannable(true);

		List<CardInstance> cards = new ArrayList<>();
		cs.cards().stream()
				.filter(c -> "AVR".equals(c.set().code()))
//				.filter(c -> c.manaCost() != null && c.manaCost().convertedCost() <= 4)
//				.filter(c -> CardRarity.Rare.equals(c.rarity()) || CardRarity.MythicRare.equals(c.rarity()))
//				.filter(c -> CardRarity.MythicRare.equals(c.rarity()))
//				.filter(c -> c.color().size() > 1)
				.map(CardInstance::new)
				.forEach(cards::add);
		ObservableList<CardInstance> model = new ObservableListWrapper<>(cards);

		NewPilesView pilesView = new NewPilesView(is, model);
		pilesView.setCache(true);

		unloadAll.setOnAction(ae -> pilesView.unloadAll());
		loadAll.setOnAction(ae -> pilesView.loadAll());
		gc.setOnAction(ae -> {
			System.gc();
			System.gc();
		});

		final Scale zoomXform = new Scale(0.5, 0.5);

		zoomXform.xProperty().bind(zoom.valueProperty());
		zoomXform.yProperty().bind(zoom.valueProperty());

		pilesView.getTransforms().add(zoomXform);

		scroll.setContent(new Group(pilesView));
		pilesPane.setTop(zoom);
		pilesPane.setCenter(scroll);

		layout.setCenter(pilesPane);

		root.getChildren().add(layout);

		stage.setScene(new Scene(root));
/*
		root.getChildren().add(new CardInstanceView(new CardInstance(cs.cards().stream()
				.filter(c -> "AVR".equals(c.set().code()))
				.filter(c -> "Avacyn, Angel of Hope".equals(c.name()))
				.findAny().get()), new RenderedImageSource()));

		stage.setScene(new Scene(root));*/
		stage.setMaximized(true);
		stage.show();
	}
}
