package emi.mtg.deckbuilder.view;

import emi.lib.mtg.Card;
import emi.lib.mtg.ImageSource;
import emi.lib.mtg.img.MtgAwtImageUtils;
import javafx.embed.swing.SwingFXUtils;
import javafx.scene.image.Image;
import javafx.scene.image.WritableImage;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.lang.ref.SoftReference;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.BasicFileAttributes;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentSkipListMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.DoubleConsumer;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;

public class Images {
	public static final double CARD_WIDTH = 220.0;
	public static final double CARD_HEIGHT = 308.0;
	public static final double CARD_PADDING = CARD_WIDTH / 100.0;

	private static final String CACHE_EXTENSION = "png";

	private static final File IMAGES = new File("images");
	private static final File FRONTS = new File(IMAGES, "fronts");
	private static final File FACES = new File(IMAGES, "faces");

	public static final Image UNAVAILABLE_CARD, UNAVAILABLE_CARD_LARGE, LOADING_CARD, LOADING_CARD_LARGE;

	static Image loadImage(String path, int w, int h, int blank) {
		try {
			InputStream input = Images.class.getResourceAsStream(path);
			Image img = new Image(input);
			input.close();
			return img;
		} catch (IOException | NullPointerException e) {
			WritableImage writable = new WritableImage(w, h);
			for (int y = 0; y < h; ++y) {
				for (int x = 0; x < w; ++x) {
					writable.getPixelWriter().setArgb(x, y, blank);
				}
			}
			return writable;
		}
	}

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

		UNAVAILABLE_CARD_LARGE = loadImage("/META-INF/unavailable.png", 2 * (int) CARD_WIDTH, 2 * (int) CARD_HEIGHT, 0xff000000);
		UNAVAILABLE_CARD = loadImage("/META-INF/unavailable-small.png", (int) CARD_WIDTH, (int) CARD_HEIGHT, 0xff000000);
		LOADING_CARD_LARGE = loadImage("/META-INF/loading.png", 2 * (int) CARD_WIDTH, 2 * (int) CARD_HEIGHT, 0xffff0000);
		LOADING_CARD = loadImage("/META-INF/loading-small.png", (int) CARD_WIDTH, (int) CARD_HEIGHT, 0xffff0000);
	}

	private static final ExecutorService IMAGE_LOAD_POOL = Executors.newCachedThreadPool(r -> {
		Thread th = Executors.defaultThreadFactory().newThread(r);
		th.setDaemon(true);
		th.setName("ImageLoad-" + th.getId());
		return th;
	});

	private static final List<ImageSource> sources;

	static {
		sources = StreamSupport.stream(ServiceLoader.load(ImageSource.class, MainApplication.PLUGIN_CLASS_LOADER).spliterator(), false)
				.sorted(Comparator.comparing(s -> -s.priority()))
				.collect(Collectors.toList());
	}

	private final Map<Card.Printing.Face, SoftReference<CompletableFuture<Image>>> faceCache = new HashMap<>();
	private final Map<Card.Printing, SoftReference<CompletableFuture<Image>>> frontCache = new HashMap<>();

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

	private static <T> CompletableFuture<Image> open(T key, Map<T, SoftReference<CompletableFuture<Image>>> cache, ImageOpener getter, Function<BufferedImage, BufferedImage> transform) {
		synchronized(cache) {
			SoftReference<CompletableFuture<Image>> ref = cache.get(key);
			CompletableFuture<Image> cached = ref == null ? null : ref.get();
			if (cached == null) {
				CompletableFuture<Image> ret = new CompletableFuture<>();
				cache.put(key, new SoftReference<>(ret));

				IMAGE_LOAD_POOL.submit(() -> {
					File f = fileFor(key);

					try {
						if (f.exists()) {
							Image image = SwingFXUtils.toFXImage(ImageIO.read(f), null);
							ret.complete(image);
							return;
						} else {
							for (ImageSource source : sources) {
								try {
									BufferedImage imgSrc = getter.open(source);

									if (imgSrc != null) {
										imgSrc = transform.apply(imgSrc);
										Image image = SwingFXUtils.toFXImage(imgSrc, null);
										ret.complete(image);

										if (key instanceof Card.Printing || source.cacheable()) {
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

					ret.complete(UNAVAILABLE_CARD);
				});

				return ret;
			}

			return cached;
		}
	}

	public CompletableFuture<Image> getFace(Card.Printing.Face face) {
		return open(face, faceCache, source -> source.open(face), MtgAwtImageUtils::clearCorners);
	}

	public CompletableFuture<Image> getThumbnail(Card.Printing printing) {
		return open(printing, frontCache, source -> source.open(printing), img -> MtgAwtImageUtils.scaled(MtgAwtImageUtils.clearCorners(img), CARD_WIDTH, CARD_HEIGHT, true));
	}

	public static class CacheCleanupResults {
		public final long filesDeleted;
		public final long deletedBytes;

		public CacheCleanupResults(long filesDeleted, long deletedBytes) {
			this.filesDeleted = filesDeleted;
			this.deletedBytes = deletedBytes;
		}
	}

	/**
	 * Cleans up unused images in the disk caches. Slightly tunable. Might overshoot. Better sorry than safe!
	 *
	 * @param accessedBefore Any images that were last accessed prior to this instant will be deleted.
	 * @param medianFraction Any image below this percentage of the median size of images will be deleted.
	 * @param progress An optional callback to report cache purge progress.
	 * @return The list of sizes of files that were deleted.
	 */
	public CacheCleanupResults cleanCache(Instant accessedBefore, double medianFraction, DoubleConsumer progress) throws IOException {
		long deletedFiles = 0, deletedBytes = 0;

		Set<Path> allImages = new HashSet<>();
		for (Path subdir : Files.newDirectoryStream(FACES.toPath(), p -> Files.isDirectory(p))) {
			for (Path image : Files.newDirectoryStream(subdir, "*.png")) {
				allImages.add(image);
			}
		}

		for (Path subdir : Files.newDirectoryStream(FRONTS.toPath(), p -> Files.isDirectory(p))) {
			for (Path image : Files.newDirectoryStream(subdir, "*.png")) {
				allImages.add(image);
			}
		}

		NavigableMap<Long, Set<Path>> sizeMap = new ConcurrentSkipListMap<>();

		long mask = allImages.size() / 100;
		mask = 1 << (63 - Long.numberOfLeadingZeros(mask));

		long i = 0;
		for (Path p : allImages) {
			BasicFileAttributes attrs = Files.readAttributes(p,BasicFileAttributes.class);

			if (attrs.lastAccessTime().toInstant().isBefore(accessedBefore)) {
				Files.delete(p);
				++deletedFiles;
				deletedBytes += attrs.size();
			} else {
				sizeMap.computeIfAbsent(attrs.size(), s -> new HashSet<>()).add(p);
			}

			if (progress != null) {
				++i;

				if ((i & mask) == 0) {
					progress.accept((double) i / (double) allImages.size());
				}
			}
		}

		i = 0;
		Long size = sizeMap.firstKey();
		while (i < sizeMap.size() / 2) {
			++i;
			size = sizeMap.higherKey(size);
		}

		for (Map.Entry<Long, Set<Path>> paths : sizeMap.headMap((long) (size * medianFraction)).entrySet()) {
			for (Path path : paths.getValue()) {
				Files.delete(path);
				++deletedFiles;
				deletedBytes += paths.getKey();
			}
		}

		return new CacheCleanupResults(deletedFiles, deletedBytes);
	}
}
