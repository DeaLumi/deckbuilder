package emi.mtg.deckbuilder.view.sortings;

import emi.lib.Service;
import emi.lib.mtg.characteristic.ManaSymbol;
import emi.mtg.deckbuilder.model.CardInstance;
import emi.mtg.deckbuilder.view.CardView;

import java.util.Objects;

@Service.Provider(CardView.Sorting.class)
@Service.Property.String(name="name", value="Mana Cost")
public class ManaCost implements CardView.Sorting {
	@Override
	public int compare(CardInstance c1, CardInstance c2) {
		if (c1.card().color().size() != c2.card().color().size()) {
			int s1 = c1.card().color().size();
			if (s1 == 0) {
				s1 = emi.lib.mtg.characteristic.Color.values().length + 1;
			}

			int s2 = c2.card().color().size();
			if (s2 == 0) {
				s2 = emi.lib.mtg.characteristic.Color.values().length + 1;
			}

			return s1 - s2;
		}

		for (int i = emi.lib.mtg.characteristic.Color.values().length - 1; i >= 0; --i) {
			emi.lib.mtg.characteristic.Color c = emi.lib.mtg.characteristic.Color.values()[i];
			long n1 = -c1.card().manaCost().symbols().stream().filter(Objects::nonNull).map(ManaSymbol::colors).filter(s -> s != null && s.contains(c)).count();
			long n2 = -c2.card().manaCost().symbols().stream().filter(Objects::nonNull).map(ManaSymbol::colors).filter(s -> s != null && s.contains(c)).count();

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
