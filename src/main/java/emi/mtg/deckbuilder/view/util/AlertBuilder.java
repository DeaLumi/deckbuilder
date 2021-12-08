package emi.mtg.deckbuilder.view.util;

import emi.mtg.deckbuilder.model.Preferences;
import javafx.application.Platform;
import javafx.event.ActionEvent;
import javafx.event.Event;
import javafx.event.EventHandler;
import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.scene.layout.VBox;
import javafx.stage.Modality;
import javafx.stage.Screen;
import javafx.stage.Window;
import javafx.stage.WindowEvent;

import java.util.Hashtable;
import java.util.Optional;
import java.util.concurrent.CompletionException;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.function.DoubleConsumer;
import java.util.function.Function;

public class AlertBuilder {
	private static final Hashtable<String, CompletionException> EXCEPTION_INDEX = new Hashtable<>();

	private static final Function<CompletionException, ButtonType> EXCEPTION_BUTTON = exc -> {
		final String key = Integer.toString(exc.hashCode());
		EXCEPTION_INDEX.put(key, exc);
		return new ButtonType(key, ButtonBar.ButtonData.BIG_GAP);
	};

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

	private final Alert alert;
	private boolean buttonsSet, buttonsCustomized;
	private Screen screen;

	private AlertBuilder() {
		this(Alert.AlertType.NONE);
	}

	private AlertBuilder(Alert.AlertType type) {
		this(type, "");
	}

	private AlertBuilder(Alert.AlertType alertType, String contentText, ButtonType... buttons) {
		alert = new Alert(alertType, contentText, buttons);
		alert.getDialogPane().setStyle(Preferences.get().theme.style());
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

	public AlertBuilder screen(Screen screen) {
		this.screen = screen;
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

	public enum Exceptions {
		/**
		 * Exceptions thrown from long-running operations will be thrown from AlertBuilder.showAndWait. Note that if you
		 * don't call AlertBuilder.showAndWait -- for instance, because you want to do some unusual alterations to the
		 * alert after it's fully ready, or want to forcibly fire the dialog immediately.
		 */
		Defer,

		/**
		 * Exceptions thrown from long-running operations will be wrapped as RuntimeExceptions and re-thrown. This will
		 * usually hit the deckbuilder's default uncaught exception handler (popping up that ugly exception dialog).
		 * Note that an exception being thrown counts as a failure of the long-running operation, so onFail will be
		 * invoked and the dialog's buttons will be reset for reattempt.
		 */
		Throw,

		/**
		 * Exceptions thrown from long-running operations will be translated to a failure of the operation. The
		 * exception itself will be ignored, so use this with care!
		 */
		Fail
	}

	private static final ButtonBar.ButtonData[] EXEC_BUTTON_PREFERENCE = {
			ButtonBar.ButtonData.APPLY,
			ButtonBar.ButtonData.OK_DONE,
			ButtonBar.ButtonData.FINISH,
			ButtonBar.ButtonData.YES,
			ButtonBar.ButtonData.NEXT_FORWARD
	};

	/**
	 * This is really complicated, so I'm adding some javadoc for ya. Basically, if you call this, the dialog will be
	 * set up for performing a progress-reporting operation on a separate thread. A progress bar will be added, a thread
	 * created, and started.
	 *
	 * @param button Which button should fire this event. If null, an internal list of preferences (above) will be tried
	 *               and the first matching button will be used.
	 * @param preExec A pre-execution hook. This will be called from the JavaFX application thread immediately before
	 *                the worker thread is started. Use this if you need to perform any validation before the thread
	 *                is started.
	 * @param operation The actual long-running code to execute. This will be called from a new thread. It can report
	 *                  progress through the provided DoubleConsumer. If it returns false, the dialog will not be
	 *                  closed.
	 * @param onFail A hook to be called if the long-running code returns false or throws an exception. This will be
	 *               called from the JavaFX event thread immediately after the dialog's buttons are reenabled. The hook
	 *               is passed a reference to the alert, since you can't access it from AlertBuilder.get() or whatever
	 *               while chaining calls.
	 * @param exceptions Prescribes how exceptions thrown from the long-running code should be handled. See the enum
	 *                   values for details.
	 * @return this AlertBuilder, for call chaining.
	 */
	public AlertBuilder longRunning(ButtonType button, Consumer<Alert> preExec, LongRunningOperation operation, Consumer<Alert> onFail, Exceptions exceptions) {
		if (!this.buttonsSet) {
			throw new IllegalStateException("Buttons haven't been settled!");
		}

		if (this.buttonsCustomized) {
			throw new IllegalStateException("Only one button customization, please!");
		}

		if (button == null) {
			int best = EXEC_BUTTON_PREFERENCE.length;
			ButtonType bestBtn = null;
			for (ButtonType bt : alert.getButtonTypes()) {
				for (int i = 0; i < EXEC_BUTTON_PREFERENCE.length; ++i) {
					if (EXEC_BUTTON_PREFERENCE[i].equals(bt.getButtonData()) && i < best) {
						bestBtn = bt;
						best = i;
					}
				}
			}

			if (bestBtn == null) {
				throw new IllegalStateException("Unable to find a button to hook!");
			}

			button = bestBtn;
		}

		if (button.getButtonData().isCancelButton()) {
			throw new IllegalArgumentException("No long-running ops on cancel buttons! Technical reasons!");
		}

		final ButtonType btn = button;
		final DialogPane pane = alert.getDialogPane();
		final ProgressBar progress = new ProgressBar(0.0);
		progress.setMaxWidth(Double.MAX_VALUE);
		progress.setMaxHeight(Double.MAX_VALUE);
		progress.setManaged(false);

		VBox box;
		if (pane.getContent() != null) {
			if (pane.getContent() instanceof VBox) {
				box = (VBox) pane.getContent();
				box.getChildren().add(progress);
			} else {
				box = new VBox(pane.getContent(), progress);
				box.setSpacing(10.0);
			}
		} else {
			Label label = new Label(pane.getContentText());
			label.setMaxWidth(Double.MAX_VALUE);
			label.setMaxHeight(Double.MAX_VALUE);
			label.setWrapText(true);
			label.getStyleClass().add("content");
			box = new VBox(10.0, label, progress);
		}
		box.setFillWidth(true);
		pane.setContent(box);

		if (!alert.getButtonTypes().contains(ButtonType.CANCEL)) {
			alert.getButtonTypes().add(ButtonType.CANCEL);
			button(ButtonType.CANCEL).setDisable(true);
		}

		button(btn).addEventFilter(ActionEvent.ACTION, event -> {
			event.consume();
			progress.setManaged(true);
			pane.getScene().getWindow().sizeToScene();

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
						alert.setResult(btn);
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

				if (exceptions != Exceptions.Fail && exc != null) {
					throw new CompletionException(exc);
				}
			}, String.format("%s long-running thread", this.toString()));
			lrThread.setDaemon(true);

			if (exceptions == Exceptions.Defer) {
				lrThread.setUncaughtExceptionHandler((t, exc) -> {
					if (exc instanceof CompletionException) {
						Platform.runLater(() -> {
							alert.setResult(EXCEPTION_BUTTON.apply((CompletionException) exc));
						});
					}
				});
			}

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

			if (preExec != null) preExec.accept(alert);
			lrThread.start();
		});

		this.buttonsCustomized = true;

		return this;
	}

	public AlertBuilder longRunning(ButtonType button, Runnable preExec, LongRunningOperation operation, Consumer<Alert> onFail, Exceptions exceptions) {
		return longRunning(button, dlg -> preExec.run(), operation, onFail, exceptions);
	}

	public AlertBuilder longRunning(LongRunningOperation op, Exceptions exceptions) {
		return longRunning(null, (Consumer<Alert>) null, op, null, exceptions);
	}

	public AlertBuilder longRunning(LongRunningOperation op) {
		return longRunning(op, Exceptions.Defer);
	}

	public Alert show() {
		alert.show();
		if ((alert.getOwner() == null || Double.isNaN(alert.getOwner().getWidth()) || alert.getOwner().getWidth() <= 0) && screen != null) FxUtils.transfer(alert, screen);
		return alert;
	}

	public Optional<ButtonType> showAndWait() {
		if ((alert.getOwner() == null || Double.isNaN(alert.getOwner().getWidth()) || alert.getOwner().getWidth() <= 0) && screen != null) {
			alert.show();
			alert.hide();
			FxUtils.transfer(alert, screen);
		}

		Optional<ButtonType> bt = alert.showAndWait();

		if (bt.isPresent() && bt.get().getButtonData().equals(ButtonBar.ButtonData.BIG_GAP)) {
			CompletionException exc = EXCEPTION_INDEX.remove(bt.get().getText());
			if (exc != null) throw exc;
		}

		return bt;
	}

	public Alert get() {
		return alert;
	}
}
