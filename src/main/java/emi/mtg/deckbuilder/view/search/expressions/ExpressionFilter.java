package emi.mtg.deckbuilder.view.search.expressions;

import emi.mtg.deckbuilder.model.CardInstance;
import emi.mtg.deckbuilder.view.search.SearchProvider;

import java.util.function.Predicate;

public class ExpressionFilter implements SearchProvider {
	@Override
	public String name() {
		return "Expression Filter";
	}

	@Override
	public String toString() {
		return name();
	}

	@Override
	public String usage() {
		return String.join("\n",
				"An extremely powerful and flexible prototype search tool. ",
				"Expression Filter can work with the following types:",
				"<ul>",
				"<li>Numbers, e.g. power/toughness, can be compared and added/subtracted/multiplied/divided.</li>",
				"<li>Strings, e.g. card names/rules text, can be compared and concatenated (combined)/counted (returning the length of the string).</li>",
				"<li>Color sets, e.g. color/identity, can be compared and combined/reduced/counted (returning the number of colors).</li>",
				"<li>Mana costs can be compared and combined/counted (returning the mana value).</li>",
				"</ul>",
				"The following operators are supported between two expressions:",
				"<ul>",
				"<li><code>+</code> &mdash; Sums numbers, concatenates strings, combines sets of colors or mana costs.</li>",
				"<li><code>-</code> &mdash; Subtracts numbers or sets of colors.</li>",
				"<li><code>*</code> &mdash; Multiplies numbers.</li>",
				"<li><code>/</code> &mdash; Divides numbers.</li>",
				"</ul>",
				"Some prefix operators are supported:",
				"<ul>",
				"<li><code>!X</code> &mdash; Means &quot;not X&quot;. Can only be applied to boolean expressions, like <code>!(X and Y)</code>.</li>",
				"<li><code>#X</code> &mdash; Means &quot;the count of X&quot;. For strings, this is their length. For color sets, this is the number of colors. For mana costs, this is the mana value.</li>",
				"</ul>",
				"<br/>",
				"The following comparisons are understood:",
				"<ul>",
				"<li><code>=</code> or <code>==</code> &mdash; The left and right sides must be equal.</li>",
				"<li><code>!=</code> &mdash; The left and right sides must <i>not</i> be equal.</li>",
				"<li><code>&gt;</code> &mdash; The left side must be greater (or fully contain) the right side.</li>",
				"<li><code>&lt;</code> &mdash; The left side must be less than (or fully contained within) the right side.</li>",
				"<li><code>&lt;=</code> &mdash; Matches <code>&lt;</code> or <code>=</code></li>",
				"<li><code>&gt;=</code> &mdash; Matches <code>&gt;</code> or <code>=</code></li>",
				"<li><code>:</code> &mdash; When comparing numbers, means <code>=</code>. Otherwise, means <code>&gt;=</code>.</li>",
				"</ul>",
				"Either or both sides of a comparison can involve a card's information. Strings left uncompared are taken to be card names.<br/>",
				"<br/>",
				"Additionally, comparisons can be combined as follows:",
				"<ul>",
				"<li><code>X and Y</code> and/or <code>X && Y</code> (or simply <code>X Y </code>) &mdash; Matches only if X and Y are true.</li>",
				"<li><code>X or Y</code> and/or <code>X || Y</code> &mdash; Matches if either X or Y is true.</li>",
				"</ul>",
				"Expressions are combined with <code>and</code> before they're combined with <code>or</code>; use parentheses (e.g. <code>(X or Y) and Z</code>) to avoid this.<br/>",
				"<br/>",
				"There are a handful of base identifiers the expression filter recognizes:",
				"<ul>",
				"<li><code>inst</code> &mdash; The card instance. This represents the particular card as included in your deck and is the only source of tag information. Otherwise, it works like a card printing.</li>",
				"<li><code>pr</code> &mdash; The particular printing of a card. Can be used to check rarity or flavor text.</li>",
				"<li><code>card</code> &mdash; The card. This is the total source of information.</li>",
				"<li><code>o</code> (for &quot;oracle&quot;) or <code>rules</code> &mdash; The combined rules text of all of a card's faces.</li>",
				"<li><code>c</code> or <code>color</code> &mdash; The combined color of all of the card's faces.</li>",
				"<li><code>ci</code> or <code>identity</code> &mdash; The combined color identity of all of the card's faces.</li>",
				"</ul>",
				"Cards, printings, faces, and instances can't be used directly, but they can be accessed with e.g. <code>card.name</code>. At some point, maybe, a full listing will be here. ",
				"The point is, Expression Filter is incredibly flexible, powerful, and complicated. It'll probably break. Maybe a lot. Sorry. I keep trying to make it better...");
	}

	@Override
	public Predicate<CardInstance> parse(String query) throws IllegalArgumentException {
		Parser parser = new Parser(query);
		Grammar.Query parsedQuery = parser.parseRoot(Grammar.Query.class);
		if (parsedQuery == null) throw new IllegalArgumentException("An unspecified parser error occurred. Sorry... You should send Emi your search query so she can debug this.");

		Compiler.Value<Boolean> compiledQuery;
		try {
			compiledQuery = Compiler.compile(parsedQuery);
		} catch (IllegalArgumentException iae) {
			throw iae;
		} catch (Throwable t) {
			t.printStackTrace();
			throw new IllegalArgumentException(t);
		}

		return ci -> {
			try {
				return compiledQuery.get(ci);
			} catch (Throwable t) {
				t.printStackTrace();
				ci.flags.add(CardInstance.Flags.Warning);
				return true;
			}
		};
	}
}
