package emi.mtg.deckbuilder.view.sortings;

import emi.lib.Service;
import emi.mtg.deckbuilder.model.CardInstance;
import emi.mtg.deckbuilder.view.components.CardView;

@Service.Provider(CardView.Sorting.class)
@Service.Property.String(name="name", value="Converted Mana Cost")
public class CMC implements CardView.Sorting {
	@Override
	public int compare(CardInstance o1, CardInstance o2) {
		return (int) (o1.card().manaCost().convertedCost() - o2.card().manaCost().convertedCost());
	}

	@Override
	public String toString() {
		return "Converted Mana Cost";
	}
}
