package emi.mtg.deckbuilder.view.util;

import com.sun.javafx.event.EventHandlerManager;
import javafx.application.Platform;
import javafx.event.ActionEvent;
import javafx.event.Event;
import javafx.event.EventHandler;
import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.stage.Modality;
import javafx.stage.Window;
import javafx.stage.WindowEvent;

import java.lang.reflect.Field;
import java.util.Optional;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.function.DoubleConsumer;

public class AlertBuilder {
	public static AlertBuilder notify(Window owner) {
		return new AlertBuilder(Alert.AlertType.INFORMATION)
				.owner(owner);
	}

	public static AlertBuilder query(Window owner) {
		return new AlertBuilder(Alert.AlertType.CONFIRMATION, "", ButtonType.YES, ButtonType.NO)
				.owner(owner);
	}

	public static AlertBuilder create() {
		return new AlertBuilder();
	}

	private static final Field dialogEventHandlerManager;
	static {
		try {
			dialogEventHandlerManager = Dialog.class.getDeclaredField("eventHandlerManager");
			dialogEventHandlerManager.setAccessible(true);
		} catch (NoSuchFieldException nsfe) {
			throw new AssertionError(nsfe);
		}
	}

	private static EventHandlerManager eventHandlerManager(Dialog<?> dialog) {
		try {
			return (EventHandlerManager) dialogEventHandlerManager.get(dialog);
		} catch (IllegalAccessException iae) {
			throw new AssertionError(iae);
		}
	}

	private final Alert alert;
	private boolean buttonsSet, buttonsCustomized;

	private AlertBuilder() {
		this(Alert.AlertType.NONE);
	}

	private AlertBuilder(Alert.AlertType type) {
		this(type, "");
	}

	private AlertBuilder(Alert.AlertType alertType, String contentText, ButtonType... buttons) {
		alert = new Alert(alertType, contentText, buttons);
		this.buttonsSet = buttons != null && buttons.length > 0;
		this.buttonsCustomized = false;
	}

	public AlertBuilder owner(Window owner) {
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
		if (this.buttonsCustomized) {
			throw new IllegalStateException("Don't change buttons after customizing them!");
		}

		alert.getButtonTypes().setAll(buttons);
		this.buttonsSet = true;
		return this;
	}

	public AlertBuilder modal(Modality modality) {
		alert.initModality(modality);
		return this;
	}

	private Button button(ButtonType button) {
		Button btn = (Button) alert.getDialogPane().lookupButton(button);
		if (btn == null) {
			throw new IllegalArgumentException(String.format("No button matching type %s to customize!", button));
		}
		return btn;
	}

	public AlertBuilder validator(ButtonType button, BooleanSupplier simple) {
		if (!this.buttonsSet) {
			throw new IllegalStateException("Buttons haven't been settled!");
		}

		if (this.buttonsCustomized) {
			throw new IllegalStateException("Only one button customization, please!");
		}

		button(button).addEventFilter(ActionEvent.ACTION, ae -> {
			if (!simple.getAsBoolean()) {
				ae.consume();
			}
		});

		this.buttonsCustomized = true;

		return this;
	}

	@FunctionalInterface
	public interface LongRunningOperation {
		boolean execute(DoubleConsumer progress) throws Exception;
	}

	public AlertBuilder longRunning(ButtonType button, Runnable preExec, LongRunningOperation operation, Consumer<Alert> onFail) {
		if (!this.buttonsSet) {
			throw new IllegalStateException("Buttons haven't been settled!");
		}

		if (this.buttonsCustomized) {
			throw new IllegalStateException("Only one button customization, please!");
		}

		if (button.getButtonData().isCancelButton()) {
			throw new IllegalArgumentException("No long-running ops on cancel buttons! Technical reasons!");
		}

		final DialogPane pane = alert.getDialogPane();
		final ProgressBar progress = new ProgressBar(0.0);
		pane.setExpandableContent(progress);

		if (!alert.getButtonTypes().contains(ButtonType.CANCEL)) {
			alert.getButtonTypes().add(ButtonType.CANCEL);
			button(ButtonType.CANCEL).setDisable(true);
		}

		button(button).addEventFilter(ActionEvent.ACTION, event -> {
			event.consume();
			pane.setExpanded(true);

			// Disable buttons except cancel button.
			final Button cancel = button(ButtonType.CANCEL);
			final Window window = pane.getScene().getWindow();

			final boolean cancelDisabled = cancel.isDisabled();
			alert.getButtonTypes().forEach(x -> button(x).setDisable(x != ButtonType.CANCEL));

			Thread lrThread = new Thread(() -> {
				boolean result;
				Exception exc;
				try {
					result = operation.execute(d -> Platform.runLater(() -> progress.setProgress(d)));
					exc = null;

					Platform.runLater(() -> {
						alert.setResult(button);
						alert.close();
					});

				} catch (Exception e) {
					result = false;
					exc = e;
				}

				if (!result) {
					Platform.runLater(() -> {
						alert.getButtonTypes().forEach(x -> button(x).setDisable(x == ButtonType.CANCEL && cancelDisabled));
						progress.setProgress(0.0);
						if (onFail != null) onFail.accept(alert);
					});
				}

				if (exc != null) {
					throw new RuntimeException(exc);
				}
			}, String.format("%s long-running thread", this.toString()));
			lrThread.setDaemon(true);

			EventHandler<Event> handleCancel = new EventHandler<Event>() {
				@Override
				public void handle(Event event) {
					if (lrThread.isAlive()) {
						Platform.runLater(() -> progress.setProgress(0.0));
						lrThread.interrupt();
						cancel.removeEventFilter(ActionEvent.ACTION,this);
						window.removeEventFilter(WindowEvent.WINDOW_CLOSE_REQUEST, this);
						event.consume();
					}
				}
			};

			button(ButtonType.CANCEL).addEventFilter(ActionEvent.ACTION, handleCancel);

			// Catch if the user clicks the dialog X button.
			// Don't use setOnCloseRequest here since that can clobber other effects.
			window.addEventFilter(WindowEvent.WINDOW_CLOSE_REQUEST, handleCancel);

			if (preExec != null) preExec.run();
			lrThread.start();
		});

		this.buttonsCustomized = true;

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
