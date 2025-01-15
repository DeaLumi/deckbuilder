package emi.mtg.deckbuilder.view.groupings;

import emi.mtg.deckbuilder.model.CardInstance;
import emi.mtg.deckbuilder.view.components.CardView;

import java.util.Collections;
import java.util.Set;

public class None implements CardView.Grouping {
	public static Group[] GROUPS = new Group[] {
		new Group() {
			@Override
			public boolean contains(CardInstance ci) {
				return true;
			}

			@Override
			public String toString() {
				return "All Cards";
			}

			@Override
			public int compareTo(Group o) {
				return 0;
			}
		}
	};

	@Override
	public String name() {
		return "None";
	}

	@Override
	public String toString() {
		return name();
	}

	@Override
	public Set<Group> groups(CardInstance card) {
		return Collections.singleton(GROUPS[0]);
	}
}
