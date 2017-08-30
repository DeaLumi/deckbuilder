package emi.mtg.deckbuilder.view.omnifilter.filters;

import emi.lib.Service;
import emi.lib.mtg.characteristic.Color;
import emi.mtg.deckbuilder.controller.Context;
import emi.mtg.deckbuilder.model.CardInstance;
import emi.mtg.deckbuilder.view.omnifilter.Omnifilter;
import emi.mtg.deckbuilder.view.omnifilter.Util;

import java.util.Set;

import static emi.mtg.deckbuilder.view.omnifilter.filters.Colors.colorsIn;

@Service.Provider(Omnifilter.Subfilter.class)
@Service.Property.String(name="key", value="identity")
@Service.Property.String(name="shorthand", value="ci")
public class ColorIdentity implements Omnifilter.Subfilter {
	private final Omnifilter.Operator operator;
	private final Set<Color> colors;

	public ColorIdentity(Context context, Omnifilter.Operator operator, String value) {
		this.operator = operator;
		this.colors = colorsIn(value);
	}

	@Override
	public boolean test(CardInstance ci) {
		Util.SetComparison ciComp = Util.compareSets(ci.card().colorIdentity(), this.colors);

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
	}
}
