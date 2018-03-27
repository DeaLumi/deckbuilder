package emi.mtg.deckbuilder.view.components;

import emi.lib.mtg.game.Zone;
import emi.mtg.deckbuilder.controller.Context;
import emi.mtg.deckbuilder.model.CardInstance;
import emi.mtg.deckbuilder.model.DeckList;
import emi.mtg.deckbuilder.view.MainWindow;
import javafx.beans.binding.Bindings;
import javafx.collections.ObservableList;
import javafx.scene.control.*;
import javafx.scene.input.KeyCode;
import javafx.scene.input.MouseButton;

import java.util.EnumMap;
import java.util.List;
import java.util.stream.Collectors;

public class VariantPane extends Tab {
	public final DeckList.Variant variant;
	public final EnumMap<Zone, CardPane> deckPanes;

	public VariantPane(MainWindow window, Context context, DeckList.Variant variant) {
		this.variant = variant;

		setText("");

		Label label = new Label();
		label.textProperty().bind(variant.nameProperty());

		TextField nameInput = new TextField();

		setGraphic(label);

		ContextMenu labelMenu = new ContextMenu();

		MenuItem infoMenuItem = new MenuItem("Info...");
		infoMenuItem.setOnAction(ae -> window.showVariantInfo(variant));

		MenuItem duplicateMenuItem = new MenuItem("Duplicate");
		duplicateMenuItem.setOnAction(ae -> window.duplicateVariant(variant));

		MenuItem exportMenuItem = new MenuItem("Export...");
		exportMenuItem.setOnAction(ae -> window.exportVariant(variant));

		labelMenu.getItems().setAll(infoMenuItem, duplicateMenuItem, new SeparatorMenuItem(), exportMenuItem);
		label.setContextMenu(labelMenu);

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

		CardView.ContextMenu contextMenu = createZoneContextMenu(context, window);

		SplitPane splitter = new SplitPane();

		this.deckPanes = new EnumMap<>(Zone.class);

		for (Zone zone : Zone.values()) {
			CardPane deckZone = new CardPane(zone.name(), context, variant.cards(zone), "Piles", CardView.DEFAULT_SORTING);
			deckZone.view().doubleClick(ci -> {
				deckZone.model().remove(ci);
				window.collection().view().scheduleRender();
			});
			deckZone.view().contextMenu(contextMenu);
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

	private CardView.ContextMenu createZoneContextMenu(Context context, MainWindow window) {
		CardView.ContextMenu zoneContextMenu = new CardView.ContextMenu();

		MenuItem removeAllMenuItem = new MenuItem("Remove All");
		removeAllMenuItem.visibleProperty().bind(zoneContextMenu.cards.sizeProperty().isEqualTo(1));
		removeAllMenuItem.setOnAction(ae -> {
			zoneContextMenu.view.get().model().removeIf(ci -> zoneContextMenu.cards.get().stream().anyMatch(ciSel -> ciSel.card() == ci.card()));
			zoneContextMenu.cards.clear();
			window.collection().view().scheduleRender();
		});

		Menu moveAllMenu = new Menu("Move All To");
		moveAllMenu.visibleProperty().bind(zoneContextMenu.cards.sizeProperty().isEqualTo(1));

		for (Zone zone : Zone.values()) {
			MenuItem moveToZoneMenuItem = new MenuItem(zone.name());
			moveToZoneMenuItem.visibleProperty().bind(Bindings.createBooleanBinding(() -> {
				if (deckPanes == null || deckPanes.get(zone) == null) {
					return false;
				}

				if (zoneContextMenu.view.get() == null) {
					return false;
				}

				return context.deck.format().deckZones().contains(zone) && zoneContextMenu.view.get().model() != deckPanes.get(zone).model();
			}, zoneContextMenu.view));
			moveToZoneMenuItem.setOnAction(ae -> {
				for (CardInstance card : zoneContextMenu.cards) {
					ObservableList<CardInstance> sourceModel = zoneContextMenu.view.get().model();
					ObservableList<CardInstance> targetModel = deckPanes.get(zone).model();

					List<CardInstance> moving = sourceModel.stream().filter(ci -> ci.card().equals(card.card())).collect(Collectors.toList());
					sourceModel.removeAll(moving);
					targetModel.addAll(moving);
				}
				zoneContextMenu.cards.clear();
				window.collection().view().scheduleRender();
			});
			moveAllMenu.getItems().add(moveToZoneMenuItem);
		}

		zoneContextMenu.getItems().addAll(removeAllMenuItem, moveAllMenu);

		return zoneContextMenu;
	}
}
