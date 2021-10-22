package emi.mtg.deckbuilder.view.groupings;

import emi.lib.mtg.characteristic.Color;
import emi.mtg.deckbuilder.model.CardInstance;

import java.util.*;

public class ColorGrouping extends CharacteristicGrouping {
	public static final ColorGrouping INSTANCE = new ColorGrouping();

	private static final Map<Color.Combination, String> GROUPS = groupsMap();
	private static final String[] GROUP_VALUES = GROUPS.values().toArray(new String[GROUPS.size()]);

	private static Map<Color.Combination, String> groupsMap() {
		Map<Color.Combination, String> tmp = new LinkedHashMap<>();

		for (Color.Combination combo : Color.Combination.values()) {
			tmp.put(combo, combo.aliases.get(0));
		}

		tmp.put(null, "???");

		return Collections.unmodifiableMap(tmp);
	}

	@Override
	public String name() {
		return "Color";
	}

	@Override
	public String[] groupValues() {
		return GROUP_VALUES;
	}

	@Override
	public String extract(CardInstance ci) {
		return GROUPS.getOrDefault(ci.card().manaCost().color(), "???");
	}
}
