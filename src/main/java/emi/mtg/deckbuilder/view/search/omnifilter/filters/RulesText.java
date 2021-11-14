package emi.mtg.deckbuilder.view.search.omnifilter.filters;

import emi.mtg.deckbuilder.model.CardInstance;
import emi.mtg.deckbuilder.view.search.omnifilter.Omnifilter;

import java.util.Arrays;
import java.util.Collection;
import java.util.function.Predicate;

public class RulesText implements Omnifilter.Subfilter {
	@Override
	public Collection<String> keys() {
		return Arrays.asList("text", "oracle", "o");
	}

	@Override
	public String description() {
		return "Search cards' rules (oracle) text.";
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
