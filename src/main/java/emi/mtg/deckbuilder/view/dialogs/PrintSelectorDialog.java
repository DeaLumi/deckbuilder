package emi.mtg.deckbuilder.view.dialogs;

import emi.lib.mtg.Card;
import emi.mtg.deckbuilder.model.CardInstance;
import emi.mtg.deckbuilder.model.Preferences;
import emi.mtg.deckbuilder.view.components.CardPane;
import emi.mtg.deckbuilder.view.components.CardView;
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

public class PrintSelectorDialog extends Dialog<Card.Print> {
	public static Optional<Card.Print> show(Scene scene, Card card) {
		return new PrintSelectorDialog(scene, card).showAndWait();
	}

	private final CardPane pane;

	public PrintSelectorDialog(Scene scene, Card card) {
		super();

		setTitle("Print Selector");
		setResult(null);

		ObservableList<CardInstance> tmpModel = FXCollections.observableList(card.prints().stream()
				.map(CardInstance::new)
				.collect(Collectors.toList()));
		pane = new CardPane("Variations", tmpModel, CardView.LAYOUT_ENGINES.get(FlowGrid.Factory.class));

		setResultConverter(bt -> {
			if (bt != ButtonType.CANCEL && pane.view().selectedCards.size() == 1) {
				return pane.view().selectedCards.iterator().next().print();
			} else {
				return null;
			}
		});

		pane.view().doubleClick(ci -> {
			setResult(ci.print());
			close();
		});

		pane.autoAction.set(ci -> {
			Platform.runLater(() -> {
				setResult(ci.print());
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
