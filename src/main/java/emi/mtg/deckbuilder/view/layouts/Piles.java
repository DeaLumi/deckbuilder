package emi.mtg.deckbuilder.view.layouts;

import emi.lib.Service;
import emi.mtg.deckbuilder.view.components.CardView;

@Service.Provider(CardView.LayoutEngine.class)
@Service.Property.String(name="name", value="Piles")
public class Piles implements CardView.LayoutEngine {
	private final static double OVERLAP_FACTOR = 0.125;

	private final CardView parent;

	public Piles(CardView parent) {
		this.parent = parent;
	}

	@Override
	public String toString() {
		return "Piles";
	}

	@Override
	public void layoutGroups(CardView.Group[] groups, boolean showEmpty) {
		double p = parent.cardPadding();
		double w = parent.cardWidth();
		double h = parent.cardHeight();

		double pwp = p + w + p;

		double x = 0;
		for (int i = 0; i < groups.length; ++i) {
			if (!showEmpty && groups[i].model.isEmpty()) {
				groups[i].labelBounds.pos.x = groups[i].labelBounds.pos.y = 0;
				groups[i].labelBounds.dim.x = groups[i].labelBounds.dim.y = 0;
				groups[i].groupBounds.pos.x = groups[i].groupBounds.pos.y = 0;
				groups[i].groupBounds.dim.x = groups[i].groupBounds.dim.y = 0;

				continue;
			}

			groups[i].labelBounds.pos.x = x;
			groups[i].labelBounds.pos.y = 0.0;
			groups[i].labelBounds.dim.x = pwp;
			groups[i].labelBounds.dim.y = 18.0;

			groups[i].groupBounds.pos.x = x;
			groups[i].groupBounds.pos.y = 18.0;
			groups[i].groupBounds.dim.x = pwp;
			groups[i].groupBounds.dim.y = p + (h * OVERLAP_FACTOR) * (groups[i].model.size() - 1) + h + p;

			x += groups[i].groupBounds.dim.x;
		}
	}

	@Override
	public CardView.MVec2d coordinatesOf(int card, CardView.MVec2d buffer) {
		if (buffer == null) {
			buffer = new CardView.MVec2d();
		}

		double p = parent.cardPadding();
		double h = parent.cardHeight();

		buffer.x = p;
		buffer.y = p + (h * OVERLAP_FACTOR) * card;

		return buffer;
	}

	@Override
	public int cardAt(CardView.MVec2d point, int groupSize) {
		double p = parent.cardPadding();
		double w = parent.cardWidth();
		double h = parent.cardHeight();

		double x = point.x;
		if (x < p || x > p + w) {
			return -1;
		}

		if (point.y < p || point.y > p + (h * OVERLAP_FACTOR) * (groupSize - 1) + h) {
			return -1;
		}

		if (point.y > p + (h * OVERLAP_FACTOR) * (groupSize - 1)) {
			return groupSize - 1;
		}

		int idx = (int) ((point.y - p) / (h * OVERLAP_FACTOR));

		return idx < 0 || idx >= groupSize ? -1 : idx;
	}
}
