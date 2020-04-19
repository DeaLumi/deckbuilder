package emi.mtg.deckbuilder.view.sortings;

import emi.lib.mtg.characteristic.ManaSymbol;
import emi.mtg.deckbuilder.model.CardInstance;
import emi.mtg.deckbuilder.view.components.CardView;

public class ManaCost implements CardView.Sorting {
	@Override
	public int compare(CardInstance c1, CardInstance c2) {
		for (int i = emi.lib.mtg.characteristic.Color.values().length - 1; i >= 0; --i) {
			emi.lib.mtg.characteristic.Color c = emi.lib.mtg.characteristic.Color.values()[i];
			long n1 = -c1.card().manaCost().symbols().stream().map(ManaSymbol::color).filter(s -> s.contains(c)).count();
			long n2 = -c2.card().manaCost().symbols().stream().map(ManaSymbol::color).filter(s -> s.contains(c)).count();

			if (n1 != n2) {
				return (int) (n2 - n1);
			}
		}

		return 0;
	}

	@Override
	public String toString() {
		return "Mana Cost";
	}
}
