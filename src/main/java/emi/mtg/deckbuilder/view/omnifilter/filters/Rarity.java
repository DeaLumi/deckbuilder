package emi.mtg.deckbuilder.view.omnifilter.filters;

import emi.lib.Service;
import emi.lib.mtg.characteristic.CardRarity;
import emi.mtg.deckbuilder.controller.Context;
import emi.mtg.deckbuilder.model.CardInstance;
import emi.mtg.deckbuilder.view.omnifilter.Omnifilter;

import java.util.Arrays;

@Service.Provider(Omnifilter.Subfilter.class)
@Service.Property.String(name="key", value="rarity")
@Service.Property.String(name="shorthand", value="r")
public class Rarity implements Omnifilter.Subfilter {
	private final Omnifilter.Operator operator;
	private final CardRarity value;

	public Rarity(Context context, Omnifilter.Operator operator, String value) {
		this.operator = operator;

		this.value = Arrays.stream(CardRarity.values())
				.filter(r -> r.toString().startsWith(value.toLowerCase()))
				.findAny()
				.orElseThrow(() -> new IllegalArgumentException("Couldn't find a card rarity for " + value));
	}

	@Override
	public boolean test(CardInstance cardInstance) {
		switch (this.operator) {
			case DIRECT:
			case EQUALS:
				return cardInstance.printing().rarity() == this.value;
			case NOT_EQUALS:
				return cardInstance.printing().rarity() != this.value;
			case GREATER_OR_EQUALS:
				return cardInstance.printing().rarity().ordinal() >= this.value.ordinal();
			case GREATER_THAN:
				return cardInstance.printing().rarity().ordinal() > this.value.ordinal();
			case LESS_OR_EQUALS:
				return cardInstance.printing().rarity().ordinal() <= this.value.ordinal();
			case LESS_THAN:
				return cardInstance.printing().rarity().ordinal() < this.value.ordinal();
			default:
				assert false;
				return false;
		}
	}
}
