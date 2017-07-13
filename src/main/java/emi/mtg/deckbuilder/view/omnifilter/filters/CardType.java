package emi.mtg.deckbuilder.view.omnifilter.filters;

import emi.lib.Service;
import emi.lib.mtg.card.CardFace;
import emi.mtg.deckbuilder.view.omnifilter.Omnifilter;

@Service.Provider(Omnifilter.Subfilter.class)
@Service.Property.String(name="key", value="type")
@Service.Property.String(name="shorthand", value="t")
public class CardType implements Omnifilter.FaceFilter {
	private final Omnifilter.Operator operator;
	private final String value;

	public CardType(Omnifilter.Operator operator, String value) {
		this.operator = operator;
		this.value = value;
	}

	@Override
	public boolean testFace(CardFace face) {
		switch (operator) {
			case DIRECT:
			case GREATER_OR_EQUALS:
				return face.type().toString().toLowerCase().contains(value.toLowerCase());
			case LESS_OR_EQUALS: // TODO
			default:
				return false;
		}
	}
}
