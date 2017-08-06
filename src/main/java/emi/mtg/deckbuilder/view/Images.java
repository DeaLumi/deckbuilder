package emi.mtg.deckbuilder.view;

import emi.lib.Service;
import emi.lib.mtg.Card;
import emi.lib.mtg.ImageSource;
import emi.lib.mtg.img.MtgImageUtils;
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
	static final double CARD_WIDTH = 220.0;
	static final double CARD_HEIGHT = 308.0;
	static final double CARD_PADDING = CARD_WIDTH / 40.0;
	private static final double ROUND_RADIUS = CARD_WIDTH / 8.0;

	static final Image CARD_BACK_THUMB = new Image("file:Back.xlhq.jpg", CARD_WIDTH, CARD_HEIGHT, true, true);
	static final Image CARD_BACK = new Image("file:Back.xlhq.jpg", CARD_WIDTH, CARD_HEIGHT, true, true);

	private static final List<ImageSource> sources;
	private static final long MAX_LOADED_IMAGES = Runtime.getRuntime().maxMemory() / (50 * 2 * 1024 * 1024);
	private static final long MAX_LOADED_THUMBNAILS = MAX_LOADED_IMAGES;

	private static final ExecutorService IMAGE_LOAD_POOL = Executors.newCachedThreadPool(r -> {
		Thread th = Executors.defaultThreadFactory().newThread(r);
		th.setDaemon(true);
		th.setName("ImageLoad-" + th.getId());
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

	public CompletableFuture<Image> get(Card.Printing.Face face) {
		synchronized(imageCache) {
			if (!imageCache.containsKey(face)) {
				imageCache.put(face, CARD_BACK);

				CompletableFuture<Image> ret = new CompletableFuture<>();

				IMAGE_LOAD_POOL.submit(() -> {
					for (ImageSource source : sources) {
						try {
							InputStream input = source.open(face);

							if (input != null) {
								if (imageCache.size() >= MAX_LOADED_IMAGES) {
									imageCache.remove(imageEvictionQueue.remove());
								}

								Image image = MtgImageUtils.clearCorners(new Image(input));
								imageEvictionQueue.add(face);
								imageCache.put(face, image);
								ret.complete(image);
								break;
							}
						} catch (IOException exc) {
							// do nothing
						}
					}

					ret.complete(CARD_BACK);
				});

				return ret;
			}
		}

		return CompletableFuture.completedFuture(imageCache.get(face));
	}

	public CompletableFuture<Image> get(Card.Printing printing) {
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

	public CompletableFuture<Image> getThumbnail(Card.Printing.Face face) {
		synchronized(thumbnailCache) {
			if (!thumbnailCache.containsKey(face)) {
				thumbnailCache.put(face, CARD_BACK_THUMB); // Stop this from effing up...
				return get(face).thenApply(image -> {
					if (image == null) {
						return CARD_BACK_THUMB;
					}

					if (thumbnailCache.size() >= MAX_LOADED_THUMBNAILS) {
						thumbnailCache.remove(thumbnailEvictionQueue.remove());
					}

					thumbnailEvictionQueue.add(face);

					Image scaled = MtgImageUtils.scaled(image, CARD_WIDTH, CARD_HEIGHT, true);
					thumbnailCache.put(face, scaled);
					return scaled;
				});
			}
		}

		return CompletableFuture.completedFuture(thumbnailCache.get(face));
	}

	public CompletableFuture<Image> getThumbnail(Card.Printing printing) {
		if (printing.face(Card.Face.Kind.Front) != null) {
			return getThumbnail(printing.face(Card.Face.Kind.Front));
		} else if (printing.face(Card.Face.Kind.Left) != null) {
			return getThumbnail(printing.face(Card.Face.Kind.Left));
		} else if (printing.faces().size() > 0) {
			return getThumbnail(printing.faces().iterator().next());
		} else {
			throw new Error(String.format("Whoa whoa whoa -- this card printing has no faces? (%s/%s)", printing.id().toString(), printing.card().fullName()));
		}
	}

}
