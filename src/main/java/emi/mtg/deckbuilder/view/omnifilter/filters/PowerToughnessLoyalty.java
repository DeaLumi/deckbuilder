package emi.mtg.deckbuilder.view.omnifilter.filters;

import emi.lib.Service;
import emi.lib.mtg.Card;
import emi.mtg.deckbuilder.controller.Context;
import emi.mtg.deckbuilder.view.omnifilter.Omnifilter;

public abstract class PowerToughnessLoyalty implements Omnifilter.FaceFilter {
	private final Omnifilter.Operator operator;
	private final boolean hasValue;
	private final double value;

	public PowerToughnessLoyalty(Omnifilter.Operator operator, String value) {
		this.operator = operator;
		this.hasValue = !value.contains("*") && !value.contains("X");
		this.value = hasValue ? Float.parseFloat(value.toLowerCase()) : -1;
	}

	@Service.Provider(Omnifilter.Subfilter.class)
	@Service.Property.String(name="key", value="power")
	@Service.Property.String(name="shorthand", value="pow")
	public static class Power extends PowerToughnessLoyalty {
		public Power(Omnifilter.Operator operator, String value) {
			super(operator, value);
		}

		@Override
		protected String faceAttribute(Card.Face face) {
			return face.power();
		}

		@Override
		protected double faceConvertedAttribute(Card.Face face) {
			return face.convertedPower();
		}
	}

	@Service.Provider(Omnifilter.Subfilter.class)
	@Service.Property.String(name="key", value="toughness")
	@Service.Property.String(name="shorthand", value="tough")
	public static class Toughness extends PowerToughnessLoyalty {
		public Toughness(Omnifilter.Operator operator, String value) {
			super(operator, value);
		}

		@Override
		protected String faceAttribute(Card.Face face) {
			return face.toughness();
		}

		@Override
		protected double faceConvertedAttribute(Card.Face face) {
			return face.convertedToughness();
		}
	}

	@Service.Provider(Omnifilter.Subfilter.class)
	@Service.Property.String(name="key", value="loyalty")
	@Service.Property.String(name="shorthand", value="loy")
	public static class Loyalty extends PowerToughnessLoyalty {
		public Loyalty(Omnifilter.Operator operator, String value) {
			super(operator, value);
		}

		@Override
		protected String faceAttribute(Card.Face face) {
			return face.loyalty();
		}

		@Override
		protected double faceConvertedAttribute(Card.Face face) {
			return face.convertedLoyalty();
		}
	}

	protected abstract String faceAttribute(Card.Face face);
	protected abstract double faceConvertedAttribute(Card.Face face);

	@Override
	public boolean testFace(Card.Face face) {
		double converted = faceConvertedAttribute(face);

		if (Double.isNaN(converted)) {
			return false;
		}

		switch (operator) {
			case DIRECT:
			case EQUALS:
				return hasValue ? value == converted : faceAttribute(face).contains("*");
			case LESS_OR_EQUALS:
				return converted <= value;
			case LESS_THAN:
				return converted < value;
			case GREATER_THAN:
				return converted > value;
			case GREATER_OR_EQUALS:
				return converted >= value;
			case NOT_EQUALS:
				return hasValue ? value != converted : !faceAttribute(face).contains("*");
			default:
				assert false;
				return false;
		}
	}
}
