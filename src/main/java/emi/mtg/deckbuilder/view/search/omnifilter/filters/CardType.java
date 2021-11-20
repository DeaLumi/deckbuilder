package emi.mtg.deckbuilder.view.search.omnifilter.filters;

import emi.lib.mtg.Card;
import emi.lib.mtg.TypeLine;
import emi.mtg.deckbuilder.model.CardInstance;
import emi.mtg.deckbuilder.view.search.omnifilter.Omnifilter;

import java.util.Arrays;
import java.util.Collection;
import java.util.function.Predicate;

public class CardType implements Omnifilter.Subfilter {
	@Override
	public Collection<String> keys() {
		return Arrays.asList("type", "t");
	}

	@Override
	public String description() {
		return "Filter cards by typeline. Any number of supertypes, card types, and subtypes can be specified.";
	}

	@Override
	public Predicate<CardInstance> create(Omnifilter.Operator operator, String value) {
		TypeLine fragment = TypeLine.Basic.parseFragment(value);
		Predicate<Card.Face> facePredicate = Omnifilter.Operator.comparison(operator == Omnifilter.Operator.DIRECT ? Omnifilter.Operator.GREATER_OR_EQUALS : operator, f -> TypeLine.COMPARATOR.compare(f.type(), fragment).value());
		return (Omnifilter.FaceFilter) facePredicate::test;
	}
}
