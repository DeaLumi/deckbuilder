package emi.mtg.deckbuilder.view.groupings;

import emi.mtg.deckbuilder.controller.Context;
import emi.mtg.deckbuilder.controller.DeckChanger;
import emi.mtg.deckbuilder.model.CardInstance;
import emi.mtg.deckbuilder.model.DeckList;
import emi.mtg.deckbuilder.view.components.CardView;

import java.util.*;
import java.util.stream.Collectors;

public class TagGrouping implements CardView.Grouping {
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
		public void add(DeckList list, CardInstance ci) {
			if (ci.tags().contains(this.tag)) return;

			if (list != null) {
				DeckChanger.addBatchedChange(
						list,
						l -> ci.tags().add(tag),
						l -> ci.tags().remove(tag)
				);
			} else {
				Context.get().tags.add(ci.card(), tag);
				ci.tags().add(tag);
			}
		}

		@Override
		public void remove(DeckList list, CardInstance ci) {
			if (!ci.tags().contains(this.tag)) return;

			if (list != null) {
				DeckChanger.addBatchedChange(
						list,
						l -> ci.tags().remove(tag),
						l -> ci.tags().add(tag)
				);
			} else {
				Context.get().tags.remove(ci.card(), tag);
				ci.tags().remove(tag);
			}
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
			return Objects.equals(tag, other.tag);
		}
	}

	private static class Untagged implements CardView.Grouping.Group {
		public Untagged() {
		}

		@Override
		public String toString() {
			return "Untagged";
		}

		@Override
		public void add(DeckList list, CardInstance ci) {
			if (ci.tags().isEmpty()) return;

			if (list != null) {
				final Set<String> oldTags = new HashSet<>(ci.tags());
				DeckChanger.addBatchedChange(
						list,
						l -> ci.tags().clear(),
						l -> ci.tags().addAll(oldTags)
				);
			} else {
				for (String tag : Context.get().tags.tags(ci.card())) Context.get().tags.remove(ci.card(), tag);
				ci.tags().clear();
			}
		}

		@Override
		public void remove(DeckList list, CardInstance ci) {
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

	// TODO can't make new tag groups all the time.
	// Make a new grouping instance on demand, based on a decklist; populate it with known groups if possible.
	@Override
	public Set<Group> groups(CardInstance card) {
		Set<Group> groups = card.tags().stream()
				.map(TagGroup::new)
				.collect(Collectors.toSet());

		if (groups.isEmpty()) groups.add(new Untagged());
		return groups;
	}

	@Override
	public boolean supportsModification() {
		return true;
	}
}
