package emi.mtg.deckbuilder.view.omnifilter;

import emi.lib.Service;
import emi.lib.mtg.card.CardFace;
import emi.mtg.deckbuilder.model.CardInstance;

import java.util.*;
import java.util.function.Predicate;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class Omnifilter {
	public enum Operator {
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

	@Service({Boolean.class, Operator.class, String.class})
	@Service.Property.String(name="key")
	@Service.Property.String(name="shorthand", required=false)
	public interface SubfilterFactory extends Predicate<CardInstance> {
	}

	public interface FaceFilterFactory extends SubfilterFactory {
		boolean allFacesMustMatch();

		boolean testFace(CardFace face);

		@Override
		default boolean test(CardInstance ci) {
			Stream<CardFace> faces = Arrays.stream(CardFace.Kind.values())
					.map(ci.card()::face)
					.filter(Objects::nonNull);

			if (this.allFacesMustMatch()) {
				return faces.allMatch(this::testFace);
			} else {
				return faces.anyMatch(this::testFace);
			}
		}
	}

	private static final Map<String, Service.Loader<SubfilterFactory>.Stub> SUBFILTER_FACTORIES = subfilterFactories();

	private static Map<String, Service.Loader<SubfilterFactory>.Stub> subfilterFactories() {
		Map<String, Service.Loader<SubfilterFactory>.Stub> map = new HashMap<>();

		for (Service.Loader<SubfilterFactory>.Stub stub : Service.Loader.load(SubfilterFactory.class)) {
			if (stub.has("shorthand")) {
				map.putIfAbsent(stub.string("shorthand"), stub);
			}
			map.put(stub.string("key"), stub);
		}

		return Collections.unmodifiableMap(map);
	}

	private static final Pattern PATTERN = Pattern.compile("(?<key>[A-Za-z]+)(?<op>[><][=]?|[!]?=)(?<value>\"[^\"]*\"|[^\\s]*)");

	public static Predicate<CardInstance> parse(String expression) {
		Matcher m = PATTERN.matcher(expression);

		Predicate<CardInstance> predicate = c -> true;

		while (m.find()) {
			boolean negate = !m.group("neg").isEmpty();
			String key = m.group("key");
			Operator op = Operator.forString(m.group("op"));

			String value = m.group("value");
			if (value.startsWith("\"") && value.endsWith("\"")) {
				value = value.substring(1, value.length() - 1);
			}

			Service.Loader<SubfilterFactory>.Stub stub = SUBFILTER_FACTORIES.get(key);

			if (stub == null) {
				// TODO: Report this somehow? Or just fail hard?
				continue;
			}

			predicate = predicate.and(stub.uncheckedInstance(negate, op, value));
		}

		return predicate;
	}
}
