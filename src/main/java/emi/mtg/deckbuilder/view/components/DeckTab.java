package emi.mtg.deckbuilder.view.components;

import emi.lib.mtg.game.Format;
import emi.mtg.deckbuilder.model.DeckList;
import emi.mtg.deckbuilder.view.MainWindow;
import emi.mtg.deckbuilder.view.dialogs.DeckInfoDialog;
import javafx.application.Platform;
import javafx.beans.Observable;
import javafx.event.Event;
import javafx.scene.control.*;
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
		pane.deck().nameProperty().addListener(this::updateLabel);
		pane.deck().modifiedProperty().addListener(this::updateLabel);
		this.updateLabel(null);

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

		MenuItem deckInfo = new MenuItem("Deck Info...");
		deckInfo.setOnAction(ae -> {
			Format oldFormat = pane().deck().format();
			if (new DeckInfoDialog(pane().getScene().getWindow(), pane().deck()).showAndWait().orElse(false) && !oldFormat.equals(pane().deck().format())) {
				pane().applyDeck();
			}
		});

		MenuItem undock = new MenuItem("Undock");
		undock.setOnAction(ae -> mainWindow().undock(DeckTab.this));

		MenuItem duplicate = new MenuItem("Open Copy");
		duplicate.setOnAction(ae -> {
			DeckList deck = pane().deck();
			DeckList copy = new DeckList(deck.name() + " - Copy", deck.author(), deck.format(), deck.description(), deck.cards());
			copy.modifiedProperty().set(true);
			mainWindow().openDeckPane(copy);
		});

		ContextMenu menu = new ContextMenu(deckInfo, undock, duplicate);
		setContextMenu(menu);
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

	protected void updateLabel(Observable on) {
		String mod = pane.deck().modified() ? "*" : "";
		if (pane.deck().name() == null || pane.deck().name().isEmpty()) {
			mod += "Unnamed Deck";
		} else {
			mod += pane.deck().name();
		}
		final String fmod = mod;
		Platform.runLater(() -> this.label.setText(fmod));
	}

	protected MainWindow mainWindow() {
		return (MainWindow) getTabPane().getScene().getWindow();
	}
}
