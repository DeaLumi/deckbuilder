package emi.mtg.deckbuilder.controller.serdes.impl;

import emi.lib.mtg.game.Zone;
import emi.mtg.deckbuilder.controller.Context;
import emi.mtg.deckbuilder.controller.serdes.DeckImportExport;
import emi.mtg.deckbuilder.model.CardInstance;
import emi.mtg.deckbuilder.model.DeckList;
import emi.mtg.deckbuilder.model.Preferences;
import emi.mtg.deckbuilder.view.components.CardView;
import emi.mtg.deckbuilder.view.groupings.ManaValue;
import emi.mtg.deckbuilder.view.layouts.FlowGrid;
import emi.mtg.deckbuilder.view.layouts.Piles;
import emi.mtg.deckbuilder.view.util.AlertBuilder;
import emi.mtg.deckbuilder.view.util.FxUtils;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.embed.swing.SwingFXUtils;
import javafx.fxml.FXML;
import javafx.geometry.HPos;
import javafx.geometry.Orientation;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.image.WritableImage;
import javafx.scene.layout.Background;
import javafx.scene.layout.BackgroundFill;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.GridPane;
import javafx.scene.paint.Color;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;
import javafx.stage.Modality;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.function.BiConsumer;

public class ImageExporter implements DeckImportExport {
	@Override
	public String toString() {
		return "Image";
	}

	@Override
	public String extension() {
		return "jpg";
	}

	@Override
	public DeckList importDeck(File from) {
		throw new UnsupportedOperationException();
	}

	@Override
	public void exportDeck(DeckList deck, File to) throws IOException {
		if (!promptForOptions(deck.format().deckZones())) return;

		deckToImageFile(deck, (zone, view) -> {
			view.layout(zoneLayouts.get(zone).getValue());
			view.grouping(zoneGroupings.get(zone).getValue());
			view.showFlagsProperty().set(false);
			view.collapseDuplicatesProperty().set(collapseCopies.isSelected());
			view.cardScaleProperty().set(cardScale.getValue());
			view.resize(widthHint.getValue().doubleValue(), heightHint.getValue().doubleValue());
		}, to.toPath());
	}

	@Override
	public EnumSet<Feature> supportedFeatures() {
		return EnumSet.of(Feature.OtherZones, Feature.CardArt, Feature.Export);
	}

	private final Alert alert;

	@FXML
	protected CheckBox collapseCopies;

	@FXML
	protected Slider cardScale;

	@FXML
	protected Label cardScaleText;

	@FXML
	protected Spinner<Double> widthHint;

	@FXML
	protected Spinner<Double> heightHint;

	@FXML
	protected GridPane grid;

	private Map<Zone, Label> zoneLabels = new EnumMap<>(Zone.class);
	private Map<Zone, FlowPane> zoneFlows = new EnumMap<>(Zone.class);
	private Map<Zone, ComboBox<CardView.LayoutEngine.Factory>> zoneLayouts = new EnumMap<>(Zone.class);
	private Map<Zone, ComboBox<CardView.Grouping>> zoneGroupings = new EnumMap<>(Zone.class);

	public ImageExporter() {
		alert = AlertBuilder.query(null)
				.modal(Modality.APPLICATION_MODAL)
				.buttons(ButtonType.OK, ButtonType.CANCEL)
				.title("Image Export Settings")
				.headerText("Please enter image export settings.")
				.get();

		FxUtils.FXML(this, alert.getDialogPane());

		widthHint.getValueFactory().setValue(2500.0);
		heightHint.getValueFactory().setValue(2500.0);
		cardScaleText.textProperty().bind(cardScale.valueProperty().multiply(100).asString("%.0f%%"));

		int i = 3;
		for (Zone zone : Zone.values()) {
			Label label = new Label(String.format("%s:", zone.name()));
			GridPane.setHalignment(label, HPos.RIGHT);
			grid.add(label, 0, i);

			Label layoutLabel = new Label("Layout:");
			ComboBox<CardView.LayoutEngine.Factory> layout = new ComboBox<>(FXCollections.observableList(CardView.LAYOUT_ENGINES));
			layout.getSelectionModel().select(zone == Zone.Command ? FlowGrid.Factory.INSTANCE : Piles.Factory.INSTANCE);

			Label groupingLabel = new Label("Grouping:");
			ComboBox<CardView.Grouping> grouping = new ComboBox<>(FXCollections.observableList(CardView.GROUPINGS));
			grouping.getSelectionModel().select(Preferences.get().zoneGroupings.getOrDefault(zone, ManaValue.INSTANCE));

			zoneLabels.put(zone, label);
			zoneFlows.put(zone, new FlowPane(2.0, 0.0, layoutLabel, layout, groupingLabel, grouping));
			zoneLayouts.put(zone, layout);
			zoneGroupings.put(zone, grouping);

			grid.add(zoneFlows.get(zone), 1, i);
			++i;
		}
	}

	private boolean promptForOptions(Collection<Zone> zones) {
		int i = 3;
		for (Zone zone : Zone.values()) {
			if (zones.contains(zone)) {
				GridPane.setRowIndex(zoneLabels.get(zone), i);
				zoneLabels.get(zone).setManaged(true);
				zoneLabels.get(zone).setVisible(true);
				GridPane.setRowIndex(zoneFlows.get(zone), i);
				zoneFlows.get(zone).setManaged(true);
				zoneFlows.get(zone).setVisible(true);
				++i;
			} else {
				GridPane.setRowIndex(zoneLabels.get(zone), 0);
				zoneLabels.get(zone).setManaged(false);
				zoneLabels.get(zone).setVisible(false);
				GridPane.setRowIndex(zoneFlows.get(zone), 0);
				zoneFlows.get(zone).setManaged(false);
				zoneFlows.get(zone).setVisible(false);
			}
		}

		return alert.showAndWait().orElse(ButtonType.CANCEL) == ButtonType.OK;
	}

	public static WritableImage deckToImage(DeckList deck, BiConsumer<Zone, CardView> viewModifier) throws IOException {
		// Prefetch all deck images.
		CompletableFuture[] futures = deck.cards().values().stream()
				.flatMap(List::stream)
				.map(CardInstance::printing)
				.map(Context.get().images::getThumbnail)
				.toArray(CompletableFuture[]::new);

		try {
			CompletableFuture.allOf(futures).get();
		} catch (InterruptedException e) {
			throw new IOException(e);
		} catch (ExecutionException e) {
			throw new IOException(e.getCause());
		}

		FlowPane box = new FlowPane(Orientation.HORIZONTAL);
		box.setBackground(new Background(new BackgroundFill(Color.TRANSPARENT, null, null)));
		box.setAlignment(Pos.CENTER);
		box.setPrefWrapLength(1.0);

		List<Label> labels = new ArrayList<>();
		double maxCVWidth = 0;

		for (Map.Entry<Zone, ObservableList<CardInstance>> zone : deck.cards().entrySet()) {
			if (zone.getValue().isEmpty()) continue;

			CardView view = new CardView(zone.getValue(),
					CardView.LAYOUT_ENGINES.get(0),
					CardView.GROUPINGS.get(0),
					CardView.DEFAULT_SORTING);
			viewModifier.accept(zone.getKey(), view);
			view.resize(view.prefWidth(-1), view.prefHeight(1800.0));
			view.layout();
			view.renderNow();
			maxCVWidth = Math.max(view.getWidth(), maxCVWidth);

			Label label = new Label(zone.getKey().name().toUpperCase());
			label.setBackground(new Background(new BackgroundFill(Color.DARKGREY.darker(), null, null)));
			label.setTextFill(Color.WHITE);
			label.setAlignment(Pos.CENTER);
			label.setMinWidth(2.0);
			label.setMaxWidth(Double.MAX_VALUE);
			label.setPrefHeight(48.0);
			label.setMaxHeight(Double.MAX_VALUE);
			label.setFont(Font.font(null, FontWeight.BOLD, 32.0));
			label.layout();
			labels.add(label);

			box.getChildren().add(label);
			box.getChildren().add(view);
		}

		for (Label label : labels) {
			label.setPrefWidth(maxCVWidth * 0.95);
		}

		box.resize(box.prefWidth(-1), box.prefHeight(-1));

		Scene scene = new Scene(box, Preferences.get().theme.base);
		return scene.snapshot(null);
	}

	public static void writeSafeImage(WritableImage fxImg, Path target) throws IOException {
		String fn = target.getFileName().toString();

		BufferedImage img = SwingFXUtils.fromFXImage(fxImg, null);
		BufferedImage buffer = new BufferedImage(img.getWidth(), img.getHeight(), BufferedImage.OPAQUE);
		buffer.createGraphics().drawImage(img, 0, 0, null);
		ImageIO.write(buffer, fn.substring(fn.indexOf('.') + 1), target.toFile());
	}

	public static void deckToImageFile(DeckList deck, BiConsumer<Zone, CardView> viewModifier, Path to) throws IOException {
		writeSafeImage(deckToImage(deck, viewModifier), to);
	}
}
