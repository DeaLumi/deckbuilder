package emi.mtg.deckbuilder.view.search.omnifilter.filters;

import emi.mtg.deckbuilder.model.CardInstance;
import emi.mtg.deckbuilder.view.search.omnifilter.Omnifilter;

import java.util.Arrays;
import java.util.Collection;
import java.util.function.Predicate;

public class RulesText implements Omnifilter.Subfilter {
	@Override
	public Collection<String> keys() {
		return Arrays.asList("text", "oracle", "o");
	}

	@Override
	public String description() {
		return "Search cards' rules (oracle) text.";
	}

	@Override
	public Predicate<CardInstance> create(Omnifilter.Operator operator, String value) {
		if (operator != Omnifilter.Operator.DIRECT) throw new IllegalArgumentException("Can only use ':' with rules filter.");

		if (value.indexOf('~') >= 0 || value.contains("CARDNAME")) {
			return (Omnifilter.FaceFilter) face -> face.rules().toLowerCase().contains(value.replace("~", face.name()).replace("CARDNAME", face.name()).toLowerCase());
		} else {
			final String search = value.toLowerCase();
			return (Omnifilter.FaceFilter) face -> face.rules().toLowerCase().contains(search);
		}
	}
}
