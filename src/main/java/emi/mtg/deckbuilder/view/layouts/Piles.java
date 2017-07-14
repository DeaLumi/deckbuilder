package emi.mtg.deckbuilder.view.layouts;

import emi.lib.Service;
import emi.mtg.deckbuilder.view.CardView;

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
	public void layoutGroups(int[] groupSizes, CardView.Bounds[] groupBounds, CardView.Bounds[] labelBounds) {
		double p = parent.cardPadding();
		double w = parent.cardWidth();
		double h = parent.cardHeight();

		double x = 0;
		for (int i = 0; i < groupSizes.length; ++i) {
			double groupWidth = p + (groupSizes[i] > 0 ? w : 0) + p;

			labelBounds[i].pos.x = x;
			labelBounds[i].pos.y = 0.0;
			labelBounds[i].dim.x = groupWidth;
			labelBounds[i].dim.y = 18.0;

			groupBounds[i].pos.x = x;
			groupBounds[i].pos.y = 18.0;
			groupBounds[i].dim.x = groupWidth;
			groupBounds[i].dim.y = p + (h * OVERLAP_FACTOR) * (groupSizes[i] - 1) + h + p;

			x += groupBounds[i].dim.x;
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
