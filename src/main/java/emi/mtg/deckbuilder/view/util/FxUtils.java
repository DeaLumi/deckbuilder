package emi.mtg.deckbuilder.view.util;

import emi.mtg.deckbuilder.model.Preferences;
import emi.mtg.deckbuilder.view.MainApplication;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Label;
import javafx.scene.layout.Pane;
import javafx.scene.layout.Region;
import javafx.scene.paint.Color;
import javafx.scene.paint.LinearGradient;
import javafx.scene.paint.Paint;
import javafx.scene.paint.RadialGradient;
import javafx.scene.text.Font;

import java.io.IOException;

public class FxUtils {
	public static <T extends Parent> void FXML(T component) {
		FXML(component, component);
	}

	public static <T extends Parent> void FXML(Object controller, T component) {
		FXMLLoader loader = new FXMLLoader();
		loader.setRoot(component);
		loader.setControllerFactory(theClass -> controller);

		String fileName = controller.getClass().getSimpleName() + ".fxml";
		try {
			loader.load(controller.getClass().getResourceAsStream(fileName));
		} catch (IOException e) {
			throw new AssertionError(e);
		}
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
