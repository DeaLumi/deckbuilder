package emi.mtg.deckbuilder.view.groupings;

import emi.lib.Service;
import emi.mtg.deckbuilder.controller.Context;
import emi.mtg.deckbuilder.model.CardInstance;
import emi.mtg.deckbuilder.view.components.CardView;

import java.util.Set;

@Service.Provider(CardView.Grouping.class)
@Service.Property.String(name="name", value="Tags")
public class Tags implements CardView.Grouping {
	private class TagGroup implements CardView.Grouping.Group {
		private final String tag;

		public TagGroup(String tag) {
			this.tag = tag;
		}

		@Override
		public void add(CardInstance ci) {
			Tags.this.tags.add(ci.card(), this.tag);
		}

		@Override
		public void remove(CardInstance ci) {
			Tags.this.tags.remove(ci.card(), this.tag);
		}

		@Override
		public boolean contains(CardInstance ci) {
			return Tags.this.tags.cards(this.tag).contains(ci.card());
		}

		@Override
		public String toString() {
			return tag;
		}
	}

	private class Untagged implements CardView.Grouping.Group {

		@Override
		public void add(CardInstance ci) {
			Tags.this.tags.tags(ci.card()).clear();
		}

		@Override
		public void remove(CardInstance ci) {
			// do nothing
		}

		@Override
		public boolean contains(CardInstance ci) {
			return Tags.this.tags.tags(ci.card()).isEmpty();
		}

		@Override
		public String toString() {
			return "Untagged";
		}
	}

	private final emi.mtg.deckbuilder.controller.Tags tags;
	private final Group[] groups;

	public Tags() {
		this.tags = Context.get().tags;

		Set<String> allTags = this.tags.tags();
		this.groups = new Group[allTags.size() + 1];
		this.groups[0] = new Untagged();
		int i = 0;
		for (String tag : allTags) {
			this.groups[++i] = new TagGroup(tag);
		}
	}

	@Override
	public Group[] groups() {
		return groups;
	}

	@Override
	public boolean supportsModification() {
		return true;
	}
}
