package emi.mtg.deckbuilder.view.layouts;

import emi.mtg.deckbuilder.view.components.CardView;

public class FlowGrid implements CardView.LayoutEngine {
	@Override
	public String name() {
		return "Flow Grid";
	}

	@Override
	public String toString() {
		return name();
	}

	private int stride(CardView view) {
		double p = view.cardPadding();
		double pwp = p + view.cardWidth() + p;
		return Math.max(1, (int) Math.floor(view.getWidth() / pwp));
	}

	@Override
	public void layoutGroups(CardView view, CardView.Bounds boundingBox, CardView.Group[] groups, boolean showEmpty) {
		double p = view.cardPadding();
		double pwp = p + view.cardWidth() + p;
		double php = p + view.cardHeight() + p;

		int stride = stride(view);
		int maxStride = 0;

		double y = 0;
		for (int i = 0; i < groups.length; ++i) {
			if (!showEmpty && groups[i].model().isEmpty()) {
				groups[i].labelBounds.pos.x = groups[i].labelBounds.pos.y = 0;
				groups[i].labelBounds.dim.x = groups[i].labelBounds.dim.y = 0;
				groups[i].groupBounds.pos.x = groups[i].groupBounds.pos.y = 0;
				groups[i].groupBounds.dim.x = groups[i].groupBounds.dim.y = 0;

				continue;
			}

			groups[i].labelBounds.pos.x = 0;
			groups[i].labelBounds.pos.y = y;
			groups[i].labelBounds.dim.y = 18.0;
			y += groups[i].labelBounds.dim.y;

			groups[i].groupBounds.pos.x = 0;
			groups[i].groupBounds.pos.y = y;

			int gs = Math.min(stride, groups[i].model().size());
			if (gs > maxStride) {
				maxStride = gs;
				for (int j = 0; j < i; ++j) {
					if (groups[j].groupBounds.dim.x == 0 && groups[j].groupBounds.dim.y == 0) continue;

					groups[j].labelBounds.dim.x = groups[j].groupBounds.dim.x = maxStride * pwp;
				}
			}

			groups[i].labelBounds.dim.x = groups[i].groupBounds.dim.x = maxStride * pwp;

			groups[i].groupBounds.dim.y = Math.max(pwp, Math.ceil((double) groups[i].model().size() / (double) maxStride) * php);
			y += groups[i].groupBounds.dim.y;
		}

		boundingBox.pos.set(0, 0);
		boundingBox.dim.set(groups.length > 0 ? groups[0].groupBounds.dim.x : 0, y);
	}

	@Override
	public CardView.MVec2d coordinatesOf(CardView view, int card, CardView.MVec2d buffer) {
		if (buffer == null) {
			buffer = new CardView.MVec2d();
		}

		double p = view.cardPadding();
		double pwp = p + view.cardWidth() + p;
		double php = p + view.cardHeight() + p;

		int stride = stride(view);

		buffer.x = p + (card % stride) * pwp;
		buffer.y = p + Math.floor(card / stride) * php;

		return buffer;
	}

	@Override
	public int cardAt(CardView view, CardView.MVec2d point, int groupSize) {
		double p = view.cardPadding();
		double pwp = p + view.cardWidth() + p;
		double php = p + view.cardHeight() + p;

		double fRow = point.y / php;
		int iRow = (int) Math.floor(fRow);
		double yInRow = (fRow - iRow) * php;

		if (yInRow < p || yInRow > php - p) {
			return -1;
		}

		double fCol = point.x / pwp;
		int iCol = (int) Math.floor(fCol);
		double xInCol = (fCol - iCol) * pwp;

		int stride = stride(view);

		if (iCol >= stride || xInCol < p || xInCol > pwp - p) {
			return -1;
		}

		int idx = iRow * stride + iCol;

		return idx < 0 || idx >= groupSize ? -1 : idx;
	}

	@Override
	public boolean cardInSelection(CardView view, CardView.MVec2d cardPos, CardView.MVec2d min, CardView.MVec2d max, int groupSize) {
		return cardPos.x + view.cardWidth() >= min.x && cardPos.x <= max.x && cardPos.y + view.cardHeight() >= min.y && cardPos.y <= max.y;
	}
}
