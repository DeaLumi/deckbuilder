package org.whitefoxy.mtg.deckbuilder.view;

import javafx.geometry.Point2D;
import javafx.scene.input.TransferMode;
import javafx.scene.layout.HBox;
import org.whitefoxy.lib.mtg.data.CardSource;
import org.whitefoxy.lib.mtg.data.ImageSource;
import org.whitefoxy.mtg.deckbuilder.model.CardInstance;

import java.io.IOException;
import java.util.UUID;

/**
 * Created by Emi on 6/15/2017.
 */
public class PilesView extends HBox {
	public PilesView(CardSource cs, ImageSource is) {
		super(50);

		this.setOnDragOver(de -> {
			de.acceptTransferModes(TransferMode.MOVE);
			de.consume();
		});

		this.setOnDragDropped(de -> {
			try {
				CardInstanceView view = new CardInstanceView(new CardInstance(cs.get((UUID) de.getDragboard().getContent(CardInstanceView.CARD_INSTANCE_VIEW))), is);

				int index;
				for (index = 0; index < this.getChildren().size(); ++index) {
					Point2D child = this.getChildren().get(index).localToScreen(0, 0);
					if (de.getScreenX() < child.getX()) {
						break;
					}
				}

				PileView pileView;

				if (((PileView) this.getChildren().get(index)).getChildren().isEmpty()) {
					pileView = (PileView) this.getChildren().get(index);
				} else if (((PileView) this.getChildren().get(index - 1)).getChildren().isEmpty()) {
					pileView = (PileView) this.getChildren().get(index - 1);
				} else {
					pileView = new PileView(cs, is);
					this.getChildren().add(index, pileView);
				}

				pileView.getChildren().add(view);

				de.setDropCompleted(true);
			} catch (IOException e) {
				e.printStackTrace();
				de.setDropCompleted(false);
			}

			de.consume();
		});
	}
}
