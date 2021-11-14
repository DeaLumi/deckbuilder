package emi.mtg.deckbuilder.view.sortings;

import emi.mtg.deckbuilder.model.CardInstance;
import emi.mtg.deckbuilder.view.components.CardView;

import java.util.Set;

public class Color implements CardView.Sorting {
	@Override
	public int compare(CardInstance o1, CardInstance o2) {
		// TODO: Fit this into libmtg somehow. These should really be using Color.Combination.
		Set<emi.lib.mtg.enums.Color> c1 = o1.card().manaCost().color();
		Set<emi.lib.mtg.enums.Color> c2 = o2.card().manaCost().color();

		int s1 = c1.size();
		int s2 = c2.size();
		if (s1 != s2) {
			if (s1 == 0) {
				return 1;
			} else if (s2 == 0) {
				return -1;
			} else {
				return s1 - s2;
			}
		}

		int ordinal1 = c1.parallelStream().filter(c -> !c2.contains(c)).mapToInt(Enum::ordinal).min().orElse(-1);
		int ordinal2 = c2.parallelStream().filter(c -> !c1.contains(c)).mapToInt(Enum::ordinal).min().orElse(-1);

		return ordinal1 - ordinal2;
	}

	@Override
	public String toString() {
		return "Color";
	}
}
