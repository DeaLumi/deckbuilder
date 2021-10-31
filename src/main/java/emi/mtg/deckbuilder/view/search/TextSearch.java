package emi.mtg.deckbuilder.view.search;

import emi.lib.mtg.Card;
import emi.mtg.deckbuilder.model.CardInstance;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class TextSearch implements SearchProvider {
	public final String NAME = "Simple Text Search";

	@Override
	public String toString() {
		return NAME;
	}

	@Override
	public String name() {
		return NAME;
	}

	@Override
	public String usage() {
		return String.join("\n",
				"Searches card names, types, and rules texts for the given terms.",
				"<ul>",
				"<li>Surround multiple terms in quotation marks to treat them as a single term.</li>",
				"<li>Tilde (<code>~</code>) and <code>CARDNAME</code> will be substituted for the card's name.</li>",
				"</ul>");
	}

	private static final Pattern TERM_PATTERN = Pattern.compile("\"([^\"]+)\"|(?<!\")([^ ]+)(?!\")");

	@Override
	public Predicate<CardInstance> parse(String query) throws IllegalArgumentException {
		boolean requiresCardname = false;
		List<String> terms = new ArrayList<>();
		Matcher matcher = TERM_PATTERN.matcher(query);
		while (matcher.find()) {
			String term = matcher.group();
			if (term.charAt(0) == '\"') term = term.substring(1);
			if (term.length() > 0 && term.charAt(term.length() - 1) == '\"') term = term.substring(0, term.length() - 1);
			terms.add(term.toLowerCase());
			if (term.contains("~") || term.contains("CARDNAME")) requiresCardname = true;
		}

		final boolean finCardname = requiresCardname;

		return ci -> {
			for (String term : terms) {
				for (Card.Face face : ci.card().faces()) {
					String finterm = term;
					if (finCardname) finterm = finterm.replaceAll("~|CARDNAME", face.name().toLowerCase());

					if (face.name().toLowerCase().contains(finterm)) return true;
					if (face.type().toString().toLowerCase().contains(finterm)) return true;
					if (face.rules().toLowerCase().contains(finterm)) return true;
				}
			}

			return false;
		};
	}
}
