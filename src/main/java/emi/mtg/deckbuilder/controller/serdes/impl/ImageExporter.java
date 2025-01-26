package emi.mtg.deckbuilder.controller.serdes.impl;

import emi.lib.mtg.game.Zone;
import emi.mtg.deckbuilder.controller.Context;
import emi.mtg.deckbuilder.controller.serdes.DeckImportExport;
import emi.mtg.deckbuilder.model.CardInstance;
import emi.mtg.deckbuilder.model.DeckList;
import emi.mtg.deckbuilder.model.Preferences;
import emi.mtg.deckbuilder.view.Images;
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
import javafx.scene.input.Clipboard;
import javafx.scene.input.ClipboardContent;
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
import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.function.BiConsumer;
import java.util.stream.Collectors;

public abstract class ImageExporter implements DeckImportExport, DeckImportExport.CopyPaste {
	public enum ImageFormat implements DeckImportExport.DataFormat {
		JPG("jpg", "image/jpeg", "Exports a JPEG file. When copying to clipboard, a temporary file is saved until the program exits."),
		PNG("png", "image/png", "Exports a PNG file. Image data is saved directly to clipboard."),
		;

		public final String extension, mime, description;

		ImageFormat(String extension, String mime, String description) {
			this.extension = extension;
			this.mime = mime;
			this.description = description;
		}

		@Override
		public String extension() {
			return extension;
		}

		@Override
		public String mime() {
			return mime;
		}

		@Override
		public String description() {
			return description;
		}

		@Override
		public javafx.scene.input.DataFormat fxFormat() {
			return javafx.scene.input.DataFormat.IMAGE;
		}

		@Override
		public Class<?> javaType() {
			return javafx.scene.image.Image.class;
		}
	}

	@Override
	public ImageFormat importFormat() {
		return null;
	}

	@Override
	public abstract ImageFormat exportFormat();

	@Override
	public DeckList importDeck(Path from) {
		throw new UnsupportedOperationException();
	}

	public double estimateViewWidth() {
		return UI.widthHint.getValue() * (Images.CARD_WIDTH * UI.cardScale.getValue() + Images.CARD_PADDING * 2);
	}

	@Override
	public void exportDeck(DeckList deck, Path to) throws IOException {
		if (!UI.promptForOptions(deck.format().deckZones())) return;
		ImageExporter.writeSafeImage(deckToImage(deck), exportFormat(), to);
	}

	@Override
	public DeckList importDeck(Clipboard from) {
		throw new UnsupportedOperationException();
	}

	public WritableImage deckToImage(DeckList deck) throws IOException {
		return ImageExporter.deckToImage(deck, (zone, view) -> {
			view.layout(UI.zoneLayouts.get(zone).getValue());
			view.grouping(UI.zoneGroupings.get(zone).getValue());
			view.uniqueness.set(UI.uniqueness.getValue());
			view.uniqueAcrossGroups.set(UI.uniqueAcrossGroups.isSelected());
			view.showFlagsProperty().set(false);
			view.cardScaleProperty().set(UI.cardScale.getValue());
			view.resize(estimateViewWidth(), Images.CARD_HEIGHT + Images.CARD_PADDING * 2);
		});
	}

	@Override
	public EnumSet<Feature> supportedFeatures() {
		return EnumSet.of(Feature.OtherZones, Feature.CardArt);
	}

	public static class PNG extends ImageExporter {
		@Override
		public String toString() {
			return "Image (PNG)";
		}

		@Override
		public ImageFormat exportFormat() {
			return ImageFormat.PNG;
		}

		@Override
		public void exportDeck(DeckList deck, ClipboardContent to) throws IOException {
			if (!UI.promptForOptions(deck.format().deckZones())) return;
			to.putImage(deckToImage(deck));
		}
	}

	public static class JPG extends ImageExporter {
		@Override
		public String toString() {
			return "Image (JPEG)";
		}

		@Override
		public ImageFormat exportFormat() {
			return ImageFormat.JPG;
		}

		@Override
		public void exportDeck(DeckList deck, ClipboardContent to) throws IOException {
			if (!UI.promptForOptions(deck.format().deckZones())) return;
			Path tmp = Files.createTempFile(deck.fileSafeName() + "-", ".jpg");
			writeSafeImage(deckToImage(deck), exportFormat(), tmp);
			to.putFiles(Collections.singletonList(tmp.toFile()));
			tmp.toFile().deleteOnExit(); // TODO: Is this really what I want? I wish I could make it live until the system shuts down...
		}
	}

	public static class ImageExportUI {
		private Alert alert;

		@FXML
		protected ComboBox<CardView.Uniqueness> uniqueness;

		@FXML
		protected CheckBox uniqueAcrossGroups;

		@FXML
		protected Slider cardScale;

		@FXML
		protected Label cardScaleText;

		@FXML
		protected Spinner<Integer> widthHint;

		@FXML
		protected GridPane grid;

		private Map<Zone, Label> zoneLabels = new EnumMap<>(Zone.class);
		private Map<Zone, FlowPane> zoneFlows = new EnumMap<>(Zone.class);
		private Map<Zone, ComboBox<CardView.LayoutEngine>> zoneLayouts = new EnumMap<>(Zone.class);
		private Map<Zone, ComboBox<CardView.Grouping>> zoneGroupings = new EnumMap<>(Zone.class);

		private void initUI() {
			alert = AlertBuilder.query(null)
					.modal(Modality.APPLICATION_MODAL)
					.buttons(ButtonType.OK, ButtonType.CANCEL)
					.title("Image Export Settings")
					.headerText("Please enter image export settings.")
					.onShown(x -> FxUtils.transfer(x, FxUtils.pointerScreen()))
					.get();

			FxUtils.FXML(this, alert.getDialogPane());

			widthHint.getValueFactory().setValue(10);
			uniqueness.setItems(FXCollections.observableArrayList(CardView.Uniqueness.values()));
			uniqueness.getSelectionModel().select(CardView.Uniqueness.Prints);
			uniqueAcrossGroups.setSelected(true);
			cardScaleText.textProperty().bind(cardScale.valueProperty().multiply(100).asString("%.0f%%"));

			int i = GridPane.getRowIndex(widthHint) + 1;
			for (Zone zone : Zone.values()) {
				Label label = new Label(String.format("%s:", zone.name()));
				GridPane.setHalignment(label, HPos.RIGHT);
				grid.add(label, 0, i);

				Label layoutLabel = new Label("Layout:");
				ComboBox<CardView.LayoutEngine> layout = new ComboBox<>(FXCollections.observableArrayList(CardView.LAYOUT_ENGINES.values()));
				layout.getSelectionModel().select(zone == Zone.Command ? CardView.LAYOUT_ENGINES.get(FlowGrid.class) : CardView.LAYOUT_ENGINES.get(Piles.class));

				Label groupingLabel = new Label("Grouping:");
				ComboBox<CardView.Grouping> grouping = new ComboBox<>(FXCollections.observableArrayList(CardView.GROUPINGS.values()));
				grouping.getSelectionModel().select(Preferences.get().zoneGroupings.getOrDefault(zone, CardView.GROUPINGS.get(ManaValue.class)));

				zoneLabels.put(zone, label);
				zoneFlows.put(zone, new FlowPane(2.0, 0.0, layoutLabel, layout, groupingLabel, grouping));
				zoneLayouts.put(zone, layout);
				zoneGroupings.put(zone, grouping);

				grid.add(zoneFlows.get(zone), 1, i);
				++i;
			}
		}

		private boolean promptForOptions(Collection<Zone> zones) {
			if (alert == null) initUI();

			int i = GridPane.getRowIndex(widthHint) + 1;
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
	}

	private static final ImageExportUI UI = new ImageExportUI();

	public static WritableImage deckToImage(DeckList deck, BiConsumer<Zone, CardView> viewModifier) throws IOException {
		// Prefetch all deck images.
		CompletableFuture[] futures = deck.cards().values().stream()
				.flatMap(List::stream)
				.map(CardInstance::print)
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

			CardView view = new CardView(
					null,
					zone.getValue(),
					CardView.LAYOUT_ENGINES.get(Piles.class),
					CardView.GROUPINGS.get(ManaValue.class),
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

	private static void writeSafeImage(WritableImage fxImg, String extension, OutputStream stream) throws IOException {
		BufferedImage img = SwingFXUtils.fromFXImage(fxImg, null);
		BufferedImage buffer = new BufferedImage(img.getWidth(), img.getHeight(), BufferedImage.OPAQUE);
		buffer.createGraphics().drawImage(img, 0, 0, null);
		ImageIO.write(buffer, extension, stream);
	}

	private static void writeSafeImage(WritableImage fxImg, ImageFormat format, Path target) throws IOException {
		try (OutputStream output = Files.newOutputStream(target)) {
			writeSafeImage(fxImg, format.extension(), output);
		}
	}

	private static void deckToImageFile(DeckList deck, BiConsumer<Zone, CardView> viewModifier, ImageFormat format, Path to) throws IOException {
		writeSafeImage(deckToImage(deck, viewModifier), format, to);
	}
}
