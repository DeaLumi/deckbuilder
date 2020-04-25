package emi.mtg.deckbuilder.view.groupings;

import emi.lib.mtg.characteristic.CardRarity;
import emi.mtg.deckbuilder.model.CardInstance;
import emi.mtg.deckbuilder.view.components.CardView;

import java.util.Arrays;
import java.util.Comparator;

public class Rarity extends CharacteristicGrouping implements CardView.Grouping {
	public static final Rarity INSTANCE = new Rarity();

	private static final String[] GROUPS;

	static {
		GROUPS = Arrays.stream(CardRarity.values())
				.sorted(Comparator.comparingInt(CardRarity::ordinal).reversed())
				.map(CardRarity::toString)
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
		return ci.printing().rarity().toString();
	}
}
