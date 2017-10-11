package emi.mtg.deckbuilder.view.components;

import emi.lib.mtg.game.Zone;
import emi.mtg.deckbuilder.controller.Context;
import emi.mtg.deckbuilder.model.DeckList;
import javafx.beans.binding.Bindings;
import javafx.scene.control.*;
import javafx.scene.input.KeyCode;
import javafx.scene.input.MouseButton;

import java.util.EnumMap;

public class VariantPane extends Tab {
	public final EnumMap<Zone, CardPane> deckPanes;

	public VariantPane(Context context, DeckList.Variant variant) {
		setText("");

		Label label = new Label();
		label.textProperty().bind(variant.nameProperty());

		TextField nameInput = new TextField();

		setGraphic(label);

		label.setOnMouseClicked(me -> {
			if (me.getButton() == MouseButton.PRIMARY && me.getClickCount() == 2) {
				nameInput.setText(variant.name());
				setGraphic(nameInput);
			}
		});

		nameInput.setOnAction(ae -> {
			if (!nameInput.getText().trim().isEmpty()) {
				variant.nameProperty().setValue(nameInput.getText());
			}

			setGraphic(label);
		});

		nameInput.setOnKeyPressed(ke -> {
			if (ke.getCode() == KeyCode.ESCAPE) {
				setGraphic(label);
			}
		});

		SplitPane splitter = new SplitPane();

		this.deckPanes = new EnumMap<>(Zone.class);

		for (Zone zone : Zone.values()) {
			CardPane deckZone = new CardPane(zone.name(), context, variant.cards(zone), "Piles", CardView.DEFAULT_SORTING);
			deckZone.view().doubleClick(ci -> deckZone.model().remove(ci));
			deckPanes.put(zone, deckZone);

			if (variant.deck().format().deckZones().contains(zone)) {
				splitter.getItems().add(deckZone);
			}
		}

		setContent(splitter);

		setOnCloseRequest(e -> {
			if (variant.deck().variants().size() == 1) {
				return; // noooo
			}

			Alert confirm = new Alert(Alert.AlertType.WARNING, "This operation can't be undone!", ButtonType.YES, ButtonType.NO);
			confirm.initOwner(this.getTabPane().getScene().getWindow());
			confirm.setTitle("Confirm Deletion");
			confirm.setHeaderText(String.format("Delete %s?", variant.name()));

			if (confirm.showAndWait().orElse(ButtonType.NO) == ButtonType.YES) {
				variant.deck().variants().remove(variant);
				return;
			}

			e.consume();
		});

		closableProperty().bind(Bindings.size(variant.deck().variants()).greaterThan(1));
	}
}
