package emi.mtg.deckbuilder.view.search.omnifilter;

import java.util.Iterator;
import java.util.function.IntPredicate;

public interface Lexer {
	class Token {
		public enum Type {
			Identifier,

			// Operators/punctuation/symbols
			Direct,
			Less,
			LessOrEqual,
			Equal,
			NotEqual,
			GreaterOrEqual,
			Greater,

			OpenParen,
			CloseParen,
			Negate,
			Count,

			// Value literals
			LiteralString,
			LiteralRegex,
			LiteralNumber,
			LiteralMana,
			;
		}

		public final Type type;
		public final String source;
		public final int start, end;

		private Token(Type type, String source, int start, int end) {
			this.type = type;
			this.source = source;
			this.start = start;
			this.end = end;
		}

		@Override
		public String toString() {
			return source.substring(start, end);
		}

		public String debug() {
			return String.format("%s(\"%s\", %d..%d)", type.name(), this, start, end);
		}
	}

	class TokenIterable implements Iterable<Token> {
		public final String source;

		public TokenIterable(String source) {
			this.source = source;
		}

		@Override
		public TokenIterator iterator() {
			return new TokenIterator(source);
		}
	}

	class TokenIterator implements Iterator<Token> {
		public final String source;
		private int pos;

		private TokenIterator(String source) {
			this.source = source.trim();
			this.pos = 0;
		}

		@Override
		public boolean hasNext() {
			return pos < source.length();
		}

		@Override
		public Token next() {
			if (pos >= source.length()) throw new IllegalStateException();

			while (Character.isWhitespace(source.charAt(pos))) ++pos;

			final char c = source.charAt(pos);
			switch (source.charAt(pos)) {
				case ':':
					return new Token(Token.Type.Direct, source, pos, ++pos);
				case '<':
					if (source.charAt(pos + 1) == '=') {
						return new Token(Token.Type.LessOrEqual, source, pos, pos += 2);
					} else {
						return new Token(Token.Type.Less, source, pos, ++pos);
					}
				case '>':
					if (source.charAt(pos + 1) == '=') {
						return new Token(Token.Type.GreaterOrEqual, source, pos, pos += 2);
					} else {
						return new Token(Token.Type.Greater, source, pos, ++pos);
					}
				case '=':
					return new Token(Token.Type.Equal, source, pos, ++pos);
				case '(':
					return new Token(Token.Type.OpenParen, source, pos, ++pos);
				case ')':
					return new Token(Token.Type.CloseParen, source, pos, ++pos);
				case '!':
					if (source.charAt(pos + 1) == '=') {
						return new Token(Token.Type.NotEqual, source, pos, pos += 2);
					} else {
						return new Token(Token.Type.Negate, source, pos, ++pos);
					}
				case '-':
					return new Token(Token.Type.Negate, source, pos, ++pos); // TODO negative numbers
				case '#':
					return new Token(Token.Type.Count, source, pos, ++pos);
				case '"':
					return escapableDelimited('"', Token.Type.LiteralString);
				case '/':
					return escapableDelimited('/', Token.Type.LiteralRegex);
/*
						LiteralMana,
 */
			}

			if (c >= '0' && c <= '9') {
				return classSequence(Token.Type.LiteralNumber, i -> i >= '0' && i <= '9' || i == '.');
			} else if (c >= 'A' && c <= 'Z' || c >= 'a' && c <= 'z') {
				return classSequence(Token.Type.Identifier, i -> i >= 'A' && i <= 'Z' || i >= 'a' && i <= 'z');
			}

			throw new IllegalArgumentException("Unrecognized token near: " + source.substring(pos, Math.max(source.length(), pos + 12)));
		}

		private Token escapableDelimited(char terminator, Token.Type kind) {
			if (source.charAt(pos) != terminator) throw new AssertionError("escapableDelimited didn't start at terminator!");

			boolean escape = false;
			int start = pos;
			++pos;
			while (pos < source.length() && (escape || source.charAt(pos) != terminator)) {
				escape = source.charAt(pos) == '\\';
				++pos;
			}
			if (pos >= source.length()) throw new IllegalArgumentException("Unterminated " + kind.name() + ": " + source.substring(start));
			++pos;
			return new Token(kind, source, start, pos);
		}

		private Token classSequence(Token.Type type, IntPredicate characterClass) {
			if (!characterClass.test(source.charAt(pos))) throw new AssertionError("classSequence didn't start at character class member!");

			int start = pos;
			++pos;
			while (pos < source.length() && characterClass.test(source.charAt(pos))) ++pos;
			return new Token(type, source, start, pos);
		}

		public static void main(String[] args) {
			final TokenIterable lexer = new TokenIterable("ci<=bugc o:\"String\\\"Constant\" re:/Deathtouch|Whenever ~ deals( combat)? damage to a creature|When ~ dies/ (cmc<=3 or cmc>=5)");
			for (Token token : lexer) {
				System.out.println(token == null ? "(null)" : token.debug());
			}
		}
	}
}
