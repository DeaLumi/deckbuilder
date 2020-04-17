package emi.mtg.deckbuilder.view.omnifilter.filters;

import emi.lib.Service;
import emi.lib.mtg.Card;
import emi.mtg.deckbuilder.view.omnifilter.Omnifilter;

@Service.Provider(Omnifilter.Subfilter.class)
@Service.Property.String(name="key", value="text")
@Service.Property.String(name="shorthand", value="o")
public class RulesText implements Omnifilter.FaceFilter {
	private final Omnifilter.Operator operator;
	private final String value;

	public RulesText(Omnifilter.Operator operator, String value) {
		this.operator = operator;
		this.value = value;
	}

	@Override
	public boolean testFace(Card.Face face) {
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
	}
}
