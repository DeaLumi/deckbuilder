package emi.mtg.deckbuilder.view.search.omnifilter.filters;

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

		protected Predicate<CardInstance> create(Omnifilter.Operator operator, int size) {
			return Omnifilter.Operator.faceComparison(operator, f -> f.color().size() - size);
		}

		protected Predicate<CardInstance> create(Omnifilter.Operator operator, Color.Combination colors) {
			return Omnifilter.Operator.faceComparison(operator, f -> Color.Combination.COMPARATOR.compare(f.color(), colors).value());
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

		protected Predicate<CardInstance> create(Omnifilter.Operator operator, int count) {
			return Omnifilter.Operator.comparison(operator, ci -> ci.card().colorIdentity().size() - count);
		}

		protected Predicate<CardInstance> create(Omnifilter.Operator operator, Color.Combination colors) {
			return Omnifilter.Operator.comparison(operator, ci -> Color.Combination.COMPARATOR.compare(ci.card().colorIdentity(), colors).value());
		}
	}

	protected abstract Predicate<CardInstance> create(Omnifilter.Operator operator, int count);

	protected abstract Predicate<CardInstance> create(Omnifilter.Operator operator, Color.Combination colors);

	@Override
	public Predicate<CardInstance> create(Omnifilter.Operator operator, String value) {
		try {
			int count = Integer.parseInt(value);
			if (operator == Omnifilter.Operator.DIRECT) operator = Omnifilter.Operator.EQUALS;
			return create(operator, count);
		} catch (NumberFormatException nfe) {
			// pass
		}

		Color.Combination colors = Color.Combination.byString(value);
		if (colors == null) throw new IllegalArgumentException("Couldn't recognize color constant \"" + value + "\"");
		if (operator == Omnifilter.Operator.DIRECT) operator = Omnifilter.Operator.LESS_OR_EQUALS;
		return create(operator, colors);
	}
}
