package org.whitefoxy.mtg.deckbuilder.view;

import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.input.ClipboardContent;
import javafx.scene.input.DataFormat;
import javafx.scene.input.Dragboard;
import javafx.scene.input.TransferMode;
import emi.lib.mtg.card.Card;
import emi.lib.mtg.data.ImageSource;
import org.whitefoxy.mtg.deckbuilder.model.CardInstance;

import java.io.IOException;
import java.net.URL;
import java.util.HashMap;
import java.util.Map;

/**
 * Created by Emi on 5/20/2017.
 */
public class CardInstanceView extends ImageView {
	static final DataFormat CARD_INSTANCE_VIEW = new DataFormat(CardInstanceView.class.getCanonicalName());

	private static final Image defaultImage = new Image("file:Back.xlhq.jpg", true);
	private static final Image defaultThumbnail = new Image("file:Back.xlhq.jpg", 200, 280, true, true, false);
	private static final Map<Card, Image> imageCache = new HashMap<>();
	private static final Map<Card, Image> thumbnailCache = new HashMap<>();

	public final CardInstance instance;

	public CardInstanceView(CardInstance instance, ImageSource is) throws IOException {
		this.setPreserveRatio(true);

		this.instance = instance;
		setImage(imageCache.computeIfAbsent(instance.card, c -> {
			URL url = is.find(c);

			if (url == null) {
				return defaultImage;
			} else {
				return new Image(url.toString(), true);
			}
		}));
		this.setSmooth(true);

		this.setOnDragDetected(me -> {
			Dragboard db = this.startDragAndDrop(TransferMode.MOVE);

			ClipboardContent content = new ClipboardContent();
			content.put(CARD_INSTANCE_VIEW, this.instance.card.id());
			db.setContent(content);
			db.setDragView(thumbnailCache.computeIfAbsent(instance.card, c -> {
				URL url = is.find(c);

				if (url == null) {
					return defaultThumbnail;
				} else {
					return new Image(url.toString(), 200, 280, true, true, false);
				}
			}));

			me.consume();
		});

		this.setOnDragDone(de -> {
			if (de.getAcceptedTransferMode() == TransferMode.MOVE) {
//				((PileView) this.getParent()).getChildren().remove(this);
				// TODO: Remove from parent container
			}
		});
	}
}
