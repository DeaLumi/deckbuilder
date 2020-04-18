package emi.mtg.deckbuilder.view.util;

import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;

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
}
