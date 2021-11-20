package emi.mtg.deckbuilder.view.search.omnifilter.filters;

import emi.lib.mtg.Card;
import emi.lib.mtg.game.validation.Commander;
import emi.mtg.deckbuilder.model.CardInstance;
import emi.mtg.deckbuilder.view.search.omnifilter.Omnifilter;

import java.util.*;
import java.util.function.Predicate;
import java.util.stream.Collectors;

public class IsFilter implements Omnifilter.Subfilter {
	private static final Map<String, Predicate<CardInstance>> OPTS;
	private static final Map<String, String> DOCS;

	static {
		Map<String, Predicate<CardInstance>> opts = new HashMap<>();
		Map<String, String> docs = new HashMap<>();

		opts.put("commander", ci -> Commander.isCommander(ci.card()));
		docs.put("commander", "Finds legendary creatures and cards with \"This can be your commander.\"");
		opts.put("split", ci -> ci.card().face(Card.Face.Kind.Right) != null);
		docs.put("split", "Finds cards with a left (or top) and right face.");
		opts.put("dfc", ci -> ci.card().face(Card.Face.Kind.Transformed) != null);
		docs.put("dfc", "Finds two-faced cards (transforming and MDFCs).");
		opts.put("flip", ci -> ci.card().face(Card.Face.Kind.Flipped) != null);
		docs.put("flip", "Finds cards which flip over (rotating 180 degrees).");
		opts.put("permanent", ci -> ci.card().faces().stream().anyMatch(f -> f.type().isPermanent()));
		docs.put("permanent", "Finds cards which are permanents.");

		OPTS = opts;
		DOCS = docs;
	}

	@Override
	public Collection<String> keys() {
		return Collections.singleton("is");
	}

	@Override
	public String description() {
		return "Tests for unusual attributes: <ul>\n" +
				DOCS.entrySet().stream().map(e -> "<li><code>is:" + e.getKey() + "</code> &mdash; " + e.getValue()).collect(Collectors.joining("\n")) +
				"</ul>";
	}

	@Override
	public Predicate<CardInstance> create(Omnifilter.Operator operator, String value) {
		if (operator != Omnifilter.Operator.DIRECT) throw new IllegalArgumentException("'is' only allows the direct comparison operator (:)");
		if (!OPTS.containsKey(value.toLowerCase())) throw new IllegalArgumentException("Unrecognzied 'is' check \"" + value + "\"");
		return OPTS.get(value.toLowerCase());
	}
}
