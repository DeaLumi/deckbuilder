package emi.mtg.deckbuilder.view.groupings;

import emi.lib.mtg.Card;
import emi.lib.mtg.Mana;
import emi.lib.mtg.enums.CardType;
import emi.mtg.deckbuilder.model.CardInstance;
import emi.mtg.deckbuilder.view.components.CardView;

public class ManaValue extends CharacteristicGrouping implements CardView.Grouping {
	private static final String[] GROUPS = { "Land", "0", "1", "2", "3", "4", "5", "6", "7", "8", "9", "10", "11", "12", "13", "14", "15", "16", "17", "18", "19", "20", "1000000", "X" };

	@Override
	public String name() {
		return "Mana Value";
	}

	@Override
	public String[] groupValues() {
		return GROUPS;
	}

	@Override
	public String extract(CardInstance ci) {
		if (ci.card().front() != null && ci.card().front().type().is(CardType.Land)) {
			return "Land";
		}

		Mana.Value mc = ci.card().manaCost();
		return mc.varies() ? "X" : Integer.toString((int) mc.value());
	}
}
