package org.whitefoxy.mtg.deckbuilder.view;

import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.input.ClipboardContent;
import javafx.scene.input.DataFormat;
import javafx.scene.input.Dragboard;
import javafx.scene.input.TransferMode;
import org.whitefoxy.mtg.deckbuilder.model.CardInstance;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

/**
 * Created by Emi on 5/20/2017.
 */
public class CardInstanceView extends ImageView {
	static final DataFormat CARD_INSTANCE_VIEW = new DataFormat(CardInstanceView.class.getCanonicalName());

	private static final Map<String, Image> imageCache = new HashMap<>();
	private static final Map<String, Image> thumbnailCache = new HashMap<>();

	public final CardInstance instance;

	public CardInstanceView(CardInstance instance) throws IOException {
		this.instance = instance;
		setImage(imageCache.computeIfAbsent(instance.card.illustration().toString(), k -> new Image(k, true)));
		this.setSmooth(true);

		this.setOnDragDetected(me -> {
			Dragboard db = this.startDragAndDrop(TransferMode.MOVE);

			ClipboardContent content = new ClipboardContent();
			content.put(CARD_INSTANCE_VIEW, this.instance.card.id());
			db.setContent(content);
			db.setDragView(thumbnailCache.computeIfAbsent(instance.card.illustration().toString(), k -> new Image(k, 200, 280, true, true, false)));

			me.consume();
		});

		this.setOnDragDone(de -> {
			if (de.getAcceptedTransferMode() == TransferMode.MOVE) {
				((PileView) this.getParent()).getChildren().remove(this);
			}
		});
	}
}
