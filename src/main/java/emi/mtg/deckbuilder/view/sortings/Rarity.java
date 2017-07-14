package emi.mtg.deckbuilder.view.sortings;

import emi.lib.Service;
import emi.mtg.deckbuilder.model.CardInstance;
import emi.mtg.deckbuilder.view.CardView;

@Service.Provider(CardView.Sorting.class)
public class Rarity implements CardView.Sorting {
	@Override
	public int compare(CardInstance o1, CardInstance o2) {
		return o1.card().rarity().ordinal() - o2.card().rarity().ordinal();
	}

	@Override
	public String toString() {
		return "Rarity";
	}
}
