package emi.mtg.deckbuilder.view.util;

import emi.mtg.deckbuilder.model.Preferences;
import emi.mtg.deckbuilder.view.MainApplication;
import javafx.fxml.FXMLLoader;
import javafx.geometry.Rectangle2D;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Dialog;
import javafx.scene.control.Label;
import javafx.scene.layout.Pane;
import javafx.scene.layout.Region;
import javafx.scene.paint.Color;
import javafx.scene.paint.LinearGradient;
import javafx.scene.paint.Paint;
import javafx.scene.paint.RadialGradient;
import javafx.scene.text.Font;
import javafx.stage.Screen;
import javafx.stage.Window;

import java.awt.*;
import java.io.IOException;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.function.BiConsumer;
import java.util.function.BinaryOperator;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Collector;

public class FxUtils {
	public static <T> void FXML(T component) {
		FXML(component, component);
	}

	public static <T> void FXML(Object controller, T component) {
		FXMLLoader loader = new FXMLLoader();
		loader.setRoot(component);
		loader.setController(controller);
		loader.setControllerFactory(theClass -> controller);

		String fileName = controller.getClass().getSimpleName() + ".fxml";
		try {
			loader.load(controller.getClass().getResourceAsStream(fileName));
		} catch (IOException e) {
			throw new AssertionError(e);
		}
	}

	public static Screen pointerScreen() {
		PointerInfo pointerInfo = MouseInfo.getPointerInfo();
		return screen(pointerInfo.getLocation().getX(), pointerInfo.getLocation().getY(), 0, 0);
	}

	public static Screen screen(double x, double y, double w, double h) {
		List<Screen> screens = Screen.getScreensForRectangle(x, y, w, h);

		if (screens.size() > 1) {
			return screens.stream().min(Comparator.comparingDouble(s -> s.getVisualBounds().getMinX())).orElseThrow(AssertionError::new);
		} else if (screens.size() == 1) {
			return screens.get(0);
		} else {
			return Screen.getPrimary();
		}
	}

	public static Screen screen(Window window) {
		return screen(window.getX(), window.getY(), window.getWidth(), window.getHeight());
	}

	public static Screen screen(Dialog<?> dialog) {
		return screen(dialog.getX(), dialog.getY(), dialog.getWidth(), dialog.getHeight());
	}

	public static void center(Dialog<?> dialog, Screen screen) {
		Rectangle2D vis = screen.getVisualBounds();
		dialog.setX(vis.getMinX() + (vis.getWidth() - dialog.getWidth()) / 2.0);
		dialog.setY(vis.getMinY() + (vis.getHeight() - dialog.getHeight()) / 2.0);
	}

	public static void transfer(Dialog<?> dialog, Screen screen) {
		Rectangle2D base = screen(dialog).getVisualBounds();
		Rectangle2D target = screen.getVisualBounds();

		dialog.setX(dialog.getX() - base.getMinX() + target.getMinX());
		dialog.setY(dialog.getY() - base.getMinY() + target.getMinY());
	}

	public static class MutRect2D {
		public double x1, y1, x2, y2;
	}

	public static final Collector<Rectangle2D, MutRect2D, Rectangle2D> MAX_BOUNDS = new Collector<Rectangle2D, MutRect2D, Rectangle2D>() {
		@Override
		public Supplier<MutRect2D> supplier() {
			return MutRect2D::new;
		}

		@Override
		public BiConsumer<MutRect2D, Rectangle2D> accumulator() {
			return (a, b) -> {
				double minX = Math.min(a.x1, b.getMinX());
				double minY = Math.min(a.y1, b.getMinY());
				double maxX = Math.max(a.x2, b.getMaxX());
				double maxY = Math.max(a.y2, b.getMaxY());
				a.x1 = minX;
				a.x2 = maxX;
				a.y1 = minY;
				a.y2 = maxY;
			};
		}

		@Override
		public BinaryOperator<MutRect2D> combiner() {
			return (a, b) -> {
				double minX = Math.min(a.x1, b.x1);
				double minY = Math.min(a.y1, b.y1);
				double maxX = Math.max(a.x2, b.x2);
				double maxY = Math.max(a.y2, b.y2);
				a.x1 = minX;
				a.x2 = maxX;
				a.y1 = minY;
				a.y2 = maxY;
				return a;
			};
		}

		@Override
		public Function<MutRect2D, Rectangle2D> finisher() {
			return a -> new Rectangle2D(a.x1, a.y1, a.x2 - a.x1, a.y2 - a.y1);
		}

		@Override
		public Set<Characteristics> characteristics() {
			return Collections.emptySet();
		}
	};

	public static Rectangle2D clampBounds(double x, double y, double w, double h, Rectangle2D bounds) {
		x = Math.max(bounds.getMinX(), Math.min(x, bounds.getMaxX() - w));
		y = Math.max(bounds.getMinY(), Math.min(y, bounds.getMaxY() - h));

		return new Rectangle2D(x, y, w, h);
	}

	public static Rectangle2D clampBounds(Rectangle2D rect, Rectangle2D bounds) {
		double x = Math.max(bounds.getMinX(), Math.min(rect.getMinX(), bounds.getMaxX() - rect.getWidth()));
		double y = Math.max(bounds.getMinY(), Math.min(rect.getMinY(), bounds.getMaxY() - rect.getHeight()));

		return new Rectangle2D(x, y, rect.getWidth(), rect.getHeight());
	}

	public static void underMouse(Dialog<?> dialog) {
		underMouse(dialog, false);
	}

	public static void underMouse(Dialog<?> dialog, boolean ignoreScreenEdges) {
		underMouse(dialog, 0.5, 0.5, ignoreScreenEdges);
	}

	public static void underMouse(Dialog<?> dialog, double offsetPctX, double offsetPctY) {
		underMouse(dialog, offsetPctX, offsetPctY, false);
	}

	public static void underMouse(Dialog<?> dialog, double offsetPctX, double offsetPctY, boolean ignoreScreenEdges) {
		PointerInfo pointer = MouseInfo.getPointerInfo();

		double x = pointer.getLocation().getX() - dialog.getWidth() * offsetPctX;
		double y = pointer.getLocation().getY() - dialog.getHeight() * offsetPctY;

		if (!ignoreScreenEdges) {
			Rectangle2D bounds = Screen.getScreensForRectangle(x, y, dialog.getWidth(), dialog.getHeight()).stream()
					.map(Screen::getVisualBounds)
					.collect(MAX_BOUNDS);

			Rectangle2D tmp = clampBounds(x, y, dialog.getWidth(), dialog.getHeight(), bounds);
			x = tmp.getMinX();
			y = tmp.getMinY();
		}

		dialog.setX(x);
		dialog.setY(y);
	}

	public static void center(Window window, Screen screen) {
		Rectangle2D vis = screen.getVisualBounds();
		window.setX(vis.getMinX() + (vis.getWidth() - window.getWidth()) / 2.0);
		window.setY(vis.getMinY() + (vis.getHeight() - window.getHeight()) / 2.0);
	}

	public static void transfer(Window window, Screen screen) {
		Rectangle2D base = screen(window).getVisualBounds();
		Rectangle2D target = screen.getVisualBounds();

		window.setX(window.getX() - base.getMinX() + target.getMinX());
		window.setY(window.getY() - base.getMinY() + target.getMinY());
	}

	public static String themeCss() {
		Label text = new Label("Exemplar");
		text.setFont(Font.font(10.0));
		Region parent = new Pane(text);
		Scene scene = new Scene(parent);
		scene.getStylesheets().add(MainApplication.class.getResource("styles.css").toExternalForm());
		parent.setStyle(Preferences.get().theme.style());

		parent.applyCss();

		Paint bg = parent.getBackground().getFills().get(0).getFill();
		String bgColor;
		if (bg instanceof Color) {
			bgColor = Preferences.Theme.hex((Color) bg);
		} else if (bg instanceof LinearGradient) {
			bgColor = ((LinearGradient) bg).toString();
		} else if (bg instanceof RadialGradient) {
			bgColor = ((RadialGradient) bg).toString();
		} else {
			bgColor = "transparent"; // Ackpth
		}

		Paint fg = text.getTextFill();
		String fgColor;
		if (fg instanceof Color) {
			fgColor = Preferences.Theme.hex((Color) fg);
		} else if (fg instanceof LinearGradient) {
			fgColor = ((LinearGradient) fg).toString();
		} else if (fg instanceof RadialGradient) {
			fgColor = ((RadialGradient) fg).toString();
		} else {
			fgColor = Preferences.Theme.hex(Preferences.get().theme.base.invert());
		}

		Font font = text.getFont();
		String fontCss = String.format("%fpt %s", font.getSize(), font.getFamily());

		return String.join("\n",
				":root {",
				"\t--bg-color: " + bgColor + ";",
				"\t--fg-color: " + fgColor + ";",
				"}",
				"",
				"body {",
				"\tuser-select: none;",
				"\t-webkit-user-select: none;",
				"\tcursor: default;",
				"\tbackground-color: " + bgColor + ";",
				"\tcolor: " + fgColor + ";",
				"\tfont: " + fontCss + ";",
				"}");
	}
}
