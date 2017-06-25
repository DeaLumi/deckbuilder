package emi.mtg.deckbuilder.view;

import emi.lib.Service;
import emi.lib.mtg.card.Card;
import emi.lib.mtg.data.ImageSource;
import javafx.application.Platform;
import javafx.embed.swing.SwingFXUtils;
import javafx.geometry.Insets;
import javafx.scene.Scene;
import javafx.scene.image.WritableImage;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;
import javafx.scene.paint.Paint;
import javafx.scene.text.*;

import javax.imageio.ImageIO;
import java.io.File;
import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.HashMap;
import java.util.Map;

/**
 * Created by Emi on 6/19/2017.
 */
@Service.Provider(ImageSource.class)
@Service.Property.String(name="name", value="Cheap Card Renderer")
public class RenderedImageSource implements ImageSource {

	private static final File PARENT_DIR = new File(new File("images"), "rendered");

	static {
		if (!PARENT_DIR.exists() && !PARENT_DIR.mkdirs()) {
			throw new Error("Unable to create a directory for rendered images...");
		}
	}

	private final Map<Card, URL> imageCache = new HashMap<>();

	private final int WIDTH = 200;
	private final int HEIGHT = 280;

	private final Font NAME_FONT = Font.font("Beleren", FontWeight.BOLD, WIDTH / 20.0);
	private final Font TEXT_FONT = Font.font("serif", WIDTH / 22.5);
	private final Font FLAVOR_FONT = Font.font("serif", FontPosture.ITALIC, WIDTH / 22.5);

	@Override
	public URL find(Card card) {
		if (!imageCache.containsKey(card)) {
			File f = new File(new File(PARENT_DIR, String.format("s%s", card.set().code())), String.format("%s%s.png", card.name(), card.variation() > 0 ? Integer.toString(card.variation()) : ""));

			if (f.exists()) {
				try {
					return f.toURI().toURL();
				} catch (MalformedURLException e) {
					throw new Error(e);
				}
			}

			BorderPane layout = new BorderPane();
			layout.setBorder(new Border(new BorderStroke(Color.BLACK, BorderStrokeStyle.SOLID, new CornerRadii(WIDTH / 15.0), new BorderWidths(WIDTH / 25.0))));

			VBox nameTypeLine = new VBox();
			BorderPane nameCostLine = new BorderPane();

			Text name = new Text(card.name());
			name.setFont(NAME_FONT);
			Text cost = new Text(card.manaCost().toString());
			cost.setFont(NAME_FONT);
			cost.setTextAlignment(TextAlignment.RIGHT);
			nameCostLine.setLeft(name);
			nameCostLine.setRight(cost);
			VBox.setMargin(nameCostLine, new Insets(WIDTH / 40.0, WIDTH / 40.0, WIDTH / 40.0, WIDTH / 40.0));

			nameTypeLine.getChildren().add(nameCostLine);

			Text type = new Text(card.type().toString());
			type.setFont(NAME_FONT);
			Text setRarity = new Text(String.format("%s-%s", card.rarity().name().substring(0,1), card.set().code()));
			setRarity.setFont(NAME_FONT);
			setRarity.setTextAlignment(TextAlignment.RIGHT);

			AnchorPane typeRarityLine = new AnchorPane(type, setRarity);
			AnchorPane.setLeftAnchor(type, 0.0);
			AnchorPane.setRightAnchor(setRarity, 0.0);

			VBox.setMargin(typeRarityLine, new Insets(WIDTH / 40.0, WIDTH / 40.0, WIDTH / 40.0, WIDTH / 40.0));

			nameTypeLine.getChildren().add(typeRarityLine);

			layout.setTop(nameTypeLine);

			TextFlow textBox = new TextFlow();
			Text rules = new Text(card.text() + "\n\n");
			rules.setFont(TEXT_FONT);
			Text flavor = new Text(card.flavor());
			flavor.setFont(FLAVOR_FONT);

			textBox.getChildren().addAll(rules, flavor);
			BorderPane.setMargin(textBox, new Insets(WIDTH / 40.0, WIDTH / 40.0, WIDTH / 40.0, WIDTH / 40.0));

			layout.setCenter(textBox);

			Text ptbox;
			if (!card.power().isEmpty() && !card.toughness().isEmpty()) {
				ptbox = new Text(String.format("%s / %s", card.power(), card.toughness()));
			} else if (!card.loyalty().isEmpty()) {
				ptbox = new Text(card.loyalty());
			} else {
				ptbox = new Text("");
			}
			ptbox.setTextAlignment(TextAlignment.RIGHT);
			ptbox.setFont(NAME_FONT);
			BorderPane ptboxbox = new BorderPane();
			ptboxbox.setRight(ptbox);
			layout.setBottom(ptboxbox);
			BorderPane.setMargin(ptboxbox, new Insets(WIDTH / 40.0, WIDTH / 40.0, WIDTH / 40.0, WIDTH / 40.0));

			Color bgColor;
			if (card.color().size() > 1) {
				bgColor = Color.PALEGOLDENROD;
			} else if (card.color().isEmpty()) {
				bgColor = Color.LIGHTGRAY;
			} else {
				switch (card.color().iterator().next()) {
					case WHITE:
						bgColor = Color.WHITE;
						break;
					case BLUE:
						bgColor = Color.LIGHTBLUE;
						break;
					case BLACK:
						bgColor = Color.DARKGRAY;
						break;
					case RED:
						bgColor = Color.LIGHTPINK;
						break;
					case GREEN:
						bgColor = Color.PALEGREEN;
						break;
					case COLORLESS:
						bgColor = Color.LIGHTGRAY;
						break;
					default:
						throw new IllegalStateException();
				}
			}

			layout.setBackground(new Background(new BackgroundFill(bgColor, new CornerRadii(WIDTH / 12.0), null)));

			Platform.runLater(() -> {
				Scene scene = new Scene(layout, WIDTH, HEIGHT, Color.TRANSPARENT);

				if (!f.getParentFile().exists() && !f.mkdirs()) {
					throw new Error("Unable to create parent directories for " + f.getAbsolutePath());
				}

				try {
					WritableImage image = scene.snapshot(null);
					ImageIO.write(SwingFXUtils.fromFXImage(image, null), "png", f);

					synchronized(RenderedImageSource.this) {
						imageCache.put(card, f.toURI().toURL());
					}
				} catch (IOException e) {
					throw new Error(e);
				}
			});

			while (!imageCache.containsKey(card)) {
				try {
					Thread.sleep(10);
				} catch (InterruptedException e) {
					e.printStackTrace();
					return null;
				}
			}
		}

		return imageCache.get(card);
	}
}
