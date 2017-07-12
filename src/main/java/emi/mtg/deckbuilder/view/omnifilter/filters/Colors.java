package emi.mtg.deckbuilder.view.omnifilter.filters;

import emi.lib.Service;
import emi.lib.mtg.card.CardFace;
import emi.lib.mtg.characteristic.Color;
import emi.mtg.deckbuilder.view.omnifilter.Omnifilter;

import java.util.Arrays;
import java.util.Collections;
import java.util.EnumSet;
import java.util.Set;

@Service.Provider(Omnifilter.SubfilterFactory.class)
@Service.Property.String(name="key", value="color")
@Service.Property.String(name="shorthand", value="c")
public class Colors implements Omnifilter.FaceFilterFactory {
	private final boolean negated;
	private final Omnifilter.Operator operator;
	private final Set<Color> colors;

	private static Set<Color> colorsIn(String in) {
		Set<Color> out = EnumSet.noneOf(Color.class);

		Arrays.stream(Color.values())
				.filter(c -> in.toLowerCase().contains(c.letter.toLowerCase()))
				.forEach(out::add);

		return Collections.unmodifiableSet(out);
	}

	public Colors(boolean negated, Omnifilter.Operator operator, String value) {
		this.negated = negated;
		this.operator = operator;
		this.colors = colorsIn(value);
	}

	@Override
	public boolean allFacesMustMatch() {
		return false;
	}

	@Override
	public boolean testFace(CardFace face) {
		return false;
	}
}
