package emi.mtg.deckbuilder.model;

import emi.lib.mtg.Card;
import emi.lib.mtg.DataSource;

import java.util.Collections;
import java.util.Set;
import java.util.UUID;

/**
 * Quick utility class for when the primary DataSource fails to initialize, so the program doesn't crash.
 */
public class EmptyDataSource implements DataSource {
	@Override
	public Set<? extends Card> cards() {
		return Collections.emptySet();
	}

	@Override
	public Card card(String name) {
		return null;
	}

	@Override
	public Set<? extends Card.Printing> printings() {
		return Collections.emptySet();
	}

	@Override
	public Card.Printing printing(UUID id) {
		return null;
	}

	@Override
	public Set<? extends emi.lib.mtg.Set> sets() {
		return Collections.emptySet();
	}

	@Override
	public emi.lib.mtg.Set set(String code) {
		return null;
	}
}
