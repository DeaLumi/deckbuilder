package emi.mtg.deckbuilder.view;

import emi.lib.mtg.card.Card;
import emi.lib.mtg.data.ImageSource;
import emi.mtg.deckbuilder.model.CardInstance;
import javafx.application.Platform;
import javafx.scene.CacheHint;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.input.ClipboardContent;
import javafx.scene.input.DataFormat;
import javafx.scene.input.Dragboard;
import javafx.scene.input.TransferMode;
import javafx.scene.shape.Rectangle;

import java.lang.ref.WeakReference;
import java.net.URL;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Created by Emi on 5/20/2017.
 */
public class CardInstanceView extends ImageView {
	private static final ExecutorService IMAGE_LOAD_POOL = Executors.newWorkStealingPool();

	static final DataFormat CARD_INSTANCE_VIEW = new DataFormat(CardInstanceView.class.getCanonicalName());

	public static final double WIDTH = 745;
	public static final double HEIGHT = 1040;

	public static final double THUMBNAIL_FACTOR = 1.0 / 4.0;

	private static final Image DEFAULT_IMAGE = new Image("file:Back.xlhq.jpg", WIDTH, HEIGHT, true, true, false);
	private static final Image DEFAULT_THUMBNAIL = new Image("file:Back.xlhq.jpg", WIDTH * THUMBNAIL_FACTOR, HEIGHT * THUMBNAIL_FACTOR, true, true, false);
	private static final Map<Card, WeakReference<Image>> imageCache = new HashMap<>();
	private static final Map<Card, Image> thumbnailCache = new HashMap<>();

	public final CardInstance instance;

	private final ImageSource images;

	public CardInstanceView(CardInstance instance, ImageSource is) {
		this.setPreserveRatio(true);

		Rectangle clip = new Rectangle(WIDTH, HEIGHT);
		clip.setArcWidth(WIDTH / 12.0);
		clip.setArcHeight(HEIGHT / 12.0);

		this.setClip(clip);

		this.images = is;

		this.instance = instance;
		this.setFitWidth(WIDTH);
		this.setFitHeight(HEIGHT);
		this.setImage(DEFAULT_IMAGE);
		this.loadImage();
		this.setSmooth(true);
		this.setCache(true);
		this.setCacheHint(CacheHint.SCALE);

		this.setOnDragDetected(me -> {
			Dragboard db = this.startDragAndDrop(TransferMode.MOVE);

			ClipboardContent content = new ClipboardContent();
			content.put(CARD_INSTANCE_VIEW, this.instance.card.id());
			db.setContent(content);
			db.setDragView(thumbnailCache.getOrDefault(this.instance.card, DEFAULT_THUMBNAIL));

			me.consume();
		});

		this.setOnDragDone(de -> {
			if (de.getAcceptedTransferMode() == TransferMode.MOVE) {
//				((PileView) this.getParent()).getChildren().remove(this);
				// TODO: Remove from parent container
			}
		});

		this.setOnMouseClicked(me -> {
			if (me.getClickCount() >= 2) {
				if (getImage() == DEFAULT_IMAGE) {
					loadImage();
				} else {
					unloadImage();
				}
			}
		});
	}

	public void loadImage() {
		IMAGE_LOAD_POOL.execute(() -> {
			Image image;
			if (!imageCache.containsKey(instance.card) || imageCache.get(instance.card).get() == null) {
				URL url = this.images.find(instance.card);

				Image thumbnail;
				if (url == null) {
					image = DEFAULT_IMAGE;
					thumbnail = DEFAULT_THUMBNAIL;
				} else {
					image = new Image(url.toString(), WIDTH, HEIGHT, true, true, false);
					thumbnail = new Image(url.toString(), WIDTH * THUMBNAIL_FACTOR, HEIGHT * THUMBNAIL_FACTOR, true, true, false);
				}

				imageCache.put(instance.card, new WeakReference<>(image));
				thumbnailCache.put(instance.card, thumbnail);
			} else {
				image = imageCache.get(instance.card).get();
			}

			Platform.runLater(() -> setImage(image));
		});
	}

	public void unloadImage() {
		setImage(DEFAULT_IMAGE);
		thumbnailCache.remove(instance.card);
	}
}
