package emi.mtg.deckbuilder.view.search.omnifilter.filters;

import emi.lib.mtg.Card;
import emi.lib.mtg.TypeLine;
import emi.lib.mtg.game.validation.Commander;
import emi.mtg.deckbuilder.model.CardInstance;
import emi.mtg.deckbuilder.view.search.omnifilter.Omnifilter;

import java.util.*;
import java.util.function.Predicate;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

public class IsFilter implements Omnifilter.Subfilter {
	private static final Map<String, Predicate<CardInstance>> OPTS;
	private static final Map<String, String> DOCS;

	private static final Pattern FETCH_PATTERN = Pattern.compile("^\\{T\\}, (Pay 1 life, )?Sacrifice [A-Za-z ]+: Search your library for an? (basic land|(Plains|Island|Swamp|Mountain|Forest) or (Plains|Island|Swamp|Mountain|Forest))");

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
		opts.put("dual", ci -> ci.card().front() != null && ci.card().front().type().is(emi.lib.mtg.enums.CardType.Land) && TypeLine.landColorIdentity(ci.card().front().type()).size() == 2);
		docs.put("dual", "Finds lands with two land types.");
		opts.put("triome", ci -> ci.card().front() != null && ci.card().front().type().is(emi.lib.mtg.enums.CardType.Land) && TypeLine.landColorIdentity(ci.card().front().type()).size() == 3);
		docs.put("triome", "Finds lands with three land types.");
		opts.put("fetch", ci -> ci.card().front() != null && ci.card().front().type().is(emi.lib.mtg.enums.CardType.Land) && FETCH_PATTERN.matcher(ci.card().front().rules()).find());
		docs.put("fetch", "Finds lands which are sacrificed to search for other lands to put onto the battlefield.");

		OPTS = opts;
		DOCS = docs;
	}

	@Override
	public Collection<String> keys() {
		return Collections.singleton("is");
	}

	@Override
	public String description() {
		return "Tests for unusual attributes:\n" +
				DOCS.entrySet().stream().map(e -> "  - `is:" + e.getKey() + "` -- " + e.getValue()).collect(Collectors.joining("\n"));
	}

	@Override
	public Predicate<CardInstance> create(Omnifilter.Operator operator, String value) {
		if (operator != Omnifilter.Operator.DIRECT) throw new IllegalArgumentException("'is' only allows the direct comparison operator (:)");
		if (!OPTS.containsKey(value.toLowerCase())) throw new IllegalArgumentException("Unrecognzied 'is' check \"" + value + "\"");
		return OPTS.get(value.toLowerCase());
	}
}
