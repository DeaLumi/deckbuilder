package emi.mtg.deckbuilder.view.omnifilter.filters;

import emi.lib.mtg.Set;
import emi.mtg.deckbuilder.controller.Context;
import emi.mtg.deckbuilder.model.CardInstance;
import emi.mtg.deckbuilder.view.omnifilter.Omnifilter;

import java.util.function.Predicate;

public class CardSet implements Omnifilter.Subfilter {
	@Override
	public String key() {
		return "set";
	}

	@Override
	public String shorthand() {
		return "s";
	}

	@Override
	public Predicate<CardInstance> create(Omnifilter.Operator operator, String value) {
		Set set = Context.get().data.sets().stream()
				.filter(s -> s.name().toLowerCase().equals(value.toLowerCase()) || s.code().toLowerCase().equals(value.toLowerCase()))
				.findAny().orElseThrow(() -> new IllegalArgumentException("No such set " + value));

		return ci -> {
			if (value == null) {
				return false;
			}

			switch (operator) {
				case LESS_OR_EQUALS:
				case EQUALS:
					return ci.set() == set;
				case NOT_EQUALS:
					return ci.set() != set;
				case GREATER_THAN:
					return ci.card().printings().stream().anyMatch(x -> x.set() == set) && ci.set() != set;
				case DIRECT:
				case GREATER_OR_EQUALS:
					return ci.card().printings().stream().anyMatch(x -> x.set() == set);
				case LESS_THAN:
					return false;
				default:
					assert false;
					return false;
			}
		};
	}
}
