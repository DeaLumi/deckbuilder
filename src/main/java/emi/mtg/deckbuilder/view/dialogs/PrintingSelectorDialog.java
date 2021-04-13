package emi.mtg.deckbuilder.view.dialogs;

import emi.lib.mtg.Card;
import emi.mtg.deckbuilder.model.CardInstance;
import emi.mtg.deckbuilder.model.Preferences;
import emi.mtg.deckbuilder.view.components.CardPane;
import emi.mtg.deckbuilder.view.layouts.FlowGrid;
import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.scene.Scene;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Dialog;
import javafx.stage.Modality;

import java.util.Optional;
import java.util.stream.Collectors;

public class PrintingSelectorDialog extends Dialog<Card.Printing> {
	public static Optional<Card.Printing> show(Scene scene, Card card) {
		return new PrintingSelectorDialog(scene, card).showAndWait();
	}

	private final CardPane pane;

	public PrintingSelectorDialog(Scene scene, Card card) {
		super();

		setTitle("Printing Selector");
		setResult(null);
		setResultConverter(bt -> getResult());

		ObservableList<CardInstance> tmpModel = FXCollections.observableList(card.printings().stream()
				.map(CardInstance::new)
				.collect(Collectors.toList()));
		pane = new CardPane("Variations", tmpModel, FlowGrid.Factory.INSTANCE);

		pane.view().doubleClick(ci -> {
			setResult(ci.printing());
			close();
		});

		pane.autoAction.set(ci -> {
			setResult(ci.printing());
			Platform.runLater(PrintingSelectorDialog.this::close);
		});

		pane.autoEnabled.set(true);

		pane.view().immutableModelProperty().set(true);
		pane.showingVersionsSeparately.set(true);
		pane.setPrefHeight(scene.getHeight() / 1.5);
		pane.setPrefWidth(scene.getWidth() / 1.5);

		getDialogPane().setStyle("-fx-base: " + Preferences.get().theme.baseHex());
		getDialogPane().setContent(pane);
		getDialogPane().getButtonTypes().add(ButtonType.CANCEL);
		initModality(Modality.WINDOW_MODAL);
		initOwner(scene.getWindow());

		setOnShown(de -> pane.filter().requestFocus());
	}
}
