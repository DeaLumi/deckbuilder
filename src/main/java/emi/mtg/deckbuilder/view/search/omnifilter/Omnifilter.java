package emi.mtg.deckbuilder.view.search.omnifilter;

import emi.lib.mtg.Card;
import emi.mtg.deckbuilder.model.CardInstance;
import emi.mtg.deckbuilder.view.search.SearchProvider;
import emi.mtg.deckbuilder.util.PluginUtils;

import java.util.*;
import java.util.function.Predicate;
import java.util.function.ToDoubleFunction;
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

		public static <T> Predicate<T> comparison(Operator operator, ToDoubleFunction<T> comparison) {
			switch (operator) {
				case EQUALS:
					return obj -> comparison.applyAsDouble(obj) == 0.0;
				case NOT_EQUALS:
					return obj -> { double v = comparison.applyAsDouble(obj); return v < 0 || v > 0; };
				case LESS_THAN:
					return obj -> comparison.applyAsDouble(obj) < 0.0;
				case GREATER_THAN:
					return obj -> comparison.applyAsDouble(obj) > 0.0;
				case LESS_OR_EQUALS:
					return obj -> comparison.applyAsDouble(obj) <= 0.0;
				case GREATER_OR_EQUALS:
					return obj -> comparison.applyAsDouble(obj) >= 0.0;
				default:
					throw new IllegalArgumentException("The direct operator isn't supported here.");
			}
		}

		public static Predicate<CardInstance> faceComparison(Operator operator, ToDoubleFunction<Card.Face> faceComparison) {
			Predicate<Card.Face> facePredicate = comparison(operator, faceComparison);
			return (FaceFilter) facePredicate::test;
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

	public static final Map<String, Subfilter> SUBFILTER_FACTORIES = subfilterFactories();

	private static Map<String, Subfilter> subfilterFactories() {
		Map<String, Subfilter> map = new HashMap<>();

		for (Subfilter factory : PluginUtils.providers(Subfilter.class)) {
			for (String key : factory.keys()) {
				if (map.containsKey(key)) throw new AssertionError(String.format("Duplicate omnifilter key: %s (from %s and %s)", key, map.get(key), factory));
				map.put(key, factory);
			}
		}

		return Collections.unmodifiableMap(map);
	}

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
				"",
				"Search terms usually take the form prefix-operator-value, e.g. `type>=creature`. ",
				"Terms with no prefix or operator search within the card's name. ",
				"Terms can be negated by prefixing them with `!` or `-`, ",
				"combined using `and` and `or`, ",
				"and surrounded by parenthesis as in `(X or Y)`. ",
				"Parenthesized terms can also be negated.",
				"",
				"The following operators are supported, though behavior may vary with search prefix:",
				"",
				"- `:` -- Tries to take the obvious meaning. Often equivalent to `>=`",
				"- `=` -- Exact match, e.g. a card's complete name or exact color identity.",
				"- `!=` -- Opposite of exact match, excluding any cards which exactly match the term.",
				"- `<` -- Less than, e.g. a card's color identity lies within the provided term.",
				"- `>` -- Greater than, e.g. a card's rules text contains this word.",
				"- `<=` -- Matches `<` or `=`",
				"- `>=` -- Matches `>` or `=`",
				"",
				"The following prefixes are recognized:",
				"");

		String filters = SUBFILTER_FACTORIES.values().stream()
				.distinct()
				.sorted(Comparator.comparing(f -> f.keys().iterator().next()))
				.map(f -> "- " + f.keys().stream().map(k -> "`" + k + "`").collect(Collectors.joining(" or ")) + " -- " + f.description())
				.collect(Collectors.joining("\n"));

		return start + "\n" + filters + "\n\n";
	}

	@Override
	public Predicate<CardInstance> parse(String expression) throws IllegalArgumentException {
		return Parser.parse(expression);
	}
}
