package emi.mtg.deckbuilder.view.omnifilter;

import emi.lib.mtg.Card;
import emi.mtg.deckbuilder.model.CardInstance;
import emi.mtg.deckbuilder.view.MainApplication;
import emi.mtg.deckbuilder.view.search.SearchProvider;

import java.util.*;
import java.util.function.Predicate;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

public class Omnifilter implements SearchProvider {
	public enum Operator {
		DIRECT (":"),
		EQUALS ("="),
		NOT_EQUALS ("!="),
		LESS_THAN ("<"),
		LESS_OR_EQUALS ("<="),
		GREATER_THAN (">"),
		GREATER_OR_EQUALS (">=");

		public final String symbol;

		Operator(String symbol) {
			this.symbol = symbol;
		}

		private static final Map<String, Operator> reverse = reverse();

		private static Map<String, Operator> reverse() {
			return Collections.unmodifiableMap(Arrays.stream(Operator.values())
				.collect(Collectors.toMap(s -> s.symbol, s -> s)));
		}

		public static Operator forString(String string) {
			Operator op = reverse.get(string);

			if (op == null) {
				throw new NoSuchElementException("No such operator " + string);
			}

			return op;
		}
	}

	public interface Subfilter {
		String key();
		String shorthand();
		String description();
		Predicate<CardInstance> create(Omnifilter.Operator operator, String value);
	}

	public interface FaceFilter extends Predicate<CardInstance> {
		default boolean allFacesMustMatch() {
			return false;
		}

		boolean testFace(Card.Face face);

		@Override
		default boolean test(CardInstance ci) {
			if (this.allFacesMustMatch()) {
				return ci.card().faces().stream().allMatch(this::testFace);
			} else {
				return ci.card().faces().stream().anyMatch(this::testFace);
			}
		}
	}

	private static final Map<String, Subfilter> SUBFILTER_FACTORIES = subfilterFactories();

	private static Map<String, Subfilter> subfilterFactories() {
		Map<String, Subfilter> map = new HashMap<>();

		for (Subfilter factory : ServiceLoader.load(Subfilter.class, MainApplication.PLUGIN_CLASS_LOADER)) {
			if (factory.shorthand() != null && !factory.shorthand().isEmpty()) {
				map.putIfAbsent(factory.shorthand(), factory);
			}
			map.put(factory.key(), factory);
		}

		return Collections.unmodifiableMap(map);
	}

	private static final Pattern PATTERN = Pattern.compile("(?<negate>[-!])?(?:(?<key>[A-Za-z]+)(?<op>[><][=]?|[!]?=|:))?(?<value>\"[^\"]+\"|[^\\s]+)");

	public static final String NAME = "Omnifilter";

	@Override
	public String toString() {
		return NAME;
	}

	@Override
	public String name() {
		return NAME;
	}

	@Override
	public String usage() {
		String start = String.join("\n",
				"A Scryfall-like search tool with some Deckbuilder-specific enhancements.",
				"Search terms with no prefix/operator search within the card's name. Otherwise:<br/>",
				"<br/>",
				"The following operators are supported, though behavior may vary with search prefix:<br/>",
				"<ul>",
				"<li><code>:</code> &mdash; Tries to take the obvious meaning. Often equivalent to <code>&gt;=</code></li>",
				"<li><code>=</code> &mdash; Exact match, e.g. a card's complete name or exact color identity.</li>",
				"<li><code>!=</code> &mdash; Opposite of exact match, excluding any cards which exactly match the term.</li>",
				"<li><code>&lt;</code> &mdash; Less than, e.g. a card's color identity lies within the provided term.</li>",
				"<li><code>&gt;</code> &mdash; Greater than, e.g. a card's rules text contains this word.</li>",
				"<li><code>&lt;=</code> &mdash; Matches <code>&lt;</code> or <code>=</code></li>",
				"<li><code>&gt;=</code> &mdash; Matches <code>&gt;</code> or <code>=</code></li>",
				"</ul>",
				"The following prefixes are recognized:<br/>");

		String filters = SUBFILTER_FACTORIES.values().stream()
				.distinct()
				.sorted(Comparator.comparing(Subfilter::key))
				.map(f -> "<li><code>" + f.key() + "</code>" + (f.shorthand() != null && !f.shorthand().isEmpty() ? " or <code>" + f.shorthand() + "</code>" : "") + " &mdash; " + f.description() + "</li>")
				.collect(Collectors.joining("\n"));

		String trailer = "Note that complex logic like parenthetical terms and boolean and/or aren't supported. All terms must match.";

		return start + "\n<ul>" + filters + "\n</ul>\n" + trailer;
	}

	@Override
	public Predicate<CardInstance> parse(String expression) throws IllegalArgumentException {
		Matcher m = PATTERN.matcher(expression);

		Predicate<CardInstance> predicate = c -> true;

		while (m.find()) {
			String tmp = m.group("value");
			if (tmp.startsWith("\"") && tmp.endsWith("\"")) {
				tmp = tmp.substring(1, tmp.length() - 1);
			}
			String value = tmp;

			String key = m.group("key");

			Predicate<CardInstance> append;

			if (key != null) {
				Operator op = Operator.forString(m.group("op"));

				Subfilter factory = SUBFILTER_FACTORIES.get(key);

				if (factory == null) {
					throw new IllegalArgumentException("Unrecognized filter " + key);
				}

				append = factory.create(op, value);
			} else {
				append = ci -> ci.card().faces().stream().anyMatch(cf -> cf.name().toLowerCase().contains(value.toLowerCase())) || ci.card().fullName().toLowerCase().contains(value.toLowerCase());
			}

			predicate = predicate.and(m.group("negate") == null ? append : append.negate());
		}

		return predicate;
	}
}
