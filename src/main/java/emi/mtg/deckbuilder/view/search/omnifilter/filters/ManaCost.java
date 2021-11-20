package emi.mtg.deckbuilder.view.search.omnifilter.filters;

import emi.lib.mtg.Mana;
import emi.mtg.deckbuilder.model.CardInstance;
import emi.mtg.deckbuilder.view.search.omnifilter.Omnifilter;

import java.util.Arrays;
import java.util.Collection;
import java.util.function.Predicate;

public class ManaCost implements Omnifilter.Subfilter {
	@Override
	public Collection<String> keys() {
		return Arrays.asList("manacost", "mana", "mc");
	}

	@Override
	public String description() {
		return "Compares against each face's mana cost. Costs containing hybrids need to be surrounded by curly braces, e.g. <code>{1}{W}{U}</code>.";
	}

	@Override
	public Predicate<CardInstance> create(Omnifilter.Operator operator, String value) {
		Mana.Value mana;
		if (value.indexOf('{') < 0) {
			mana = Mana.Value.of(value.chars().mapToObj(c -> Mana.Symbol.parsePure("" + (char) c)).toArray(Mana.Symbol.Pure[]::new));
		} else {
			mana = Mana.Value.parse(value);
		}

		if (operator == Omnifilter.Operator.DIRECT) operator = Omnifilter.Operator.EQUALS;
		return Omnifilter.Operator.faceComparison(operator, f -> Mana.Value.SEARCH_COMPARATOR.compare(f.manaCost(), mana).value());
	}
}
