package emi.mtg.deckbuilder.view.search.omnifilter;

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
		Collection<String> keys();
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
			for (String key : factory.keys()) {
				if (map.containsKey(key)) throw new AssertionError(String.format("Duplicate omnifilter key: %s (from %s and %s)", key, map.get(key), factory));
				map.put(key, factory);
			}
		}

		return Collections.unmodifiableMap(map);
	}

	private static final Pattern PAREN_PATTERN = Pattern.compile("\"[^\"]+\"|(?<preor> or )?(?<negate>[!-])?\\((?<subexpr>[^)]+)\\)");
	private static final Pattern OR_PATTERN = Pattern.compile("\"[^\"]+\"| or (?<orterm>.+)");
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
				"<p>A Scryfall-like search tool with some Deckbuilder-specific enhancements.</p>",
				"<p>Search terms usually take the form prefix-operator-value, e.g. <code>type>=creature</code>. ",
				"Terms with no prefix or operator search within the card's name. ",
				"Terms can be negated by prefixing them with <code>!</code> or <code>-</code>, ",
				"combined using <code>and</code> and <code>or</code>, ",
				"and surrounded by parenthesis as in <code>(X or Y)</code>. ",
				"Parenthesized terms can also be negated.</p>",
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
				.sorted(Comparator.comparing(f -> f.keys().iterator().next()))
				.map(f -> "<li>" + f.keys().stream().map(k -> "<code>" + k + "</code>").collect(Collectors.joining(" or ")) + " &mdash; " + f.description() + "</li>")
				.collect(Collectors.joining("\n"));

		return start + "\n<ul>" + filters + "\n</ul>\n";
	}

	@Override
	public Predicate<CardInstance> parse(String expression) throws IllegalArgumentException {
		Predicate<CardInstance> predicate = null;

		Matcher pm = PAREN_PATTERN.matcher(expression);
		int lastEnd = 0;
		while (pm.find()) {
			if (pm.group("subexpr") == null) continue;

			if (pm.start() > lastEnd) {
				String prefaceStr = expression.substring(lastEnd, pm.start()).trim();
				boolean or = false;
				if (prefaceStr.startsWith("or")) {
					or = true;
					prefaceStr = prefaceStr.substring(2).trim();
				}
				Predicate<CardInstance> preface = parse(prefaceStr);
				predicate = predicate == null ? preface : (or ? predicate.or(preface) : predicate.and(preface));
			}

			Predicate<CardInstance> parenthesized = parse(pm.group("subexpr").trim());
			if (pm.group("negate") != null) parenthesized = parenthesized.negate();
			predicate = predicate == null ? parenthesized : (pm.group("preor") == null ? predicate.and(parenthesized) : predicate.or(parenthesized));

			lastEnd = pm.end();
		}

		expression = expression.substring(lastEnd).trim();

		pm = OR_PATTERN.matcher(expression);
		lastEnd = 0;
		while (pm.find()) {
			if (pm.group("orterm") == null) continue;

			Predicate<CardInstance> result;
			if (pm.start() > lastEnd) {
				String prefaceStr = expression.substring(lastEnd, pm.start()).trim();
				result = parse(prefaceStr);
			} else {
				throw new IllegalArgumentException("???");
			}

			result = result.or(parse(pm.group("orterm")));
			return result;
		}

		Matcher m = PATTERN.matcher(expression);

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

			if (m.group("negate") != null) append = append.negate();
			predicate = predicate == null ? append : predicate.and(append);
		}

		return predicate;
	}
}
