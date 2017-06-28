package emi.mtg.deckbuilder.view;

import com.google.gson.Gson;
import emi.lib.mtg.data.ImageSource;
import emi.mtg.deckbuilder.model.CardInstance;
import javafx.collections.ObservableList;
import javafx.collections.transformation.FilteredList;
import javafx.geometry.Orientation;
import javafx.scene.Group;
import javafx.scene.Node;
import javafx.scene.canvas.Canvas;
import javafx.scene.control.*;
import javafx.scene.input.TransferMode;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;

import java.util.*;
import java.util.function.Function;
import java.util.function.Predicate;

/**
 * Created by Emi on 6/25/2017.
 */
public class CardView extends BorderPane {
	private final FilteredList<CardInstance> filteredCards;
	private final SortedMap<String, CardGroup> groupMap;
	private String engine;
	private ScrollPane layout;

	public CardView(ObservableList<CardInstance> cards, Comparator<String> groupSort, Function<CardInstance, String> group, Comparator<CardInstance> sort, ImageSource images, Gson gson, String layout) {
		this.filteredCards = cards.filtered(c -> true);
		this.groupMap = new TreeMap<>(groupSort);

		for (CardInstance ci : cards) {
			final String groupName = group.apply(ci);
			ci.tags().add(groupName);
			this.groupMap.computeIfAbsent(groupName, g -> new CardGroup(g, gson, images, filteredCards, sort, layout, TransferMode.ANY, new TransferMode[] { TransferMode.MOVE }));
		}

		MenuBar viewMenu = new MenuBar();
		Menu displayMenu = new Menu("Display");

		for (String engine : CardGroup.layoutEngineNames()) {
			MenuItem item = new MenuItem(engine);
			item.setOnAction(ae -> this.layout(engine));
			displayMenu.getItems().add(item);
		}

		Menu sortMenu = new Menu("Sort");
		viewMenu.getMenus().addAll(displayMenu, sortMenu);
		setTop(viewMenu);

		setCenter(this.layout = new ScrollPane());
		layout(layout);
	}

	public void layout(String engine) {
		Orientation orientation = CardGroup.layoutEngineOrientation(engine);

		if (orientation != null) {
			layout.setContent(null);
			Pane content;
			switch (orientation) {
				case HORIZONTAL:
					content = new HBox();
					layout.setFitToWidth(false);
					break;
				case VERTICAL:
					content = new VBox();
					((VBox) content).setFillWidth(true);
					content.setPrefWidth(Double.MAX_VALUE);
					layout.setFitToWidth(true);
					break;
				default:
					throw new Error("wut");
			}

			for (Map.Entry<String, CardGroup> entry : groupMap.entrySet()) {
				entry.getValue().layout(engine);
				Node label = new Label(" " + entry.getKey() + " ");

				if (orientation == Orientation.HORIZONTAL) {
					label.setRotate(-90.0);
					label = new Group(label);
				}

				content.getChildren().addAll(label, entry.getValue());
			}

			layout.setContent(content);
			layout();
		}
	}

	public void filter(Predicate<CardInstance> filter) {
		this.filteredCards.setPredicate(filter);
	}
}
