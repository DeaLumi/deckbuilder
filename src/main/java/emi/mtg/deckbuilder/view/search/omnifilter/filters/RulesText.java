package emi.mtg.deckbuilder.view.search.omnifilter.filters;

import emi.mtg.deckbuilder.model.CardInstance;
import emi.mtg.deckbuilder.view.search.omnifilter.Omnifilter;

import java.util.*;
import java.util.function.*;
import java.util.stream.Collectors;

public class RulesText implements Omnifilter.Subfilter {
	@Override
	public Collection<String> keys() {
		return Arrays.asList("text", "oracle", "o");
	}

	@Override
	public String description() {
		return "Search cards' rules (oracle) text. Only responds to the `:` operator.";
	}

	private static BiPredicate<String, String> create(String searchText) {
		if (searchText.indexOf('~') < 0 && !searchText.contains("CARDNAME")) {
			return (rules, name) -> rules.toLowerCase().contains(searchText.toLowerCase());
		}

		List<MatchUtils.SequenceMatcher> rope = Arrays.stream(searchText.split("(~|CARDNAME)"))
				.filter(s -> !s.isEmpty())
				.map(String::toLowerCase)
				.map(MatchUtils::charSequenceMatcher)
				.collect(Collectors.toList());

		return (rules, name) -> MatchUtils.matchesRope(rules, 0, new MatchUtils.Delimited<>(rope, MatchUtils.cardNameOrSubNameMatcher(name))) >= 0;
	}

	@Override
	public Predicate<CardInstance> create(Omnifilter.Operator operator, String value) {
		if (operator != Omnifilter.Operator.DIRECT) throw new IllegalArgumentException("Can only use ':' with rules filter.");

		BiPredicate<String, String> base = RulesText.create(value);
		return (Omnifilter.FaceFilter) face -> base.test(face.rules(), face.name());
	}
}
