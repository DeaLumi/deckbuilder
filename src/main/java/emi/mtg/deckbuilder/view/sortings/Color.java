package emi.mtg.deckbuilder.view.sortings;

import emi.mtg.deckbuilder.model.CardInstance;
import emi.mtg.deckbuilder.view.components.CardView;

public class Color implements CardView.Sorting {
	@Override
	public int compare(CardInstance o1, CardInstance o2) {
		return emi.lib.mtg.enums.Color.Combination.EMPTY_LAST_COMPARATOR.compare(o1.card().manaCost().color(), o2.card().manaCost().color());
	}

	@Override
	public String toString() {
		return "Color";
	}
}
