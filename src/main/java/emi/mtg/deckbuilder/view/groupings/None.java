package emi.mtg.deckbuilder.view.groupings;

import emi.mtg.deckbuilder.model.CardInstance;
import emi.mtg.deckbuilder.view.CardView;

public class None implements CardView.Grouping {
	private static String[] GROUPS = new String[] { "All Cards" };

	@Override
	public String[] groups() {
		return GROUPS;
	}

	@Override
	public String extract(CardInstance ci) {
		return "All Cards";
	}

	@Override
	public void add(CardInstance ci, String which) {
		throw new UnsupportedOperationException();
	}

	@Override
	public void remove(CardInstance ci, String which) {
		throw new UnsupportedOperationException();
	}
}
