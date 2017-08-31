package emi.mtg.deckbuilder.view.groupings;

import emi.mtg.deckbuilder.controller.Context;
import emi.mtg.deckbuilder.model.CardInstance;
import emi.mtg.deckbuilder.view.CardView;

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

	public CharacteristicGrouping(Context context) {
		/* do nothing */
	}

	public abstract String[] groupValues();
	public abstract String extract(CardInstance ci);

	private Group[] groups;

	@Override
	public Group[] groups() {
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
}