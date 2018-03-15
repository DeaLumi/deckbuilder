package emi.mtg.deckbuilder.view.omnifilter;

import emi.lib.Service;
import emi.lib.mtg.Card;
import emi.mtg.deckbuilder.controller.Context;
import emi.mtg.deckbuilder.model.CardInstance;

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

	@Service({Context.class, Operator.class, String.class})
	@Service.Property.String(name="key")
	@Service.Property.String(name="shorthand", required=false)
	public interface Subfilter extends Predicate<CardInstance> {
	}

	public interface FaceFilter extends Subfilter {
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

	private static final Map<String, Service.Loader<Subfilter>.Stub> SUBFILTER_FACTORIES = subfilterFactories();

	private static Map<String, Service.Loader<Subfilter>.Stub> subfilterFactories() {
		Map<String, Service.Loader<Subfilter>.Stub> map = new HashMap<>();

		for (Service.Loader<Subfilter>.Stub stub : Service.Loader.load(Subfilter.class)) {
			if (stub.has("shorthand")) {
				map.putIfAbsent(stub.string("shorthand"), stub);
			}
			map.put(stub.string("key"), stub);
		}

		return Collections.unmodifiableMap(map);
	}

	private static final Pattern PATTERN = Pattern.compile("(?<negate>[-!])?(?:(?<key>[A-Za-z]+)(?<op>[><][=]?|[!]?=|:))?(?<value>\"[^\"]*\"|[^\\s]*)");

	public static Predicate<CardInstance> parse(Context context, String expression) {
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

				Service.Loader<Subfilter>.Stub stub = SUBFILTER_FACTORIES.get(key);

				if (stub == null) {
					// TODO: Report this somehow? Or just fail hard?
					continue;
				}

				append = stub.uncheckedInstance(context, op, value);
			} else {
				append = ci -> ci.card().faces().stream().anyMatch(cf -> cf.name().toLowerCase().contains(value.toLowerCase())) || ci.card().fullName().toLowerCase().contains(value.toLowerCase());
			}

			predicate = predicate.and(m.group("negate") == null ? append : append.negate());
		}

		return predicate;
	}
}
