package emi.mtg.deckbuilder.view.omnifilter.filters;

import emi.lib.mtg.characteristic.CardRarity;
import emi.mtg.deckbuilder.model.CardInstance;
import emi.mtg.deckbuilder.view.omnifilter.Omnifilter;

import java.util.Arrays;
import java.util.function.Predicate;

public class Rarity implements Omnifilter.Subfilter {
	@Override
	public String key() {
		return "rarity";
	}

	@Override
	public String shorthand() {
		return "r";
	}

	@Override
	public String description() {
		return "Find cards printed at/above/below the given rarity.";
	}

	@Override
	public Predicate<CardInstance> create(Omnifilter.Operator operator, String svalue) {
		CardRarity value = Arrays.stream(CardRarity.values())
				.filter(r -> r.toString().toLowerCase().startsWith(svalue.toLowerCase()))
				.findAny()
				.orElseThrow(() -> new IllegalArgumentException("Couldn't find a card rarity for " + svalue));

		return ci -> {
			switch (operator) {
				case DIRECT:
				case EQUALS:
					return ci.printing().rarity() == value;
				case NOT_EQUALS:
					return ci.printing().rarity() != value;
				case GREATER_OR_EQUALS:
					return ci.printing().rarity().ordinal() >= value.ordinal();
				case GREATER_THAN:
					return ci.printing().rarity().ordinal() > value.ordinal();
				case LESS_OR_EQUALS:
					return ci.printing().rarity().ordinal() <= value.ordinal();
				case LESS_THAN:
					return ci.printing().rarity().ordinal() < value.ordinal();
				default:
					assert false;
					return false;
			}
		};
	}
}
