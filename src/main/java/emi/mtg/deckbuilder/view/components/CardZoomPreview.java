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
import javafx.event.Event;
import javafx.event.EventType;
import javafx.geometry.Point2D;
import javafx.geometry.Pos;
import javafx.geometry.Rectangle2D;
import javafx.scene.Group;
import javafx.scene.Scene;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.input.*;
import javafx.scene.layout.HBox;
import javafx.stage.Screen;
import javafx.stage.Stage;
import javafx.stage.StageStyle;
import javafx.util.Duration;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class CardZoomPreview extends Stage {
	private static final double DURATION = 150;

	private final CardView owner;
	private final Rectangle2D start;
	private final List<ImageView> imageViewList;
	private final HBox row;
	private ParallelTransition runningAnimation;

	public CardZoomPreview(CardView owner, Rectangle2D start, Card.Print print) throws ExecutionException, InterruptedException {
		super(StageStyle.TRANSPARENT);

		this.start = start;
		this.owner = owner;

		// work out the images
		List<Card.Print.Face> faces = print.faces().stream()
				.filter(f -> print.faces().stream().noneMatch(other -> f != other && other.contains(f)))
				.collect(Collectors.toList());

		this.imageViewList = new ArrayList<>(faces.size());

		for (Card.Print.Face face : faces) {
			CompletableFuture<Image> image = Context.get().images.getFace(face);

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
		scene.setFill(null);

		scene.addEventFilter(EventType.ROOT, this::passToParent);

		this.initOwner(owner.getScene().getWindow());
		this.setAlwaysOnTop(true);
		this.setScene(scene);
		this.show();
		this.resizeStage();
	}

	private static final Set<EventType<?>> RETAINED_EVENTS = Collections.unmodifiableSet(Stream.of(
			MouseEvent.MOUSE_ENTERED,
			MouseEvent.MOUSE_ENTERED_TARGET,
			MouseEvent.MOUSE_EXITED,
			MouseEvent.MOUSE_EXITED_TARGET
	).collect(Collectors.toSet()));

	private void passToParent(Event event) {
		// Special handling for mouse events to remap based on screen.
		if (RETAINED_EVENTS.contains(event.getEventType())) return;

		if (event instanceof MouseEvent) {
			MouseEvent me = (MouseEvent) event;
			double screenX = me.getScreenX(), screenY = me.getScreenY();
			// Documentation incorrectly states this should be in local coordinates if new source is a node.
			// In fact, it should always be in scene coordinates, unless I want to construct the pick result myself.
			Point2D newSceneCoords = owner.localToScene(owner.screenToLocal(screenX, screenY));

			event = new MouseEvent(
					owner,
					owner,
					me.getEventType(),
					newSceneCoords.getX(),
					newSceneCoords.getY(),
					screenX,
					screenY,
					me.getButton(),
					me.getClickCount(),
					me.isShiftDown(),
					me.isControlDown(),
					me.isAltDown(),
					me.isMetaDown(),
					me.isPrimaryButtonDown(),
					me.isMiddleButtonDown(),
					me.isSecondaryButtonDown(),
					me.isSynthesized(),
					me.isPopupTrigger(),
					me.isStillSincePress(),
					null
			);
		} else {
			event = event.copyFor(owner, owner);
		}

		owner.fireEvent(event);
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

		this.setX(end.getMinX());
		this.setY(end.getMinY());
		this.setWidth(end.getWidth());
		this.setHeight(end.getHeight());

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
		super.close();
	}
}
