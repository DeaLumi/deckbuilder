package emi.mtg.deckbuilder.view;

import javafx.animation.FadeTransition;
import javafx.animation.ParallelTransition;
import javafx.animation.ScaleTransition;
import javafx.animation.TranslateTransition;
import javafx.collections.ObservableList;
import javafx.geometry.Rectangle2D;
import javafx.scene.Group;
import javafx.scene.Scene;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.paint.Color;
import javafx.stage.Screen;
import javafx.stage.Stage;
import javafx.stage.StageStyle;
import javafx.util.Duration;

public class CardZoomPreview {
	private final double DURATION = 50;

	private final Stage stage;

	public CardZoomPreview(Rectangle2D start, Image cardImage) {
		double iw = cardImage.getWidth();
		double ih = cardImage.getHeight();

		Rectangle2D endUnbound = new Rectangle2D(start.getMinX() - (iw - start.getWidth()) / 2.0,
				start.getMinY() - (ih - start.getHeight()) / 2.0,
				iw,
				ih);

		ObservableList<Screen> screens = Screen.getScreensForRectangle(endUnbound);

		double minX = Double.MAX_VALUE, minY = Double.MAX_VALUE,
				maxX = -Double.MAX_VALUE, maxY = -Double.MAX_VALUE;

		for (Screen scr : screens) {
			Rectangle2D bounds = scr.getVisualBounds();

			minX = Math.min(bounds.getMinX(), minX);
			minY = Math.min(bounds.getMinY(), minY);
			maxX = Math.max(bounds.getMaxX(), maxX);
			maxY = Math.max(bounds.getMaxY(), maxY);
		}

		Rectangle2D end = new Rectangle2D(
				Math.max(minX, Math.min(endUnbound.getMinX(), maxX - endUnbound.getWidth())),
				Math.max(minY, Math.min(endUnbound.getMinY(), maxY - endUnbound.getHeight())),
				endUnbound.getWidth(),
				endUnbound.getHeight()
		);

		ScaleTransition st = new ScaleTransition(Duration.millis(DURATION));
		st.setFromX(start.getWidth() / end.getWidth());
		st.setFromY(start.getHeight() / end.getHeight());
		st.setToX(1.0);
		st.setToY(1.0);

		TranslateTransition tt = new TranslateTransition(Duration.millis(DURATION));
		tt.setFromX(start.getMinX() - end.getMinX() - (end.getWidth() - start.getWidth()) / 2.0);
		tt.setFromY(start.getMinY() - end.getMinY() - (end.getHeight() - start.getHeight()) / 2.0);
		tt.setToX(0.0);
		tt.setToY(0.0);

		FadeTransition ft = new FadeTransition(Duration.millis(DURATION));
		ft.setFromValue(0.0);
		ft.setToValue(1.0);

		ImageView image = new ImageView(cardImage);
		image.setFitHeight(iw);
		image.setFitHeight(ih);

		this.stage = new Stage(StageStyle.TRANSPARENT);

		Scene scene = new Scene(new Group(image));
		scene.setFill(Color.TRANSPARENT);

		this.stage.setScene(scene);
		this.stage.setX(end.getMinX());
		this.stage.setY(end.getMinY());
		this.stage.setWidth(end.getWidth());
		this.stage.setHeight(end.getHeight());
		this.stage.show();

		ParallelTransition pt = new ParallelTransition(image, st, ft, tt);
		pt.play();
	}

	public void close() {
		this.stage.close();
	}
}
