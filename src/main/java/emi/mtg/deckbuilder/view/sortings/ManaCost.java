package emi.mtg.deckbuilder.view.sortings;

import emi.lib.mtg.Mana;
import emi.mtg.deckbuilder.model.CardInstance;
import emi.mtg.deckbuilder.view.components.CardView;

public class ManaCost implements CardView.Sorting {
	@Override
	public int compare(CardInstance c1, CardInstance c2) {
		return Mana.Value.SYMBOL_COMPARATOR.compare(c1.card().manaCost(), c2.card().manaCost());
	}

	@Override
	public String toString() {
		return "Mana Cost";
	}
}
