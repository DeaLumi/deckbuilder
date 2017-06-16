package org.whitefoxy.mtg.deckbuilder.view;

import javafx.application.Application;
import javafx.scene.Scene;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.Slider;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.StackPane;
import javafx.scene.transform.Scale;
import javafx.stage.Stage;
import org.whitefoxy.lib.mtg.data.CardSource;
import org.whitefoxy.lib.mtg.data.mtgjson.MtgJsonCardSource;
import org.whitefoxy.mtg.deckbuilder.model.CardInstance;
import org.whitefoxy.mtg.deckbuilder.view.CardInstanceView;
import org.whitefoxy.mtg.deckbuilder.view.PileView;

import java.io.IOException;

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

		stage.setTitle("MTG Deckbuilder - Main Window");

		StackPane root = new StackPane();

		BorderPane layout = new BorderPane();

		Slider zoom = new Slider(0.25, 1.0, 0.5);
		layout.setTop(zoom);

		ScrollPane scroll = new ScrollPane();

		PilesView pilesView = new PilesView(cs);

		for (int i = 11; i <= 16; ++i) {
			final int finalI = i;
			PileView pileView = new PileView(cs);
			cs.cards().stream()
					.filter(c -> c.manaCost() != null && c.manaCost().convertedCost() == finalI)
					.sorted((c1, c2) -> c1.set().name().compareTo(c2.set().name()))
					.map(CardInstance::new)
					.map(ci -> {
						try {
							return new CardInstanceView(ci);
						} catch (IOException e) {
							e.printStackTrace();
							return null;
						}
					})
					.forEach(pileView.getChildren()::add);
			pilesView.getChildren().add(i - 11, pileView);
		}

		final Scale zoomXform = new Scale(0.5, 0.5);

		zoom.valueProperty().addListener(change -> {
			zoomXform.setX(zoom.getValue());
			zoomXform.setY(zoom.getValue());
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
