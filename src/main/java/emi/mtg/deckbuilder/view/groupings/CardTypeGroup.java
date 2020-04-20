package emi.mtg.deckbuilder.view.groupings;

import emi.lib.mtg.characteristic.CardType;
import emi.mtg.deckbuilder.model.CardInstance;
import emi.mtg.deckbuilder.view.components.CardView;

import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;

public class CardTypeGroup extends CharacteristicGrouping implements CardView.Grouping {
	public static class Factory implements CardView.Grouping.Factory {
		public static final Factory INSTANCE = new Factory();

		@Override
		public String name() {
			return "Card Type";
		}

		@Override
		public CardView.Grouping create() {
			return new CardTypeGroup();
		}
	}

	private CardType[] PRIORITIES = { CardType.Creature, CardType.Artifact };

	@Override
	public String[] groupValues() {
		return Arrays.stream(CardType.values()).map(CardType::name).toArray(String[]::new);
	}

	@Override
	public String extract(CardInstance ci) {
		for (CardType ct : PRIORITIES) {
			if (ci.card().faces().stream().anyMatch(cf -> cf.type().cardTypes().contains(ct))) {
				return ct.name();
			}
		}

		Set<CardType> type = ci.card().faces().stream()
				.flatMap(f -> f.type().cardTypes().stream())
				.collect(Collectors.toSet());

		assert type.size() == 0 : "Disambiguate: " + type.stream().map(CardType::name).collect(Collectors.joining(", "));

		return type.iterator().next().name();
	}
}
