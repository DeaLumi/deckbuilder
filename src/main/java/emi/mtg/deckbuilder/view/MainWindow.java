package emi.mtg.deckbuilder.view;

import com.sun.javafx.collections.ObservableListWrapper;
import javafx.application.Application;
import javafx.collections.ObservableList;
import javafx.scene.Scene;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.Slider;
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

		BorderPane layout = new BorderPane();

		Slider zoom = new Slider(0.25, 1.5, 0.5);
		layout.setTop(zoom);

		ScrollPane scroll = new ScrollPane();

		List<CardInstance> cards = new ArrayList<>();
		cs.cards().stream()
				.filter(c -> "AVR".equals(c.set().code()))
				.filter(c -> c.manaCost() != null && c.manaCost().convertedCost() <= 4)
//				.filter(c -> CardRarity.Rare.equals(c.rarity()) || CardRarity.MythicRare.equals(c.rarity()))
//				.filter(c -> c.color().size() > 1)
				.map(CardInstance::new)
				.forEach(cards::add);
		ObservableList<CardInstance> model = new ObservableListWrapper<>(cards);

		NewPilesView pilesView = new NewPilesView(is, model, NewPilesView.COLOR_SORT.thenComparing(NewPilesView.NAME_SORT), ci -> ci.card.rarity().toString(), (r1, r2) -> CardRarity.forString(r1).compareTo(CardRarity.forString(r2)));

		final Scale zoomXform = new Scale(0.5, 0.5);

		zoom.valueProperty().addListener(change -> {
			zoomXform.setX(zoom.getValue());
			zoomXform.setY(zoom.getValue());
			scroll.requestLayout();
		});

		pilesView.getTransforms().add(zoomXform);

		scroll.setContent(pilesView);
		layout.setCenter(scroll);

		root.getChildren().add(layout);

		stage.setScene(new Scene(root, 512, 512));
		stage.setMaximized(true);
		stage.show();
	}
}
