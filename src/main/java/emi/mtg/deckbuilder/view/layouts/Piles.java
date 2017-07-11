package emi.mtg.deckbuilder.view.layouts;

import emi.lib.Service;
import emi.mtg.deckbuilder.view.CardView;

import static emi.mtg.deckbuilder.view.CardView.*;

@Service.Provider(CardView.LayoutEngine.class)
@Service.Property.String(name="name", value="Piles")
public class Piles implements CardView.LayoutEngine {
	private final static double OVERLAP_FACTOR = 0.125;

	private final CardView parent;
	private double[] xs;

	public Piles(CardView parent) {
		this.parent = parent;
	}

	@Override
	public String toString() {
		return "Piles";
	}

	@Override
	public CardView.Bounds[] layoutGroups(int[] groupSizes) {
		CardView.Bounds[] bounds = new CardView.Bounds[groupSizes.length];

		if (xs == null || xs.length != groupSizes.length) {
			xs = new double[groupSizes.length];
		}

		double p = parent.cardPadding();
		double w = parent.cardWidth();
		double h = parent.cardHeight();

		double x = 0;
		for (int i = 0; i < groupSizes.length; ++i) {
			bounds[i] = new Bounds();
			bounds[i].pos.x = xs[i] = x;
			bounds[i].pos.y = 0.0;
			bounds[i].dim.x = p + (groupSizes[i] > 0 ? w : 0) + p;
			bounds[i].dim.y = p + (h * OVERLAP_FACTOR) * (groupSizes[i] - 1) + h + p;

			x += bounds[i].dim.x;
		}

		return bounds;
	}

	@Override
	public CardView.MVec2d coordinatesOf(int group, int card, CardView.MVec2d buffer) {
		if (this.xs == null) {
			throw new IllegalStateException("Haven't layoutGroups yet!");
		}

		if (buffer == null) {
			buffer = new CardView.MVec2d();
		}

		double p = parent.cardPadding();
		double h = parent.cardHeight();

		buffer.x = xs[group] + p;
		buffer.y = p + (h * OVERLAP_FACTOR) * card;

		return buffer;
	}

	@Override
	public int groupAt(CardView.MVec2d point) {
		if (this.xs == null) {
			throw new IllegalStateException("Haven't layoutGroups yet!");
		}

		for (int i = 0; i < xs.length; ++i) {
			if (xs[i] > point.x) {
				return i - 1;
			}
		}

		return xs.length - 1;
	}

	@Override
	public int cardAt(CardView.MVec2d point, int group, int groupSize) {
		if (this.xs == null) {
			throw new IllegalStateException("Haven't layoutGroups yet!");
		}

		double p = parent.cardPadding();
		double w = parent.cardWidth();
		double h = parent.cardHeight();

		double x = point.x - xs[group];
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
