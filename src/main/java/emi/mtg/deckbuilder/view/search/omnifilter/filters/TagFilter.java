package emi.mtg.deckbuilder.view.search.omnifilter.filters;

import emi.mtg.deckbuilder.model.CardInstance;
import emi.mtg.deckbuilder.view.search.omnifilter.Omnifilter;
import emi.mtg.deckbuilder.view.search.omnifilter.Util;

import java.util.Arrays;
import java.util.Collection;
import java.util.HashSet;
import java.util.Set;
import java.util.function.Predicate;

public class TagFilter implements Omnifilter.Subfilter {
	@Override
	public Collection<String> keys() {
		return Arrays.asList("tagged", "tag");
	}

	@Override
	public String description() {
		return "Find cards based on tags. Separate multiple tags with commas.";
	}

	@Override
	public Predicate<CardInstance> create(Omnifilter.Operator operator, String value) {
		Set<String> tagSet = new HashSet<>(Arrays.asList(value.split(",")));

		return ci -> {
			Util.SetComparison compare = Util.compareStringSetsInsensitive(ci.tags(), tagSet);

			switch (operator) {
				case EQUALS:
					return compare == Util.SetComparison.EQUALS;
				case NOT_EQUALS:
					return compare != Util.SetComparison.EQUALS;
				case LESS_THAN:
					return compare == Util.SetComparison.LESS_THAN;
				case GREATER_THAN:
					return compare == Util.SetComparison.GREATER_THAN;
				case LESS_OR_EQUALS:
					return compare == Util.SetComparison.LESS_THAN || compare == Util.SetComparison.EQUALS;
				case DIRECT:
				case GREATER_OR_EQUALS:
					return compare == Util.SetComparison.GREATER_THAN || compare == Util.SetComparison.EQUALS;
				default:
					return false;
			}
		};
	}
}
