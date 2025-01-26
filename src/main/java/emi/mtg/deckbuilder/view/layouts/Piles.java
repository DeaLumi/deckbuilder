package emi.mtg.deckbuilder.view.layouts;

import emi.mtg.deckbuilder.view.components.CardView;

public class Piles implements CardView.LayoutEngine {
	@Override
	public String name() {
		return "Piles";
	}

	@Override
	public String toString() {
		return name();
	}

	private final static double OVERLAP_FACTOR = 0.110;

	@Override
	public void layoutGroups(CardView view, CardView.Bounds boundingBox, CardView.Group[] groups, boolean showEmpty) {
		double p = view.cardPadding();
		double w = view.cardWidth();
		double h = view.cardHeight();

		double pwp = p + w + p;

		double x = 0;
		double height = 0;
		for (int i = 0; i < groups.length; ++i) {
			if (!showEmpty && groups[i].model().isEmpty()) {
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
			groups[i].groupBounds.dim.y = p + (h * OVERLAP_FACTOR) * (groups[i].model().size() - 1) + h + p;

			if (groups[i].groupBounds.dim.y > height) {
				height = groups[i].groupBounds.dim.y;

				for (int j = 0; j < i; ++j) {
					groups[j].groupBounds.dim.y = height;
				}
			} else {
				groups[i].groupBounds.dim.y = height;
			}

			x += groups[i].groupBounds.dim.x;
		}

		boundingBox.pos.set(0, 0);
		boundingBox.dim.set(x, height);
	}

	@Override
	public CardView.MVec2d coordinatesOf(CardView view, int card, CardView.MVec2d buffer) {
		if (buffer == null) {
			buffer = new CardView.MVec2d();
		}

		double p = view.cardPadding();
		double h = view.cardHeight();

		buffer.x = p;
		buffer.y = p + (h * OVERLAP_FACTOR) * card;

		return buffer;
	}

	@Override
	public int cardAt(CardView view, CardView.MVec2d point, int groupSize) {
		double p = view.cardPadding();
		double w = view.cardWidth();
		double h = view.cardHeight();

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

	@Override
	public boolean cardInSelection(CardView view, CardView.MVec2d cardPos, CardView.MVec2d min, CardView.MVec2d max, int groupSize) {
		double p = view.cardPadding();
		double h = view.cardHeight();
		boolean last = cardPos.y >= p + (h * OVERLAP_FACTOR) * (groupSize - 1);

		return cardPos.x + view.cardWidth() >= min.x && cardPos.x <= max.x && cardPos.y + view.cardHeight() * OVERLAP_FACTOR + (last ? h : 0) >= min.y && cardPos.y <= max.y;
	}
}
