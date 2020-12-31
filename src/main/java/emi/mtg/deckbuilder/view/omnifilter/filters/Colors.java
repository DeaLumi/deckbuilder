package emi.mtg.deckbuilder.view.omnifilter.filters;

import emi.lib.mtg.characteristic.Color;
import emi.mtg.deckbuilder.model.CardInstance;
import emi.mtg.deckbuilder.view.omnifilter.Omnifilter;
import emi.mtg.deckbuilder.view.omnifilter.Util;

import java.util.Arrays;
import java.util.Collections;
import java.util.EnumSet;
import java.util.Set;
import java.util.function.Predicate;

public class Colors implements Omnifilter.Subfilter {
	static Set<Color> colorsIn(String in) {
		Set<Color> out = EnumSet.noneOf(Color.class);

		Arrays.stream(Color.values())
				.filter(c -> in.toLowerCase().contains(c.letter.toLowerCase()))
				.forEach(out::add);

		return Collections.unmodifiableSet(out);
	}

	@Override
	public String key() {
		return "color";
	}

	@Override
	public String shorthand() {
		return "c";
	}

	@Override
	public Predicate<CardInstance> create(Omnifilter.Operator operator, String value) {
		try {
			int count = Integer.parseInt(value);
			return (Omnifilter.FaceFilter) face -> {
				switch (operator) {
					case EQUALS:
					case DIRECT:
						return face.color().size() == count;
					case NOT_EQUALS:
						return face.color().size() != count;
					case LESS_THAN:
						return face.color().size() < count;
					case GREATER_THAN:
						return face.color().size() > count;
					case LESS_OR_EQUALS:
						return face.color().size() <= count;
					case GREATER_OR_EQUALS:
						return face.color().size() >= count;
					default:
						assert false;
						return false;
				}
			};
		} catch (NumberFormatException nfe) {
			// pass
		}

		Set<Color> colors = colorsIn(value);

		return (Omnifilter.FaceFilter) face -> {
			Util.SetComparison ciComp = Util.compareSets(face.color(), colors);

			switch (operator) {
				case EQUALS:
					return ciComp == Util.SetComparison.EQUALS;
				case NOT_EQUALS:
					return ciComp != Util.SetComparison.EQUALS;
				case DIRECT:
				case LESS_OR_EQUALS:
					return ciComp == Util.SetComparison.EQUALS || ciComp == Util.SetComparison.LESS_THAN;
				case GREATER_OR_EQUALS:
					return ciComp == Util.SetComparison.EQUALS || ciComp == Util.SetComparison.GREATER_THAN;
				case LESS_THAN:
					return ciComp == Util.SetComparison.LESS_THAN;
				case GREATER_THAN:
					return ciComp == Util.SetComparison.GREATER_THAN;
				default:
					assert false;
					return false;
			}
		};
	}
}
