package emi.mtg.deckbuilder.view.components;

import emi.lib.mtg.Card;
import emi.mtg.deckbuilder.controller.Context;
import emi.mtg.deckbuilder.view.Images;
import javafx.animation.FadeTransition;
import javafx.animation.ParallelTransition;
import javafx.animation.ScaleTransition;
import javafx.animation.TranslateTransition;
import javafx.application.Platform;
import javafx.collections.ObservableList;
import javafx.geometry.Pos;
import javafx.geometry.Rectangle2D;
import javafx.scene.Group;
import javafx.scene.Scene;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.HBox;
import javafx.scene.paint.Color;
import javafx.stage.Screen;
import javafx.stage.Stage;
import javafx.stage.StageStyle;
import javafx.util.Duration;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;

public class CardZoomPreview {
	private static final double DURATION = 300;

	private final Stage stage;
	private final Rectangle2D start;
	private final List<ImageView> imageViewList;
	private final HBox row;
	private ParallelTransition runningAnimation;

	public CardZoomPreview(Context context, Rectangle2D start, Card.Printing printing) throws ExecutionException, InterruptedException {
		this.start = start;

		List<Card.Printing.Face> faces = new ArrayList<>();

		// work out the images
		if (printing.face(Card.Face.Kind.Transformed) != null) {
			faces.addAll(Arrays.asList(printing.face(Card.Face.Kind.Front), printing.face(Card.Face.Kind.Transformed)));
		} else if (printing.face(Card.Face.Kind.Left) != null) {
			faces.addAll(Arrays.asList(printing.face(Card.Face.Kind.Left), printing.face(Card.Face.Kind.Right)));
		} else if (printing.face(Card.Face.Kind.Flipped) != null) {
			faces.addAll(Arrays.asList(printing.face(Card.Face.Kind.Front), printing.face(Card.Face.Kind.Flipped)));
		} else {
			faces.add(printing.face(Card.Face.Kind.Front));
		}

		this.stage = new Stage(StageStyle.TRANSPARENT);

		this.imageViewList = new ArrayList<>(faces.size());

		for (Card.Printing.Face face : faces) {
			CompletableFuture<Image> image = context.images.getFace(face);

			Image img = image.getNow(Images.LOADING_CARD_LARGE);
			ImageView view = new ImageView(img);
			imageViewList.add(view);

			if (!image.isDone()) {
				image.thenAcceptAsync(loaded -> Platform.runLater(() -> {
					view.setImage(loaded);
					this.resizeStage();
				}));
			}
		}

		this.row = new HBox();
		this.row.setAlignment(Pos.CENTER_LEFT);
		this.row.getChildren().setAll(imageViewList);
		this.row.setOpacity(0.0);

		Scene scene = new Scene(new Group(this.row));
		scene.setFill(Color.TRANSPARENT);

		this.stage.setScene(scene);
		this.stage.show();
		this.resizeStage();
	}

	private void resizeStage() {
		double iw = imageViewList.stream().map(ImageView::getImage).mapToDouble(Image::getWidth).sum();
		double ih = imageViewList.stream().map(ImageView::getImage).mapToDouble(Image::getHeight).max().orElseThrow(IllegalArgumentException::new);

		Rectangle2D endUnbound = new Rectangle2D(this.start.getMinX() - (iw - start.getWidth()) / 2.0,
				this.start.getMinY() - (ih - start.getHeight()) / 2.0,
				iw,
				ih);

		ObservableList<Screen> screens = Screen.getScreensForRectangle(start);

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

		this.stage.setX(end.getMinX());
		this.stage.setY(end.getMinY());
		this.stage.setWidth(end.getWidth());
		this.stage.setHeight(end.getHeight());

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
		ft.setFromValue(row.getOpacity());
		ft.setToValue(1.0);

		Duration oldProgress = runningAnimation != null ? runningAnimation.getCurrentTime() : Duration.ZERO;
		runningAnimation = new ParallelTransition(row, st, ft, tt);
		runningAnimation.playFrom(oldProgress);
	}

	public void close() {
		this.runningAnimation.stop();
		this.stage.close();
	}
}
