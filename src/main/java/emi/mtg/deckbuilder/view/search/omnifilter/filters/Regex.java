package emi.mtg.deckbuilder.view.search.omnifilter.filters;

import emi.mtg.deckbuilder.model.CardInstance;
import emi.mtg.deckbuilder.view.search.omnifilter.Omnifilter;

import java.util.function.Predicate;
import java.util.regex.Pattern;

public class Regex implements Omnifilter.Subfilter {
	@Override
	public String key() {
		return "regex";
	}

	@Override
	public String shorthand() {
		return "re";
	}

	@Override
	public String description() {
		return "Search cards' rules text with a regular expression.";
	}

	@Override
	public Predicate<CardInstance> create(Omnifilter.Operator operator, String value) {
		if (operator != Omnifilter.Operator.DIRECT) {
			throw new IllegalArgumentException("Can only use ':' with regex filter.");
		}

		Pattern pattern = Pattern.compile(value);
		return (Omnifilter.FaceFilter) face -> pattern.matcher(face.rules()).find();
	}
}
