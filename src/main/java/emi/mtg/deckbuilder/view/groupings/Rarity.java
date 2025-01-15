package emi.mtg.deckbuilder.view.groupings;

import emi.mtg.deckbuilder.model.CardInstance;
import emi.mtg.deckbuilder.view.components.CardView;

import java.util.Arrays;
import java.util.Comparator;

public class Rarity extends CharacteristicGrouping implements CardView.Grouping {
	public static final Rarity INSTANCE = new Rarity();

	private static final String[] GROUPS;

	static {
		GROUPS = Arrays.stream(emi.lib.mtg.enums.Rarity.values())
				.sorted(Comparator.comparingInt(emi.lib.mtg.enums.Rarity::ordinal).reversed())
				.map(emi.lib.mtg.enums.Rarity::toString)
				.toArray(String[]::new);
	}

	@Override
	public String name() {
		return "Rarity";
	}

	@Override
	public String toString() {
		return name();
	}

	@Override
	public String[] groupValues() {
		return GROUPS;
	}

	@Override
	public String extract(CardInstance ci) {
		return ci.print().rarity().toString();
	}
}
