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
	public CardView.Indices indicesAt(CardView.MVec2d point, CardView.Indices buffer) {
		if (buffer == null) {
			buffer = new CardView.Indices();
		}

		buffer.group = (int) (point.x / (WIDTH + PADDING + WIDTH));
		if (buffer.group < 0) {
			buffer.card = 0;
			return buffer;
		}

		buffer.card = (int) ((point.y - PADDING) / (HEIGHT * OVERLAP_FACTOR));

		return buffer;
	}
}
