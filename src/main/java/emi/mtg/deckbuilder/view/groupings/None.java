package emi.mtg.deckbuilder.view.groupings;

import emi.mtg.deckbuilder.model.CardInstance;
import emi.mtg.deckbuilder.view.components.CardView;

public class None implements CardView.Grouping {
	public static final None INSTANCE = new None();

	private static Group[] GROUPS = new Group[] {
		new Group() {
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
				return true;
			}

			@Override
			public String toString() {
				return "All Cards";
			}
		}
	};

	@Override
	public String name() {
		return "None";
	}

	@Override
	public Group[] groups(CardView unused) {
		return GROUPS;
	}

	@Override
	public boolean supportsModification() {
		return false;
	}
}
