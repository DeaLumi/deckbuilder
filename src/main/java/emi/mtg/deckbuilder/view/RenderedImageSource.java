package emi.mtg.deckbuilder.view;

import emi.lib.Service;
import emi.lib.mtg.card.Card;
import emi.lib.mtg.data.ImageSource;
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

	private final Map<Card, URL> imageCache = new HashMap<>();

	private final int WIDTH = 800;
	private final int HEIGHT = 1120;

	private final Font NAME_FONT = Font.font("Beleren", FontWeight.BOLD, WIDTH / 20.0);
	private final Font TEXT_FONT = Font.font("serif", WIDTH / 22.5);
	private final Font FLAVOR_FONT = Font.font("serif", FontPosture.ITALIC, WIDTH / 22.5);

	@Override
	public URL find(Card card) {
		return imageCache.computeIfAbsent(card, c -> {
			File f = new File(new File(String.format("rs%s", c.set().code())), String.format("%s%s.png", c.name(), c.variation() > 0 ? Integer.toString(c.variation()) : ""));

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

			Text name = new Text(c.name());
			name.setFont(NAME_FONT);
			Text cost = new Text(c.manaCost().toString());
			cost.setFont(NAME_FONT);
			cost.setTextAlignment(TextAlignment.RIGHT);
			nameCostLine.setLeft(name);
			nameCostLine.setRight(cost);
			VBox.setMargin(nameCostLine, new Insets(12, 12, 12, 12));

			nameTypeLine.getChildren().add(nameCostLine);

			BorderPane typeRarityLine = new BorderPane();

			Text type = new Text(c.type().toString());
			type.setFont(NAME_FONT);
			Text setRarity = new Text(String.format("%s-%s", c.rarity().name().substring(0,1), c.set().code()));
			setRarity.setFont(NAME_FONT);
			setRarity.setTextAlignment(TextAlignment.RIGHT);
			typeRarityLine.setLeft(type);
			typeRarityLine.setRight(setRarity);
			VBox.setMargin(typeRarityLine, new Insets(12, 12, 12, 12));

			nameTypeLine.getChildren().add(typeRarityLine);

			layout.setTop(nameTypeLine);

			TextFlow textBox = new TextFlow();
			Text rules = new Text(c.text() + "\n\n");
			rules.setFont(TEXT_FONT);
			Text flavor = new Text(c.flavor());
			flavor.setFont(FLAVOR_FONT);

			textBox.getChildren().addAll(rules, flavor);
			BorderPane.setMargin(textBox, new Insets(12, 12, 12, 12));

			layout.setCenter(textBox);

			Text ptbox;
			if (c.power() != null && c.toughness() != null) {
				ptbox = new Text(String.format("%s / %s", c.power(), c.toughness()));
			} else if (c.loyalty() != null) {
				ptbox = new Text(c.loyalty());
			} else {
				ptbox = new Text("");
			}
			ptbox.setTextAlignment(TextAlignment.RIGHT);
			ptbox.setFont(NAME_FONT);
			BorderPane ptboxbox = new BorderPane();
			ptboxbox.setRight(ptbox);
			layout.setBottom(ptboxbox);
			BorderPane.setMargin(ptboxbox, new Insets(12, 12, 12, 12));

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

			Scene scene = new Scene(layout, WIDTH, HEIGHT, Color.TRANSPARENT);

			if (!f.getParentFile().exists() && !f.mkdirs()) {
				throw new Error("Unable to create parent directories for " + f.getAbsolutePath());
			}

			try {
				WritableImage image = scene.snapshot(null);
				ImageIO.write(SwingFXUtils.fromFXImage(image, null), "png", f);
				return f.toURI().toURL();
			} catch (IOException e) {
				throw new Error(e);
			}
		});
	}
}
