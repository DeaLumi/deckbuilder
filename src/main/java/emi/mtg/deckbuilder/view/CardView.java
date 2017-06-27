package emi.mtg.deckbuilder.view;

import com.google.gson.Gson;
import emi.lib.mtg.data.ImageSource;
import emi.mtg.deckbuilder.model.CardInstance;
import javafx.collections.ObservableList;
import javafx.scene.input.TransferMode;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;

import java.util.Comparator;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Function;

/**
 * Created by Emi on 6/25/2017.
 */
public class CardView extends VBox {
	private Map<String, CardGroup> groupMap = new HashMap<>();

	public CardView(ObservableList<CardInstance> cards, Function<CardInstance, String> group, Comparator<CardInstance> sort, ImageSource images, Gson gson) {
		setBorder(new Border(new BorderStroke(Color.BLACK, BorderStrokeStyle.SOLID, CornerRadii.EMPTY, new BorderWidths(5.0))));
		setFillWidth(true);
		setPrefWidth(Double.MAX_VALUE);

		for (CardInstance ci : cards) {
			groupMap.computeIfAbsent(group.apply(ci), g -> {
				CardGroup groupNode = new CardGroup(g, gson, images, cards.filtered(c -> g.equals(group.apply(c))), sort, "Flow", TransferMode.ANY, TransferMode.NONE);
				CardView.this.getChildren().add(groupNode);
				return groupNode;
			});
		}

		groupMap.values().forEach(CardGroup::render);

		this.setOnMouseClicked(ce -> {
			groupMap.values().forEach(CardGroup::render);
			requestLayout();
			ce.consume();
		});
	}

	@Override
	public void requestLayout() {
		super.requestLayout();
	}
}
