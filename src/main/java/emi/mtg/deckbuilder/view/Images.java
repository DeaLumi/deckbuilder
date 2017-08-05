package emi.mtg.deckbuilder.view;

import emi.lib.Service;
import emi.lib.mtg.Card;
import emi.lib.mtg.ImageSource;
import javafx.embed.swing.SwingFXUtils;
import javafx.scene.image.Image;

import javax.imageio.ImageIO;
import java.io.IOException;
import java.io.InputStream;
import java.io.PipedInputStream;
import java.io.PipedOutputStream;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.stream.Collectors;

public class Images {
	private static final List<ImageSource> sources;
	private static final long MAX_LOADED_IMAGES = Runtime.getRuntime().maxMemory() / (100 * 2 * 1024 * 1024);
	private static final long MAX_LOADED_THUMBNAILS = MAX_LOADED_IMAGES;

	static final ExecutorService IMAGE_LOAD_POOL = Executors.newCachedThreadPool(r -> {
		Thread th = Executors.defaultThreadFactory().newThread(r);
		th.setDaemon(true);
		return th;
	});

	static {
		sources = Service.Loader.load(ImageSource.class).stream()
				.sorted(Comparator.comparing(s -> -s.number("priority")))
				.map(Service.Loader.Stub::uncheckedInstance)
				.collect(Collectors.toList());

		System.out.println("Images will attempt to store at most " + MAX_LOADED_IMAGES + " images.");
	}

	private final Map<Card.Printing.Face, Image> imageCache;
	private final Map<Card.Printing.Face, Image> thumbnailCache;
	private final Queue<Card.Printing.Face> imageEvictionQueue;
	private final Queue<Card.Printing.Face> thumbnailEvictionQueue;

	public Images() {
		imageCache = new HashMap<>();
		imageEvictionQueue = new LinkedList<>();

		thumbnailCache = new HashMap<>();
		thumbnailEvictionQueue = new LinkedList<>();
	}

	public Image get(Card.Printing.Face face) {
		synchronized(imageCache) {
			if (!imageCache.containsKey(face)) {
				for (ImageSource source : sources) {
					try {
						InputStream input = source.open(face);

						if (input != null) {
							if (imageCache.size() >= MAX_LOADED_IMAGES) {
								imageCache.remove(imageEvictionQueue.remove());
							}

							imageEvictionQueue.add(face);
							imageCache.put(face, new Image(input));
						}
					} catch (IOException exc) {
						// do nothing
					}
				}
			}
		}

		return imageCache.get(face);
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

	public Image getThumbnail(Card.Printing.Face face) {
		synchronized(thumbnailCache) {
			if (!thumbnailCache.containsKey(face)) {
				Image image = get(face);

				if (image != null) {
					if (thumbnailCache.size() >= MAX_LOADED_THUMBNAILS) {
						thumbnailCache.remove(thumbnailEvictionQueue.remove());
					}

					thumbnailEvictionQueue.add(face);

					PipedOutputStream output = new PipedOutputStream();
					PipedInputStream input = new PipedInputStream(output);

					ImageIO.write(SwingFXUtils.fromFXImage(image, null), "png", output);
					thumbnailCache.put(face, ImageIO.read(input));
				}
			}
		}

		return thumbnailCache.get(face);
	}
}
