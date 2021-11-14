package emi.mtg.deckbuilder.view.search.omnifilter.filters;

import emi.mtg.deckbuilder.model.CardInstance;
import emi.mtg.deckbuilder.view.search.omnifilter.Omnifilter;

import java.util.Arrays;
import java.util.Collection;
import java.util.function.Predicate;

public class Rarity implements Omnifilter.Subfilter {
	@Override
	public Collection<String> keys() {
		return Arrays.asList("rarity", "r");
	}

	@Override
	public String description() {
		return "Find cards printed at/above/below the given rarity.";
	}

	@Override
	public Predicate<CardInstance> create(Omnifilter.Operator operator, String svalue) {
		emi.lib.mtg.enums.Rarity value = Arrays.stream(emi.lib.mtg.enums.Rarity.values())
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
