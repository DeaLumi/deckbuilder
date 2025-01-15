package emi.mtg.deckbuilder.model;

import emi.lib.mtg.Card;
import emi.lib.mtg.DataSource;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Collections;
import java.util.Set;
import java.util.UUID;
import java.util.function.DoubleConsumer;

/**
 * Quick utility class for when the primary DataSource fails to initialize, so the program doesn't crash.
 */
public class EmptyDataSource implements DataSource {
	@Override
	public String toString() {
		return "Dummy Data (Empty)";
	}

	@Override
	public boolean loadData(Path dataDir, DoubleConsumer progress) throws IOException {
		// No-op
		return true;
	}

	@Override
	public Card card(String name, char variation) {
		return null;
	}

	@Override
	public Set<? extends Card> cards() {
		return Collections.emptySet();
	}

	@Override
	public Set<? extends Card.Print> prints() {
		return Collections.emptySet();
	}

	@Override
	public Card.Print print(UUID id) {
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
