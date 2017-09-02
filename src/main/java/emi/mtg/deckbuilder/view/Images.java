package emi.mtg.deckbuilder.view;

import emi.lib.Service;
import emi.lib.mtg.Card;
import emi.lib.mtg.ImageSource;
import emi.lib.mtg.img.MtgImageUtils;
import javafx.embed.swing.SwingFXUtils;
import javafx.scene.image.Image;

import javax.imageio.ImageIO;
import java.io.*;
import java.lang.ref.SoftReference;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.stream.Collectors;

public class Images {
	public static final double CARD_WIDTH = 220.0;
	public static final double CARD_HEIGHT = 308.0;
	public static final double CARD_PADDING = CARD_WIDTH / 40.0;
	private static final double ROUND_RADIUS = CARD_WIDTH / 8.0;

	public static final Image CARD_BACK_THUMB = new Image("file:Back.xlhq.jpg", CARD_WIDTH, CARD_HEIGHT, true, true);
	public static final Image CARD_BACK = new Image("file:Back.xlhq.jpg", CARD_WIDTH, CARD_HEIGHT, true, true);

	private static final List<ImageSource> sources;

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
	}

	private final Map<Card.Printing.Face, SoftReference<Image>> imageCache;
	private final Map<Card.Printing.Face, SoftReference<Image>> thumbnailCache;

	public Images() {
		imageCache = new HashMap<>();
		thumbnailCache = new HashMap<>();
	}

	public CompletableFuture<Image> get(Card.Printing.Face face) {
		synchronized(imageCache) {
			if (!imageCache.containsKey(face) || imageCache.get(face).get() == null) {
				imageCache.put(face, new SoftReference<>(CARD_BACK));

				CompletableFuture<Image> ret = new CompletableFuture<>();

				IMAGE_LOAD_POOL.submit(() -> {
					for (ImageSource source : sources) {
						try {
							InputStream input = source.open(face);

							if (input != null) {
								Image image = MtgImageUtils.clearCorners(new Image(input));
								imageCache.put(face, new SoftReference<>(image));
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

		return CompletableFuture.completedFuture(imageCache.get(face).get());
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

	private static File THUMB_PARENT = new File("thumbnails");

	static {
		if (!THUMB_PARENT.isDirectory() && !THUMB_PARENT.mkdirs()) {
			throw new Error("Couldn't create parent directory thumbnails/");
		}
	}

	public CompletableFuture<Image> getThumbnail(Card.Printing.Face face) {
		synchronized(thumbnailCache) {
			if (!thumbnailCache.containsKey(face) || thumbnailCache.get(face).get() == null) {
				File thumbFile = new File(new File(THUMB_PARENT, String.format("s%s", face.printing().set().code())), String.format("%s%d.png", face.face().name(), face.printing().variation()));

				thumbnailCache.put(face, new SoftReference<>(CARD_BACK_THUMB)); // Stop this from effing up...

				if (thumbFile.isFile()) {
					CompletableFuture<Image> ret = new CompletableFuture<>();

					IMAGE_LOAD_POOL.submit(() -> {
						try {
							Image thumbnail = new Image(new FileInputStream(thumbFile));
							thumbnailCache.put(face, new SoftReference<>(thumbnail));
							ret.complete(thumbnail);
						} catch (FileNotFoundException e) {
							// do nothing
						}

						ret.complete(CARD_BACK_THUMB);
					});

					return ret;
				} else {
					return get(face).thenApply(image -> {
						if (image == null) {
							return CARD_BACK_THUMB;
						}

						Image scaled = MtgImageUtils.scaled(image, CARD_WIDTH, CARD_HEIGHT, true);
						thumbnailCache.put(face, new SoftReference<>(scaled));

						try {
							if (!thumbFile.getParentFile().isDirectory() && !thumbFile.getParentFile().mkdirs()) {
								throw new IOException("Couldn't create parent directory for set " + face.printing().set().code());
							}

							ImageIO.write(SwingFXUtils.fromFXImage(scaled, null), "png", thumbFile);
						} catch (IOException e) {
							e.printStackTrace();
						}

						return scaled;
					});
				}
			}
		}

		return CompletableFuture.completedFuture(thumbnailCache.get(face).get());
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
