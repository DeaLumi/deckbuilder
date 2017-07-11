package emi.mtg.deckbuilder.view.layouts;

import emi.lib.Service;
import emi.mtg.deckbuilder.view.CardView;

import static emi.mtg.deckbuilder.view.CardView.*;

@Service.Provider(CardView.LayoutEngine.class)
@Service.Property.String(name="name", value="Piles")
public class Piles implements CardView.LayoutEngine {
	private final static double OVERLAP_FACTOR = 0.125;

	private double[] xs;

	public Piles(CardView c) {

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

		double x = 0;
		for (int i = 0; i < groupSizes.length; ++i) {
			bounds[i] = new Bounds();
			bounds[i].pos.x = xs[i] = x;
			bounds[i].pos.y = 0.0;
			bounds[i].dim.x = PADDING + (groupSizes[i] > 0 ? WIDTH : 0) + PADDING;
			bounds[i].dim.y = PADDING + (HEIGHT * OVERLAP_FACTOR) * (groupSizes[i] - 1) + HEIGHT + PADDING;

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

		buffer.x = xs[group] + PADDING;
		buffer.y = PADDING + (HEIGHT * OVERLAP_FACTOR) * card;

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

		double x = point.x - xs[group];
		if (x < PADDING || x > PADDING + WIDTH) {
			return -1;
		}

		if (point.y < PADDING || point.y > PADDING + (HEIGHT * OVERLAP_FACTOR) * (groupSize - 1) + HEIGHT) {
			return -1;
		}

		if (point.y > PADDING + (HEIGHT * OVERLAP_FACTOR) * (groupSize - 1)) {
			return groupSize - 1;
		}

		int idx = (int) ((point.y - PADDING) / (HEIGHT * OVERLAP_FACTOR));

		return idx < 0 || idx >= groupSize ? -1 : idx;
	}
}
