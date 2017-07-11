package emi.mtg.deckbuilder.view.layouts;

import emi.lib.Service;
import emi.mtg.deckbuilder.view.CardView;

@Service.Provider(CardView.LayoutEngine.class)
@Service.Property.String(name="name", value="Flow Grid")
public class FlowGrid implements CardView.LayoutEngine {
	private final CardView parent;
	private int stride;
	private double[] ys;

	public FlowGrid(CardView parent) {
		this.parent = parent;
	}

	@Override
	public String toString() {
		return "FlowGrid Grid";
	}

	@Override
	public CardView.Bounds[] layoutGroups(int[] groupSizes) {
		CardView.Bounds[] bounds = new CardView.Bounds[groupSizes.length];

		if (ys == null || ys.length != groupSizes.length) {
			ys = new double[groupSizes.length];
		}

		double p = parent.cardPadding();
		double pwp = p + parent.cardWidth() + p;
		double php = p + parent.cardHeight() + p;

		stride = Math.max(1, (int) Math.floor(parent.getWidth() / pwp));

		double y = 0;
		for (int i = 0; i < groupSizes.length; ++i) {
			bounds[i] = new CardView.Bounds();
			bounds[i].pos.x = 0;
			bounds[i].pos.y = ys[i] = y;
			bounds[i].dim.x = parent.getWidth();

			bounds[i].dim.y = Math.ceil((double) groupSizes[i] / (double) stride) * php;
			y += bounds[i].dim.y;
		}

		return bounds;
	}

	@Override
	public CardView.MVec2d coordinatesOf(int group, int card, CardView.MVec2d buffer) {
		if (this.ys == null) {
			throw new IllegalStateException("Haven't layoutGroups yet!");
		}

		if (buffer == null) {
			buffer = new CardView.MVec2d();
		}

		double p = parent.cardPadding();
		double pwp = p + parent.cardWidth() + p;
		double php = p + parent.cardHeight() + p;

		buffer.x = p + (card % stride) * pwp;
		buffer.y = ys[group] + p + Math.floor(card / stride) * php;

		return buffer;
	}

	@Override
	public int groupAt(CardView.MVec2d point) {
		if (this.ys == null) {
			throw new IllegalStateException("Haven't layoutGroups yet!");
		}

		for (int i = 0; i < ys.length; ++i) {
			if (ys[i] > point.y) {
				return i - 1;
			}
		}

		return ys.length - 1;
	}

	@Override
	public int cardAt(CardView.MVec2d point, int group, int groupSize) {
		if (this.ys == null) {
			throw new IllegalStateException("Haven't layoutGroups yet!");
		}

		double p = parent.cardPadding();
		double pwp = p + parent.cardWidth() + p;
		double php = p + parent.cardHeight() + p;

		double fRow = (point.y - ys[group]) / php;
		int iRow = (int) Math.floor(fRow);
		double yInRow = (fRow - iRow) * php;

		if (yInRow < p || yInRow > php - p) {
			return -1;
		}

		double fCol = point.x / pwp;
		int iCol = (int) Math.floor(fCol);
		double xInCol = (fCol - iCol) * pwp;

		if (xInCol < p || xInCol > pwp - p) {
			return -1;
		}

		int idx = iRow * stride + iCol;

		return idx < 0 || idx >= groupSize ? -1 : idx;
	}
}
