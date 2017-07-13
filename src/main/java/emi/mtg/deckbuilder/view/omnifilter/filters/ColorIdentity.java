package emi.mtg.deckbuilder.view.omnifilter.filters;

import emi.lib.Service;
import emi.lib.mtg.card.CardFace;
import emi.lib.mtg.characteristic.Color;
import emi.mtg.deckbuilder.view.omnifilter.Omnifilter;

import java.util.Arrays;
import java.util.Collections;
import java.util.EnumSet;
import java.util.Set;

import static emi.mtg.deckbuilder.view.omnifilter.filters.Colors.colorsIn;

@Service.Provider(Omnifilter.Subfilter.class)
@Service.Property.String(name="key", value="identity")
@Service.Property.String(name="shorthand", value="ci")
public class ColorIdentity implements Omnifilter.FaceFilter {
	private final Omnifilter.Operator operator;
	private final Set<Color> colors;

	public ColorIdentity(Omnifilter.Operator operator, String value) {
		this.operator = operator;
		this.colors = colorsIn(value);
	}

	@Override
	public boolean testFace(CardFace face) {
		Set<Color> cardColors = face.color();

		switch (operator) {
			case EQUALS:
				return cardColors.equals(colors);
			case NOT_EQUALS:
				return !cardColors.equals(colors);
			case DIRECT:
			case LESS_OR_EQUALS:
				return colors.containsAll(cardColors);
			case GREATER_OR_EQUALS:
				return cardColors.containsAll(colors);
			case LESS_THAN:
				return colors.containsAll(cardColors) && cardColors.size() < colors.size();
			case GREATER_THAN:
				return cardColors.containsAll(colors) && cardColors.size() > colors.size();
			default:
				assert false;
				return false;
		}
	}
}
