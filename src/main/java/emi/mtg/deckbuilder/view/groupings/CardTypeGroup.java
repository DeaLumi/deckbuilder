package emi.mtg.deckbuilder.view.groupings;

import emi.lib.mtg.characteristic.CardType;
import emi.mtg.deckbuilder.model.CardInstance;
import emi.mtg.deckbuilder.view.components.CardView;

import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

public class CardTypeGroup extends CharacteristicGrouping implements CardView.Grouping {
	public static final CardTypeGroup INSTANCE = new CardTypeGroup();

	private CardType[] PRIORITIES = { CardType.Creature, CardType.Artifact };

	@Override
	public String name() {
		return "Card Type";
	}

	@Override
	public String[] groupValues() {
		List<String> types = Arrays.stream(CardType.values()).map(CardType::name).collect(Collectors.toList());;
		types.add("Unknown");
		return types.toArray(new String[0]);
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

		if (type.isEmpty()) {
			return "Unknown";
		}

		assert type.size() == 1 : "Disambiguate: " + type.stream().map(CardType::name).collect(Collectors.joining(", "));

		return type.iterator().next().name();
	}
}
