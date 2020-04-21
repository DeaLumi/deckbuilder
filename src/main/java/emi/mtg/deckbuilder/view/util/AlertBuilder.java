package emi.mtg.deckbuilder.view.util;

import javafx.scene.Node;
import javafx.scene.control.Alert;
import javafx.scene.control.ButtonType;
import javafx.stage.Stage;

import java.util.Optional;

public class AlertBuilder {
	public static AlertBuilder notify(Stage owner) {
		return new AlertBuilder()
				.owner(owner)
				.type(Alert.AlertType.INFORMATION)
				.buttons(ButtonType.OK);
	}

	public static AlertBuilder query(Stage owner) {
		return new AlertBuilder()
				.owner(owner)
				.type(Alert.AlertType.CONFIRMATION)
				.buttons(ButtonType.YES, ButtonType.NO);
	}

	public static AlertBuilder create() {
		return new AlertBuilder();
	}

	private final Alert alert;

	private AlertBuilder() {
		alert = new Alert(Alert.AlertType.NONE);
	}

	public AlertBuilder owner(Stage owner) {
		alert.initOwner(owner);
		return this;
	}

	public AlertBuilder type(Alert.AlertType type) {
		alert.setAlertType(type);
		return this;
	}

	public AlertBuilder title(String title) {
		alert.setTitle(title);
		return this;
	}

	public AlertBuilder headerText(String headerText) {
		alert.setHeaderText(headerText);
		return this;
	}

	public AlertBuilder contentText(String contentText) {
		alert.setContentText(contentText);
		return this;
	}

	public AlertBuilder contentNode(Node content) {
		alert.getDialogPane().setContent(content);
		return this;
	}

	public AlertBuilder buttons(ButtonType... buttons) {
		alert.getButtonTypes().setAll(buttons);
		return this;
	}

	public Alert show() {
		alert.show();
		return alert;
	}

	public Optional<ButtonType> showAndWait() {
		return alert.showAndWait();
	}

	public Alert get() {
		return alert;
	}
}
