package emi.mtg.deckbuilder.view;

import com.google.gson.Gson;
import emi.lib.mtg.characteristic.Color;
import emi.lib.mtg.characteristic.ManaSymbol;
import emi.lib.mtg.data.ImageSource;
import emi.mtg.deckbuilder.model.CardInstance;
import javafx.collections.ObservableList;
import javafx.scene.input.TransferMode;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.Pane;

import java.io.IOException;
import java.util.Comparator;
import java.util.function.Function;

/**
 * Created by Emi on 6/17/2017.
 */
public class NewPilesView extends GridPane implements CardViewManager.ManagedView {
	public final static Comparator<CardInstance> NAME_SORT = (c1, c2) -> c1.card.name().compareTo(c2.card.name());
	public final static Function<CardInstance, String> CMC_GROUP = c -> c.card.manaCost().varies() ? "X" : Integer.toString(c.card.manaCost().convertedCost());
	public final static Comparator<String> CMC_SORT = (s1, s2) -> {
		if ("X".equals(s1)) {
			return "X".equals(s2) ? 0 : 1;
		} else if ("X".equals(s2)) {
			return "X".equals(s1) ? 0 : -1;
		} else {
			return Integer.parseInt(s1) - Integer.parseInt(s2);
		}
	};

	public final static Comparator<CardInstance> COLOR_SORT = (c1, c2) -> {
		if (c1.card.color().size() != c2.card.color().size()) {
			int s1 = c1.card.color().size();
			if (s1 == 0) {
				s1 = Color.values().length + 1;
			}

			int s2 = c2.card.color().size();
			if (s2 == 0) {
				s2 = Color.values().length + 1;
			}

			return s1 - s2;
		}

		for (int i = Color.values().length - 1; i >= 0; --i) {
			Color c = Color.values()[i];
			long n1 = -c1.card.manaCost().symbols().stream().map(ManaSymbol::colors).filter(s -> s.contains(c)).count();
			long n2 = -c2.card.manaCost().symbols().stream().map(ManaSymbol::colors).filter(s -> s.contains(c)).count();

			if (n1 != n2) {
				return (int) (n2 - n1);
			}
		}

		return 0;
	};

	private final CardViewManager manager;

	public NewPilesView(ImageSource images, Gson gson, ObservableList<CardInstance> cards, Comparator<CardInstance> sort, Function<CardInstance, String> group, Comparator<String> groupSort) throws IOException {
		super();

		this.setCache(true);
		this.setVgap(CardInstanceView.HEIGHT * -8.85 / 10.0);
		this.setHgap(CardInstanceView.WIDTH * 1.0 / 10.0);

		this.manager = new CardViewManager(this, images, gson, cards, ci -> true, sort, group, groupSort);
		this.manager.reconfigure(ci -> true, sort, group, groupSort);

		this.setOnDragOver(de -> {
			de.acceptTransferModes(TransferMode.MOVE);
			de.consume();
		});

		this.setOnDragDropped(de -> {
			CardInstance instance = gson.fromJson(de.getDragboard().getContent(CardInstanceView.CARD_INSTANCE_VIEW).toString(), CardInstance.class);
			cards.add(instance);
			de.setDropCompleted(true);
			de.consume();
			manager.reconfigure(null, null, null, null);
		});
	}

	public NewPilesView(ImageSource images, Gson gson, ObservableList<CardInstance> cards, Comparator<CardInstance> sort) throws IOException {
		this(images, gson, cards, sort, CMC_GROUP, CMC_SORT);
	}

	public NewPilesView(ImageSource images, Gson gson, ObservableList<CardInstance> cards, Function<CardInstance, String> group) throws IOException {
		this(images, gson, cards, COLOR_SORT.thenComparing(NAME_SORT), group, String::compareTo);
	}

	public NewPilesView(ImageSource images, Gson gson, ObservableList<CardInstance> cards) throws IOException {
		this(images, gson, cards, COLOR_SORT.thenComparing(NAME_SORT), CMC_GROUP, CMC_SORT);
	}

	@Override
	public Pane parentOf(CardInstance instance) {
		return this;
	}

	@Override
	public void adjustLayout() {
		String currentGroup = null;
		int row = -1, column = -1;
		for (CardInstance ci : manager.sortedCards) {
			String cardGroup = manager.group.apply(ci);
			if (!cardGroup.equals(currentGroup)) {
				row = -1;
				++column;
				currentGroup = cardGroup;
			}

			GridPane.setConstraints(manager.viewMap.get(ci), column, ++row);
		}

		requestLayout();
	}

	@Override
	public CardViewManager manager() {
		return manager;
	}
}
