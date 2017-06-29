package emi.mtg.deckbuilder.view.engines;

import emi.lib.Service;
import emi.mtg.deckbuilder.view.CardView;

import static emi.mtg.deckbuilder.view.CardView.HEIGHT;
import static emi.mtg.deckbuilder.view.CardView.PADDING;
import static emi.mtg.deckbuilder.view.CardView.WIDTH;

@Service.Provider(CardView.LayoutEngine.class)
@Service.Property.String(name="name", value="Flow Grid")
public class Flow implements CardView.LayoutEngine {
	private final CardView parent;
	private int stride;
	private double[] ys;

	public Flow(CardView parent) {
		this.parent = parent;
	}

	@Override
	public CardView.Bounds[] layoutGroups(int[] groupSizes) {
		CardView.Bounds[] bounds = new CardView.Bounds[groupSizes.length];

		if (ys == null || ys.length != groupSizes.length) {
			ys = new double[groupSizes.length];
		}

		stride = Math.max(1, (int) Math.floor(parent.getWidth() / (PADDING + WIDTH + PADDING)));

		double y = 0;
		for (int i = 0; i < groupSizes.length; ++i) {
			bounds[i] = new CardView.Bounds();
			bounds[i].pos.x = 0;
			bounds[i].pos.y = ys[i] = y;
			bounds[i].dim.x = parent.getWidth();

			bounds[i].dim.y = Math.ceil((double) groupSizes[i] / (double) stride) * (PADDING + HEIGHT + PADDING);
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

		buffer.x = PADDING + (card % stride) * (PADDING + WIDTH + PADDING);
		buffer.y = ys[group] + PADDING + Math.floor(card / stride) * (PADDING + HEIGHT + PADDING);

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

		return -1;
	}

	@Override
	public int cardAt(CardView.MVec2d point, int group, int groupSize) {
		if (this.ys == null) {
			throw new IllegalStateException("Haven't layoutGroups yet!");
		}

		double fRow = (point.y - ys[group]) / (PADDING + HEIGHT + PADDING);
		int iRow = (int) Math.floor(fRow);
		double yInRow = (fRow - iRow) * (PADDING + HEIGHT + PADDING);

		if (yInRow < PADDING || yInRow > PADDING + HEIGHT) {
			return -1;
		}

		double fCol = point.x / (PADDING + WIDTH + PADDING);
		int iCol = (int) Math.floor(fCol);
		double xInCol = (fCol - iCol) * (PADDING + HEIGHT + PADDING);

		if (xInCol < PADDING || xInCol > PADDING + WIDTH) {
			return -1;
		}

		int idx = iRow * stride + iCol;

		return idx < 0 || idx >= groupSize ? -1 : idx;
	}
}
