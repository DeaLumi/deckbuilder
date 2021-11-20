package emi.mtg.deckbuilder.view.search.omnifilter.filters;

import emi.mtg.deckbuilder.model.CardInstance;
import emi.mtg.deckbuilder.view.search.omnifilter.Omnifilter;

import java.util.Arrays;
import java.util.Collection;
import java.util.function.Predicate;

public class ManaValue implements Omnifilter.Subfilter {
	@Override
	public Collection<String> keys() {
		return Arrays.asList("cmc", "mv");
	}

	@Override
	public String description() {
		return "Filter by mana value (converted mana cost).";
	}

	@Override
	public Predicate<CardInstance> create(Omnifilter.Operator operator, String value) {
		double doubleValue = Double.parseDouble(value);
		if (operator == Omnifilter.Operator.DIRECT) operator = Omnifilter.Operator.EQUALS;
		return Omnifilter.Operator.faceComparison(operator, f -> f.manaValue() - doubleValue);
	}
}
