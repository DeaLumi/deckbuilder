package emi.mtg.deckbuilder.view.components;

import javafx.beans.binding.Bindings;
import javafx.event.Event;
import javafx.scene.control.Label;
import javafx.scene.control.Tab;
import javafx.scene.control.TextField;
import javafx.scene.input.*;

import java.util.Collections;

public class DeckTab extends Tab {
	public static final DataFormat DRAGGED_TAB = new DataFormat("application/deckbuilder-dragged-tab");
	public static DeckTab draggedTab = null;

	private final DeckPane pane;
	private final Label label;
	private final TextField textField;

	public DeckTab(DeckPane pane) {
		super();

		this.pane = pane;

		this.label = new Label();
		this.label.textProperty().bind(Bindings.createStringBinding(() -> {
			String mod = pane.deck().modified() ? "*" : "";
			if (pane.deck().name() == null || pane.deck().name().isEmpty()) return mod + "Unnamed Deck";
			return mod + pane.deck().name();
		}, pane.deck().nameProperty(), pane.deck().modifiedProperty()));

		this.textField = new TextField();

		this.label.setOnMousePressed(me -> {
			if (me.getButton() == MouseButton.MIDDLE) me.consume();
		});

		this.label.setOnDragDetected(me -> {
			if (me.getButton() != MouseButton.PRIMARY) return;

			Dragboard db = this.label.startDragAndDrop(TransferMode.MOVE);
			DeckTab.draggedTab = DeckTab.this;
			db.setContent(Collections.singletonMap(DRAGGED_TAB, pane().deck().name()));
			me.consume();
		});

		this.label.setOnDragDone(de -> {
			DeckTab.draggedTab = null;
			de.consume();
		});

		this.label.setOnMouseClicked(ce -> {
			if (ce.getButton() == MouseButton.PRIMARY && ce.getClickCount() == 2) {
				DeckTab.this.textField.setText(pane.deck().name());
				DeckTab.this.setGraphic(DeckTab.this.textField);
				DeckTab.this.textField.requestFocus();
				ce.consume();
			} else if (ce.getButton() == MouseButton.MIDDLE) {
				DeckTab.this.close();
				ce.consume();
			}
		});

		this.textField.setOnAction(ae -> {
			if (!pane.deck().name().equals(DeckTab.this.textField.getText())) {
				pane.deck().nameProperty().setValue(DeckTab.this.textField.getText());
				pane.deck().modifiedProperty().set(true);
			}
			DeckTab.this.setGraphic(DeckTab.this.label);
			ae.consume();
		});

		this.textField.setOnKeyPressed(ke -> {
			if (ke.getCode() == KeyCode.ESCAPE) {
				DeckTab.this.setGraphic(DeckTab.this.label);
				ke.consume();
			}
		});

		this.textField.focusedProperty().addListener((prop, old, newl) -> {
			if (!newl) {
				DeckTab.this.setGraphic(DeckTab.this.label);
			}
		});

		setGraphic(this.label);
		setContent(pane);
	}

	public DeckPane pane() {
		return pane;
	}

	public boolean close() {
		if (!isClosable()) return false;
		return forceClose();
	}

	public boolean forceClose() {
		Event close = new Event(Tab.CLOSED_EVENT);
		if (getOnCloseRequest() != null) getOnCloseRequest().handle(close);
		if (close.isConsumed()) return false;
		reallyForceClose(close);
		return true;
	}

	public void reallyForceClose() {
		reallyForceClose(new Event(Tab.CLOSED_EVENT));
	}

	protected void reallyForceClose(Event close) {
		getTabPane().getTabs().remove(DeckTab.this);
		if (getOnClosed() != null) getOnClosed().handle(close);
	}
}
