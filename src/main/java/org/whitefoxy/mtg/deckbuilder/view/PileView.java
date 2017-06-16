package org.whitefoxy.mtg.deckbuilder.view;

import javafx.geometry.Point2D;
import javafx.scene.input.TransferMode;
import javafx.scene.layout.VBox;
import org.whitefoxy.lib.mtg.data.CardSource;
import org.whitefoxy.mtg.deckbuilder.model.CardInstance;

import java.io.IOException;
import java.util.UUID;

/**
 * Created by Emi on 6/15/2017.
 */
public class PileView extends VBox {
	private final CardSource cs;

	public PileView(CardSource cs) {
		super(-900);

		this.setMinWidth(50);

		this.cs = cs;

		this.setOnDragOver(de -> {
			de.acceptTransferModes(TransferMode.MOVE);
			de.consume();
		});

		this.setOnDragDropped(de -> {
			try {
				CardInstanceView view = new CardInstanceView(new CardInstance(cs.get((UUID) de.getDragboard().getContent(CardInstanceView.CARD_INSTANCE_VIEW))));

				int index;
				for (index = 0; index < this.getChildren().size(); ++index) {
					Point2D child = this.getChildren().get(index).localToScreen(0, 0);
					if (de.getScreenY() < child.getY()) {
						break;
					}
				}

				this.getChildren().add(index, view);

				de.setDropCompleted(true);
			} catch (IOException e) {
				e.printStackTrace();
				de.setDropCompleted(false);
			}

			de.consume();
		});
	}
}
