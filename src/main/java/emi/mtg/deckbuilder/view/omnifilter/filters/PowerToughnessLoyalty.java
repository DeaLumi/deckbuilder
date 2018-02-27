package emi.mtg.deckbuilder.view.omnifilter.filters;

import emi.lib.Service;
import emi.lib.mtg.Card;
import emi.mtg.deckbuilder.controller.Context;
import emi.mtg.deckbuilder.view.omnifilter.Omnifilter;

public abstract class PowerToughnessLoyalty implements Omnifilter.FaceFilter {
	private final Omnifilter.Operator operator;
	private final boolean hasValue;
	private final double value;

	public PowerToughnessLoyalty(Context context, Omnifilter.Operator operator, String value) {
		this.operator = operator;
		this.hasValue = !value.contains("*") && !value.contains("X");
		this.value = hasValue ? -1 : Float.parseFloat(value.toLowerCase());
	}

	@Service.Provider(Omnifilter.Subfilter.class)
	@Service.Property.String(name="key", value="power")
	@Service.Property.String(name="shorthand", value="pow")
	public static class Power extends PowerToughnessLoyalty {
		public Power(Context context, Omnifilter.Operator operator, String value) {
			super(context, operator, value);
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
		public Toughness(Context context, Omnifilter.Operator operator, String value) {
			super(context, operator, value);
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
		public Loyalty(Context context, Omnifilter.Operator operator, String value) {
			super(context, operator, value);
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
		switch (operator) {
			case DIRECT:
			case EQUALS:
				return hasValue ? value == face.convertedPower() : face.power().contains("*");
			case LESS_OR_EQUALS:
				return value <= face.convertedPower();
			case LESS_THAN:
				return value < face.convertedPower();
			case GREATER_THAN:
				return value > face.convertedPower();
			case GREATER_OR_EQUALS:
				return value >= face.convertedPower();
			case NOT_EQUALS:
				return hasValue ? value != face.convertedPower() : !face.power().contains("*");
			default:
				assert false;
				return false;
		}
	}
}
