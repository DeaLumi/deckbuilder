package emi.mtg.deckbuilder.view.omnifilter.filters;

import emi.mtg.deckbuilder.model.CardInstance;
import emi.mtg.deckbuilder.view.omnifilter.Omnifilter;

import java.util.function.Predicate;

public class ConvertedManaCost implements Omnifilter.Subfilter {
	@Override
	public String key() {
		return "cmc";
	}

	@Override
	public String shorthand() {
		return null;
	}

	@Override
	public Predicate<CardInstance> create(Omnifilter.Operator operator, String value) {
		double doubleValue = Double.parseDouble(value);

		return (Omnifilter.FaceFilter) face -> {
			double cmc = face.convertedManaCost();

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
