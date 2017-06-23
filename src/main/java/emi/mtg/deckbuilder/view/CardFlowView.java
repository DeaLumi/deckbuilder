package emi.mtg.deckbuilder.view;

import com.google.gson.Gson;
import emi.lib.mtg.data.ImageSource;
import emi.mtg.deckbuilder.model.CardInstance;
import javafx.collections.ObservableList;
import javafx.scene.input.TransferMode;
import javafx.scene.layout.*;

import java.io.IOException;
import java.util.Comparator;
import java.util.function.Function;

/**
 * Created by Emi on 6/17/2017.
 */
public class CardFlowView extends FlowPane implements CardViewManager.ManagedView {
	private final CardViewManager manager;

	public CardFlowView(ImageSource images, Gson gson, ObservableList<CardInstance> cards, Comparator<CardInstance> sort, Function<CardInstance, String> group, Comparator<String> groupSort) throws IOException {
		super(CardInstanceView.WIDTH * 5.0 / 100.0, CardInstanceView.WIDTH * 5.0 / 100.0);

		this.setCache(true);
		this.setPrefWrapLength(CardInstanceView.WIDTH * 20.0);

		this.manager = new CardViewManager(this, images, gson, cards, ci -> true, sort, group, groupSort);
		this.manager.reconfigure(ci -> true, sort, group, groupSort);

		this.setOnDragOver(de -> {
			de.acceptTransferModes(TransferMode.MOVE);
			de.consume();
		});

		this.setOnDragDropped(de -> {
			CardInstance instance = gson.fromJson(de.getDragboard().getContent(CardInstanceView.CARD_INSTANCE_VIEW).toString(), CardInstance.class);
			cards.add(instance);
			manager.reconfigure(null, null, null, null);
			de.setDropCompleted(true);
			de.consume();
		});
	}

	public CardFlowView(ImageSource images, Gson gson, ObservableList<CardInstance> cards, Comparator<CardInstance> sort) throws IOException {
		this(images, gson, cards, sort, NewPilesView.CMC_GROUP, NewPilesView.CMC_SORT);
	}

	public CardFlowView(ImageSource images, Gson gson, ObservableList<CardInstance> cards, Function<CardInstance, String> group) throws IOException {
		this(images, gson, cards, NewPilesView.COLOR_SORT.thenComparing(NewPilesView.NAME_SORT), group, String::compareTo);
	}

	public CardFlowView(ImageSource images, Gson gson, ObservableList<CardInstance> cards) throws IOException {
		this(images, gson, cards, NewPilesView.COLOR_SORT.thenComparing(NewPilesView.NAME_SORT), NewPilesView.CMC_GROUP, NewPilesView.CMC_SORT);
	}

	@Override
	public Pane parentOf(CardInstance instance) {
		return this;
	}

	@Override
	public void adjustLayout() {
		/*
		int column = -1, row = 0;
		for (CardInstance ci : manager.sortedCards) {
			GridPane.setConstraints(manager.viewMap.get(ci), ++column, row);

			if (column * CardInstanceView.WIDTH * 11.0 / 10.0 > getWidth()) {
				column = -1;
				++row;
			}
		}
		*/
	}

	@Override
	public CardViewManager manager() {
		return manager;
	}

}
