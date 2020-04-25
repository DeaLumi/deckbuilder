package emi.mtg.deckbuilder.view.groupings;

import emi.mtg.deckbuilder.controller.Context;
import emi.mtg.deckbuilder.model.CardInstance;
import emi.mtg.deckbuilder.view.components.CardView;

import java.util.Set;

public class Tags implements CardView.Grouping {
	public static final Tags INSTANCE = new Tags();

	private static class TagGroup implements CardView.Grouping.Group {
		private final String tag;

		public TagGroup(String tag) {
			this.tag = tag;
		}

		@Override
		public void add(CardInstance ci) {
			Context.get().tags.add(ci.card(), this.tag);
		}

		@Override
		public void remove(CardInstance ci) {
			Context.get().tags.remove(ci.card(), this.tag);
		}

		@Override
		public boolean contains(CardInstance ci) {
			return Context.get().tags.cards(this.tag).contains(ci.card());
		}

		@Override
		public String toString() {
			return tag;
		}
	}

	private static class Untagged implements CardView.Grouping.Group {

		@Override
		public void add(CardInstance ci) {
			Context.get().tags.tags(ci.card()).clear();
		}

		@Override
		public void remove(CardInstance ci) {
			// do nothing
		}

		@Override
		public boolean contains(CardInstance ci) {
			return Context.get().tags.tags(ci.card()).isEmpty();
		}

		@Override
		public String toString() {
			return "Untagged";
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
	public Group[] groups(CardView unused) {
		Set<String> allTags = Context.get().tags.tags();
		Group[] groups = new Group[allTags.size() + 1];
		groups[0] = new Untagged();
		int i = 0;
		for (String tag : allTags) {
			groups[++i] = new TagGroup(tag);
		}

		return groups;
	}

	@Override
	public boolean supportsModification() {
		return true;
	}
}
