package emi.mtg.deckbuilder.view;

import emi.lib.Service;
import emi.lib.mtg.card.Card;
import emi.lib.mtg.card.CardFace;
import emi.lib.mtg.data.ImageSource;

import java.io.IOException;
import java.io.InputStream;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

public class UnifiedImageSource implements ImageSource {
	private static final List<ImageSource> sources;

	static {
		sources = Service.Loader.load(ImageSource.class).stream()
				.sorted(Comparator.comparing(s -> -s.number("priority")))
				.map(Service.Loader.Stub::uncheckedInstance)
				.collect(Collectors.toList());
	}

	@Override
	public InputStream open(CardFace face) throws IOException {
		for (ImageSource source : sources) {
			try {
				InputStream input = source.open(face);

				if (input != null) {
					return input;
				}
			} catch (IOException exc) {
				// do nothing
			}
		}

		return null;
	}
}
