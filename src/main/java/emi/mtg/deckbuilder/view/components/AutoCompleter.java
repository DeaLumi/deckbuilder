package emi.mtg.deckbuilder.view.components;

import javafx.application.Platform;
import javafx.beans.binding.Bindings;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.value.ChangeListener;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.scene.control.ListView;
import javafx.scene.control.TextField;
import javafx.scene.input.KeyEvent;
import javafx.stage.Popup;

import java.util.List;
import java.util.concurrent.ForkJoinPool;
import java.util.function.Consumer;
import java.util.function.Function;

public class AutoCompleter extends Popup {
	private final SimpleBooleanProperty enabled = new SimpleBooleanProperty(false);
	private final TextField target;
	private final Function<String, List<String>> suggester;
	private final ObservableList<String> suggestions = FXCollections.observableArrayList();
	private final ListView<String> suggestionList;
	private volatile long generation = 0;
	private volatile boolean ignoreInput = false;

	public AutoCompleter(TextField target, Function<String, List<String>> suggester, Consumer<String> onCompleted) {
		this.target = target;
		this.suggester = suggester;

		enabled.addListener((prop, oldv, newv) -> {
			if (!oldv && newv) {
				target.textProperty().addListener(this.textListener);
				target.focusedProperty().addListener(this.focusListener);
			} else if (oldv && !newv) {
				target.textProperty().removeListener(this.textListener);
				target.focusedProperty().removeListener(this.focusListener);
			}
		});

		this.suggestions.clear();

		this.suggestionList = new ListView<>(this.suggestions);
		this.suggestionList.setFixedCellSize(24.0);
		this.suggestionList.prefHeightProperty().bind(Bindings.min(20, Bindings.size(this.suggestions)).multiply(suggestionList.fixedCellSizeProperty()).add(2.0));

		this.suggestionList.addEventFilter(KeyEvent.KEY_PRESSED, ke -> {
			switch (ke.getCode()) {
				case A:
					if (ke.isShortcutDown()) {
						this.target.selectAll();
						ke.consume();
					}
					break;
				case HOME:
					if (ke.isShiftDown()) {
						this.target.selectRange(this.target.getCaretPosition(), 0);
					} else {
						this.target.positionCaret(0);
					}
					ke.consume();
					break;
				case END:
					if (ke.isShiftDown()) {
						this.target.selectRange(this.target.getCaretPosition(), this.target.getText().length());
					} else {
						this.target.positionCaret(this.target.getText().length());
					}
					ke.consume();
					break;
				case ESCAPE:
					if (isHideOnEscape()) {
						hide();
						ke.consume();
					}
					break;
				case TAB:
				case ENTER:
					ignoreInput = true;
					target.setText(suggestionList.getSelectionModel().getSelectedItem());
					target.positionCaret(target.getText().length());
					onCompleted.accept(suggestionList.getSelectionModel().getSelectedItem());
					ignoreInput = false;
					hide();
					ke.consume();
					break;
				default:
					break;
			}
		});

		this.getContent().add(this.suggestionList);

		this.setHideOnEscape(true);
	}

	public boolean enabled() {
		return enabled.get();
	}

	public SimpleBooleanProperty enabledProperty() {
		return enabled;
	}

	public void enabled(boolean value) {
		this.enabled.set(value);
	}

	public TextField target() {
		return this.target;
	}

	public Function<String, List<String>> suggestor() {
		return this.suggester;
	}

	public ListView<String> suggestionList() {
		return this.suggestionList;
	}

	private final ChangeListener<String> textListener = (property, oldValue, newValue) -> {
		if (ignoreInput) return;

		final long thisGen = ++generation;

		ForkJoinPool.commonPool().submit(() -> {
			List<String> suggestions = suggestor().apply(newValue);

			if (thisGen < generation) return;
			Platform.runLater(() -> {
				if (thisGen < generation) return;

				String oldSel = suggestionList().getSelectionModel().getSelectedItem();
				this.suggestions.setAll(suggestions);
				if (suggestions.contains(oldSel)) {
					suggestionList().getSelectionModel().select(oldSel);
				} else {
					suggestionList().getSelectionModel().selectFirst();
				}

				if (!this.suggestions.isEmpty() && !isShowing()) {
					show(target(),
							target().localToScreen(0,0).getX(),
							target().localToScreen(0, target().getHeight()).getY());
				} else if (this.suggestions.isEmpty() && isShowing()) {
					hide();
				}
			});
		});
	};

	private final ChangeListener<Boolean> focusListener = (property, oldValue, newValue) -> {
		// Remove suggestions.
		this.suggestions.clear();

		if (this.isShowing()) this.hide();
	};
}
