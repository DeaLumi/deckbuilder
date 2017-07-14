package emi.mtg.deckbuilder.view.layouts;

import emi.lib.Service;
import emi.mtg.deckbuilder.view.CardView;

@Service.Provider(CardView.LayoutEngine.class)
@Service.Property.String(name="name", value="Flow Grid")
public class FlowGrid implements CardView.LayoutEngine {
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
	public String toString() {
		return "FlowGrid Grid";
	}

	@Override
	public void layoutGroups(int[] groupSizes, CardView.Bounds[] groupBounds, CardView.Bounds[] labelBounds) {
		double p = parent.cardPadding();
		double php = p + parent.cardHeight() + p;

		int stride = stride();

		double y = 0;
		for (int i = 0; i < groupSizes.length; ++i) {
			labelBounds[i].pos.x = 0;
			labelBounds[i].pos.y = y;
			labelBounds[i].dim.x = parent.getWidth();
			labelBounds[i].dim.y = 18.0;
			y += labelBounds[i].dim.y;

			groupBounds[i].pos.x = 0;
			groupBounds[i].pos.y = y;
			groupBounds[i].dim.x = parent.getWidth();

			groupBounds[i].dim.y = Math.ceil((double) groupSizes[i] / (double) stride) * php;
			y += groupBounds[i].dim.y;
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
