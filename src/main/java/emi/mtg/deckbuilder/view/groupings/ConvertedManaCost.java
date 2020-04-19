package emi.mtg.deckbuilder.view.groupings;

import emi.lib.mtg.Card;
import emi.lib.mtg.characteristic.CardType;
import emi.lib.mtg.characteristic.ManaCost;
import emi.mtg.deckbuilder.model.CardInstance;
import emi.mtg.deckbuilder.view.components.CardView;

public class ConvertedManaCost extends CharacteristicGrouping implements CardView.Grouping {
	private static final String[] GROUPS = { "Land", "0", "1", "2", "3", "4", "5", "6", "7", "8", "9", "10", "11", "12", "13", "14", "15", "16", "1000000", "X" };

	@Override
	public String toString() {
		return "CMC";
	}

	@Override
	public String[] groupValues() {
		return GROUPS;
	}

	@Override
	public String extract(CardInstance ci) {
		if (ci.card().face(Card.Face.Kind.Front) != null && ci.card().face(Card.Face.Kind.Front).type().cardTypes().contains(CardType.Land)) {
			return "Land";
		}

		ManaCost mc = ci.card().manaCost();
		return mc.varies() ? "X" : Integer.toString((int) mc.convertedCost());
	}
}
