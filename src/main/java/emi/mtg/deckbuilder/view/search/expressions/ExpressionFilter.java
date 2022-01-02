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
				"",
				"- Numbers, e.g. power/toughness, can be compared and added/subtracted/multiplied/divided.",
				"- Strings, e.g. card names/rules text, can be compared and concatenated (combined)/counted (returning the length of the string).",
				"- Color sets, e.g. color/identity, can be compared and combined/reduced/counted (returning the number of colors).",
				"- Mana costs can be compared and combined/counted (returning the mana value).",
				"",
				"The following operators are supported between two expressions:",
				"",
				"- `+` -- Sums numbers, concatenates strings, combines sets of colors or mana costs.",
				"- `-` -- Subtracts numbers or sets of colors.",
				"- `*` -- Multiplies numbers.",
				"- `/` -- Divides numbers.",
				"",
				"Some prefix operators are supported:",
				"",
				"- `!X` -- Means &quot;not X&quot;. Can only be applied to boolean expressions, like `!(X and Y)`.",
				"- `#X` -- Means &quot;the count of X&quot;. For strings, this is their length. For color sets, this is the number of colors. For mana costs, this is the mana value.",
				"",
				"The following comparisons are understood:",
				"",
				"- `=` or `==` -- The left and right sides must be equal.",
				"- `!=` -- The left and right sides must _not_ be equal.",
				"- `>` -- The left side must be greater (or fully contain) the right side.",
				"- `<` -- The left side must be less than (or fully contained within) the right side.",
				"- `<=` -- Matches `<` or `=`",
				"- `>=` -- Matches `>` or `=`",
				"- `:` -- When comparing numbers, means `=`. Otherwise, means `>=`.",
				"",
				"Either or both sides of a comparison can involve a card's information. Strings left uncompared are taken to be card names.",
				"",
				"Additionally, comparisons can be combined as follows:",
				"",
				"- `X and Y` and/or `X && Y` (or simply `X Y`) -- Matches only if X and Y are true.",
				"- `X or Y` and/or `X || Y` -- Matches if either X or Y is true.",
				"",
				"Expressions are combined with `and` before they're combined with `or`; use parentheses (e.g. `(X or Y) and Z`) to control this.",
				"",
				"There are a handful of base identifiers the expression filter recognizes:",
				"",
				"- `inst` -- The card instance. This represents the particular card as included in your deck and is the only source of tag information. Otherwise, it works like a card printing.",
				"- `pr` -- The particular printing of a card. Can be used to check rarity or flavor text.",
				"- `card` -- The card. This is the total source of information.",
				"- `o` (for \"oracle\") or `rules` -- The combined rules text of all of a card's faces.",
				"- `c` or `color` -- The combined color of all of the card's faces.",
				"- `ci` or `identity` -- The combined color identity of all of the card's faces.",
				"",
				"Cards, printings, faces, and instances can't be used directly, but they can be accessed with e.g. `card.name`. At some point, maybe, a full listing will be here. ",
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
