package emi.mtg.deckbuilder.view.groupings;

import emi.mtg.deckbuilder.model.CardInstance;
import emi.mtg.deckbuilder.view.components.CardView;
import javafx.collections.ListChangeListener;

import java.util.List;

public class None implements CardView.Grouping {
	public static final None INSTANCE = new None();

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
	public boolean requireRegroup(Group[] existing, ListChangeListener.Change<? extends CardInstance> change) {
		return false;
	}

	@Override
	public Group[] groups(List<CardInstance> unused) {
		return GROUPS;
	}
}
