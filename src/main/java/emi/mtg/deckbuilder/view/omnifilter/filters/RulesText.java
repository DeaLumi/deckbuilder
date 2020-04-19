package emi.mtg.deckbuilder.view.omnifilter.filters;

import emi.mtg.deckbuilder.model.CardInstance;
import emi.mtg.deckbuilder.view.omnifilter.Omnifilter;

import java.util.function.Predicate;

public class RulesText implements Omnifilter.Subfilter {
	@Override
	public String key() {
		return "text";
	}

	@Override
	public String shorthand() {
		return "o";
	}

	@Override
	public Predicate<CardInstance> create(Omnifilter.Operator operator, String value) {
		return (Omnifilter.FaceFilter) face -> {
			switch (operator) {
				case DIRECT:
				case GREATER_OR_EQUALS:
					return face.rules().toLowerCase().contains(value.toLowerCase());
				case LESS_OR_EQUALS:
				case NOT_EQUALS:
				case LESS_THAN:
				case EQUALS:
				case GREATER_THAN:
					// TODO: Maybe throw in constructor...
					return true;
				default:
					return false;
			}
		};
	}
}
