package emi.mtg.deckbuilder.view.engines;

import emi.lib.Service;
import emi.mtg.deckbuilder.view.CardView;

import static emi.mtg.deckbuilder.view.CardView.*;

@Service.Provider(CardView.LayoutEngine.class)
@Service.Property.String(name="name", value="Piles")
public class Piles implements CardView.LayoutEngine {
	private final static double OVERLAP_FACTOR = 0.125;

	public Piles(CardView c) {

	}

	@Override
	public CardView.Bounds[] layoutGroups(int[] groupSizes) {
		CardView.Bounds[] bounds = new CardView.Bounds[groupSizes.length];

		for (int i = 0; i < groupSizes.length; ++i) {
			bounds[i] = new Bounds();
			bounds[i].pos.x = (PADDING + WIDTH + PADDING) * i;
			bounds[i].pos.y = 0.0;
			bounds[i].dim.x = PADDING + WIDTH + PADDING;
			bounds[i].dim.y = PADDING + (HEIGHT * OVERLAP_FACTOR) * (groupSizes[i] - 1) + HEIGHT + PADDING;
		}

		return bounds;
	}

	@Override
	public CardView.MVec2d coordinatesOf(CardView.Indices indices, CardView.MVec2d buffer) {
		if (buffer == null) {
			buffer = new CardView.MVec2d();
		}

		buffer.x = (PADDING + WIDTH + PADDING) * indices.group + PADDING;
		buffer.y = PADDING + (HEIGHT * OVERLAP_FACTOR) * indices.card;

		return buffer;
	}

	@Override
	public int groupAt(CardView.MVec2d point) {
		return (int) (point.x / (PADDING + WIDTH + PADDING));
	}

	@Override
	public int cardAt(CardView.MVec2d point, int groupSize) {
		if (point.x < PADDING || point.x > PADDING + WIDTH) {
			return -1;
		}

		if (point.y < PADDING || point.y > PADDING + (HEIGHT * OVERLAP_FACTOR) * (groupSize - 1) + HEIGHT) {
			return -1;
		}

		if (point.y > PADDING + (HEIGHT * OVERLAP_FACTOR) * (groupSize - 1)) {
			return groupSize - 1;
		}

		return (int) ((point.y - PADDING) / (HEIGHT * OVERLAP_FACTOR));
	}
}
