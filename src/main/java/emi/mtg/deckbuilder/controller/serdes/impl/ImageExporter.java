package emi.mtg.deckbuilder.controller.serdes.impl;

import emi.lib.mtg.game.Zone;
import emi.mtg.deckbuilder.controller.Context;
import emi.mtg.deckbuilder.controller.serdes.DeckImportExport;
import emi.mtg.deckbuilder.model.CardInstance;
import emi.mtg.deckbuilder.model.DeckList;
import emi.mtg.deckbuilder.view.components.CardView;
import emi.mtg.deckbuilder.view.groupings.ConvertedManaCost;
import emi.mtg.deckbuilder.view.layouts.FlowGrid;
import emi.mtg.deckbuilder.view.layouts.Piles;
import javafx.collections.ObservableList;
import javafx.embed.swing.SwingFXUtils;
import javafx.geometry.Orientation;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.Label;
import javafx.scene.layout.Background;
import javafx.scene.layout.BackgroundFill;
import javafx.scene.layout.FlowPane;
import javafx.scene.paint.Color;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;
import sun.reflect.generics.reflectiveObjects.NotImplementedException;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;

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
		throw new NotImplementedException();
	}

	@Override
	public void exportDeck(DeckList deck, File to) throws IOException {
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
					zone.getKey() == Zone.Command ? FlowGrid.Factory.INSTANCE : Piles.Factory.INSTANCE,
					Context.get().preferences.zoneGroupings.getOrDefault(zone.getKey(), ConvertedManaCost.INSTANCE),
					CardView.DEFAULT_SORTING);
			view.showFlagsProperty().set(false);
			view.cardScaleProperty().set(0.85);
			view.resize(1800.0, 1800.0);
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

		Scene scene = new Scene(box, Color.WHITE);
		BufferedImage img = SwingFXUtils.fromFXImage(scene.snapshot(null), null);

		BufferedImage buffer = new BufferedImage(img.getWidth(), img.getHeight(), BufferedImage.OPAQUE);
		buffer.createGraphics().drawImage(img, 0, 0, null);
		ImageIO.write(buffer, "jpg", to);
	}

	@Override
	public EnumSet<Feature> supportedFeatures() {
		return EnumSet.of(Feature.OtherZones, Feature.CardArt, Feature.Export);
	}
}
