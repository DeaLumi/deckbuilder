package emi.mtg.deckbuilder.view.sortings;

import emi.lib.Service;
import emi.mtg.deckbuilder.model.CardInstance;
import emi.mtg.deckbuilder.view.CardView;

@Service.Provider(CardView.Sorting.class)
public class Name implements CardView.Sorting {
	@Override
	public int compare(CardInstance c1, CardInstance c2) {
		return c1.card().name().compareTo(c2.card().name());
	}
}
