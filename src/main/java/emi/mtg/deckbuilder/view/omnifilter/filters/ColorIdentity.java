package emi.mtg.deckbuilder.view.omnifilter.filters;

import emi.lib.mtg.characteristic.Color;
import emi.mtg.deckbuilder.model.CardInstance;
import emi.mtg.deckbuilder.view.omnifilter.Omnifilter;
import emi.mtg.deckbuilder.view.omnifilter.Util;

import java.util.Set;
import java.util.function.Predicate;

import static emi.mtg.deckbuilder.view.omnifilter.filters.Colors.colorsIn;

public class ColorIdentity implements Omnifilter.Subfilter {
	@Override
	public String key() {
		return "identity";
	}

	@Override
	public String shorthand() {
		return "ci";
	}

	@Override
	public Predicate<CardInstance> create(Omnifilter.Operator operator, String value) {
		Set<Color> colors = colorsIn(value);
		return ci -> {
			Util.SetComparison ciComp = Util.compareSets(ci.card().colorIdentity(), colors);

			switch (operator) {
				case EQUALS:
					return ciComp == Util.SetComparison.EQUALS;
				case NOT_EQUALS:
					return ciComp != Util.SetComparison.EQUALS;
				case DIRECT:
				case LESS_OR_EQUALS:
					return ciComp == Util.SetComparison.EQUALS || ciComp == Util.SetComparison.LESS_THAN;
				case GREATER_OR_EQUALS:
					return ciComp == Util.SetComparison.EQUALS || ciComp == Util.SetComparison.GREATER_THAN;
				case LESS_THAN:
					return ciComp == Util.SetComparison.LESS_THAN;
				case GREATER_THAN:
					return ciComp == Util.SetComparison.GREATER_THAN;
				default:
					assert false;
					return false;
			}
		};
	}
}
