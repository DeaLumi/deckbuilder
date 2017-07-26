package emi.mtg.deckbuilder.view.omnifilter.filters;

import emi.lib.Service;
import emi.mtg.deckbuilder.model.CardInstance;
import emi.mtg.deckbuilder.view.omnifilter.Omnifilter;

@Service.Provider(Omnifilter.Subfilter.class)
@Service.Property.String(name="key", value="cmc")
public class ConvertedManaCost implements Omnifilter.Subfilter {
	private final Omnifilter.Operator operator;
	private final double value;

	public ConvertedManaCost(Omnifilter.Operator operator, String value) {
		this.operator = operator;
		this.value = Double.parseDouble(value);
	}

	@Override
	public boolean test(CardInstance ci) {
		double cmc = ci.card().manaCost().convertedCost();

		switch (operator) {
			case DIRECT:
			case EQUALS:
				return cmc == value;
			case NOT_EQUALS:
				return cmc != value;
			case LESS_OR_EQUALS:
				return cmc <= value;
			case LESS_THAN:
				return cmc < value;
			case GREATER_OR_EQUALS:
				return cmc >= value;
			case GREATER_THAN:
				return cmc > value;
			default:
				assert false;
				return false;
		}
	}
}
