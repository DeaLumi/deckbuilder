package emi.mtg.deckbuilder.view.search.omnifilter;

import emi.lib.mtg.Mana;
import emi.mtg.deckbuilder.model.CardInstance;

import java.io.IOException;
import java.util.*;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.regex.Pattern;

/*
 * filter: options EOF;
 * options: clauses ('or' clauses)* ;
 * clauses: clause ('and'? clause)* ;
 * clause: NEGATE? COUNT? value (operator COUNT? value)?
 * 		 / NEGATE? '(' options ')' ;
 * value: IDENTIFIER / MANA-LITERAL / STRING-LITERAL / REGEX-LITERAL / NUMBER-LITERAL ;
 * operator: LESS / LESS-OR-EQUAL / GREATER / GREATER-OR-EQUAL / EQUAL / NOT-EQUAL / DIRECT ;
 */
public class Parser {
	private interface Node {
		void unparse(Appendable to) throws IOException;

		Predicate<CardInstance> compile();
	}

	private static class Filter implements Node {
		public static Filter parse(Parser parser) {
			Options options = parser.expect(Options::parse, "Expected one or more filters");
			if (parser.symbol != null) throw new IllegalArgumentException("Expected end-of-filter, got " + parser.symbol.debug());
			return new Filter(options);
		}

		public final Options options;

		private Filter(Options options) {
			this.options = options;
		}

		@Override
		public void unparse(Appendable to) throws IOException {
			options.unparse(to);
		}

		@Override
		public Predicate<CardInstance> compile() {
			return options.compile();
		}
	}

	private static class Options implements Node {
		public static Options parse(Parser parser) {
			Clauses first = parser.expect(Clauses::parse, "Expected one or more filters");

			List<Clauses> options = new ArrayList<>();
			options.add(first);

			while (parser.accept(t -> t != null && t.type == Lexer.Token.Type.Identifier && "or".equals(t.source.substring(t.start, t.end))) != null) {
				Clauses next = parser.expect(Clauses::parse, "Expected one or more filters");
				options.add(next);
			}

			return new Options(options);
		}

		public final List<Clauses> options;

		private Options(List<Clauses> options) {
			this.options = Collections.unmodifiableList(options);
		}

		@Override
		public void unparse(Appendable to) throws IOException {
			for (int i = 0; i < options.size(); ++i) {
				if (i > 0) to.append(" or ");
				options.get(i).unparse(to);
			}
		}

		@Override
		public Predicate<CardInstance> compile() {
			Predicate<CardInstance> tmp = options.get(0).compile();
			for (int i = 1; i < options.size(); ++i) {
				tmp = tmp.or(options.get(i).compile());
			}
			return tmp;
		}
	}

	private static class Clauses implements Node {
		public static Clauses parse(Parser parser) {
			Clause first = parser.expect(Clause::parse, "Expected filter");

			List<Clause> clauses = new ArrayList<>();
			clauses.add(first);

			while (true) {
				parser.accept(t -> t != null && t.type == Lexer.Token.Type.Identifier && "and".equals(t.source.substring(t.start, t.end)));
				Clause next = Clause.parse(parser);
				if (next == null) break;
				clauses.add(next);
			}

			return new Clauses(clauses);
		}

		public final List<Clause> clauses;

		private Clauses(List<Clause> clauses) {
			this.clauses = Collections.unmodifiableList(clauses);
		}

		@Override
		public void unparse(Appendable to) throws IOException {
			for (int i = 0; i < clauses.size(); ++i) {
				if (i > 0) to.append(' ');
				clauses.get(i).unparse(to);
			}
		}

		@Override
		public Predicate<CardInstance> compile() {
			Predicate<CardInstance> tmp = clauses.get(0).compile();
			for (int i = 1; i < clauses.size(); ++i) {
				tmp = tmp.and(clauses.get(i).compile());
			}
			return tmp;
		}
	}

	private static class Clause implements Node {
		public static Clause parse(Parser parser) {
			boolean negate = parser.accept(Lexer.Token.Type.Negate) != null;

			Value lhs = Value.parse(parser);
			if (lhs != null) {
				Operator operator = Operator.parse(parser);
				if (operator != null) {
					Value rhs = parser.expect(Value::parse, "Expected value");
					return new Clause(negate, lhs, operator, rhs);
				} else {
					return new Clause(negate, lhs);
				}
			} else if (parser.accept(Lexer.Token.Type.OpenParen) != null) {
				Options options = parser.expect(Options::parse, "Expected filters");
				parser.expect((Predicate<Lexer.Token>) t -> t.type == Lexer.Token.Type.CloseParen, "Expected close-paren");
				return new Clause(negate, options);
			}

			return null;
		}

		public final boolean negate;
		public final Value lhs;
		public final Operator operator;
		public final Value rhs;
		public final Options parenthetical;

		private Clause(boolean negate, Value value) {
			this.negate = negate;
			this.lhs = value;
			this.operator = null;
			this.rhs = null;
			this.parenthetical = null;
		}

		private Clause(boolean negate, Value lhs, Operator operator, Value rhs) {
			this.negate = negate;
			this.lhs = lhs;
			this.operator = operator;
			this.rhs = rhs;
			this.parenthetical = null;
		}

		private Clause(boolean negate, Options parenthetical) {
			this.negate = negate;
			this.lhs = null;
			this.operator = null;
			this.rhs = null;
			this.parenthetical = parenthetical;
		}

		@Override
		public void unparse(Appendable to) throws IOException {
			if (negate) to.append('-');

			if (lhs != null && parenthetical == null) {
				lhs.unparse(to);
				if (operator != null && rhs != null) {
					operator.unparse(to);
					rhs.unparse(to);
				} else if (operator != null || rhs != null) {
					throw new AssertionError();
				}
			} else if (lhs == null && parenthetical != null) {
				to.append('(');
				parenthetical.unparse(to);
				to.append(')');
			} else {
				throw new AssertionError();
			}
		}

		@Override
		public Predicate<CardInstance> compile() {
			Predicate<CardInstance> tmp;

			if (lhs != null && parenthetical == null) {
				if (operator == null && rhs == null) {
					final String literal = lhs.stringContents;
					switch (lhs.token.type) {
						case Identifier:
						case LiteralString:
							tmp = ci -> ci.card().fullName().contains(literal);
							break;
						case LiteralRegex:
							final Pattern pattern = Pattern.compile(literal);
							tmp = ci -> pattern.matcher(ci.card().rules()).find();
							break;
						case LiteralMana:
							final Mana.Value mana = Mana.Value.parse(literal);
							tmp = Omnifilter.Operator.faceComparison(Omnifilter.Operator.EQUALS, f -> Mana.Value.SEARCH_COMPARATOR.compare(f.manaCost(), mana).value());
							break;
						case LiteralNumber:
							final double val = Double.parseDouble(literal);
							tmp = ci -> ci.card().manaCost().value() == val;
							break;
						default:
							throw new AssertionError();
					}
				} else if (operator != null && rhs != null) {
					if (lhs.token.type != Lexer.Token.Type.Identifier) throw new IllegalArgumentException("Expected filter name near " + lhs.token.debug() + ", got " + lhs.token.type.name());
					Omnifilter.Subfilter filter = Omnifilter.SUBFILTER_FACTORIES.get(lhs.stringContents);
					if (filter == null) throw new IllegalArgumentException("Unrecognized filter " + lhs.stringContents);
					tmp = filter.create(operator.equivalent, rhs.stringContents);
				} else {
					throw new AssertionError();
				}
			} else if (lhs == null && parenthetical != null) {
				tmp = parenthetical.compile();
			} else {
				throw new AssertionError();
			}

			if (negate) tmp = tmp.negate();
			return tmp;
		}
	}

	private static class Value implements Node {
		public static Value parse(Parser parser) {
			Lexer.Token token = parser.accept(t -> {
				if (t == null) return false;
				switch (t.type) {
					case Identifier:
						return !"and".equals(t.source.substring(t.start, t.end)) && !"or".equals(t.source.substring(t.start, t.end));
					case LiteralString:
					case LiteralNumber:
					case LiteralRegex:
					case LiteralMana:
						return true;
					default:
						return false;
				}
			});
			if (token == null) return null;
			return new Value(token);
		}

		public final Lexer.Token token;
		public final String stringContents;

		private Value(Lexer.Token token) {
			this.token = token;

			switch (token.type) {
				case Identifier:
				case LiteralNumber:
				case LiteralMana:
					this.stringContents = token.source.substring(token.start, token.end);
					break;
				case LiteralString:
				case LiteralRegex:
					this.stringContents = token.source.substring(token.start + 1, token.end - 1);
					break;
				default:
					throw new IllegalArgumentException("token type " + token.type.name());
			}
		}

		@Override
		public void unparse(Appendable to) throws IOException {
			to.append(token.source.substring(token.start, token.end));
		}

		@Override
		public Predicate<CardInstance> compile() {
			throw new UnsupportedOperationException();
		}
	}

	private static class Operator implements Node {
		public static Operator parse(Parser parser) {
			Lexer.Token token = parser.accept(Lexer.Token.Type.Direct, Lexer.Token.Type.Less, Lexer.Token.Type.LessOrEqual, Lexer.Token.Type.Equal, Lexer.Token.Type.NotEqual, Lexer.Token.Type.GreaterOrEqual, Lexer.Token.Type.Greater);
			if (token == null) return null;
			return new Operator(token);
		}

		public final Lexer.Token token;
		public final Omnifilter.Operator equivalent;

		private Operator(Lexer.Token token) {
			this.token = token;
			this.equivalent = Omnifilter.Operator.forString(token.toString());
		}

		@Override
		public void unparse(Appendable to) throws IOException {
			to.append(token.source.substring(token.start, token.end));
		}

		@Override
		public Predicate<CardInstance> compile() {
			throw new UnsupportedOperationException();
		}
	}

	private final Lexer.TokenIterator source;
	private Lexer.Token symbol;

	public Parser(Lexer.TokenIterable source) {
		this.source = source.iterator();
		this.symbol = this.source.next();
	}

	private Lexer.Token accept(Predicate<Lexer.Token> requirement) {
		if (requirement.test(symbol)) {
			Lexer.Token tmp = symbol;
			symbol = source.hasNext() ? source.next() : null;
			return tmp;
		}

		return null;
	}

	private Lexer.Token accept(Lexer.Token.Type... types) {
		return accept(t -> t != null && Arrays.binarySearch(types, t.type) >= 0);
	}

	private Lexer.Token expect(Predicate<Lexer.Token> requirement, String error) {
		Lexer.Token tmp = accept(requirement);
		if (tmp == null) throw new IllegalArgumentException(error + " near " + (symbol == null ? "???" : symbol.debug()));
		return tmp;
	}

	private <T> T expect(Function<Parser, T> parser, String error) {
		T tmp = parser.apply(this);
		if (tmp == null) throw new IllegalArgumentException(error + " near " + (symbol == null ? "???" : symbol.debug()));
		return tmp;
	}

	public static Predicate<CardInstance> parse(String expression) {
		Lexer.TokenIterable tokens = new Lexer.TokenIterable(expression);
		Parser parser = new Parser(tokens);
		Filter filter = Filter.parse(parser);
		return filter.compile();
	}

	public static void main(String[] args) throws IOException {
		String source = "(ci<=bugc (o:deathtouch or re:\"When ~ dies|Whenever ~ deals combat damage to a creature\") and cmc=3) or (ci<=rbc and (o:exile o:\"you may play\")) or (ci<=rugc o:\"mutate {\")";
		System.out.println(source);
		Lexer.TokenIterable tokens = new Lexer.TokenIterable(source);
		Parser parser = new Parser(tokens);
		Filter filter = Filter.parse(parser);
		filter.unparse(System.out);
		System.out.println();
	}
}
