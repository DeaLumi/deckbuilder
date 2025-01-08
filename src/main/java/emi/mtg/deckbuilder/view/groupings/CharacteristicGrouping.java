package emi.mtg.deckbuilder.view.groupings;

import emi.mtg.deckbuilder.model.CardInstance;
import emi.mtg.deckbuilder.view.components.CardView;

import java.util.Collections;
import java.util.Set;

public abstract class CharacteristicGrouping implements CardView.Grouping {
	protected class CharacteristicGroup implements CardView.Grouping.Group {
		public final String value;

		public CharacteristicGroup(String value) {
			this.value = value;
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

	private synchronized void ensureGroups() {
		if (groups == null) {
			groups = new CharacteristicGroup[groupValues().length];

			for (int i = 0; i < groupValues().length; ++i) {
				groups[i] = new CharacteristicGroup(groupValues()[i]);
			}
		}
	}

	@Override
	public Set<Group> groups(CardInstance card) {
		ensureGroups();

		final String str = extract(card);

		for (int i = 0; i < groups.length; ++i) {
			if (str.equals(groups[i].value)) {
				return Collections.singleton(groups[i]);
			}
		}

		throw new IllegalArgumentException();
	}

	@Override
	public String toString() {
		return name();
	}
}
