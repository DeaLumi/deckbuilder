package emi.mtg.deckbuilder.view.groupings;

import emi.lib.Service;
import emi.lib.mtg.characteristic.CardType;
import emi.lib.mtg.characteristic.ManaCost;
import emi.mtg.deckbuilder.model.CardInstance;
import emi.mtg.deckbuilder.view.CardView;

@Service.Provider(CardView.Grouping.class)
@Service.Property.String(name="name", value="CMC")
public class ConvertedManaCost implements CardView.Grouping {
	private static final String[] GROUPS = { "Land", "0", "1", "2", "3", "4", "5", "6", "7", "8", "9", "10", "11", "12", "13", "14", "15", "16", "X" };

	@Override
	public String[] groups() {
		return GROUPS;
	}

	@Override
	public String extract(CardInstance ci) {
		if (ci.card().front() != null && ci.card().front().type().cardTypes().contains(CardType.Land)) {
			return "Land";
		}

		ManaCost mc = ci.card().manaCost();
		return mc.varies() ? "X" : Integer.toString(mc.convertedCost());
	}

	@Override
	public void add(CardInstance ci, String which) {
		// do nothing
	}

	@Override
	public void remove(CardInstance ci, String which) {
		// do nothing
	}
}
