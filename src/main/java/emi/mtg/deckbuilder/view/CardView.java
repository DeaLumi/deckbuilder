package emi.mtg.deckbuilder.view;

import com.google.gson.Gson;
import emi.lib.mtg.data.ImageSource;
import emi.mtg.deckbuilder.model.CardInstance;
import javafx.collections.ObservableList;
import javafx.collections.transformation.FilteredList;
import javafx.scene.input.TransferMode;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;

import java.util.*;
import java.util.function.Function;
import java.util.function.Predicate;

/**
 * Created by Emi on 6/25/2017.
 */
public class CardView extends VBox {
	private final FilteredList<CardInstance> filteredCards;
	private final SortedMap<String, CardGroup> groupMap;

	public CardView(ObservableList<CardInstance> cards, Comparator<String> groupSort, Function<CardInstance, String> group, Comparator<CardInstance> sort, ImageSource images, Gson gson) {
		setBorder(new Border(new BorderStroke(Color.BLACK, BorderStrokeStyle.SOLID, CornerRadii.EMPTY, new BorderWidths(5.0))));
		setFillWidth(true);
		setPrefWidth(Double.MAX_VALUE);

		this.filteredCards = cards.filtered(c -> true);
		this.groupMap = new TreeMap<>(groupSort);

		for (CardInstance ci : cards) {
			final String groupName = group.apply(ci);
			ci.tags().add(groupName);
			this.groupMap.computeIfAbsent(groupName, g -> new CardGroup(g, gson, images, filteredCards, sort, "Flow", TransferMode.ANY, new TransferMode[] { TransferMode.MOVE }));
		}

		getChildren().addAll(this.groupMap.values());
		this.groupMap.values().forEach(CardGroup::render);
	}

	public void filter(Predicate<CardInstance> filter) {
		this.filteredCards.setPredicate(filter);
	}
}
