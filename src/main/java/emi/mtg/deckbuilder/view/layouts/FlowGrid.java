package emi.mtg.deckbuilder.view.layouts;

import emi.mtg.deckbuilder.view.components.CardView;

public class FlowGrid implements CardView.LayoutEngine {
	public static class Factory implements CardView.LayoutEngine.Factory {
		public static final Factory INSTANCE = new Factory();

		@Override
		public CardView.LayoutEngine create(CardView parent) {
			return new FlowGrid(parent);
		}

		@Override
		public String name() {
			return "Flow Grid";
		}
	}

	private final CardView parent;

	public FlowGrid(CardView parent) {
		this.parent = parent;
	}

	private int stride() {
		double p = parent.cardPadding();
		double pwp = p + parent.cardWidth() + p;
		return Math.max(1, (int) Math.floor(parent.getWidth() / pwp));
	}

	@Override
	public void layoutGroups(CardView.Group[] groups, boolean showEmpty) {
		double p = parent.cardPadding();
		double pwp = p + parent.cardWidth() + p;
		double php = p + parent.cardHeight() + p;

		int stride = stride();

		double y = 0;
		for (int i = 0; i < groups.length; ++i) {
			if (!showEmpty && groups[i].model.isEmpty()) {
				groups[i].labelBounds.pos.x = groups[i].labelBounds.pos.y = 0;
				groups[i].labelBounds.dim.x = groups[i].labelBounds.dim.y = 0;
				groups[i].groupBounds.pos.x = groups[i].groupBounds.pos.y = 0;
				groups[i].groupBounds.dim.x = groups[i].groupBounds.dim.y = 0;

				continue;
			}

			groups[i].labelBounds.pos.x = 0;
			groups[i].labelBounds.pos.y = y;
			groups[i].labelBounds.dim.x = stride * pwp;
			groups[i].labelBounds.dim.y = 18.0;
			y += groups[i].labelBounds.dim.y;

			groups[i].groupBounds.pos.x = 0;
			groups[i].groupBounds.pos.y = y;
			groups[i].groupBounds.dim.x = stride * pwp;
			groups[i].groupBounds.dim.y = Math.max(pwp, Math.ceil((double) groups[i].model.size() / (double) stride) * php);
			y += groups[i].groupBounds.dim.y;
		}
	}

	@Override
	public CardView.MVec2d coordinatesOf(int card, CardView.MVec2d buffer) {
		if (buffer == null) {
			buffer = new CardView.MVec2d();
		}

		double p = parent.cardPadding();
		double pwp = p + parent.cardWidth() + p;
		double php = p + parent.cardHeight() + p;

		int stride = stride();

		buffer.x = p + (card % stride) * pwp;
		buffer.y = p + Math.floor(card / stride) * php;

		return buffer;
	}

	@Override
	public int cardAt(CardView.MVec2d point, int groupSize) {
		double p = parent.cardPadding();
		double pwp = p + parent.cardWidth() + p;
		double php = p + parent.cardHeight() + p;

		double fRow = point.y / php;
		int iRow = (int) Math.floor(fRow);
		double yInRow = (fRow - iRow) * php;

		if (yInRow < p || yInRow > php - p) {
			return -1;
		}

		double fCol = point.x / pwp;
		int iCol = (int) Math.floor(fCol);
		double xInCol = (fCol - iCol) * pwp;

		int stride = stride();

		if (iCol >= stride || xInCol < p || xInCol > pwp - p) {
			return -1;
		}

		int idx = iRow * stride + iCol;

		return idx < 0 || idx >= groupSize ? -1 : idx;
	}
}
