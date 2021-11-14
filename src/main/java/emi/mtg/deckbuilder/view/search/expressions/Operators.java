package emi.mtg.deckbuilder.view.search.expressions;

import emi.lib.mtg.Mana;
import emi.lib.mtg.enums.CardType;
import emi.lib.mtg.TypeLine;
import emi.lib.mtg.enums.Color;
import emi.lib.mtg.enums.Supertype;
import emi.lib.mtg.util.CollectionComparator;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.util.*;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.function.ToDoubleBiFunction;
import java.util.regex.Pattern;

public class Operators {
	enum UnaryIntent {
		Count ("#"),
		LogicalNot ("!");

		private final String string;

		UnaryIntent(String string) {
			this.string = string;
		}

		@Override
		public String toString() {
			return string;
		}
	}

	enum BinaryIntent {
		Add ("+"),
		Subtract ("-"),
		Multiply ("*"),
		Divide ("/"),

		LessThan ("<"),
		LessThanOrEqualTo ("<="),
		EqualTo ("="),
		DirectCompare (":"),
		NotEqualTo ("!="),
		GreaterThanOrEqualTo (">="),
		GreaterThan (">"),

		LogicalAnd ("&&"),
		LogicalOr ("||");

		private final String string;

		BinaryIntent(String string) {
			this.string = string;
		}

		@Override
		public String toString() {
			return string;
		}
	}

	public static class UnaryOperatorRegistry<A, T> {
		public final Class<A> objectType;
		public final UnaryIntent intent;
		public final Class<T> returnType;
		public final Function<A, T> operator;

		public UnaryOperatorRegistry(Class<A> objectType, UnaryIntent intent, Class<T> returnType, Function<A, T> operator) {
			this.objectType = objectType;
			this.intent = intent;
			this.returnType = returnType;
			this.operator = operator;
		}

		public int keyHash() {
			return keyHash(objectType, intent);
		}

		public static <A> int keyHash(Class<A> type, UnaryIntent intent) {
			int raw = (((type.getName().hashCode() * 31) + 0) * 29 + intent.name().hashCode()) * 27;
			return Integer.remainderUnsigned(raw, UNARY_REGISTRY.length);
		}
	}

	public static class BinaryOperatorRegistry<A, B, T> {
		public final Class<A> left;
		public final Class<B> right;
		public final BinaryIntent intent;
		public final Class<T> type;
		public final BiFunction<A, B, T> operator;

		public BinaryOperatorRegistry(Class<A> left, Class<B> right, BinaryIntent intent, Class<T> type, BiFunction<A, B, T> operator) {
			this.left = left;
			this.right = right;
			this.intent = intent;
			this.type = type;
			this.operator = operator;
		}

		@Override
		public String toString() {
			return String.format("<binary operator %s %s %s (hash %d)>", left.getSimpleName(), intent, right.getSimpleName(), keyHash());
		}

		public int keyHash() {
			return keyHash(left, right, intent);
		}

		public static <A, B> int keyHash(Class<A> left, Class<B> right, BinaryIntent intent) {
			int raw = (((left.getName().hashCode() * 31) + right.getName().hashCode()) * 29 + intent.name().hashCode()) * 27;
			return Integer.remainderUnsigned(raw, BINARY_REGISTRY.length);
		}
	}

	public static class BinaryOperatorLookupResult<A1, B1, A2, B2, T> {
		public Class<A1> inLeft;
		public Function<A1, A2> leftCast;
		public Class<A2> outLeft;

		public Class<B1> inRight;
		public Function<B1, B2> rightCast;
		public Class<B2> outRight;

		public BiFunction<A2, B2, T> operator;
		public Class<T> type;
	}

	private static final Map<Class<?>, Map<Class<?>, Function<?, ?>>> CAST_REGISTRY = new HashMap<>();
	private static final UnaryOperatorRegistry<?, ?>[] UNARY_REGISTRY = new UnaryOperatorRegistry[37];
	private static final BinaryOperatorRegistry<?, ?, ?>[] BINARY_REGISTRY = new BinaryOperatorRegistry[1009];

	private static <A, B> void registerCast(Class<A> from, Class<B> to, Function<A, B> fn) {
		Map<Class<?>, Function<?, ?>> subMap = CAST_REGISTRY.computeIfAbsent(from, k -> new HashMap<>());
		if (subMap.containsKey(to)) throw new AssertionError(String.format("Cast from %s to %s already exists!", from, to));
		subMap.put(to, fn);
	}

	private static <A, B> Function<A, B> lookupCast(Class<A> from, Class<B> to) {
		Map<Class<?>, Function<?, ?>> subMap = CAST_REGISTRY.get(from);
		if (subMap == null) return null;
		Function<?, ?> fn = subMap.get(to);
		return (Function<A, B>) fn;
	}

	private static <A, T> void register(Class<A> object, UnaryIntent intent, Class<T> type, Function<A, T> operator) {
		UnaryOperatorRegistry<A, T> registry = new UnaryOperatorRegistry<>(object, intent, type, operator);
		int hash = registry.keyHash();
		if (UNARY_REGISTRY[hash] != null) throw new AssertionError(String.format("Binary operator hash collision: %s collided with %s", registry, UNARY_REGISTRY[hash]));
		UNARY_REGISTRY[hash] = registry;
	}

	private static <A, B, T> void register(Class<A> left, Class<B> right, BinaryIntent intent, Class<T> returnType, BiFunction<A, B, T> operator) {
		BinaryOperatorRegistry<A, B, T> registry = new BinaryOperatorRegistry<>(left, right, intent, returnType, operator);
		int hash = registry.keyHash();
		if (BINARY_REGISTRY[hash] != null) throw new AssertionError(String.format("Binary operator hash collision: %s collided with %s", registry, BINARY_REGISTRY[hash]));
		BINARY_REGISTRY[hash] = registry;
	}

	private static <A, B> void registerCompare(Class<A> left, Class<B> right, ToDoubleBiFunction<A, B> compare) {
		register(left, right, BinaryIntent.LessThan, boolean.class, (a, b) -> compare.applyAsDouble(a, b) < 0);
		register(left, right, BinaryIntent.LessThanOrEqualTo, boolean.class, (a, b) -> compare.applyAsDouble(a, b) <= 0);
		register(left, right, BinaryIntent.EqualTo, boolean.class, (a, b) -> compare.applyAsDouble(a, b) == 0);
		register(left, right, BinaryIntent.NotEqualTo, boolean.class, (a, b) -> compare.applyAsDouble(a, b) != 0);
		register(left, right, BinaryIntent.GreaterThanOrEqualTo, boolean.class, (a, b) -> compare.applyAsDouble(a, b) >= 0);
		register(left, right, BinaryIntent.GreaterThan, boolean.class, (a, b) -> compare.applyAsDouble(a, b) > 0);
	}

	public static <A, T> UnaryOperatorRegistry<A, T> lookup(Class<A> object, UnaryIntent intent) {
		UnaryOperatorRegistry<?, ?> fn = UNARY_REGISTRY[UnaryOperatorRegistry.keyHash(object, intent)];
		if (fn == null) return null;
		assert fn.objectType == object;
		assert fn.intent == intent;
		return (UnaryOperatorRegistry<A, T>) fn;
	}

	public static <A, B, X, Y, T> void lookup(Class<A> left, Class<B> right, BinaryIntent intent, BinaryOperatorLookupResult<A, B, X, Y, T> result) {
		BinaryOperatorRegistry<?, ?, ?> fn = BINARY_REGISTRY[BinaryOperatorRegistry.keyHash(left, right, intent)];

		if (fn == null) {
			// This is where a bilevel map would be more efficient -- rather than guessing at workable types, we can
			// iterate... but Map<X, Map<Y, Map<Z, Function>>> is too gross for me.
			for (BinaryOperatorRegistry<?, ?, ?> reg : BINARY_REGISTRY) {
				if (reg == null) continue;
				if (reg.intent != intent) continue;

				if (left != reg.left) {
					Function<A, X> castL = (Function<A, X>) lookupCast(left, reg.left);
					if (castL == null) continue;
					result.inLeft = left;
					result.leftCast = castL;
					result.outLeft = (Class<X>) reg.left;
				} else {
					result.inLeft = left;
					result.leftCast = null;
					result.outLeft = (Class<X>) left;
				}

				if (right != reg.right) {
					Function<B, Y> castR = (Function<B, Y>) lookupCast(right, reg.right);
					if (castR == null) continue;
					result.inRight = right;
					result.rightCast = castR;
					result.outRight = (Class<Y>) reg.right;
				} else {
					result.inRight = right;
					result.rightCast = null;
					result.outRight = (Class<Y>) right;
				}

				result.operator = (BiFunction<X, Y, T>) reg.operator;
				result.type = (Class<T>) reg.type;

				System.err.printf("Warning: Implicit cast to invoke %s %s %s as %s %s %s\n", result.outLeft, intent, result.outRight, result.inLeft, intent, result.inRight);

				return;
			}
		}

		result.leftCast = null;
		result.outLeft = null;

		result.rightCast = null;
		result.outRight = null;

		if (fn == null) {
			result.inLeft = null;
			result.inRight = null;
			result.operator = null;
			result.type = null;
			return;
		}

		assert fn.left == left;
		assert fn.right == right;
		assert fn.intent == intent;

		result.inLeft = left;
		result.inRight = right;

		result.operator = (BiFunction<X, Y, T>) fn.operator;
		result.type = (Class<T>) fn.type;
	}

	static {
		register(boolean.class, boolean.class, BinaryIntent.LogicalOr, boolean.class, (a, b) -> a || b);
		register(boolean.class, boolean.class, BinaryIntent.LogicalAnd, boolean.class, (a, b) -> a && b);
		register(boolean.class, UnaryIntent.LogicalNot, boolean.class, a -> !a);

		registerCast(int.class, double.class, x -> x == null ? Double.NaN : (double) x);
		register(double.class, double.class, BinaryIntent.Add, double.class, Double::sum);
		register(double.class, double.class, BinaryIntent.Subtract, double.class, (a, b) -> a - b);
		register(double.class, double.class, BinaryIntent.Multiply, double.class, (a, b) -> a * b);
		register(double.class, double.class, BinaryIntent.Divide, double.class, (a, b) -> a / b);
		register(double.class, double.class, BinaryIntent.LessThan, boolean.class, (a, b) -> a < b);
		register(double.class, double.class, BinaryIntent.LessThanOrEqualTo, boolean.class, (a, b) -> a <= b);
		register(double.class, double.class, BinaryIntent.EqualTo, boolean.class, Double::equals);
		register(double.class, double.class, BinaryIntent.NotEqualTo, boolean.class, (a, b) -> a != b);
		register(double.class, double.class, BinaryIntent.GreaterThanOrEqualTo, boolean.class, (a, b) -> a >= b);
		register(double.class, double.class, BinaryIntent.GreaterThan, boolean.class, (a, b) -> a > b);

		register(String.class, String.class, BinaryIntent.Add, String.class, (a, b) -> a + b);
		register(String.class, String.class, BinaryIntent.LessThan, boolean.class, (a, b) -> b.contains(a) && !b.equals(a));
		register(String.class, String.class, BinaryIntent.LessThanOrEqualTo, boolean.class, (a, b) -> b.contains(a));
		register(String.class, String.class, BinaryIntent.EqualTo, boolean.class, String::equals);
		register(String.class, String.class, BinaryIntent.NotEqualTo, boolean.class, (a, b) -> !a.equals(b));
		register(String.class, String.class, BinaryIntent.GreaterThanOrEqualTo, boolean.class, String::contains);
		register(String.class, String.class, BinaryIntent.GreaterThan, boolean.class, (a, b) -> a.contains(b) && !a.equals(b));
		register(String.class, Pattern.class, BinaryIntent.DirectCompare, boolean.class, (a, b) -> b.matcher(a).find());
		register(String.class, UnaryIntent.Count, double.class, a -> (double) a.length());

		registerCompare(Color.Combination.class, Color.Combination.class, ColorFunctions::compare);
		register(Color.Combination.class, Color.Combination.class, BinaryIntent.Add, Color.Combination.class, ColorFunctions::plus);
		register(Color.Combination.class, Color.Combination.class, BinaryIntent.Subtract, Color.Combination.class, ColorFunctions::minus);
		register(Color.Combination.class, UnaryIntent.Count, double.class, ColorFunctions::count);

		registerCompare(Mana.Value.class, Mana.Value.class, ManaFunctions::compare);
		register(Mana.Value.class, Mana.Value.class, BinaryIntent.Add, Mana.Value.class, ManaFunctions::plus);
		register(Mana.Value.class, UnaryIntent.Count, double.class, ManaFunctions::count);

		registerCast(java.util.Set.class, java.util.Collection.class, x -> x);
		registerCompare(java.util.Collection.class, String.class, StringlikeCollectionFunctions::compare);
		register(java.util.Collection.class, String.class, BinaryIntent.DirectCompare, boolean.class, StringlikeCollectionFunctions::contains);
		register(java.util.Collection.class, UnaryIntent.Count, double.class, StringlikeCollectionFunctions::count);

		registerCast(String.class, TypeLine.class, TypeLineFunctions::parse);
		registerCompare(TypeLine.class, TypeLine.class, TypeLineFunctions::compare);
	}

	@Target({ElementType.TYPE})
	@Retention(RetentionPolicy.RUNTIME)
	private @interface OperatorContainer {
	}

	@Target({ElementType.METHOD})
	@Retention(RetentionPolicy.RUNTIME)
	private @interface UnaryOperator {
		UnaryIntent value();
	}

	@Target({ElementType.METHOD})
	@Retention(RetentionPolicy.RUNTIME)
	private @interface BinaryOperator {
		BinaryIntent value();
	}

	@Target({ElementType.METHOD})
	@Retention(RetentionPolicy.RUNTIME)
	private @interface Cast {
	}

	static class ColorFunctions {
		static Color.Combination plus(Color.Combination left, Color.Combination right) {
			return left.plus(right);
		}

		static Color.Combination minus(Color.Combination left, Color.Combination right) {
			return left.minus(right);
		}

		static double compare(Color.Combination left, Color.Combination right) {
			return Color.Combination.COMPARATOR.compare(left, right).value();
		}

		static double count(Color.Combination object) {
			return object.size();
		}
	}

	static class ManaFunctions {
		static Mana.Value plus(Mana.Value left, Mana.Value right) {
			return left.copy().add(right);
		}

		static double compare(Mana.Value left, Mana.Value right) {
			return Mana.Value.SEARCH_COMPARATOR.compare(left, right);
		}

		static double count(Mana.Value object) {
			return object.value();
		}
	}

	static class StringlikeCollectionFunctions {
		static boolean contains(java.util.Collection set, String element) {
			if (set.isEmpty()) return false;

			Object el = element;

			if (set.stream().findAny().get() instanceof Enum) {
				try {
					el = Enum.valueOf((Class<? extends Enum>) set.stream().findAny().get().getClass(), element);
				} catch (IllegalArgumentException iae) {
					return false;
				}
			}

			return set.contains(el);
		}

		static double compare(java.util.Collection set, String element) {
			return contains(set, element) ? (set.size() == 1 ? CollectionComparator.Result.Equal.value() : CollectionComparator.Result.Contains.value()) : CollectionComparator.Result.Disjoint.value();
		}

		static double count(java.util.Collection set) {
			return set.size();
		}
	}

	static class TypeLineFunctions {
		static TypeLine parse(String from) {
			Set<Supertype> supertypes = EnumSet.noneOf(Supertype.class);
			Set<CardType> cardTypes = EnumSet.noneOf(CardType.class);
			Set<String> subtypes = new HashSet<>();

			for (String elem : from.split(" ")) {
				if (elem.contains("-") || elem.contains("\u2014")) continue;

				String capped = elem.substring(0, 1).toUpperCase() + elem.substring(1).toLowerCase();

				try {
					supertypes.add(Supertype.valueOf(capped));
					continue;
				} catch (IllegalArgumentException iae) {
					// ignore
				}

				if (!"Elemental".equals(capped)) {
					try {
						cardTypes.add(CardType.valueOf(capped));
						continue;
					} catch (IllegalArgumentException iae) {
						// ignore
					}
				}

				subtypes.add(capped);
			}

			return new TypeLine.Basic(supertypes, cardTypes, subtypes);
		}

		static double compare(TypeLine left, TypeLine right) {
			if (left == null) return right == null ? CollectionComparator.Result.Equal.value() : CollectionComparator.Result.ContainedIn.value();
			if (right == null) return CollectionComparator.Result.Contains.value();

			CollectionComparator.Result supertypes = CollectionComparator.SET_COMPARATOR.compare(left.supertypes(), right.supertypes());
			CollectionComparator.Result cardTypes = CollectionComparator.SET_COMPARATOR.compare(left.cardTypes(), right.cardTypes());
			CollectionComparator.Result subtypes = CollectionComparator.SET_COMPARATOR.compare(left.subtypes(), right.subtypes());

			if (supertypes == CollectionComparator.Result.Intersects || cardTypes == CollectionComparator.Result.Intersects || subtypes == CollectionComparator.Result.Intersects) return CollectionComparator.Result.Intersects.value();

			double min = Math.min(supertypes.value(), Math.min(cardTypes.value(), subtypes.value()));
			double max = Math.max(supertypes.value(), Math.max(cardTypes.value(), subtypes.value()));

			if (min >= 0 && max > 0) return CollectionComparator.Result.Contains.value();
			if (min == 0 && max == 0) return CollectionComparator.Result.Equal.value();
			if (min < 0 && max <= 0) return CollectionComparator.Result.ContainedIn.value();

			return CollectionComparator.Result.Disjoint.value();
		}
	}
}
