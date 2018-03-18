package emi.mtg.deckbuilder.view.sortings;

import emi.lib.Service;
import emi.lib.mtg.characteristic.ManaCost;
import emi.mtg.deckbuilder.model.CardInstance;
import emi.mtg.deckbuilder.view.components.CardView;

@Service.Provider(CardView.Sorting.class)
@Service.Property.String(name="name", value="Converted Mana Cost")
public class CMC implements CardView.Sorting {
	@Override
	public int compare(CardInstance o1, CardInstance o2) {
		ManaCost mc1 = o1.card().manaCost();
		ManaCost mc2 = o2.card().manaCost();

		if (mc1.varies() != mc2.varies()) {
			return mc1.varies() ? 1 : -1;
		} else {
			return Double.compare(o1.card().manaCost().convertedCost(), o2.card().manaCost().convertedCost());
		}
	}

	@Override
	public String toString() {
		return "Converted Mana Cost";
	}
}
