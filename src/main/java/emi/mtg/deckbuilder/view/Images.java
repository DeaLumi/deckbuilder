package emi.mtg.deckbuilder.view;

import emi.lib.Service;
import emi.lib.mtg.Card;
import emi.lib.mtg.ImageSource;
import javafx.scene.image.Image;

import java.io.IOException;
import java.io.InputStream;
import java.util.*;
import java.util.stream.Collectors;

public class Images {
	private static final List<ImageSource> sources;
	private static final long MAX_LOADED_IMAGES = Runtime.getRuntime().maxMemory() / (2 * 1024 * 1024);

	static {
		sources = Service.Loader.load(ImageSource.class).stream()
				.sorted(Comparator.comparing(s -> -s.number("priority")))
				.map(Service.Loader.Stub::uncheckedInstance)
				.collect(Collectors.toList());

		System.out.println("Images will attempt to store at most " + MAX_LOADED_IMAGES + " images.");
	}

	private final Map<Card.Printing.Face, Image> imageCache;
	private final Queue<Card.Printing.Face> evictionQueue;

	public Images() {
		imageCache = new HashMap<>();
		evictionQueue = new LinkedList<>();
	}

	public Image get(Card.Printing.Face face) {
		return imageCache.computeIfAbsent(face, f -> {
			if (imageCache.size() >= MAX_LOADED_IMAGES) {
				imageCache.remove(evictionQueue.remove());
			}

			for (ImageSource source : sources) {
				try {
					InputStream input = source.open(face);

					if (input != null) {
						evictionQueue.add(face);
						return new Image(input);
					}
				} catch (IOException exc) {
					// do nothing
				}
			}

			return null;
		});
	}

	public Image get(Card.Printing printing) {
		if (printing.face(Card.Face.Kind.Front) != null) {
			return get(printing.face(Card.Face.Kind.Front));
		} else if (printing.face(Card.Face.Kind.Left) != null) {
			return get(printing.face(Card.Face.Kind.Left));
		} else if (printing.faces().size() > 0) {
			return get(printing.faces().iterator().next());
		} else {
			throw new Error(String.format("Whoa whoa whoa -- this card printing has no faces? (%s/%s)", printing.id().toString(), printing.card().fullName()));
		}
	}
}
