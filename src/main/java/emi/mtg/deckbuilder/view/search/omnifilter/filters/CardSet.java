package emi.mtg.deckbuilder.view.search.omnifilter.filters;

import emi.lib.mtg.Set;
import emi.mtg.deckbuilder.controller.Context;
import emi.mtg.deckbuilder.model.CardInstance;
import emi.mtg.deckbuilder.view.search.omnifilter.Omnifilter;

import java.util.Arrays;
import java.util.Collection;
import java.util.function.Predicate;

public class CardSet implements Omnifilter.Subfilter {
	@Override
	public Collection<String> keys() {
		return Arrays.asList("set", "s", "expansion", "e");
	}

	@Override
	public String description() {
		return "Find cards printed in the named set, by full name or set code. `=` will show _only_ prints in that set.";
	}

	@Override
	public Predicate<CardInstance> create(Omnifilter.Operator operator, String value) {
		Set set = Context.get().data.sets().stream()
				.filter(s -> s.name().equalsIgnoreCase(value) || s.code().equalsIgnoreCase(value))
				.findAny().orElseThrow(() -> new IllegalArgumentException("No such set " + value));

		return ci -> {
			if (set == null) {
				return false;
			}

			switch (operator) {
				case LESS_OR_EQUALS:
				case EQUALS:
					return ci.set() == set;
				case NOT_EQUALS:
					return ci.set() != set;
				case GREATER_THAN:
					return ci.card().prints().stream().anyMatch(x -> x.set() == set) && ci.set() != set;
				case DIRECT:
				case GREATER_OR_EQUALS:
					return ci.card().prints().stream().anyMatch(x -> x.set() == set);
				case LESS_THAN:
					return ci.card().prints().stream().noneMatch(x -> x.set() == set);
				default:
					assert false;
					return false;
			}
		};
	}
}
