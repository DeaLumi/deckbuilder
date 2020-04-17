package emi.mtg.deckbuilder.view.groupings;

import emi.lib.Service;
import emi.mtg.deckbuilder.model.CardInstance;
import emi.mtg.deckbuilder.view.components.CardView;

@Service.Provider(CardView.Grouping.class)
@Service.Property.String(name="name", value="None")
public class None implements CardView.Grouping {
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

	public None() {
		/* do nothing */
	}

	@Override
	public Group[] groups() {
		return GROUPS;
	}

	@Override
	public boolean supportsModification() {
		return false;
	}
}
