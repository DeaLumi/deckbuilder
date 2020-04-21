package emi.mtg.deckbuilder.view.groupings;

import emi.lib.mtg.characteristic.Color;
import emi.mtg.deckbuilder.model.CardInstance;

import java.util.*;

public class ColorGrouping extends CharacteristicGrouping {
	public static final ColorGrouping INSTANCE = new ColorGrouping();

	private static final Map<Set<Color>, String> GROUPS = groupsMap();
	private static final String[] GROUP_VALUES = GROUPS.values().toArray(new String[GROUPS.size()]);

	private static Map<Set<Color>, String> groupsMap() {
		Map<Set<Color>, String> tmp = new LinkedHashMap<>();

		tmp.put(EnumSet.noneOf(Color.class), "Colorless");

		tmp.put(EnumSet.of(Color.WHITE), "White");
		tmp.put(EnumSet.of(Color.BLUE), "Blue");
		tmp.put(EnumSet.of(Color.BLACK), "Black");
		tmp.put(EnumSet.of(Color.RED), "Red");
		tmp.put(EnumSet.of(Color.GREEN), "Green");

		tmp.put(EnumSet.of(Color.WHITE, Color.BLUE), "Azorius");
		tmp.put(EnumSet.of(Color.WHITE, Color.BLACK), "Orzhov");
		tmp.put(EnumSet.of(Color.BLUE, Color.BLACK), "Dimir");
		tmp.put(EnumSet.of(Color.BLUE, Color.RED), "Izzet");
		tmp.put(EnumSet.of(Color.BLACK, Color.RED), "Rakdos");
		tmp.put(EnumSet.of(Color.BLACK, Color.GREEN), "Golgari");
		tmp.put(EnumSet.of(Color.RED, Color.GREEN), "Gruul");
		tmp.put(EnumSet.of(Color.RED, Color.WHITE), "Boros");
		tmp.put(EnumSet.of(Color.GREEN, Color.WHITE), "Selesnya");
		tmp.put(EnumSet.of(Color.GREEN, Color.BLUE), "Simic");

		tmp.put(EnumSet.of(Color.WHITE, Color.BLUE, Color.BLACK), "Esper");
		tmp.put(EnumSet.of(Color.WHITE, Color.BLUE, Color.RED), "Jeskai");
		tmp.put(EnumSet.of(Color.WHITE, Color.BLUE, Color.GREEN), "Bant");
		tmp.put(EnumSet.of(Color.WHITE, Color.BLACK, Color.RED), "Mardu");
		tmp.put(EnumSet.of(Color.WHITE, Color.BLACK, Color.GREEN), "Abzan");
		tmp.put(EnumSet.of(Color.WHITE, Color.RED, Color.GREEN), "Naya");
		tmp.put(EnumSet.of(Color.BLUE, Color.BLACK, Color.RED), "Grixis");
		tmp.put(EnumSet.of(Color.BLUE, Color.BLACK, Color.GREEN), "Sultai");
		tmp.put(EnumSet.of(Color.BLUE, Color.RED, Color.GREEN), "Temur");
		tmp.put(EnumSet.of(Color.BLACK, Color.RED, Color.GREEN), "Jund");

		tmp.put(EnumSet.of(Color.WHITE, Color.BLUE, Color.BLACK, Color.RED), "Artifice");
		tmp.put(EnumSet.of(Color.BLUE, Color.BLACK, Color.RED, Color.GREEN), "Chaos");
		tmp.put(EnumSet.of(Color.BLACK, Color.RED, Color.GREEN, Color.WHITE), "Aggression");
		tmp.put(EnumSet.of(Color.RED, Color.GREEN, Color.WHITE, Color.BLUE), "Altruism");
		tmp.put(EnumSet.of(Color.GREEN, Color.WHITE, Color.BLUE, Color.BLACK), "Growth");

		tmp.put(EnumSet.of(Color.WHITE, Color.BLUE, Color.BLACK, Color.RED, Color.GREEN), "Five-Color");

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
