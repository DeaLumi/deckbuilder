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
import java.util.Comparator;
import java.util.List;

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
