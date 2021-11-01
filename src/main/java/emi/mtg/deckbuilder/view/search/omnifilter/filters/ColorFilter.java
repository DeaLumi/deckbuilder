package emi.mtg.deckbuilder.view.search.omnifilter.filters;

import emi.lib.mtg.Card;
import emi.lib.mtg.characteristic.Color;
import emi.lib.mtg.util.CollectionComparator;
import emi.mtg.deckbuilder.model.CardInstance;
import emi.mtg.deckbuilder.view.search.omnifilter.Omnifilter;

import java.util.Arrays;
import java.util.function.Predicate;

public abstract class ColorFilter implements Omnifilter.Subfilter {
	static Color.Combination colorsIn(String in) {
		return Arrays.stream(Color.values())
				.filter(c -> in.toLowerCase().contains(c.letter.toLowerCase()))
				.collect(Color.Combination.COLOR_COLLECTOR);
	}

	public static class CardColor extends ColorFilter {
		@Override
		public String key() {
			return "color";
		}

		@Override
		public String shorthand() {
			return "c";
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
		public String key() {
			return "identity";
		}

		@Override
		public String shorthand() {
			return "ci";
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
			return (Omnifilter.FaceFilter) face -> {
				switch (operator) {
					case EQUALS:
					case DIRECT:
						return get(face).size() == count;
					case NOT_EQUALS:
						return get(face).size() != count;
					case LESS_THAN:
						return get(face).size() < count;
					case GREATER_THAN:
						return get(face).size() > count;
					case LESS_OR_EQUALS:
						return get(face).size() <= count;
					case GREATER_OR_EQUALS:
						return get(face).size() >= count;
					default:
						assert false;
						return false;
				}
			};
		} catch (NumberFormatException nfe) {
			// pass
		}

		Color.Combination colors = colorsIn(value);

		return (Omnifilter.FaceFilter) face -> {
			CollectionComparator.Result result = Color.Combination.COMPARATOR.compare(Color.Combination.byColors(get(face)), colors);

			switch (operator) {
				case EQUALS:
					return result == CollectionComparator.Result.Equal;
				case NOT_EQUALS:
					return result != CollectionComparator.Result.Equal;
				case DIRECT:
				case LESS_OR_EQUALS:
					return result == CollectionComparator.Result.Equal || result == CollectionComparator.Result.ContainedIn;
				case GREATER_OR_EQUALS:
					return result == CollectionComparator.Result.Equal || result == CollectionComparator.Result.Contains;
				case LESS_THAN:
					return result == CollectionComparator.Result.ContainedIn;
				case GREATER_THAN:
					return result == CollectionComparator.Result.Contains;
				default:
					assert false;
					return false;
			}
		};
	}
}
