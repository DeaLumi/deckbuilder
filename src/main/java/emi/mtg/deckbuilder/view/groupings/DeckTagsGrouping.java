package emi.mtg.deckbuilder.view.groupings;

import emi.mtg.deckbuilder.model.CardInstance;
import emi.mtg.deckbuilder.view.components.CardView;

import java.util.Deque;
import java.util.LinkedList;
import java.util.Set;
import java.util.stream.Collectors;

public class DeckTagsGrouping implements CardView.Grouping {
	public static final DeckTagsGrouping INSTANCE = new DeckTagsGrouping();

	private static class DeckTagGroup implements CardView.Grouping.Group {
		private final String tag;

		public DeckTagGroup(String tag) {
			this.tag = tag;
		}

		@Override
		public String toString() {
			return tag;
		}

		@Override
		public void add(CardInstance ci) {
			ci.tags().add(tag);
		}

		@Override
		public void remove(CardInstance ci) {
			ci.tags().remove(tag);
		}

		@Override
		public boolean contains(CardInstance ci) {
			return ci.tags().contains(tag);
		}
	}

	private static class Untagged implements CardView.Grouping.Group {
		public static final Untagged INSTANCE = new Untagged();

		@Override
		public String toString() {
			return "Untagged";
		}

		@Override
		public void add(CardInstance ci) {
			ci.tags().clear();
		}

		@Override
		public void remove(CardInstance ci) {
			// Do nothing
		}

		@Override
		public boolean contains(CardInstance ci) {
			return ci.tags().isEmpty();
		}
	}

	@Override
	public String name() {
		return "Deck Tags";
	}

	@Override
	public String toString() {
		return name();
	}

	@Override
	public Group[] groups(CardView view) {
		Deque<Group> groups = view.model().stream()
				.map(CardInstance::tags)
				.flatMap(Set::stream)
				.distinct()
				.sorted()
				.map(DeckTagGroup::new)
				.collect(Collectors.toCollection(LinkedList::new));
		groups.addFirst(Untagged.INSTANCE);
		return groups.toArray(new Group[0]);
	}

	@Override
	public boolean supportsModification() {
		return true;
	}
}
