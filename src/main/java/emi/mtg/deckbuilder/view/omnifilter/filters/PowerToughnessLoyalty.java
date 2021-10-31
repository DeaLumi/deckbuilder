package emi.mtg.deckbuilder.view.omnifilter.filters;

import emi.lib.mtg.Card;
import emi.mtg.deckbuilder.model.CardInstance;
import emi.mtg.deckbuilder.view.omnifilter.Omnifilter;

import java.util.function.Function;
import java.util.function.Predicate;

public class PowerToughnessLoyalty implements Omnifilter.FaceFilter {
	private final Omnifilter.Operator operator;
	private final boolean hasValue;
	private final double value;
	private final Function<Card.Face, String> characteristic;
	private final Function<Card.Face, Double> convertedCharacteristic;

	private PowerToughnessLoyalty(Omnifilter.Operator operator, String value, Function<Card.Face, String> characteristic, Function<Card.Face, Double> converted) {
		this.operator = operator;
		this.hasValue = !value.contains("*") && !value.contains("X");
		this.value = hasValue ? Float.parseFloat(value.toLowerCase()) : -1;
		this.characteristic = characteristic;
		this.convertedCharacteristic = converted;
	}

	public static class Power implements Omnifilter.Subfilter {
		@Override
		public String key() {
			return "power";
		}

		@Override
		public String shorthand() {
			return "pow";
		}

		@Override
		public String description() {
			return "Search cards based on power. Use * or X to find cards with variable power.";
		}

		@Override
		public Predicate<CardInstance> create(Omnifilter.Operator operator, String value) {
			return new PowerToughnessLoyalty(operator, value, Card.Face::power, Card.Face::convertedPower);
		}
	}

	public static class Toughness implements Omnifilter.Subfilter {
		@Override
		public String key() {
			return "toughness";
		}

		@Override
		public String shorthand() {
			return "tough";
		}

		@Override
		public String description() {
			return "Search cards based on power. Use * or X to find cards with variable toughness.";
		}

		@Override
		public Predicate<CardInstance> create(Omnifilter.Operator operator, String value) {
			return new PowerToughnessLoyalty(operator, value, Card.Face::toughness, Card.Face::convertedToughness);
		}
	}

	public static class Loyalty implements Omnifilter.Subfilter {
		@Override
		public String key() {
			return "loyalty";
		}

		@Override
		public String shorthand() {
			return "loy";
		}

		@Override
		public String description() {
			return "Search cards based on power. Use * or X to find cards with variable loyalty.";
		}

		@Override
		public Predicate<CardInstance> create(Omnifilter.Operator operator, String value) {
			return new PowerToughnessLoyalty(operator, value, Card.Face::loyalty, Card.Face::convertedLoyalty);
		}
	}

	@Override
	public boolean testFace(Card.Face face) {
		double converted = convertedCharacteristic.apply(face);

		if (Double.isNaN(converted)) {
			return false;
		}

		switch (operator) {
			case DIRECT:
			case EQUALS:
				return hasValue ? value == converted : characteristic.apply(face).contains("*");
			case LESS_OR_EQUALS:
				return converted <= value;
			case LESS_THAN:
				return converted < value;
			case GREATER_THAN:
				return converted > value;
			case GREATER_OR_EQUALS:
				return converted >= value;
			case NOT_EQUALS:
				return hasValue ? value != converted : !characteristic.apply(face).contains("*");
			default:
				assert false;
				return false;
		}
	}
}
