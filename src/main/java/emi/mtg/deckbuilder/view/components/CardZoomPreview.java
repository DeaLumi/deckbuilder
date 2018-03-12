package emi.mtg.deckbuilder.view.components;

import emi.lib.mtg.Card;
import emi.mtg.deckbuilder.controller.Context;
import javafx.animation.FadeTransition;
import javafx.animation.ParallelTransition;
import javafx.animation.ScaleTransition;
import javafx.animation.TranslateTransition;
import javafx.application.Platform;
import javafx.collections.ObservableList;
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
import java.util.function.IntFunction;
import java.util.stream.Collectors;

public class CardZoomPreview {
	private final double DURATION = 100;

	private final Stage stage;
	private boolean closing;

	public CardZoomPreview(Context context, Rectangle2D start, Card.Printing printing) throws ExecutionException, InterruptedException {
		this.closing = false;

		List<Card.Printing.Face> faces = new ArrayList<>();

		// work out the images
		if (printing.face(Card.Face.Kind.Transformed) != null) {
			faces.addAll(Arrays.asList(printing.face(Card.Face.Kind.Front), printing.face(Card.Face.Kind.Transformed)));
		} else {
			faces.add(printing.face(Card.Face.Kind.Front));
		}

		this.stage = new Stage(StageStyle.TRANSPARENT);

		CompletableFuture<Image>[] images = faces.stream().map(context.images::get).toArray((IntFunction<CompletableFuture<Image>[]>) CompletableFuture[]::new);
		CompletableFuture.allOf(images).thenRun(() -> Platform.runLater(() -> {
			// In case we got cancelled...
			if (this.closing) {
				return;
			}

			List<Image> imageList = Arrays.stream(images).map(i -> {
				try {
					return i.get();
				} catch (InterruptedException | ExecutionException e) {
					throw new Error(e);
				}
			}).collect(Collectors.toList());

			double iw = imageList.stream().mapToDouble(Image::getWidth).sum();
			double ih = imageList.stream().mapToDouble(Image::getHeight).max().orElseThrow(IllegalArgumentException::new);

			Rectangle2D endUnbound = new Rectangle2D(start.getMinX() - (iw - start.getWidth()) / 2.0,
					start.getMinY() - (ih - start.getHeight()) / 2.0,
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

			HBox row = new HBox();

			for (Image img : imageList) {
				ImageView image = new ImageView(img);
				row.getChildren().add(image);
			}

			Scene scene = new Scene(new Group(row));
			scene.setFill(Color.TRANSPARENT);

			this.stage.setScene(scene);
			this.stage.setX(end.getMinX());
			this.stage.setY(end.getMinY());
			this.stage.setWidth(end.getWidth());
			this.stage.setHeight(end.getHeight());
			this.stage.show();

			ParallelTransition pt = new ParallelTransition(row, st, ft, tt);
			pt.play();
		}));
	}

	public void close() {
		this.closing = true;
		this.stage.close();
	}
}
