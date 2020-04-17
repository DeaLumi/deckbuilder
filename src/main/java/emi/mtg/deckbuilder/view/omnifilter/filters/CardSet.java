package emi.mtg.deckbuilder.view.omnifilter.filters;

import emi.lib.Service;
import emi.mtg.deckbuilder.controller.Context;
import emi.mtg.deckbuilder.model.CardInstance;
import emi.mtg.deckbuilder.view.omnifilter.Omnifilter;

@Service.Provider(Omnifilter.Subfilter.class)
@Service.Property.String(name="key", value="set")
@Service.Property.String(name="shorthand", value="s")
public class CardSet implements Omnifilter.Subfilter {
	private final Omnifilter.Operator operator;
	private final emi.lib.mtg.Set value;

	public CardSet(Omnifilter.Operator operator, String value) {
		this.operator = operator;
		this.value = Context.get().data.sets().stream()
			.filter(s -> s.name().toLowerCase().equals(value.toLowerCase()) || s.code().toLowerCase().equals(value.toLowerCase()))
			.findAny().orElse(null);
	}

	@Override
	public boolean test(CardInstance ci) {
		if (value == null) {
			return false;
		}

		switch (operator) {
			case LESS_OR_EQUALS:
			case EQUALS:
				return ci.set() == value;
			case NOT_EQUALS:
				return ci.set() != value;
			case GREATER_THAN:
				return ci.card().printings().stream().anyMatch(x -> x.set() == value) && ci.set() != value;
			case DIRECT:
			case GREATER_OR_EQUALS:
				return ci.card().printings().stream().anyMatch(x -> x.set() == value);
			case LESS_THAN:
				return false;
			default:
				assert false;
				return false;
		}
	}
}
