package emi.mtg.deckbuilder.view.groupings;

import emi.mtg.deckbuilder.controller.DeckChanger;
import emi.mtg.deckbuilder.model.CardInstance;
import emi.mtg.deckbuilder.model.DeckList;
import emi.mtg.deckbuilder.view.components.CardView;
import javafx.collections.ListChangeListener;

import java.util.*;
import java.util.stream.Collectors;

public class TagGrouping implements CardView.Grouping {
	public static final TagGrouping INSTANCE = new TagGrouping();

	private static class TagGroup implements CardView.Grouping.Group {
		private final DeckList list;
		private final String tag;

		public TagGroup(DeckList list, String tag) {
			this.list = list;
			this.tag = tag;
		}

		@Override
		public String toString() {
			return tag;
		}

		@Override
		public void add(CardInstance ci) {
			if (ci.tags().contains(this.tag)) return;

			DeckChanger.addBatchedChange(
					list,
					l -> ci.tags().add(tag),
					l -> ci.tags().remove(tag)
			);
		}

		@Override
		public void remove(CardInstance ci) {
			if (!ci.tags().contains(this.tag)) return;

			DeckChanger.addBatchedChange(
					list,
					l -> ci.tags().remove(tag),
					l -> ci.tags().add(tag)
			);
		}

		@Override
		public boolean contains(CardInstance ci) {
			return ci.tags().contains(tag);
		}

		@Override
		public int compareTo(Group o) {
			if (o instanceof Untagged) return 1;
			if (o instanceof TagGroup) return tag.compareTo(((TagGroup) o).tag);
			throw new UnsupportedOperationException();
		}

		@Override
		public int hashCode() {
			return tag.hashCode() ^ 0x1483abcd;
		}

		@Override
		public boolean equals(Object obj) {
			if (obj == null) return false;
			if (!(obj instanceof TagGroup)) return false;
			TagGroup other = (TagGroup) obj;
			return Objects.equals(tag, other.tag) && Objects.equals(list, other.list);
		}
	}

	private static class Untagged implements CardView.Grouping.Group {
		private final DeckList list;

		public Untagged(DeckList list) {
			this.list = list;
		}

		@Override
		public String toString() {
			return "Untagged";
		}

		@Override
		public void add(CardInstance ci) {
			if (ci.tags().isEmpty()) return;

			final Set<String> oldTags = new HashSet<>(ci.tags());
			DeckChanger.addBatchedChange(
					list,
					l -> ci.tags().clear(),
					l -> ci.tags().addAll(oldTags)
			);
		}

		@Override
		public void remove(CardInstance ci) {
			// Do nothing
		}

		@Override
		public boolean contains(CardInstance ci) {
			return ci.tags().isEmpty();
		}

		@Override
		public int compareTo(Group o) {
			if (o instanceof Untagged) return 0;
			if (o instanceof TagGroup) return -1;
			throw new UnsupportedOperationException();
		}

		@Override
		public int hashCode() {
			return 0x1483abcd * 2;
		}

		@Override
		public boolean equals(Object obj) {
			return obj instanceof Untagged;
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
	public boolean requireRegroup(Group[] existing, ListChangeListener.Change<? extends CardInstance> change) {
		Set<String> currentTags = Arrays.stream(existing)
				.filter(g -> g instanceof TagGroup)
				.map(g -> ((TagGroup) g).tag)
				.collect(Collectors.toSet());

		while (change.next()) {
			if (change.wasAdded() || change.wasUpdated()) {
				for (CardInstance ci : change.getList().subList(change.getFrom(), change.getTo())) {
					for (String tag : ci.tags()) {
						if (!currentTags.contains(tag)) return true;
					}
				}
			}
		}

		return false;
	}

	@Override
	public Group[] groups(DeckList list, List<CardInstance> model) {
		Deque<Group> groups = model.stream()
				.map(CardInstance::tags)
				.flatMap(Set::stream)
				.distinct()
				.sorted()
				.map(tag -> new TagGroup(list, tag))
				.collect(Collectors.toCollection(LinkedList::new));
		groups.addFirst(new Untagged(list));
		return groups.toArray(new Group[0]);
	}

	@Override
	public boolean supportsModification() {
		return true;
	}
}
