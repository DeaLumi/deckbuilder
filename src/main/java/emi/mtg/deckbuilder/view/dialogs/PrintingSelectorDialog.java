package emi.mtg.deckbuilder.view.dialogs;

import emi.lib.mtg.Card;
import emi.mtg.deckbuilder.model.CardInstance;
import emi.mtg.deckbuilder.model.Preferences;
import emi.mtg.deckbuilder.view.components.CardPane;
import emi.mtg.deckbuilder.view.layouts.FlowGrid;
import javafx.application.Platform;
import javafx.beans.binding.Bindings;
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

		ObservableList<CardInstance> tmpModel = FXCollections.observableList(card.printings().stream()
				.map(CardInstance::new)
				.collect(Collectors.toList()));
		pane = new CardPane("Variations", tmpModel, FlowGrid.Factory.INSTANCE);

		setResultConverter(bt -> {
			if (bt != ButtonType.CANCEL && pane.view().selectedCards.size() == 1) {
				return pane.view().selectedCards.iterator().next().printing();
			} else {
				return null;
			}
		});

		pane.view().doubleClick(ci -> {
			setResult(ci.printing());
			close();
		});

		pane.autoAction.set(ci -> {
			Platform.runLater(() -> {
				setResult(ci.printing());
				close();
			});
		});

		pane.autoEnabled.set(true);

		pane.showingVersionsSeparately.set(true);
		pane.setPrefHeight(scene.getHeight() / 1.5);
		pane.setPrefWidth(scene.getWidth() / 1.5);

		getDialogPane().setStyle(Preferences.get().theme.style());
		getDialogPane().setContent(pane);
		getDialogPane().getButtonTypes().setAll(ButtonType.OK, ButtonType.CANCEL);
		getDialogPane().lookupButton(ButtonType.OK).disableProperty().bind(Bindings.size(pane.view().selectedCards).isNotEqualTo(1));
		initModality(Modality.WINDOW_MODAL);
		initOwner(scene.getWindow());

		setOnShown(de -> pane.filter().requestFocus());
	}
}
