package emi.mtg.deckbuilder.view.omnifilter.filters;

import emi.lib.Service;
import emi.lib.mtg.card.CardFace;
import emi.lib.mtg.characteristic.Color;
import emi.mtg.deckbuilder.view.omnifilter.Omnifilter;

import java.util.Arrays;
import java.util.Collections;
import java.util.EnumSet;
import java.util.Set;

@Service.Provider(Omnifilter.Subfilter.class)
@Service.Property.String(name="key", value="color")
@Service.Property.String(name="shorthand", value="c")
public class Colors implements Omnifilter.FaceFilter {
	private final Omnifilter.Operator operator;
	private final Set<Color> colors;

	static Set<Color> colorsIn(String in) {
		Set<Color> out = EnumSet.noneOf(Color.class);

		Arrays.stream(Color.values())
				.filter(c -> in.toLowerCase().contains(c.letter.toLowerCase()))
				.forEach(out::add);

		return Collections.unmodifiableSet(out);
	}

	public Colors(Omnifilter.Operator operator, String value) {
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
