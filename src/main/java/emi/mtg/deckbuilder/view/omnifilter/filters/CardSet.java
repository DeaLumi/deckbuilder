package emi.mtg.deckbuilder.view.omnifilter.filters;

import emi.lib.Service;
import emi.mtg.deckbuilder.model.CardInstance;
import emi.mtg.deckbuilder.view.omnifilter.Omnifilter;

@Service.Provider(Omnifilter.Subfilter.class)
@Service.Property.String(name="key", value="set")
@Service.Property.String(name="shorthand", value="s")
public class CardSet implements Omnifilter.Subfilter {
	private final Omnifilter.Operator operator;
	private final String value;

	public CardSet(Omnifilter.Operator operator, String value) {
		this.operator = operator;
		this.value = value.toLowerCase();
	}

	@Override
	public boolean test(CardInstance ci) {
		switch (operator) {
			case DIRECT:
			case EQUALS:
				return ci.printing().set().name().toLowerCase().equals(value) || ci.printing().set().code().toLowerCase().equals(value);
			case NOT_EQUALS:
				return !ci.printing().set().name().toLowerCase().equals(value) && !ci.printing().set().code().toLowerCase().equals(value);
			case LESS_THAN:
			case GREATER_THAN:
			case LESS_OR_EQUALS:
			case GREATER_OR_EQUALS:
				// TODO: Maybe throw in constructor...
				return true;
			default:
				assert false;
				return false;
		}
	}
}
