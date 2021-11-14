package emi.mtg.deckbuilder.view.search.expressions;

/*

#
# Quick Notes
#
# This is the complete grammar for the Expression Filter expression language. It doesn't strictly match any particular
# meta-grammar, but loosely follows Ford's PEG metagrammar, except the colon symbol (:) replaces the
# left-arrow (← or <-) like some kind of silly EBNF ripoff. As you can see, I'm using Python comment notation because
# this is embedded in a Java file, so I can't use Java comment notation. (I could use line comments, but that's
# difficult to parse (heh) against the background of the ordered-choice operator.)
#
# In short, if you're familiar with formal grammars in general and PEGs in particular, you should be able to understand
# this document without much difficulty. If you're not, do some learning on regular expressions -- PEGs function a lot
# like them, only instead of characters, it matches other regexes and composed regexes.
#
# As an aside, this grammar is NOT parsed entirely as a PEG. Specifically note the deviation around undifferentiated
# constants. There's an automatic ambiguity in MtG search syntax where e.g. "4" could mean the mana cost {4} or the
# number 4.0, and "BUG" could mean the set of Sultai colors [blue, green, black], or the mana cost {B}{U}{G}. Since the
# meaning of these substrings is contextual, the undiff-constant rule parses all of them and stores any that match for
# the compiler to use later.
#

#
# Nonterminals
#

query
	: expression EOF
	;

expression
	: or-expression
	;

or-expression
	: and-expression (whitespace 'or' whitespace / whitespace? '||' whitespace?) and-expression)*
	;

and-expression
	: prefixed-relation (whitespace ('and' whitespace)? / whitespace? '&&' whitespace?) prefixed-relation)*
	;

prefixed-relation
	: [-!]? relate-expression

relate-expression
	: add-expression (whitespace? relate-operator whitespace? add-expression)*
	;

add-expression
	: multiply-expression ([+-] multiply-expression)*
	;

# Blegh, my IDE keeps trying to turn these two characters into a start- or end-of-block-comment.
# I use '|' here since the order is irrelevant.
multiply-expression
	: prefix-expression (('/' | '*') prefix-expression)*
	;

# prefix-operator is a terminal since it appears as a first of a nonterminal
prefix-expression
	: prefix-operator* access-expression
	;

access-expression
	: value ('.' identifier)*
	;

value
	: constant
	/ identifier
	/ LPAREN value-expression RPAREN
	;

constant
	: string-constant
	/ regex
	/ number-constant
	/ color-constant
	/ mana-constant
	;

mana-constant
	: (LBRACE (mana-hybrid / mana-pure) RBRACE)+
	/ mana-pure+ ![A-Za-z]
	;

mana-hybrid
	: mana-pure ('/' mana-pure)+
	;

#
# Terminals
#

relate-operator
	: '!'? [:<>=] '='?
	;

relate-prefix-operator
	: [-!]
	;

prefix-operator
	: '#'
	;

string-constant
	: '"' ([^"]|[\]["])+ '"'
	;

regex
	: '/' ([^/]|[\][/])+ '/'
	;

number-constant
	: [-+]? [0-9]+ ![HWUBRGCSPXYZhwubrgcspXYZ0-9\u00bd\u221e] ([.] [0-9]+)? ([Ee] [0-9]+)?
	;

mana-pure
	: ([1-9][0-9]+ !'.' '\u00bd'?) / '0' !'.' / '\u00bd' / '\u221e' / ([Hh]?[WUBRGCwubrgc] / [SPsp]) ![AD-FI-QTVad-fi-qtv] / [XYZxyz] ![AD-FI-QTVad-fi-qtv]
	;

color-constant
	: '['? [WUBRGCwubrgc]+ ![A-Za-z] ']'?
	;

identifier
	: !(and / or) [A-Za-z]+
	;

whitespace
	: [ ]+
	;

LPAREN: '(';
RPAREN: ')';
LBRACE: '{';
RBRACE: '}';

 */

/**
 * Notes for morning:
 *
 * You still have to update the whitespacing around operators in or-expression/and-expression/relate-expression/etc.
 * Note that 'or' is currently being parsed as an identifier. I think I need to make it a reserved word.
 * Also, see if you can write a more clear identifier-exclusion clause for number-constant and mana-constant...
 * Having so many unquoted things sucks. :c
 */

import emi.lib.mtg.Mana;
import emi.lib.mtg.enums.Color;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.util.*;
import java.util.function.IntPredicate;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

public class Grammar {
	public static int match(CharSequence sequence, Parser.Entry[] table, int start) {
		final int len = sequence.length();
		int end = start;
		for (; end < table.length && end - start < len; ++end) {
			if (sequence.charAt(end) != table[end].ch) return end;
		}
		return end;
	}

	public static int charStar(IntPredicate condition, Parser.Entry[] table, int start) {
		int end = start;
		for (; end < table.length; ++end) {
			if (!condition.test(table[end].ch)) return end;
		}
		return end;
	}

	public static class Rule {
		public final String source, lexeme;
		public final int start, end;

		protected Rule(String source, int start, int end) {
			this.source = source;
			this.lexeme = source.substring(start, end);
			this.start = start;
			this.end = end;
		}

		@Override
		public String toString() {
			return String.format("%s(%s)", getClass().getSimpleName(), lexeme);
		}
	}

	/**
	 * Indicates which other rules (terminals or nonterminals) might appear first in a valid parsing of this rule.
	 * This is used to build the seed-parents map, and must be accurate!
	 */
	@Retention(RetentionPolicy.RUNTIME)
	public @interface First {
		Class<? extends Rule>[] value();
	}

	/**
	 * Terminals
	 */

	public static class LeftParenthesis extends Rule {
		public static LeftParenthesis parse(String source, Parser.Entry[] table, int start) {
			if (table[start].ch != '(') return null;

			return new LeftParenthesis(source, start, start + 1);
		}

		protected LeftParenthesis(String source, int start, int end) {
			super(source, start, end);
		}
	}

	public static class RightParenthesis extends Rule {
		public static RightParenthesis parse(String source, Parser.Entry[] table, int start) {
			if (table[start].ch != ')') return null;

			return new RightParenthesis(source, start, start + 1);
		}

		protected RightParenthesis(String source, int start, int end) {
			super(source, start, end);
		}
	}

	public static class LeftCurlyBrace extends Rule {
		public static LeftCurlyBrace parse(String source, Parser.Entry[] table, int start) {
			if (table[start].ch != '{') return null;

			return new LeftCurlyBrace(source, start, start + 1);
		}

		protected LeftCurlyBrace(String source, int start, int end) {
			super(source, start, end);
		}
	}

	public static class RightCurlyBrace extends Rule {
		public static RightCurlyBrace parse(String source, Parser.Entry[] table, int start) {
			if (table[start].ch != '}') return null;

			return new RightCurlyBrace(source, start, start + 1);
		}

		protected RightCurlyBrace(String source, int start, int end) {
			super(source, start, end);
		}
	}

	public static class Whitespace extends Rule {
		public static Whitespace parse(String source, Parser.Entry[] table, int start) {
			int end = charStar(Character::isWhitespace, table, start);
			if (end == start) return null;
			return new Whitespace(source, start, end);
		}

		protected Whitespace(String source, int start, int end) {
			super(source, start, end);
		}
	}

	public static class Identifier extends Rule {
		public static boolean isIdentChar(int ch) {
			return (ch >= 'A' && ch <= 'Z') || (ch >= 'a' && ch <= 'z') || ch == '-';
		}

		public static Identifier parse(String source, Parser.Entry[] table, int start) {
			int end = start;
			if (table[end].ch == 'a') {
				++end;

				if (end < table.length && table[end].ch == 'n') {
					++end;

					if (end < table.length && table[end].ch == 'd') {
						return null;
					}
				}
			}

			if (table[end].ch == 'o') {
				++end;

				if (end < table.length && table[end].ch == 'r') {
					return null;
				}
			}

			end = charStar(Identifier::isIdentChar, table, end);
			if (end == start) return null;

			return new Identifier(source, start, end);
		}

		protected Identifier(String source, int start, int end) {
			super(source, start, end);
		}
	}

	public static class ColorConstant extends Rule {
		public static boolean isColorChar(int ch) {
			return (ch == 'W' || ch == 'w' ||
					ch == 'U' || ch == 'u' ||
					ch == 'B' || ch == 'b' ||
					ch == 'R' || ch == 'r' ||
					ch == 'G' || ch == 'g' ||
					ch == 'C' || ch == 'c');
		}

		public static ColorConstant parse(String source, Parser.Entry[] table, int start) {
			// TODO: Allow square brackets around this.
			Color.Combination combo = Color.Combination.Empty;
			int end = start;
			for (; end < table.length; ++end) {
				if (!isColorChar(table[end].ch)) break;
				combo = combo.plus(Color.forChar(Character.toUpperCase((char) table[end].ch)));
			}

			if (end == start) return null;

			if (end < table.length && !isColorChar(table[end].ch) && Identifier.isIdentChar(table[end].ch)) return null;

			return new ColorConstant(source, start, end, combo);
		}

		public final Color.Combination value;

		protected ColorConstant(String source, int start, int end, Color.Combination value) {
			super(source, start, end);
			this.value = value;
		}
	}

	public static class ManaPure extends Rule {
		public static boolean isManaChar(int ch) {
			if (ColorConstant.isColorChar(ch)) return true;
			if (ch >= '0' && ch <= '9') return true;
			switch (ch) {
				case '\u00bd':
				case '\u221e':
				case 'H':
				case 'h':
				case 'S':
				case 's':
				case 'P':
				case 'p':
				case 'X':
				case 'x':
				case 'Y':
				case 'y':
				case 'Z':
				case 'z':
					return true;
			}

			return false;
		}

		public static ManaPure parse(String source, Parser.Entry[] table, int start) {
			// mana-pure : [1-9][0-9]* !'.' \u00bd? (Generic integer, possibly plus one half)
			if (table[start].ch >= '1' && table[start].ch <= '9') {
				int end = start + 1;
				while (end < table.length && table[end].ch >= '0' && table[end].ch <= '9') ++end;
				if (end < table.length && table[end].ch != '.') {
					if (table[end].ch == '\u00bd') ++end;

					return new ManaPure(source, start, end, Mana.Symbol.Generic.parse(source.substring(start, end)));
				}
			}

			// mana-pure : '0' !'.' (0)
			if (table[start].ch == '0') {
				int end = start + 1;
				if (end < table.length && table[end].ch != '.') {
					return new ManaPure(source, start, start + 1, Mana.Symbol.Generic.ZERO);
				}
			}

			// mana-pure : '\u00bd' (1/2)
			if (table[start].ch == '\u00bd') {
				return new ManaPure(source, start, start + 1, Mana.Symbol.Generic.HALF);
			}

			// mana-pure : '\u221e' (Infinity)
			if (table[start].ch == '\u221e') {
				return new ManaPure(source, start, start + 1, Mana.Symbol.Generic.INFINITY);
			}

			// half- and full color symbols
			boolean half = table[start].ch == 'H' || table[start].ch == 'h';
			if (half && start + 1 > table.length) return null;

			if (half || ColorConstant.isColorChar(table[start].ch) || table[start].ch == 'S' || table[start].ch == 's' || table[start].ch == 'P' || table[start].ch == 'p') {
				if (half && !ColorConstant.isColorChar(table[start + 1].ch)) return null; // Gets rid of the HS/HP false symbols.
//				int next = table[start + (half ? 2 : 1)].ch;
//				if (!isManaChar(next) && Identifier.isIdentChar(next)) return null;

				Mana.Symbol.Atom atom = Mana.Symbol.Atom.forString(source.substring(start, start + (half ? 2 : 1)).toUpperCase());
				return new ManaPure(source, start, start + (half ? 2 : 1), atom);
			}

			if ((table[start].ch >= 'X' && table[start].ch <= 'Z') || (table[start].ch >= 'x' && table[start].ch <= 'z')) {
//				int next = table[start + 1].ch;
//				if (!isManaChar(next) && Identifier.isIdentChar(next)) return null;

				Mana.Symbol.Variable variable = Mana.Symbol.Variable.valueOf(String.valueOf(Character.toUpperCase((char) table[start].ch)));
				return new ManaPure(source, start, start + 1, variable);
			}

			return null;
		}

		public final Mana.Symbol.Pure value;

		protected ManaPure(String source, int start, int end, Mana.Symbol.Pure value) {
			super(source, start, end);
			this.value = value;
		}
	}

	public static class NumberConstant extends Rule {
		public static NumberConstant parse(String source, Parser.Entry[] table, int start) {
			int end = start;

			boolean negate = table[end].ch == '-';
			if (negate || table[end].ch == '+') ++end;

			if (end >= table.length || table[end].ch < '0' || table[end].ch > '9') return null;

			long base = 0;
			do {
				base *= 10;
				base += table[end].ch - '0';
				++end;
			} while (end < table.length && table[end].ch >= '0' && table[end].ch <= '9');

			double value = base;

			if (end < table.length && ManaPure.isManaChar(table[end].ch)) return null;

			if (end + 1 < table.length && table[end].ch == '.') {
				++end;
				if (table[end].ch < '0' || table[end].ch > '9') return null;

				long decimal = 0;
				int decimalLen = 0;
				do {
					decimal *= 10;
					decimal += table[end].ch - '0';
					decimalLen += 1;
					++end;
				} while (end < table.length && table[end].ch >= '0' && table[end].ch <= '9');

				value += decimal / Math.pow(10, decimalLen);
			}

			if (end + 1 < table.length && (table[end].ch == 'E' || table[end].ch == 'e')) {
				++end;
				if (table[end].ch < '0' || table[end].ch > '9') return null;

				int exponent = 0;
				do {
					exponent *= 10;
					exponent += table[end].ch - '0';
					++end;
				} while (end < table.length && table[end].ch >= '0' && table[end].ch <= '9');

				for (int i = 0; i < exponent; ++i) value *= 10;
			}

			return new NumberConstant(source, start, end, value);
		}

		public final double value;

		protected NumberConstant(String source, int start, int end, double value) {
			super(source, start, end);
			this.value = value;
		}
	}

	public static class Regex extends Rule {
		public static Regex parse(String source, Parser.Entry[] table, int start) {
			if (table[start].ch != '/') return null;

			int end = start + 1;
			boolean escape = false;
			for (; end < table.length; ++end) {
				if (table[end].ch == '/' && !escape) break;
				escape = table[end].ch == '\\';
			}

			if (end >= table.length) return null;
			++end;

			return new Regex(source, start, end, Pattern.compile(source.substring(start + 1, end - 1)));
		}

		public final Pattern value;

		protected Regex(String source, int start, int end, Pattern value) {
			super(source, start, end);
			this.value = value;
		}
	}

	public static class StringConstant extends Rule {
		public static StringConstant parse(String source, Parser.Entry[] table, int start) {
			if (table[start].ch != '\"') return null;

			int end = start + 1;
			boolean escape = false;
			for (; end < table.length; ++end) {
				if (table[end].ch == '\"' && !escape) break;
				escape = table[end].ch == '\\';
			}

			if (end >= table.length) return null;
			++end;

			return new StringConstant(source, start, end, source.substring(start + 1, end - 1));
		}

		public final String value;

		protected StringConstant(String source, int start, int end, String value) {
			super(source, start, end);
			this.value = value;
		}
	}

	public static class RelateOperator extends Rule {
		public enum Base {
			Direct (':'),
			Equal ('='),
			Greater ('>'),
			Less ('<');

			public static String SYMBOLS = Arrays.stream(Base.values())
					.map(o -> o.symbol)
					.map(String::valueOf)
					.collect(Collectors.joining());

			public final char symbol;

			Base(char symbol) {
				this.symbol = symbol;
			}
		}

		public static RelateOperator parse(String source, Parser.Entry[] table, int start) {
			int end = start;

			boolean not = table[end].ch == '!';
			if (not) {
				++end;
			}

			int ordinal = Base.SYMBOLS.indexOf(table[end].ch);
			if (ordinal < 0) return null;

			Base base = Base.values()[ordinal];
			++end;

			boolean orEquals = table[end].ch == '=';
			if (orEquals) {
				++end;
			}

			return new RelateOperator(source, start, end, not, base, orEquals);
		}

		public final boolean not;
		public final Base base;
		public final boolean orEqual;
		public final Operators.BinaryIntent intent;

		protected RelateOperator(String source, int start, int end, boolean not, Base base, boolean orEqual) {
			super(source, start, end);
			this.not = not;
			this.base = base;
			this.orEqual = orEqual;

			switch (base) {
				case Direct:
					this.intent = Operators.BinaryIntent.DirectCompare;
					break;
				case Greater:
					this.intent = orEqual ? Operators.BinaryIntent.GreaterThanOrEqualTo : Operators.BinaryIntent.GreaterThan;
					break;
				case Less:
					this.intent = orEqual ? Operators.BinaryIntent.LessThanOrEqualTo : Operators.BinaryIntent.LessThan;
					break;
				case Equal:
					this.intent = not ? Operators.BinaryIntent.NotEqualTo : Operators.BinaryIntent.EqualTo;
					break;
				default:
					throw new Error("Impossible relation operator: base is " + base + ", not is " + not + ", orEqual is " + orEqual);
			}
		}
	}

	public static class RelatePrefixOperator extends Rule {
		public enum Kind {
			LogicalNot ('!', Operators.UnaryIntent.LogicalNot),
			LogicalNot2 ('-', Operators.UnaryIntent.LogicalNot);

			public static final String SYMBOLS = Arrays.stream(Kind.values())
					.map(k -> k.symbol)
					.map(String::valueOf)
					.collect(Collectors.joining());

			public final char symbol;
			public final Operators.UnaryIntent intent;

			Kind(char symbol, Operators.UnaryIntent intent) {
				this.symbol = symbol;
				this.intent = intent;
			}
		}

		public static RelatePrefixOperator parse(String source, Parser.Entry[] table, int start) {
			int ordinal = Kind.SYMBOLS.indexOf(table[start].ch);
			if (ordinal < 0) return null;

			return new RelatePrefixOperator(source, start, start + 1, Kind.values()[ordinal]);
		}

		public final Kind kind;

		public RelatePrefixOperator(String source, int start, int end, Kind kind) {
			super(source, start, end);
			this.kind = kind;
		}
	}

	public static class PrefixOperator extends Rule {
		public enum Kind {
			LogicalNot ('!', Operators.UnaryIntent.LogicalNot),
			Count ('#', Operators.UnaryIntent.Count);

			public static final String SYMBOLS = Arrays.stream(Kind.values())
					.map(k -> k.symbol)
					.map(String::valueOf)
					.collect(Collectors.joining());

			public final char symbol;
			public final Operators.UnaryIntent intent;

			Kind(char symbol, Operators.UnaryIntent intent) {
				this.symbol = symbol;
				this.intent = intent;
			}
		}

		public static PrefixOperator parse(String source, Parser.Entry[] table, int start) {
			int ordinal = Kind.SYMBOLS.indexOf(table[start].ch);
			if (ordinal < 0) return null;

			return new PrefixOperator(source, start, start + 1, Kind.values()[ordinal]);
		}

		public final Kind kind;

		protected PrefixOperator(String source, int start, int end, Kind kind) {
			super(source, start, end);
			this.kind = kind;
		}
	}

	/**
	 * Nonterminals
	 */

	@First({ManaPure.class})
	public static class ManaHybrid extends Rule {
		public static ManaHybrid parse(String source, Parser.Entry[] table, int start) {
			ManaPure pure = table[start].node(ManaPure.class, source, table, start);

			if (pure == null) return null;
			if (pure.end < table.length && table[pure.end].ch != '/') return null;

			List<Mana.Symbol.Pure> options = new ArrayList<>();
			options.add(pure.value);

			while (table[pure.end].ch == '/') {
				if (pure.end + 1 >= table.length) break;
				ManaPure next = table[pure.end + 1].node(ManaPure.class, source, table, pure.end + 1);
				if (next == null) break;
				pure = next;
				options.add(pure.value);
			}

			Mana.Symbol.Hybrid value = Mana.Symbol.Hybrid.of(options);

			return new ManaHybrid(source, start, pure.end, value);
		}

		public final Mana.Symbol.Hybrid value;

		protected ManaHybrid(String source, int start, int end, Mana.Symbol.Hybrid value) {
			super(source, start, end);
			this.value = value;
		}
	}

	@First({LeftCurlyBrace.class, ManaHybrid.class})
	public static class ManaConstant extends Rule {
		public static ManaConstant parse(String source, Parser.Entry[] table, int start) {
			int end = start;

			LeftCurlyBrace braced = table[end].node(LeftCurlyBrace.class, source, table, start);

			if (braced != null) {
				end = braced.end;
			}

			List<Mana.Symbol> symbols = new ArrayList<>();
			do {
				Rule symRule = null;

				if (braced != null) {
					symRule = table[end].node(ManaHybrid.class, source, table, end);
				}

				if (symRule == null) {
					symRule = table[end].node(ManaPure.class, source, table, end);
				}

				if (symRule == null) {
					if (braced != null) {
						return null;
					} else {
						break;
					}
				}

				Mana.Symbol sym;

				if (symRule instanceof ManaHybrid) {
					sym = ((ManaHybrid) symRule).value;
				} else {
					sym = ((ManaPure) symRule).value;
				}

				symbols.add(sym);
				end = symRule.end;

				if (braced != null) {
					RightCurlyBrace r = table[end].node(RightCurlyBrace.class, source, table, end);
					if (r == null) return null;
					end = r.end;

					LeftCurlyBrace l = table[end].node(LeftCurlyBrace.class, source, table, end);
					if (l == null) break;
					end = l.end;
				}
			} while(end < table.length);

			if (braced == null && Identifier.isIdentChar(table[end].ch)) return null;

			if (symbols.isEmpty()) {
				return null;
			}

			return new ManaConstant(source, start, end, Mana.Value.of(symbols));
		}

		public final Mana.Value value;

		protected ManaConstant(String source, int start, int end, Mana.Value value) {
			super(source, start, end);
			this.value = value;
		}
	}

	@First({StringConstant.class, Regex.class, NumberConstant.class, ColorConstant.class, ManaConstant.class})
	public static class Constant extends Rule {
		public static Constant parse(String source, Parser.Entry[] table, int start) {
			StringConstant string = table[start].node(StringConstant.class, source, table, start);
			if (string != null) {
				return new Constant(source, start, string.end, string);
			}

			Regex regex = table[start].node(Regex.class, source, table, start);
			if (regex != null) {
				return new Constant(source, start, regex.end, regex);
			}

			NumberConstant number = table[start].node(NumberConstant.class, source, table, start);
			ColorConstant colors = table[start].node(ColorConstant.class, source, table, start);
			ManaConstant mana = table[start].node(ManaConstant.class, source, table, start);

			if (number != null && colors != null && mana != null) {
				throw new Error("Somehow we parsed numbers, colors, and mana all from the same input? " + mana + ", " + colors + ", " + number);
			}

			if (number != null && mana != null) {
				if (mana.end > number.end) {
					return new Constant(source, start, mana.end, mana);
				} else {
					return new Constant(source, start, number.end, number);
				}
			}

			if (colors != null && mana != null) {
				if (mana.end > colors.end) {
					return new Constant(source, start, mana.end, mana);
				} else {
					return new Constant(source, start, colors.end, colors);
				}
			}

			if (number != null && colors != null) {
				throw new Error("Somehow we parsed both numbers and colors from the same input? " + number + ", " + colors);
			}

			if (mana != null) {
				return new Constant(source, start, mana.end, mana);
			}

			if (number != null) {
				return new Constant(source, start, number.end, number);
			}

			if (colors != null) {
				return new Constant(source, start, colors.end, colors);
			}

			return null;
		}

		public final StringConstant string;
		public final Regex regex;
		public final NumberConstant number;
		public final ColorConstant colors;
		public final ManaConstant mana;

		private Constant(String source, int start, int end, StringConstant string, Regex regex, NumberConstant number, ColorConstant colors, ManaConstant mana) {
			super(source, start, end);
			this.string = string;
			this.regex = regex;
			this.number = number;
			this.colors = colors;
			this.mana = mana;
		}

		protected Constant(String source, int start, int end, StringConstant string) {
			this(source, start, end, string, null, null, null, null);
		}

		protected Constant(String source, int start, int end, Regex regex) {
			this(source, start, end, null, regex, null, null, null);
		}

		protected Constant(String source, int start, int end, NumberConstant number) {
			this(source, start, end, null, null, number, null, null);
		}

		protected Constant(String source, int start, int end, ColorConstant colors) {
			this(source, start, end, null, null, null, colors, null);
		}

		protected Constant(String source, int start, int end, ManaConstant mana) {
			this(source, start, end, null, null, null, null, mana);
		}
	}

	@First({Constant.class, Identifier.class, LeftParenthesis.class})
	public static class Value extends Rule {
		public static Value parse(String source, Parser.Entry[] table, int start) {
			Constant constant = table[start].node(Constant.class, source, table, start);
			if (constant != null) {
				return new Value(source, start, constant.end, constant);
			}

			Identifier identifier = table[start].node(Identifier.class, source, table, start);
			if (identifier != null) {
				return new Value(source, start, identifier.end, identifier);
			}

			LeftParenthesis left = table[start].node(LeftParenthesis.class, source, table, start);
			if (left != null) {
				Expression expression = table[left.end].node(Expression.class, source, table, left.end);
				if (expression == null) return null;
				RightParenthesis right = table[expression.end].node(RightParenthesis.class, source, table, expression.end);
				if (right == null) return null;
				return new Value(source, start, right.end, expression);
			}

			return null;
		}

		public final Constant constant;
		public final Identifier identifier;
		public final Expression expression;

		public Value(String source, int start, int end, Constant constant) {
			super(source, start, end);
			this.constant = constant;
			this.identifier = null;
			this.expression = null;
		}

		public Value(String source, int start, int end, Identifier identifier) {
			super(source, start, end);
			this.constant = null;
			this.identifier = identifier;
			this.expression = null;
		}

		public Value(String source, int start, int end, Expression expression) {
			super(source, start, end);
			this.constant = null;
			this.identifier = null;
			this.expression = expression;
		}
	}

	@First({Value.class})
	public static class AccessExpression extends Rule {
		public static AccessExpression parse(String source, Parser.Entry[] table, int start) {
			Value value = table[start].node(Value.class, source, table, start);
			if (value == null) return null;
			int end = value.end;

			List<Identifier> members = new ArrayList<>();
			while (end < table.length && table[end].ch == '.') {
				++end;
				Identifier ident = table[end].node(Identifier.class, source, table, end);
				if (ident == null) return null;
				members.add(ident);
				end = ident.end;
			}

			return new AccessExpression(source, start, end, value, members.isEmpty() ? Collections.emptyList() : members);
		}

		public final Value base;
		public final List<Identifier> members;

		protected AccessExpression(String source, int start, int end, Value base, List<Identifier> members) {
			super(source, start, end);
			this.base = base;
			this.members = Collections.unmodifiableList(members);
		}
	}

	@First({PrefixOperator.class, AccessExpression.class})
	public static class PrefixExpression extends Rule {
		public static PrefixExpression parse(String source, Parser.Entry[] table, int start) {
			int end = start;
			List<PrefixOperator> operators = new ArrayList<>();

			PrefixOperator prefix = table[end].node(PrefixOperator.class, source, table, end);
			while (prefix != null) {
				end = prefix.end;
				operators.add(0, prefix);
				prefix = table[end].node(PrefixOperator.class, source, table, end);
			}

			AccessExpression base = table[end].node(AccessExpression.class, source, table, end);
			if (base == null) return null;
			end = base.end;

			return new PrefixExpression(source, start, end, operators.isEmpty() ? Collections.emptyList() : operators, base);
		}

		public final List<PrefixOperator> operators;
		public final AccessExpression base;

		protected PrefixExpression(String source, int start, int end, List<PrefixOperator> operators, AccessExpression base) {
			super(source, start, end);
			this.operators = Collections.unmodifiableList(operators);
			this.base = base;
		}
	}

	@First({PrefixExpression.class})
	public static class MultiplyExpression extends Rule {
		public enum Operator {
			Multiply ('*', Operators.BinaryIntent.Multiply),
			Divide ('/', Operators.BinaryIntent.Divide);

			public static String SYMBOLS = Arrays.stream(Operator.values())
					.map(o -> o.symbol)
					.map(String::valueOf)
					.collect(Collectors.joining());

			public final char symbol;
			public final Operators.BinaryIntent intent;

			Operator(char symbol, Operators.BinaryIntent intent) {
				this.symbol = symbol;
				this.intent = intent;
			}
		}

		public static MultiplyExpression parse(String source, Parser.Entry[] table, int start) {
			PrefixExpression term = table[start].node(PrefixExpression.class, source, table, start);
			if (term == null) return null;
			int end = term.end;

			List<PrefixExpression> terms = new ArrayList<>();
			terms.add(term);
			List<Operator> operators = new ArrayList<>();

			while (end + 1 < table.length && Operator.SYMBOLS.indexOf(table[end].ch) >= 0) {
				int ordinal = Operator.SYMBOLS.indexOf(table[end].ch);
				if (ordinal < 0) return null;
				Operator operator = Operator.values()[ordinal];
				++end;

				term = table[end].node(PrefixExpression.class, source, table, end);
				if (term == null) return null;
				end = term.end;

				terms.add(term);
				operators.add(operator);
			}

			return new MultiplyExpression(source, start, end, terms, operators.isEmpty() ? Collections.emptyList() : operators);
		}

		public final List<PrefixExpression> terms;
		public final List<Operator> operators;

		protected MultiplyExpression(String source, int start, int end, List<PrefixExpression> terms, List<Operator> operators) {
			super(source, start, end);
			this.terms = Collections.unmodifiableList(terms);
			this.operators = Collections.unmodifiableList(operators);
		}
	}

	@First({MultiplyExpression.class})
	public static class AddExpression extends Rule {
		public enum Operator {
			Add ('+', Operators.BinaryIntent.Add),
			Subtract ('-', Operators.BinaryIntent.Subtract);

			public static String SYMBOLS = Arrays.stream(Operator.values())
					.map(o -> o.symbol)
					.map(String::valueOf)
					.collect(Collectors.joining());

			public final char symbol;
			public final Operators.BinaryIntent intent;

			Operator(char symbol, Operators.BinaryIntent intent) {
				this.symbol = symbol;
				this.intent = intent;
			}
		}

		public static AddExpression parse(String source, Parser.Entry[] table, int start) {
			MultiplyExpression term = table[start].node(MultiplyExpression.class, source, table, start);
			if (term == null) return null;
			int end = term.end;

			List<MultiplyExpression> terms = new ArrayList<>();
			terms.add(term);
			List<Operator> operators = new ArrayList<>();

			while (end + 1 < table.length && Operator.SYMBOLS.indexOf(table[end].ch) >= 0) {
				int ordinal = Operator.SYMBOLS.indexOf(table[end].ch);
				if (ordinal < 0) return null;
				Operator operator = Operator.values()[ordinal];
				++end;

				term = table[end].node(MultiplyExpression.class, source, table, end);
				if (term == null) return null;
				end = term.end;

				terms.add(term);
				operators.add(operator);
			}

			return new AddExpression(source, start, end, terms, operators.isEmpty() ? Collections.emptyList() : operators);
		}

		public final List<MultiplyExpression> terms;
		public final List<Operator> operators;

		protected AddExpression(String source, int start, int end, List<MultiplyExpression> terms, List<Operator> operators) {
			super(source, start, end);
			this.terms = Collections.unmodifiableList(terms);
			this.operators = Collections.unmodifiableList(operators);
		}
	}

	@First({AddExpression.class})
	public static class RelateExpression extends Rule {
		public static RelateExpression parse(String source, Parser.Entry[] table, int start) {
			AddExpression term = table[start].node(AddExpression.class, source, table, start);
			if (term == null) return null;
			int end = term.end;

			List<AddExpression> terms = new ArrayList<>();
			terms.add(term);
			List<RelateOperator> operators = new ArrayList<>();

			while (end + 1 < table.length) {
				int tmpEnd = end;

				Whitespace ws = table[tmpEnd].node(Whitespace.class, source, table, tmpEnd);
				if (ws != null) tmpEnd = ws.end;

				RelateOperator operator = table[tmpEnd].node(RelateOperator.class, source, table, tmpEnd);
				if (operator == null) break;
				tmpEnd = operator.end;

				ws = table[tmpEnd].node(Whitespace.class, source, table, tmpEnd);
				if (ws != null) tmpEnd = ws.end;

				term = table[tmpEnd].node(AddExpression.class, source, table, tmpEnd);
				if (term == null) return null;
				tmpEnd = term.end;

				terms.add(term);
				operators.add(operator);
				end = tmpEnd;
			}

			return new RelateExpression(source, start, end, terms, operators.isEmpty() ? Collections.emptyList() : operators);
		}

		public final List<AddExpression> terms;
		public final List<RelateOperator> operators;

		protected RelateExpression(String source, int start, int end, List<AddExpression> terms, List<RelateOperator> operators) {
			super(source, start, end);
			this.terms = Collections.unmodifiableList(terms);
			this.operators = Collections.unmodifiableList(operators);
		}
	}

	@First({RelatePrefixOperator.class, RelateExpression.class})
	public static class PrefixedRelation extends Rule {
		public static PrefixedRelation parse(String source, Parser.Entry[] table, int start) {
			int end = start;
			List<RelatePrefixOperator> operators = new ArrayList<>();

			RelatePrefixOperator prefix = table[end].node(RelatePrefixOperator.class, source, table, end);
			while (prefix != null) {
				end = prefix.end;
				operators.add(0, prefix);
				prefix = table[end].node(RelatePrefixOperator.class, source, table, end);
			}

			RelateExpression base = table[end].node(RelateExpression.class, source, table, end);
			if (base == null) return null;
			end = base.end;

			return new PrefixedRelation(source, start, end, operators.isEmpty() ? Collections.emptyList() : operators, base);
		}

		public final List<RelatePrefixOperator> prefixes;
		public final RelateExpression relation;

		protected PrefixedRelation(String source, int start, int end, List<RelatePrefixOperator> prefixes, RelateExpression relation) {
			super(source, start, end);
			this.prefixes = Collections.unmodifiableList(prefixes);
			this.relation = relation;
		}
	}

	@First({PrefixedRelation.class})
	public static class AndExpression extends Rule {
		public static AndExpression parse(String source, Parser.Entry[] table, int start) {
			PrefixedRelation term = table[start].node(PrefixedRelation.class, source, table, start);
			if (term == null) return null;
			int end = term.end;

			List<PrefixedRelation> terms = new ArrayList<>();
			terms.add(term);

			Whitespace ws = table[end].node(Whitespace.class, source, table, end);
			while (ws != null) {
				end = ws.end;

				if (table[end].ch == '&') {
					++end;

					if (table[end].ch != '&') return null;
					++end;

					ws = table[end].node(Whitespace.class, source, table, end);
					if (ws == null) return null;
					end = ws.end;
				} else if (table[end].ch == 'a') {
					++end;

					if (table[end].ch != 'n') return null;
					++end;

					if (table[end].ch != 'd') return null;
					++end;

					ws = table[end].node(Whitespace.class, source, table, end);
					if (ws == null) return null;
					end = ws.end;
				}

				term = table[end].node(PrefixedRelation.class, source, table, end);
				if (term == null) break; // TODO Error recovery
				end = term.end;

				terms.add(term);

				ws = table[end].node(Whitespace.class, source, table, end);
			}

			return new AndExpression(source, start, terms.get(terms.size() - 1).end, terms);
		}

		public final List<PrefixedRelation> terms;

		protected AndExpression(String source, int start, int end, List<PrefixedRelation> terms) {
			super(source, start, end);
			this.terms = Collections.unmodifiableList(terms);
		}
	}

	/*
	or-expression
		: and-expression (whitespace 'or' whitespace / whitespace? '||' whitespace) and-expression)*
		;
	 */

	@First({AndExpression.class})
	public static class OrExpression extends Rule {
		public static OrExpression parse(String source, Parser.Entry[] table, int start) {
			AndExpression term = table[start].node(AndExpression.class, source, table, start);
			if (term == null) return null;
			int end = term.end;

			List<AndExpression> terms = new ArrayList<>();
			terms.add(term);

			Whitespace ws = table[end].node(Whitespace.class, source, table, end);
			while (ws != null) {
				end = ws.end;

				if (table[end].ch == 'o') {
					++end;

					if(table[end].ch != 'r') return null;
					++end;
				} else if (table[end].ch == '|') {
					++end;

					if (table[end].ch != '|') return null;
					++end;
				} else {
					break;
				}

				ws = table[end].node(Whitespace.class, source, table, end);
				if (ws == null) return null;
				end = ws.end;

				term = table[end].node(AndExpression.class, source, table, end);
				if (term == null) break;
				end = term.end;

				terms.add(term);

				ws = table[end].node(Whitespace.class, source, table, end);
			}

			return new OrExpression(source, start, terms.get(terms.size() - 1).end, terms);
		}

		public final List<AndExpression> terms;

		protected OrExpression(String source, int start, int end, List<AndExpression> terms) {
			super(source, start, end);
			this.terms = Collections.unmodifiableList(terms);
		}
	}

	@First({OrExpression.class})
	public static class Expression extends Rule {
		public static Expression parse(String source, Parser.Entry[] table, int start) {
			int end = start;

			OrExpression or = table[end].node(OrExpression.class, source, table, end);
			if (or == null) return null;
			end = or.end;

			return new Expression(source, start, end, or);
		}

		public final OrExpression or;

		protected Expression(String source, int start, int end, OrExpression or) {
			super(source, start, end);
			this.or = or;
		}
	}

	@First({Expression.class})
	public static class Query extends Rule {
		public static Query parse(String source, Parser.Entry[] table, int start) {
			int end = start;

			Expression expression = table[end].node(Expression.class, source, table, start);
			if (expression == null) return null;
			end = expression.end;

			if (table[end].ch != -1) return null;

			return new Query(source, start, end, expression);
		}

		public final Expression expression;

		protected Query(String source, int start, int end, Expression expression) {
			super(source, start, end);
			this.expression = expression;
		}
	}
}
