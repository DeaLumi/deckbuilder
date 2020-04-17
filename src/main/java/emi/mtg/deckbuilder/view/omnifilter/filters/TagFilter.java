package emi.mtg.deckbuilder.view.omnifilter.filters;

import emi.lib.Service;
import emi.mtg.deckbuilder.controller.Context;
import emi.mtg.deckbuilder.model.CardInstance;
import emi.mtg.deckbuilder.view.omnifilter.Omnifilter;
import emi.mtg.deckbuilder.view.omnifilter.Util;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

@Service.Provider(Omnifilter.Subfilter.class)
@Service.Property.String(name="key", value="tagged")
@Service.Property.String(name="shorthand", value="tag")
public class TagFilter implements Omnifilter.Subfilter {
	private final Omnifilter.Operator operator;
	private final Set<String> tagSet;

	public TagFilter(Omnifilter.Operator operator, String value) {
		this.operator = operator;
		this.tagSet = new HashSet<>(Arrays.asList(value.split(",")));
	}

	@Override
	public boolean test(CardInstance ci) {
		Util.SetComparison compare = Util.compareStringSetsInsensitive(Context.get().tags.tags(ci.card()), tagSet);

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
	}
}
