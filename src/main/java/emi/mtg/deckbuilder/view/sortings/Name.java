package emi.mtg.deckbuilder.view.sortings;

import emi.mtg.deckbuilder.model.CardInstance;
import emi.mtg.deckbuilder.view.components.CardView;

public class Name implements CardView.Sorting {
	@Override
	public int compare(CardInstance c1, CardInstance c2) {
		return c1.card().name().compareTo(c2.card().name());
	}

	@Override
	public String toString() {
		return "Name";
	}
}
