package emi.mtg.deckbuilder.view.search.expressions;

import emi.lib.mtg.Card;
import emi.lib.mtg.DataSource;
import emi.lib.mtg.Mana;
import emi.lib.mtg.enums.Color;
import emi.mtg.deckbuilder.model.CardInstance;
import emi.mtg.deckbuilder.model.Preferences;
import emi.mtg.deckbuilder.view.MainApplication;

import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Modifier;
import java.util.Collection;
import java.util.Collections;
import java.util.NoSuchElementException;
import java.util.Scanner;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

public class Compiler {
	public interface Value<T> {
		Class<T> type();
		T get(CardInstance ci);
		String typeRep();

		class Constant<T> implements Value<T> {
			public final Class<T> type;
			public final T value;

			public Constant(Class<T> cls, T val) {
				this.type = cls;
				this.value = val;
			}

			@Override
			public Class<T> type() {
				return type;
			}

			@Override
			public T get(CardInstance ci) {
				return value;
			}

			public T get() {
				return value;
			}

			@Override
			public String toString() {
				return typeRep() + (value != null ? " " + value : "");
			}

			@Override
			public String typeRep() {
				return "constant " + (value == null ? "null" : type.getSimpleName());
			}
		}

		static <T> Value<T> of(Class<T> cls, Function<CardInstance, T> calculator) {
			return new Value<T>() {
				@Override
				public Class<T> type() {
					return cls;
				}

				@Override
				public T get(CardInstance ci) {
					return calculator.apply(ci);
				}

				@Override
				public String toString() {
					return typeRep();
				}

				@Override
				public String typeRep() {
					return "dynamic " + cls.getSimpleName();
				}
			};
		}

		static <T> Value<T> constant(Class<T> cls, T constant) {
			return new Constant<T>(cls, constant);
		}

		static <T> Value<T> constant(T constant) {
			return constant((Class<T>) constant.getClass(), constant);
		}

		static <A, B, R> Value<R> applyBinaryOperator(Value<A> left, BiFunction<A, B, R> operator, Value<B> right, Class<R> finalType) {
			if (left instanceof Value.Constant) {
				final A l = ((Value.Constant<A>) left).get();

				if (right instanceof Constant) {
					final B r = ((Value.Constant<B>) right).get();

					return Value.constant(finalType, operator.apply(l, r));
				}

				return Value.of(finalType, x -> operator.apply(l, right.get(x)));
			}

			if (right instanceof Constant) {
				final B r = ((Value.Constant<B>) right).get();

				return Value.of(finalType, x -> operator.apply(left.get(x), r));
			}

			return Value.of(finalType, x -> operator.apply(left.get(x), right.get(x)));
		}

		static <A, B, X, Y, T> Value<?> applyBinaryOperator(Value<A> left, Value<B> right, Operators.BinaryOperatorLookupResult<A, B, X, Y, T> result) {
			Value<X> lval;
			if (result.leftCast != null) {
				lval = applyUnaryOperator(left, result.leftCast, result.outLeft);
			} else {
				lval = (Value<X>) left;
			}

			Value<Y> rval;
			if (result.rightCast != null) {
				rval = applyUnaryOperator(right, result.rightCast, result.outRight);
			} else {
				rval = (Value<Y>) right;
			}

			return applyBinaryOperator(lval, result.operator, rval, result.type);
		}

		static <T, R> Value<R> applyUnaryOperator(Value<T> value, Function<T, R> operator, Class<R> finalType) {
			if (value instanceof Value.Constant) {
				final T v = ((Value.Constant<T>) value).get();
				return Value.constant(finalType, operator.apply(v));
			}

			return Value.of(finalType, x -> operator.apply(value.get(x)));
		}
	}

	public static class NoOperatorException extends IllegalArgumentException {
		public final Value<?> value, right;
		public final Operators.UnaryIntent unaryOperator;
		public final Operators.BinaryIntent binaryOperator;
		public final Grammar.Rule node;

		public NoOperatorException(Value<?> left, Value<?> right, Operators.BinaryIntent operator, Grammar.Rule node) {
			super(String.format("No operator matches %s %s %s (at %s).", left.typeRep(), operator, right.typeRep(), node));
			this.value = left;
			this.unaryOperator = null;
			this.binaryOperator = operator;
			this.right = right;
			this.node = node;
		}

		public NoOperatorException(Value<?> object, Operators.UnaryIntent intent, Grammar.Rule node) {
			super(String.format("No operator matches %s %s (at %s).", intent, object.typeRep(), node));
			this.value = object;
			this.right = null;
			this.unaryOperator = intent;
			this.binaryOperator = null;
			this.node = node;
		}
	}

	public static Value<Boolean> compile(Grammar.Query query) {
		Value<?> val = compile(query.expression);

		if (val.type() == String.class && val instanceof Value.Constant) {
			String str = ((Value.Constant<String>) val).get();
			val = Value.of(Boolean.class, ci -> ci.card().name().contains(str));
		}

		if (val.type() != boolean.class) throw new IllegalArgumentException("Query does not evaluate to a boolean!");
		if (val instanceof Value.Constant) throw new IllegalArgumentException("Query is constant!");

		return (Value<Boolean>) val;
	}

	protected static Value<?> compile(Grammar.Expression expression) {
		return compile(expression.or);
	}

	protected static Value<?> compile(Grammar.OrExpression or) {
		Value base = compile(or.terms.get(0));
		if (or.terms.size() == 1) return base;

		Operators.BinaryOperatorLookupResult lookupResult = new Operators.BinaryOperatorLookupResult();

		for (int i = 1; i < or.terms.size(); ++i) {
			Value next = compile(or.terms.get(i));

			Operators.lookup(base.type(), next.type(), Operators.BinaryIntent.LogicalOr, lookupResult);
			if (lookupResult.operator == null) throw new NoOperatorException(base, next, Operators.BinaryIntent.LogicalOr, or);

			base = Value.applyBinaryOperator(base, next, lookupResult);
		}

		return base;
	}

	protected static Value<?> compile(Grammar.AndExpression and) {
		Value base = compile(and.terms.get(0));
		if (and.terms.size() == 1) return base;

		Operators.BinaryOperatorLookupResult lookupResult = new Operators.BinaryOperatorLookupResult();

		for (int i = 1; i < and.terms.size(); ++i) {
			Value next = compile(and.terms.get(i));

			if (next.type() == String.class) {
				final Value<String> name = next;
				next = Value.of(boolean.class, x -> x.card().name().contains(name.get(x)));
			}

			Operators.lookup(base.type(), next.type(), Operators.BinaryIntent.LogicalAnd, lookupResult);
			if (lookupResult.operator == null) throw new NoOperatorException(base, next, Operators.BinaryIntent.LogicalAnd, and);

			base = Value.applyBinaryOperator(base, next, lookupResult);
		}

		return base;
	}

	protected static Value<?> compile(Grammar.PrefixedRelation prefixedRelation) {
		Value<?> relation = compile(prefixedRelation.relation);

		for (int i = 0; i < prefixedRelation.prefixes.size(); ++i) {
			Operators.UnaryIntent intent = prefixedRelation.prefixes.get(i).kind.intent;
			Operators.UnaryOperatorRegistry<?, ?> operator = Operators.lookup(relation.type(), intent);
			if (operator == null) throw new NoOperatorException(relation, intent, prefixedRelation);
			relation = Value.applyUnaryOperator(relation, (Function) operator.operator, operator.returnType);
		}

		return relation;
	}

	protected static Value<?> compile(Grammar.RelateExpression relation) {
		if (relation.terms.size() == 1) return compile(relation.terms.get(0));

		Operators.BinaryOperatorLookupResult lookupResult = new Operators.BinaryOperatorLookupResult();

		Value last = compile(relation.terms.get(0));
		Value combination = null;
		for (int i = 1; i < relation.terms.size(); ++i) {
			Value next = compile(relation.terms.get(i));

			Operators.BinaryIntent intent = relation.operators.get(i - 1).intent;
			Operators.lookup(last.type(), next.type(), intent, lookupResult);
			if (lookupResult.operator == null) throw new NoOperatorException(last, next, intent, relation);

			Value related = Value.applyBinaryOperator(last, next, lookupResult);

			if (combination == null) {
				combination = related;
			} else {
				Operators.lookup(combination.type(), related.type(), Operators.BinaryIntent.LogicalAnd, lookupResult);
				if (lookupResult.operator == null) throw new NoOperatorException(combination, related, Operators.BinaryIntent.LogicalAnd, relation);
				combination = Value.applyBinaryOperator(combination, related, lookupResult);
			}
		}

		return combination;
	}

	protected static Value<?> compile(Grammar.AddExpression sum) {
		if (sum.terms.size() == 1) return compile(sum.terms.get(0));

		Operators.BinaryOperatorLookupResult lookupResult = new Operators.BinaryOperatorLookupResult();

		Value<?> value = compile(sum.terms.get(0));
		for (int i = 1; i < sum.terms.size(); ++i) {
			Value<?> next = compile(sum.terms.get(i));

			Operators.BinaryIntent intent = sum.operators.get(i - 1).intent;
			Operators.lookup(value.type(), next.type(), intent, lookupResult);
			if (lookupResult.operator == null) throw new NoOperatorException(value, next, intent, sum);

			value = Value.applyBinaryOperator(value, next, lookupResult);
		}

		return value;
	}

	protected static Value<?> compile(Grammar.MultiplyExpression product) {
		if (product.terms.size() == 1) return compile(product.terms.get(0));

		Operators.BinaryOperatorLookupResult lookupResult = new Operators.BinaryOperatorLookupResult();

		Value<?> value = compile(product.terms.get(0));
		for (int i = 1; i < product.terms.size(); ++i) {
			Value<?> next = compile(product.terms.get(i));

			Operators.BinaryIntent intent = product.operators.get(i - 1).intent;
			Operators.lookup(value.type(), next.type(), intent, lookupResult);
			if (lookupResult.operator == null) throw new NoOperatorException(value, next, intent, product);

			value = Value.applyBinaryOperator(value, next, lookupResult);
		}

		return value;
	}

	protected static Value<?> compile(Grammar.PrefixExpression prefix) {
		Value<?> base = compile(prefix.base);

		for (Grammar.PrefixOperator op : prefix.operators) {
			Operators.UnaryOperatorRegistry<?, ?> operator = Operators.lookup(base.type(), op.kind.intent);
			if (operator == null) throw new NoOperatorException(base, op.kind.intent, prefix);
			base = Value.applyUnaryOperator(base, (Function) operator.operator, operator.returnType);
		}

		return base;
	}

	protected static Class<?> cast(Class<?> type) {
		return type;
	}

	protected static Object sanitize(Object input, Class<?> realType, Class<?> intendedType) {
		if (input == null) {
			if (intendedType == boolean.class) return false;
			if (intendedType == double.class) return Double.NaN;
			if (intendedType == String.class) return "";
			if (intendedType == Collection.class || Collection.class.isAssignableFrom(intendedType)) return Collections.emptySet();
			return null;
		}

		return input;
	}

	protected static Value<?> compile(Grammar.AccessExpression access) {
		Value<?> base = compile(access.base);

		for (Grammar.Identifier member : access.members) {
			// TODO: This should not be purely reflective. I need to rewrite Types.java to work with the new paradigm.
			try {
				final Class<?> baseType = base.type();
				java.lang.reflect.Method method = baseType.getMethod(member.lexeme);
				if (!Modifier.isPublic(method.getModifiers())) throw new NoSuchElementException(); // Hit the catch block.
				final Class<?> returnType = cast(method.getReturnType());
				base = Value.applyUnaryOperator(base, (Function) x -> {
					if (x == null) return sanitize(null, method.getReturnType(), returnType);
					try {
						return sanitize(method.invoke(x), method.getReturnType(), returnType);
					} catch (IllegalAccessException | InvocationTargetException e) {
						throw new Error(String.format("Unable to access member %s of %s (type %s): %s", member.lexeme, x, baseType.getSimpleName(), e), e);
					}
				}, returnType);
			} catch (NoSuchMethodException e) {
				throw new IllegalArgumentException(String.format("Value %s (type %s) has no member %s", base, base.type().getSimpleName(), member));
			}
		}

		return base;
	}

	protected static Value<?> compile(Grammar.Value value) {
		if (value.constant != null) return compile(value.constant);
		if (value.identifier != null) return compile(value.identifier);
		if (value.expression != null) return compile(value.expression);

		throw new Error("Impossible value: " + value);
	}

	protected static Value<?> compile(Grammar.Constant constant) {
		if (constant.string != null) {
			if (constant.string.value.contains("CARDNAME") || constant.string.value.contains("~")) {
				// TODO: This should actually replace with each face's name as it's searching that face.
				// In other words, these values are contextual to things they're being compared against.
				// More and more I'm wondering if this complex expression parser is actually what we want to be doing. *sigh*
				return Value.of(String.class, x -> constant.string.value.replaceAll("(CARDNAME|~)", x.card().name()));
			} else {
				return Value.constant(String.class, constant.string.value);
			}
		}

		if (constant.regex != null) {
			if (constant.regex.value.pattern().contains("CARDNAME") || constant.regex.value.pattern().contains("~")) {
				// TODO: See the note above for string constants.
				// Also, comparisons can't actually handle "compare against regex" yet. See the code under compile(RelateExpression).
				// I need a more flexible type system that supports binary operations between any two types...
				return Value.of(Pattern.class, x -> Pattern.compile(constant.regex.value.pattern().replaceAll("(CARDNAME|~)", x.card().name())));
			} else {
				return Value.constant(Pattern.class, constant.regex.value);
			}
		}

		if (constant.number != null) {
			return Value.constant(double.class, constant.number.value);
		}

		if (constant.colors != null) {
			return Value.constant(Color.Combination.class, constant.colors.value);
		}

		if (constant.mana != null) {
			return Value.constant(Mana.Value.class, constant.mana.value);
		}

		throw new Error("Impossible constant: " + constant);
	}

	protected static Value<?> compile(Grammar.Identifier identifier) {
		switch (identifier.lexeme) {
			case "inst":
				return Value.of(CardInstance.class, ci -> ci);
			case "card":
				return Value.of(Card.class, ci -> ci.card());
			case "pr":
				return Value.of(Card.Printing.class, ci -> ci.printing());
			case "o":
			case "rules":
				return Value.of(String.class, ci -> ci.card().faces().stream().map(Card.Face::rules).collect(Collectors.joining("\n//\n")));
			case "c":
			case "color":
				return Value.of(Color.Combination.class, ci -> ci.card().faces().stream().map(Card.Face::color).collect(Color.Combination.COMBO_COLLECTOR));
			case "ci":
			case "identity":
				return Value.of(Color.Combination.class, ci -> ci.card().colorIdentity());
		}

		// TODO: Honestly, we should throw an exception when these are used as the part of a larger expression.
		return Value.constant(String.class, identifier.lexeme);
	}

	public static void main(String[] args) throws IOException {
		System.out.println("# Loading data...");
		Preferences.instantiate();
		DataSource data = MainApplication.DATA_SOURCES.stream().filter(ds -> ds.toString().equals("Scryfall")).findFirst().orElseThrow(() -> new Error("Couldn't load Scryfall data."));
		data.loadData(Preferences.get().dataPath, d -> {
			if (Math.floor(d * 100.0) % 10 == 0) System.out.printf("# %.0f%%\n", Math.floor(100.0 * d));
		});
		emi.lib.mtg.Set afc = data.set("afc");
		System.out.println("# Ready! Querying: " + afc.name());
		Scanner in = new Scanner(System.in);
		while (true) {
			Grammar.Query query;
			long start, end;

			System.out.print("> ");
			Parser parser = new Parser(in.nextLine());
			try {
				start = System.nanoTime();
				query = parser.parseRoot(Grammar.Query.class);
				end = System.nanoTime();
				System.out.printf("# Parse: %.1f msec\n", (end - start) / 1e6);

				if (query == null) throw new Exception("Parse error!");
			} catch (Exception e) {
				System.out.printf("! Parse error: %s\n", e);
				continue;
			}

			Value<Boolean> evaluator;
			try {
				start = System.nanoTime();
				evaluator = Compiler.compile(query);
				end = System.nanoTime();
				System.out.printf("# Compile: %.1f msec\n", (end - start) / 1e6);
			} catch (IllegalArgumentException | Error e) {
				System.out.printf("! Compile error: %s\n", e);
				continue;
			}

			start = System.nanoTime();
			afc.printings().stream()
					.map(CardInstance::new)
					.filter(ci -> {
						Object result = evaluator.get(ci);
						if (result instanceof Boolean) return (Boolean) result;
						System.out.printf("! %s (%s) %s - Evaluator did not produce boolean.\n", ci.card().name(), ci.set().code(), ci.collectorNumber());
						return false;
					})
					.forEach(ci -> System.out.printf("< %s (%s) %s\n", ci.card().name(), ci.set().code(), ci.collectorNumber()));
			end = System.nanoTime();
			System.out.printf("# Filter %d cards: %.1f msec\n", afc.printings().size(), (end - start) / 1e6);
		}
	}
}
