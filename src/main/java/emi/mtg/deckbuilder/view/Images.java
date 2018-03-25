package emi.mtg.deckbuilder.view;

import emi.lib.Service;
import emi.lib.mtg.Card;
import emi.lib.mtg.ImageSource;
import emi.lib.mtg.img.MtgAwtImageUtils;
import emi.lib.mtg.img.MtgImageUtils;
import javafx.embed.swing.SwingFXUtils;
import javafx.scene.image.Image;
import javafx.scene.image.WritableImage;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.lang.ref.SoftReference;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Function;
import java.util.stream.Collectors;

public class Images {
	public static final double CARD_WIDTH = 220.0;
	public static final double CARD_HEIGHT = 308.0;
	public static final double CARD_PADDING = CARD_WIDTH / 40.0;

	private static final String CACHE_EXTENSION = "png";

	private static final File IMAGES = new File("images");
	private static final File FRONTS = new File(IMAGES, "fronts");
	private static final File FACES = new File(IMAGES, "faces");

	public static final Image UNAVAILABLE_CARD, UNAVAILABLE_CARD_LARGE, LOADING_CARD, LOADING_CARD_LARGE;

	static {
		if (!IMAGES.exists() && !IMAGES.mkdirs()) {
			throw new Error("Unable to create directory for card images...");
		}

		if (!FRONTS.exists() && !FRONTS.mkdirs()) {
			throw new Error("Unable to create directory for card front-side images...");
		}

		if (!FACES.exists() && !FACES.mkdirs()) {
			throw new Error("Unable to create directory for card face images...");
		}

		Image unavailable;
		try {
			InputStream input = Images.class.getResourceAsStream("/META-INF/unavailable.png");
			unavailable = new Image(input);
			input.close();
		} catch (IOException | NullPointerException e) {
			WritableImage writable = new WritableImage((int) CARD_WIDTH, (int) CARD_HEIGHT);
			for (int y = 0; y < CARD_HEIGHT; ++y) {
				for (int x = 0; x < CARD_WIDTH; ++x) {
					writable.getPixelWriter().setArgb(x, y, 0xff000000);
				}
			}
			unavailable = writable;
		}
		UNAVAILABLE_CARD_LARGE = unavailable;
		UNAVAILABLE_CARD = MtgImageUtils.scaled(unavailable, CARD_WIDTH, CARD_HEIGHT, true);

		Image loading;
		try {
			InputStream input = Images.class.getResourceAsStream("/META-INF/loading.png");
			loading = new Image(input);
			input.close();
		} catch (IOException | NullPointerException e) {
			WritableImage writable = new WritableImage((int) CARD_WIDTH, (int) CARD_HEIGHT);
			for (int y = 0; y < CARD_HEIGHT; ++y) {
				for (int x = 0; x < CARD_WIDTH; ++x) {
					writable.getPixelWriter().setArgb(x, y, 0xffff0000);
				}
			}
			loading = writable;
		}
		LOADING_CARD_LARGE = loading;
		LOADING_CARD = MtgImageUtils.scaled(loading, CARD_WIDTH, CARD_HEIGHT, true);
	}

	private static final ExecutorService IMAGE_LOAD_POOL = Executors.newCachedThreadPool(r -> {
		Thread th = Executors.defaultThreadFactory().newThread(r);
		th.setDaemon(true);
		th.setName("ImageLoad-" + th.getId());
		return th;
	});

	private static final List<ImageSource> sources;

	static {
		sources = Service.Loader.load(ImageSource.class).stream()
				.sorted(Comparator.comparing(s -> -s.number("priority")))
				.map(Service.Loader.Stub::uncheckedInstance)
				.collect(Collectors.toList());
	}

	private final Map<Card.Printing.Face, SoftReference<Image>> faceCache = new HashMap<>();
	private final Map<Card.Printing, SoftReference<Image>> frontCache = new HashMap<>();

	private static <T> File fileFor(T object) {
		Card.Printing printing;
		Card.Printing.Face face;

		String file;

		if (object instanceof Card.Printing.Face) {
			face = (Card.Printing.Face) object;
			printing = face.printing();

			file = face.face().name();
		} else if (object instanceof Card.Printing) {
			face = null;
			printing = (Card.Printing) object;

			file = printing.card().fullName();
		} else {
			throw new IllegalArgumentException();
		}

		String parent = String.format("s%s", printing.set().code().toLowerCase());

		file = file.toLowerCase().replaceAll("[-/!&()'\",]", "").replaceAll("\\s+", "-") +
				'-' + printing.variation();

		if (object instanceof Card.Printing.Face) {
			file += '-' + face.face().kind().name().toLowerCase();
		}

		file += '.' + CACHE_EXTENSION;

		return new File(new File(object instanceof Card.Printing ? FRONTS : FACES, parent), file);
	}

	@FunctionalInterface
	private interface ImageOpener {
		BufferedImage open(ImageSource source) throws IOException;
	}

	private static <T> CompletableFuture<Image> open(T key, Map<T, SoftReference<Image>> cache, ImageOpener getter, Function<BufferedImage, BufferedImage> transform) {
		synchronized(cache) {
			if (!cache.containsKey(key) || cache.get(key) == null || cache.get(key).get() == null) {
				cache.put(key, new SoftReference<>(LOADING_CARD));

				CompletableFuture<Image> ret = new CompletableFuture<>();
				IMAGE_LOAD_POOL.submit(() -> {
					File f = fileFor(key);

					try {
						if (f.exists()) {
							Image image = SwingFXUtils.toFXImage(ImageIO.read(f), null);
							cache.put(key, new SoftReference<>(image));
							ret.complete(image);
							return;
						} else {
							for (ImageSource source : sources) {
								try {
									BufferedImage imgSrc = getter.open(source);

									if (imgSrc != null) {
										imgSrc = transform.apply(imgSrc);
										Image image = SwingFXUtils.toFXImage(imgSrc, null);
										cache.put(key, new SoftReference<>(image));
										ret.complete(image);

										if (source.cacheable()) {
											if ((!f.getParentFile().exists() || !f.getParentFile().isDirectory()) && !f.getParentFile().mkdirs()) {
												throw new IOException();
											}
											ImageIO.write(imgSrc, CACHE_EXTENSION, f);
										}

										return;
									}
								} catch (IOException ioe) {
									ioe.printStackTrace();
								}
							}
						}
					} catch (IOException ioe) {
						ioe.printStackTrace();
					}

					cache.put(key, new SoftReference<>(UNAVAILABLE_CARD));
					ret.complete(UNAVAILABLE_CARD);
				});

				return ret;
			}
		}

		return CompletableFuture.completedFuture(cache.get(key).get());
	}

	public CompletableFuture<Image> getFace(Card.Printing.Face face) {
		return open(face, faceCache, source -> source.open(face), MtgAwtImageUtils::clearCorners);
	}

	public CompletableFuture<Image> getThumbnail(Card.Printing printing) {
		return open(printing, frontCache, source -> source.open(printing), img -> MtgAwtImageUtils.scaled(MtgAwtImageUtils.clearCorners(img), CARD_WIDTH, CARD_HEIGHT, true));
	}
}
