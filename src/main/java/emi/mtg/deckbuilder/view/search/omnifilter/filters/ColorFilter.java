package emi.mtg.deckbuilder.view.search.omnifilter.filters;

import emi.lib.mtg.Card;
import emi.lib.mtg.enums.Color;
import emi.mtg.deckbuilder.model.CardInstance;
import emi.mtg.deckbuilder.view.search.omnifilter.Omnifilter;

import java.util.Arrays;
import java.util.Collection;
import java.util.function.Predicate;

public abstract class ColorFilter implements Omnifilter.Subfilter {
	public static class CardColor extends ColorFilter {
		@Override
		public Collection<String> keys() {
			return Arrays.asList("color", "c");
		}

		@Override
		public String description() {
			return "Find cards by color. Comparison operators perform set comparisons. You can also compare against a number.";
		}

		@Override
		protected Color.Combination get(Card.Face face) {
			return Color.Combination.byColors(face.color());
		}
	}

	public static class ColorIdentity extends ColorFilter {
		@Override
		public Collection<String> keys() {
			return Arrays.asList("identity", "id", "ci");
		}

		@Override
		public String description() {
			return "Find cards by color identity, including colors of symbols in the card's text.";
		}

		@Override
		protected Color.Combination get(Card.Face face) {
			return Color.Combination.byColors(face.colorIdentity());
		}
	}

	protected abstract Color.Combination get(Card.Face face);

	@Override
	public Predicate<CardInstance> create(Omnifilter.Operator operator, String value) {
		try {
			int count = Integer.parseInt(value);
			if (operator == Omnifilter.Operator.DIRECT) operator = Omnifilter.Operator.EQUALS;
			return Omnifilter.Operator.faceComparison(operator, f -> get(f).size() - count);
		} catch (NumberFormatException nfe) {
			// pass
		}

		Color.Combination colors = Color.Combination.byString(value);
		if (colors == null) throw new IllegalArgumentException("Couldn't recognize color constant \"" + value + "\"");
		if (operator == Omnifilter.Operator.DIRECT) operator = Omnifilter.Operator.LESS_OR_EQUALS;
		return Omnifilter.Operator.faceComparison(operator, f -> Color.Combination.COMPARATOR.compare(get(f), colors).value());
	}
}
