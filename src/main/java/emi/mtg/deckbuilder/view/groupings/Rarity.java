package emi.mtg.deckbuilder.view.groupings;

import emi.lib.Service;
import emi.lib.mtg.characteristic.CardRarity;
import emi.mtg.deckbuilder.model.CardInstance;
import emi.mtg.deckbuilder.view.CardView;

import java.util.Arrays;
import java.util.Comparator;

@Service.Provider(CardView.Grouping.class)
@Service.Property.String(name="name", value="Rarity")
public class Rarity implements CardView.Grouping {
	private static final String[] GROUPS;

	static {
		GROUPS = Arrays.stream(CardRarity.values())
				.sorted(Comparator.comparingInt(CardRarity::ordinal).reversed())
				.map(CardRarity::toString)
				.toArray(String[]::new);
	}

	@Override
	public String toString() {
		return "Rarity";
	}

	@Override
	public String[] groups() {
		return GROUPS;
	}

	@Override
	public String extract(CardInstance ci) {
		return ci.printing().rarity().toString();
	}

	@Override
	public void add(CardInstance ci, String which) {
		// do nothing
	}

	@Override
	public void remove(CardInstance ci, String which) {
		// do nothing
	}
}
