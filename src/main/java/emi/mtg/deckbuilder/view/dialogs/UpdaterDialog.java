package emi.mtg.deckbuilder.view.dialogs;

import emi.mtg.deckbuilder.controller.Updateable;
import emi.mtg.deckbuilder.model.Preferences;
import emi.mtg.deckbuilder.util.PluginUtils;
import emi.mtg.deckbuilder.view.MainApplication;
import emi.mtg.deckbuilder.view.components.ProgressTextBar;
import emi.mtg.deckbuilder.view.util.AlertBuilder;
import emi.mtg.deckbuilder.view.util.FxUtils;
import javafx.application.Platform;
import javafx.beans.binding.Bindings;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.geometry.HPos;
import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;
import javafx.scene.text.Text;
import javafx.scene.text.TextAlignment;
import javafx.stage.Screen;

import java.io.IOException;
import java.util.ArrayList;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.atomic.AtomicLong;

public class UpdaterDialog extends Dialog<Boolean> {
	private final ObservableList<Button> updatesChecking, updatesAvailable, updatesUnavailable, updatesRunning;
	private volatile long checkCount, availableCount;

	@FXML
	protected GridPane updatesGrid;

	@FXML
	protected ButtonType updateAllButtonType;

	private final BooleanProperty forceEnabled;

	public static void checkForUpdates(Screen screen, boolean autoclose) {
		UpdaterDialog dialog = new UpdaterDialog(autoclose);

		if (autoclose) {
			try {
				// Briefly yield to let the common pool try to check for updates.
				Thread.sleep(0);

				// If there's still checks in progress, wait up to 1/10th of a second.
				if (dialog.checkCount > 0) {
					Thread.sleep(100);
				}

				// If there are no checks in progress and no updates available, don't bother showing the dialog.
				if (dialog.checkCount == 0 && dialog.availableCount == 0) {
					return;
				}
			} catch (InterruptedException ie) {
				// Disregard. Worst case, we show a dialog we don't need to.
			}
		}

		FxUtils.center(dialog, screen);
		dialog.showAndWait();
	}

	protected UpdaterDialog(boolean autoclose) {
		super();
		FxUtils.FXML(this);
		getDialogPane().setStyle(Preferences.get().theme.style());
		getDialogPane().getScene().getStylesheets().add(MainApplication.class.getResource("styles.css").toExternalForm());

		this.forceEnabled = new SimpleBooleanProperty(false);
		this.updatesChecking = FXCollections.observableArrayList();
		this.updatesAvailable = FXCollections.observableArrayList();
		this.updatesUnavailable = FXCollections.observableArrayList();
		this.updatesRunning = FXCollections.observableArrayList();

		this.checkCount = 0;
		this.availableCount = 0;

		int row = -1;
		for (Updateable target : PluginUtils.providers(Updateable.class)) {
			Tooltip tooltip = new Tooltip(target.description());
			Label label = new Label(target.toString());
			label.setStyle("-fx-font-size: 1.1em;");
			label.setTooltip(tooltip);
			GridPane.setHalignment(label, HPos.RIGHT);

			ProgressTextBar progress = new ProgressTextBar(ProgressBar.INDETERMINATE_PROGRESS, "Checking for updates...");
			progress.setMaxWidth(Double.MAX_VALUE);
			progress.setMaxHeight(Double.MAX_VALUE);
			progress.setTooltip(tooltip);
			GridPane.setHgrow(progress, Priority.ALWAYS);

			final AtomicLong guiUpdate = new AtomicLong(System.nanoTime());

			Button button = new Button();
			button.textProperty().bind(Bindings.when(forceEnabled).then("Force").otherwise("Update"));
			button.setPrefWidth(100.0);
			button.setTooltip(tooltip);
			button.setDisable(true);
			button.disableProperty().bind(Bindings.createBooleanBinding(() -> updatesAvailable.contains(button) || (updatesUnavailable.contains(button) && forceEnabled.get()), updatesAvailable, updatesUnavailable, forceEnabled).not());
			button.setOnAction(ae -> {
				progress.set(0.0, "Updating...");
				progress.setDisable(false);
				updatesRunning.add(button);
				updatesAvailable.remove(button);
				updatesUnavailable.remove(button);
				ForkJoinPool.commonPool().submit(() -> {
					try {
						// TODO rate-limit all GUI updates
						target.update(Preferences.get().dataPath,
								(p, m) -> {
									if (p != 0.0 && p != 1.0 && System.nanoTime() < guiUpdate.get()) return;
									Platform.runLater(() -> progress.set(p, m));
									guiUpdate.set(System.nanoTime() + 1000*1000/60);
								});
						Platform.runLater(() -> {
							progress.set(1.0, "Done!");
							updatesRunning.remove(button);

							if (autoclose && updatesAvailable.isEmpty() && updatesRunning.isEmpty()) {
								UpdaterDialog.this.close();
							}
						});
					} catch (IOException ioe) {
						Platform.runLater(() -> {
							progress.setText("Failed!");
							AlertBuilder.notify(null)
									.type(Alert.AlertType.ERROR)
									.title("Error Updating " + target)
									.headerText("Failed to update " + target + "!")
									.contentText(ioe.getMessage())
									.show();
						});
					}
				});
			});
			button.setOnMouseClicked(me -> {
				if (forceEnabled.get()) {
					button.fire();
					me.consume();
				}
			});

			updatesGrid.addRow(++row, label, progress, button);

			updatesChecking.add(button);
			++checkCount;
			ForkJoinPool.commonPool().submit(() -> {
				boolean available = target.updateAvailable(Preferences.get().dataPath);
				--checkCount;
				if (available) {
					++availableCount;
					Platform.runLater(() -> {
						updatesAvailable.add(button);
						updatesChecking.remove(button);
						progress.set(0.0, "Update Available!");
					});
				} else {
					Platform.runLater(() -> {
						updatesUnavailable.add(button);
						updatesChecking.remove(button);
						progress.setDisable(true);
						progress.set(0.0, "Up-to-Date");
					});
				}
			});
		}

		Node updateAllButton = getDialogPane().lookupButton(updateAllButtonType);
		updateAllButton.addEventFilter(ActionEvent.ACTION, event -> {
			event.consume();
			new ArrayList<>(updatesAvailable).forEach(Button::fire);
			if (forceEnabled.get()) new ArrayList<>(updatesUnavailable).forEach(Button::fire);
		});
		updateAllButton.addEventFilter(MouseEvent.MOUSE_CLICKED, me -> {
			if (forceEnabled.get()) {
				((Button) updateAllButton).fire();
				me.consume();
			}
		});
		updateAllButton.disableProperty().bind(Bindings.isEmpty(updatesAvailable).and(Bindings.isEmpty(updatesUnavailable).or(forceEnabled.not())));
		getDialogPane().lookupButton(ButtonType.CLOSE).disableProperty().bind(Bindings.isEmpty(updatesRunning).not());

		getDialogPane().addEventFilter(KeyEvent.ANY, this::handleKey);

		setResultConverter(btn -> true);
	}

	private void handleKey(KeyEvent ke) {
		if (ke.getCode() != KeyCode.CONTROL) return;
		if (ke.getEventType() == KeyEvent.KEY_PRESSED) {
			forceEnabled.set(true);
		} else if (ke.getEventType() == KeyEvent.KEY_RELEASED) {
			forceEnabled.set(false);
		}
	}
}
