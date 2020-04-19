package emi.mtg.deckbuilder.view.omnifilter;

import emi.lib.mtg.Card;
import emi.mtg.deckbuilder.model.CardInstance;
import emi.mtg.deckbuilder.view.MainApplication;

import java.util.*;
import java.util.function.Predicate;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

public class Omnifilter {
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

	public static Predicate<CardInstance> parse(String expression) {
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
					// TODO: Report this somehow? Or just fail hard?
					continue;
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
