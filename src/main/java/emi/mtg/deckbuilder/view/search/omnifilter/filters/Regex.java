package emi.mtg.deckbuilder.view.search.omnifilter.filters;

import emi.mtg.deckbuilder.model.CardInstance;
import emi.mtg.deckbuilder.view.search.omnifilter.Omnifilter;

import java.util.*;
import java.util.function.BiPredicate;
import java.util.function.Predicate;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

public class Regex implements Omnifilter.Subfilter {
	@Override
	public Collection<String> keys() {
		return Arrays.asList("regex", "re");
	}

	@Override
	public String description() {
		return "Search cards' rules text with a regular expression. Only responds to the `:` operator.";
	}

	/**
	 * Segments a Magic card name with up to five optional non-capturing groups. In Magic card text, if the card's name
	 * contains an appellation like Derevi, Empyrial Tactician, the appellation is sometimes dropped in the card's text.
	 * This function creates a regex that will match the shortened version too.
	 * @param cardname The annotated card name
	 * @return A regex which will match that card name or any obvious shortenings.
	 */
	private static String segmentSubNames(String cardname) {
		StringBuilder sb = new StringBuilder(cardname.length());

		int cparen = 0;
		for (int i = 0; i < cardname.length(); ++i) {
			char c = cardname.charAt(i);
			if (cparen < 5) {
				if (c == ',') {
					++cparen;
					sb.append("(?:,");
					if (i + 1 < cardname.length() && cardname.charAt(i + 1) == ' ') {
						++i;
						sb.append(' ');
					}
					continue;
				} else if (c == ' ') {
					++cparen;
					sb.append("(?: ");
					continue;
				}
			}

			sb.append(c);
		}

		for (int i = 0; i < cparen; ++i) sb.append(")?");

		return sb.toString();
	}

	static BiPredicate<String, String> create(String regex, boolean forceMonolithic) {
		// If the pattern doesn't contain `~|CARDNAME`, we can just precompile it.
		// Otherwise, as long as the pattern doesn't contain backreferences and there's no `~|CARDNAME` within a group,
		// we can split the pattern on those and do a rope-match.
		// But if one of the above is the case, we'll have to substitute card names into the pattern for each face and
		// recompile it.
		// (forceMonolithic is for profiling the algorithm in MatchUtils)

		int parentheses = 0, cardname = 0;
		boolean escape = false;
		boolean anyCardname = false, segmentDisqualified = false;
		for (int i = 0; i < regex.length(); ++i) {
			switch (regex.charAt(i)) {
				case '\\':
					escape = true;
					break;
				case '(':
					if (!escape) ++parentheses;
					escape = false;
					break;
				case ')':
					if (!escape) --parentheses;
					escape = false;
					break;
				case '~':
					if (parentheses > 0) segmentDisqualified = true;
					anyCardname = true;
					escape = false;
					break;
				case '1':
				case '2':
				case '3':
				case '4':
				case '5':
				case '6':
				case '7':
				case '8':
				case '9':
					if (escape) segmentDisqualified = true;
					escape = false;
					break;
				default:
					escape = false;
					if (regex.charAt(i) == "CARDNAME".charAt(cardname)) {
						++cardname;

						if (cardname >= "CARDNAME".length()) {
							if (parentheses > 0) segmentDisqualified = true;
							anyCardname = true;
							cardname = 0;
						}
					} else {
						cardname = 0;
					}
					break;
			}
		}

		if (!anyCardname) {
			final Pattern pattern = Pattern.compile(regex);
			return (rules, name) -> pattern.matcher(rules).find();
		} else if (segmentDisqualified || forceMonolithic) {
			return (rules, name) -> {
				String xform = segmentSubNames(name);
				Pattern pattern = Pattern.compile(regex.replace("~", xform).replace("CARDNAME", xform));
				return pattern.matcher(rules).find();
			};
		}

		List<MatchUtils.SequenceMatcher> rope = Arrays.stream(regex.split("(~|CARDNAME)"))
				.filter(s -> !s.isEmpty())
				.map(Pattern::compile)
				.map(MatchUtils::regexMatcher)
				.collect(Collectors.toList());

		return (rules, name) -> MatchUtils.matchesRope(rules, 0, new MatchUtils.Delimited<>(rope, MatchUtils.cardNameOrSubNameMatcher(name))) >= 0;
	}

	@Override
	public Predicate<CardInstance> create(Omnifilter.Operator operator, String value) {
		if (operator != Omnifilter.Operator.DIRECT) {
			throw new IllegalArgumentException("Can only use ':' with regex filter.");
		}

		BiPredicate<String, String> base = Regex.create(value, false);
		return (Omnifilter.FaceFilter) face -> base.test(face.rules(), face.name());
	}
}
