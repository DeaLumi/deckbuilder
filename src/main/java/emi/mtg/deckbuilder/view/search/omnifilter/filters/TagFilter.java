package emi.mtg.deckbuilder.view.search.omnifilter.filters;

import emi.lib.mtg.util.CollectionComparator;
import emi.mtg.deckbuilder.model.CardInstance;
import emi.mtg.deckbuilder.view.search.omnifilter.Omnifilter;

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

		if (operator == Omnifilter.Operator.DIRECT) operator = Omnifilter.Operator.GREATER_OR_EQUALS;
		return Omnifilter.Operator.comparison(operator, ci -> CollectionComparator.SET_COMPARATOR.compare(ci.tags(), tagSet).value());
	}
}
