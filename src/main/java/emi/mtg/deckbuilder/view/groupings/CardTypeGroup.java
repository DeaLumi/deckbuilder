package emi.mtg.deckbuilder.view.groupings;

import emi.lib.mtg.Card;
import emi.lib.mtg.enums.CardType;
import emi.mtg.deckbuilder.model.CardInstance;
import emi.mtg.deckbuilder.view.components.CardView;

import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

public class CardTypeGroup extends CharacteristicGrouping implements CardView.Grouping {
	public static final CardTypeGroup INSTANCE = new CardTypeGroup();

	private List<CardType> PRIORITIES = Arrays.asList(
			CardType.Creature,
			CardType.Enchantment,
			CardType.Sorcery,
			CardType.Instant,
			CardType.Planeswalker,
			CardType.Land,
			CardType.Artifact
	);

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
		int min = PRIORITIES.size();
		for (Card.Face face : ci.card().faces()) {
			for (CardType type : face.type().cardTypes()) {
				int idx = PRIORITIES.indexOf(type);
				if (idx >= 0 && idx < min) {
					min = idx;
				}
			}
		}

		if (min < PRIORITIES.size()) return PRIORITIES.get(min).toString();

		Set<CardType> type = ci.card().faces().stream()
				.flatMap(f -> f.type().cardTypes().stream())
				.collect(Collectors.toSet());

		if (type.size() > 1) throw new AssertionError("Disambiguate " + type.toString() + " of card " + ci.card().name());

		return type.iterator().next().toString();
	}
}
