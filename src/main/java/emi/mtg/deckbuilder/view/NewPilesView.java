package emi.mtg.deckbuilder.view;

import emi.lib.mtg.characteristic.Color;
import emi.lib.mtg.characteristic.ManaSymbol;
import emi.lib.mtg.data.ImageSource;
import emi.mtg.deckbuilder.model.CardInstance;
import javafx.collections.ListChangeListener;
import javafx.collections.ObservableList;
import javafx.collections.transformation.SortedList;
import javafx.scene.layout.GridPane;

import java.io.IOException;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Created by Emi on 6/17/2017.
 */
public class NewPilesView extends GridPane implements ListChangeListener<CardInstance> {
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

	private final ImageSource images;
	private final Map<CardInstance, CardInstanceView> viewMap;
	private final SortedList<CardInstance> sortedCards;
	private Comparator<CardInstance> sort;
	private Function<CardInstance, String> group;
	private Comparator<String> groupSort;

	public NewPilesView(ImageSource images, ObservableList<CardInstance> cards, Comparator<CardInstance> sort, Function<CardInstance, String> group, Comparator<String> groupSort) throws IOException {
		super();

		this.setVgap(CardInstanceView.HEIGHT * -8.85 / 10.0);
		this.setHgap(CardInstanceView.WIDTH * 1.0 / 10.0);

		this.images = images;
		this.viewMap = new HashMap<>();

		this.sortedCards = cards.sorted(sort);
		this.sortedCards.addListener(this);
		this.reconfigure(sort, group, groupSort);
	}

	public NewPilesView(ImageSource images, ObservableList<CardInstance> cards, Comparator<CardInstance> sort) throws IOException {
		this(images, cards, sort, CMC_GROUP, CMC_SORT);
	}

	public NewPilesView(ImageSource images, ObservableList<CardInstance> cards, Function<CardInstance, String> group) throws IOException {
		this(images, cards, COLOR_SORT.thenComparing(NAME_SORT), group, String::compareTo);
	}

	public NewPilesView(ImageSource images, ObservableList<CardInstance> cards) throws IOException {
		this(images, cards, COLOR_SORT.thenComparing(NAME_SORT), CMC_GROUP, CMC_SORT);
	}

	public void reconfigure(Comparator<CardInstance> sort, Function<CardInstance, String> group, Comparator<String> groupSort) {
		if (sort != null) {
			this.sort = sort;
		}

		if (group != null) {
			this.group = group;
		}

		if (groupSort != null) {
			this.groupSort = groupSort;
		}

		Comparator<CardInstance> groupSort2 = (c1, c2) -> this.groupSort.compare(this.group.apply(c1), this.group.apply(c2));
		Comparator<CardInstance> overall = groupSort2.thenComparing(this.sort);

		this.sortedCards.setComparator(overall);
	}

	@Override
	public void onChanged(Change<? extends CardInstance> c) {
		while(c.next()) {
			c.getRemoved().forEach(this.viewMap::remove);
			this.getChildren().removeAll(c.getRemoved());

			this.getChildren().addAll(this.sortedCards.stream()
					.filter(ci -> !viewMap.containsKey(ci))
					.map(ci -> {
						CardInstanceView view = new CardInstanceView(ci, this.images);
						viewMap.put(ci, view);
						return view;
					})
					.filter(civ -> civ != null)
					.collect(Collectors.toList()));

			String currentGroup = null;
			int row = -1, column = -1;
			for (CardInstance ci : sortedCards) {
				String cardGroup = group.apply(ci);
				if (!cardGroup.equals(currentGroup)) {
					row = -1;
					++column;
					currentGroup = cardGroup;
				}

				GridPane.setConstraints(viewMap.get(ci), column, ++row);
			}
		}

		requestLayout();
	}

	public void unloadAll() {
		viewMap.values().forEach(CardInstanceView::unloadImage);
	}

	public void loadAll() {
		viewMap.values().forEach(CardInstanceView::loadImage);
	}
}
