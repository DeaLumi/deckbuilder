package emi.mtg.deckbuilder.view.sortings;

import emi.mtg.deckbuilder.model.CardInstance;
import emi.mtg.deckbuilder.view.components.CardView;

public class Rarity implements CardView.Sorting {
	@Override
	public int compare(CardInstance o1, CardInstance o2) {
		return o1.print().rarity().ordinal() - o2.print().rarity().ordinal();
	}

	@Override
	public String toString() {
		return "Rarity";
	}
}
