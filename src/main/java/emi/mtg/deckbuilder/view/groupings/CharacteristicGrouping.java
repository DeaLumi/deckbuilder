package emi.mtg.deckbuilder.view.groupings;

import emi.mtg.deckbuilder.model.CardInstance;
import emi.mtg.deckbuilder.model.DeckList;
import emi.mtg.deckbuilder.view.components.CardView;
import javafx.collections.ListChangeListener;

import java.util.List;

public abstract class CharacteristicGrouping implements CardView.Grouping {
	protected class CharacteristicGroup implements CardView.Grouping.Group {
		private final String value;

		public CharacteristicGroup(String value) {
			this.value = value;
		}

		@Override
		public void add(CardInstance ci) {
			throw new UnsupportedOperationException();
		}

		@Override
		public void remove(CardInstance ci) {
			throw new UnsupportedOperationException();
		}

		@Override
		public boolean contains(CardInstance ci) {
			return value.equals(CharacteristicGrouping.this.extract(ci));
		}

		@Override
		public String toString() {
			return value;
		}
	}

	public CharacteristicGrouping() {
		/* do nothing */
	}

	public abstract String[] groupValues();
	public abstract String extract(CardInstance ci);

	private Group[] groups;

	@Override
	public boolean requireRegroup(Group[] existing, ListChangeListener.Change<? extends CardInstance> change) {
		// Characteristics groupings have a fixed set of possible values and never require regrouping.
		return false;
	}

	@Override
	public Group[] groups(DeckList list, List<CardInstance> unused) {
		if (groups == null) {
			groups = new Group[groupValues().length];

			for (int i = 0; i < groupValues().length; ++i) {
				groups[i] = new CharacteristicGroup(groupValues()[i]);
			}
		}

		return groups;
	}

	@Override
	public boolean supportsModification() {
		return false;
	}

	@Override
	public String toString() {
		return name();
	}
}
