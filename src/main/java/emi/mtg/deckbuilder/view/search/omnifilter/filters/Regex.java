package emi.mtg.deckbuilder.view.search.omnifilter.filters;

import emi.mtg.deckbuilder.model.CardInstance;
import emi.mtg.deckbuilder.view.search.omnifilter.Omnifilter;

import java.util.Arrays;
import java.util.Collection;
import java.util.function.Predicate;
import java.util.regex.Pattern;

public class Regex implements Omnifilter.Subfilter {
	@Override
	public Collection<String> keys() {
		return Arrays.asList("regex", "re");
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

		if (value.indexOf('~') >= 0 || value.contains("CARDNAME")) {
			return (Omnifilter.FaceFilter) face -> {
				Pattern pattern = Pattern.compile(value.replace("~", face.name()).replace("CARDNAME", face.name()));
				return pattern.matcher(face.rules()).find();
			};
		} else {
			Pattern pattern = Pattern.compile(value);
			return (Omnifilter.FaceFilter) face -> pattern.matcher(face.rules()).find();
		}
	}
}
