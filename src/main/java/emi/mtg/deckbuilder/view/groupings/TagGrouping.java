package emi.mtg.deckbuilder.view.groupings;

import emi.mtg.deckbuilder.model.CardInstance;
import emi.mtg.deckbuilder.view.components.CardView;

import java.util.Deque;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

public class TagGrouping implements CardView.Grouping {
	public static final TagGrouping INSTANCE = new TagGrouping();

	private static class TagGroup implements CardView.Grouping.Group {
		private final String tag;

		public TagGroup(String tag) {
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
		return "Tags";
	}

	@Override
	public String toString() {
		return name();
	}

	@Override
	public Group[] groups(List<CardInstance> model) {
		Deque<Group> groups = model.stream()
				.map(CardInstance::tags)
				.flatMap(Set::stream)
				.distinct()
				.sorted()
				.map(TagGroup::new)
				.collect(Collectors.toCollection(LinkedList::new));
		groups.addFirst(Untagged.INSTANCE);
		return groups.toArray(new Group[0]);
	}

	@Override
	public boolean supportsModification() {
		return true;
	}
}
