package emi.mtg.deckbuilder.view.sortings;

import emi.lib.mtg.Mana;
import emi.mtg.deckbuilder.model.CardInstance;
import emi.mtg.deckbuilder.view.components.CardView;

public class ManaValue implements CardView.Sorting {
	@Override
	public int compare(CardInstance o1, CardInstance o2) {
		Mana.Value mc1 = o1.card().manaCost();
		Mana.Value mc2 = o2.card().manaCost();

		if (mc1.varies() != mc2.varies()) {
			return mc1.varies() ? 1 : -1;
		} else {
			return Double.compare(o1.card().manaCost().value(), o2.card().manaCost().value());
		}
	}

	@Override
	public String toString() {
		return "Mana Value";
	}
}
