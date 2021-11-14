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

		return (Omnifilter.FaceFilter) face -> {
			double cmc = face.manaValue();

			switch (operator) {
				case DIRECT:
				case EQUALS:
					return cmc == doubleValue;
				case NOT_EQUALS:
					return cmc != doubleValue;
				case LESS_OR_EQUALS:
					return cmc <= doubleValue;
				case LESS_THAN:
					return cmc < doubleValue;
				case GREATER_OR_EQUALS:
					return cmc >= doubleValue;
				case GREATER_THAN:
					return cmc > doubleValue;
				default:
					assert false;
					return false;
			}
		};
	}
}
