package emi.mtg.deckbuilder.view.groupings;

import emi.lib.Service;
import emi.lib.mtg.characteristic.CardType;
import emi.mtg.deckbuilder.model.CardInstance;
import emi.mtg.deckbuilder.view.CardView;

import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Service.Provider(CardView.Grouping.class)
@Service.Property.String(name="name", value="Card Type")
public class CardTypeGroup extends CharacteristicGrouping implements CardView.Grouping {
	private CardType[] PRIORITIES = { CardType.Creature, CardType.Artifact };

	public CardTypeGroup(List<CardInstance> model) {
		super(model);
	}

	@Override
	public String toString() {
		return "Card Type";
	}

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
