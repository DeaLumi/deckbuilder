package emi.mtg.deckbuilder.view.omnifilter.filters;

import emi.lib.Service;
import emi.lib.mtg.card.CardFace;
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
	public boolean testFace(CardFace face) {
		switch (operator) {
			case DIRECT:
			case GREATER_THAN:
				return face.text().toLowerCase().contains(value.toLowerCase());
			default:
				return false;
		}
	}
}
