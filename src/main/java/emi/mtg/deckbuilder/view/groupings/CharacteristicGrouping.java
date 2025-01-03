package emi.mtg.deckbuilder.view.groupings;

import emi.mtg.deckbuilder.model.CardInstance;
import emi.mtg.deckbuilder.model.DeckList;
import emi.mtg.deckbuilder.view.components.CardView;
import javafx.collections.ListChangeListener;

import java.util.List;

public abstract class CharacteristicGrouping implements CardView.Grouping {
	protected class CharacteristicGroup implements CardView.Grouping.Group {
		public final String value;

		public CharacteristicGroup(String value) {
			this.value = value;
		}

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
			return value.equals(CharacteristicGrouping.this.extract(ci));
		}

		@Override
		public String toString() {
			return value;
		}

		@Override
		public int compareTo(Group o) {
			if (o == this || equals(o)) return 0;
			if (o instanceof CharacteristicGroup) {
				for (int i = 0; i < CharacteristicGrouping.this.groups.length; ++i) {
					if (CharacteristicGrouping.this.groups[i] == this) return -1;
					if (CharacteristicGrouping.this.groups[i] == o) return 1;
				}

				throw new IllegalStateException();
			}
			throw new UnsupportedOperationException();
		}

		@Override
		public int hashCode() {
			return value.hashCode() ^ 0x1483abcd;
		}

		private CharacteristicGrouping parent() {
			return CharacteristicGrouping.this;
		}

		@Override
		public boolean equals(Object obj) {
			if (!(obj instanceof CharacteristicGroup)) return false;
			CharacteristicGroup other = (CharacteristicGroup) obj;
			return value.equals(other.value) && CharacteristicGrouping.this == other.parent();
		}
	}

	public CharacteristicGrouping() {
		/* do nothing */
	}

	public abstract String[] groupValues();
	public abstract String extract(CardInstance ci);

	private CharacteristicGroup[] groups;

	@Override
	public boolean requireRegroup(Group[] existing, ListChangeListener.Change<? extends CardInstance> change) {
		// Characteristics groupings have a fixed set of possible values and never require regrouping.
		return false;
	}

	private synchronized void ensureGroups() {
		if (groups == null) {
			groups = new CharacteristicGroup[groupValues().length];

			for (int i = 0; i < groupValues().length; ++i) {
				groups[i] = new CharacteristicGroup(groupValues()[i]);
			}
		}
	}

	@Override
	public Group[] groups(DeckList list, List<CardInstance> unused) {
		ensureGroups();
		return groups;
	}

	@Override
	public boolean supportsModification() {
		return false;
	}

	@Override
	public String toString() {
		return name();
	}
}
