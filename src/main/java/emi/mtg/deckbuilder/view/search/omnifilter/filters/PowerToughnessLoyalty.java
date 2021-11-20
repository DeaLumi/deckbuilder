package emi.mtg.deckbuilder.view.search.omnifilter.filters;

import emi.lib.mtg.Card;
import emi.mtg.deckbuilder.model.CardInstance;
import emi.mtg.deckbuilder.view.search.omnifilter.Omnifilter;

import java.util.Arrays;
import java.util.Collection;
import java.util.function.Predicate;

public abstract class PowerToughnessLoyalty implements Omnifilter.Subfilter {
	protected abstract double getValue(Card.Face face);

	protected abstract String getRawValue(Card.Face face);

	public static class Power extends PowerToughnessLoyalty {
		@Override
		public Collection<String> keys() {
			return Arrays.asList("power", "pow");
		}

		@Override
		public String description() {
			return "Search cards based on power. Use * or X to find cards with variable power.";
		}

		@Override
		protected double getValue(Card.Face face) {
			return face.power();
		}

		@Override
		protected String getRawValue(Card.Face face) {
			return face.printedPower();
		}
	}

	public static class Toughness extends PowerToughnessLoyalty {
		@Override
		public Collection<String> keys() {
			return Arrays.asList("toughness", "tou");
		}

		@Override
		public String description() {
			return "Search cards based on toughness. Use * or X to find cards with variable toughness.";
		}

		@Override
		protected double getValue(Card.Face face) {
			return face.toughness();
		}

		@Override
		protected String getRawValue(Card.Face face) {
			return face.printedToughness();
		}
	}

	public static class Loyalty extends PowerToughnessLoyalty {
		@Override
		public Collection<String> keys() {
			return Arrays.asList("loyalty", "loy");
		}

		@Override
		public String description() {
			return "Search cards based on loyalty. Use * or X to find cards with variable loyalty.";
		}

		@Override
		protected double getValue(Card.Face face) {
			return face.loyalty();
		}

		@Override
		protected String getRawValue(Card.Face face) {
			return face.printedLoyalty();
		}
	}

	@Override
	public Predicate<CardInstance> create(Omnifilter.Operator operator, String value) {
		if (value.contains("*") || value.contains("X")) {
			switch (operator) {
				case DIRECT:
				case EQUALS:
					return (Omnifilter.FaceFilter) f -> getRawValue(f).contains("*") || getRawValue(f).contains("X");
				case NOT_EQUALS:
					return (Omnifilter.FaceFilter) f -> !getRawValue(f).contains("*") && !getRawValue(f).contains("X");
				default:
					throw new IllegalArgumentException("Can't use comparison operators with variable values.");
			}
		}

		double doubleValue = Double.parseDouble(value.toLowerCase());
		if (operator == Omnifilter.Operator.DIRECT) operator = Omnifilter.Operator.EQUALS;
		return Omnifilter.Operator.faceComparison(operator, f -> getValue(f) - doubleValue);
	}
}
