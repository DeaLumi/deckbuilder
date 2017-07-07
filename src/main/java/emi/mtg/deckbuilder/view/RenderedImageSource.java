package emi.mtg.deckbuilder.view;

import emi.lib.Service;
import emi.lib.mtg.card.CardFace;
import emi.lib.mtg.data.ImageSource;
import javafx.application.Platform;
import javafx.concurrent.Task;
import javafx.embed.swing.SwingFXUtils;
import javafx.geometry.HPos;
import javafx.geometry.VPos;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.control.Label;
import javafx.scene.image.WritableImage;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;
import javafx.scene.text.Font;
import javafx.scene.text.FontPosture;
import javafx.scene.text.FontWeight;

import javax.imageio.ImageIO;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.EnumMap;
import java.util.Map;

@Service.Provider(ImageSource.class)
@Service.Property.String(name="name", value="Cheap Card Renderer")
@Service.Property.Number(name="priority", value=0.01)
public class RenderedImageSource implements ImageSource {

	private static final File PARENT_DIR = new File(new File("images"), "rendered");

	static {
		if (!PARENT_DIR.exists() && !PARENT_DIR.mkdirs()) {
			throw new Error("Unable to create a directory for rendered images...");
		}
	}

	private static Font fallback(FontWeight weight, FontPosture posture, double size, String... families) {
		Font font;

		for(String family : families) {
			font = Font.font(family, weight, posture, size);

			if (family.toLowerCase().equals(font.getFamily().toLowerCase())) {
				return font;
			}
		}

		return Font.font(Font.getDefault().getFamily(), weight, posture, size);
	}

	private static Font fallback(FontWeight weight, double size, String... families) {
		return fallback(weight, FontPosture.REGULAR, size, families);
	}

	private static Font fallback(FontPosture posture, double size, String... families) {
		return fallback(FontWeight.NORMAL, posture, size, families);
	}

	private static Font fallback(double size, String... families) {
		return fallback(FontWeight.NORMAL, FontPosture.REGULAR, size, families);
	}

	private static final int WIDTH = 400;
	private static final int HEIGHT = 560;

	private static final String[] NAME_FAMILIES = { "Beleren", "Gaudy Medieval", "Times New Roman", "serif" };
	private static final String[] TEXT_FAMILIES = { "Gaudy Medieval", "Times New Roman", "serif" };

	private static final Font NAME_FONT = fallback(FontWeight.BOLD, WIDTH / 15.0, NAME_FAMILIES);
	private static final Font TEXT_FONT = fallback(WIDTH / 18.5, TEXT_FAMILIES);
	private static final Font FLAVOR_FONT = fallback(FontPosture.ITALIC, WIDTH / 18.5, TEXT_FAMILIES);

	private static class CardRenderLayout extends Pane {
		public enum Characteristic {
			Name,
			ManaCost,
			TypeLine,
			SetCode,
			RulesText,
			FlavorText,
			PTBox
		}

		private Map<Characteristic, Node> nodes = new EnumMap<>(Characteristic.class);

		public CardRenderLayout(CardFace face) {
			setBorder(new Border(new BorderStroke(Color.BLACK, BorderStrokeStyle.SOLID, new CornerRadii(WIDTH / 20.0), new BorderWidths(WIDTH / 30.0))));

			Color bgColor;
			if (face.color().size() > 1) {
				bgColor = Color.PALEGOLDENROD;
			} else if (face.color().isEmpty()) {
				bgColor = Color.LIGHTGRAY;
			} else {
				switch (face.color().iterator().next()) {
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

			setBackground(new Background(new BackgroundFill(bgColor, new CornerRadii(WIDTH / 15.0), null)));

			{
				Label name = new Label(face.name());
				name.setFont(NAME_FONT);
				getChildren().add(name);
				nodes.put(Characteristic.Name, name);
			}

			{
				Label mc = new Label(face.manaCost().toString());
				mc.setFont(NAME_FONT);
				getChildren().add(mc);
				nodes.put(Characteristic.ManaCost, mc);
			}

			{
				Label type = new Label(face.type().toString());
				type.setFont(NAME_FONT);
				getChildren().add(type);
				nodes.put(Characteristic.TypeLine, type);
			}

			{
				Label set = new Label(String.format("%s-%s", face.card().set().code(), face.card().rarity().name().substring(0, 1)));
				set.setFont(NAME_FONT);
				getChildren().add(set);
				nodes.put(Characteristic.SetCode, set);
			}

			{
				Label rules = new Label(face.text());
				rules.setWrapText(true);
				rules.setFont(TEXT_FONT);
				getChildren().add(rules);
				nodes.put(Characteristic.RulesText, rules);
			}

			{
				Label flavor = new Label(face.flavor());
				flavor.setWrapText(true);
				flavor.setFont(FLAVOR_FONT);
				getChildren().add(flavor);
				nodes.put(Characteristic.FlavorText, flavor);
			}

			{
				Label pt;
				if (!face.power().isEmpty() && !face.toughness().isEmpty()) {
					pt = new Label(String.format("%s / %s", face.power(), face.toughness()));
				} else if (!face.loyalty().isEmpty()) {
					pt = new Label(face.loyalty());
				} else {
					pt = new Label("");
				}
				pt.setFont(NAME_FONT);
				getChildren().add(pt);
				nodes.put(Characteristic.PTBox, pt);
			}
		}

		protected void layoutManaCost(Node mc) {
			double w = mc.prefWidth(getWidth() / 20.0);
			double h = mc.prefHeight(w);
			double x = getWidth() * 19.0 / 20.0 - w;
			double y = getWidth() / 20.0;

			layoutInArea(mc, x, y, w, h, 0.0, HPos.RIGHT, VPos.TOP);
		}

		protected void layoutName(Node name, Node mc) {
			double w = name.prefWidth(getWidth() / 20.0);
			double h = name.prefHeight(w);
			double x = getWidth() / 20.0;
			double y = getWidth() / 20.0;

			w = Math.min(w, mc.getBoundsInParent().getMinX() - x);

			layoutInArea(name, x, y, w, h, 0.0, HPos.LEFT, VPos.TOP);
		}

		protected void layoutSetCode(Node set, Node mc) {
			double w = set.prefWidth(getWidth() / 20.0);
			double h = set.prefHeight(w);
			double x = getWidth() * 19.0 / 20.0 - w;
			double y = mc.getBoundsInParent().getMaxY() + getWidth() / 40.0;

			layoutInArea(set, x, y, w, h, 0.0, HPos.RIGHT, VPos.TOP);
		}

		protected void layoutType(Node type, Node name, Node set) {
			double w = type.prefWidth(getWidth() / 20.0);
			double h = type.prefWidth(w);
			double x = getWidth() / 20.0;
			double y = name.getBoundsInParent().getMaxY() + getWidth() / 40.0;

			w = Math.min(w, set.getBoundsInParent().getMinX() - x);

			layoutInArea(type, x, y, w, h, 0.0, HPos.RIGHT, VPos.TOP);
		}

		protected void layoutRules(Node rules, Node type) {
			double w = getWidth() * 18.0 / 20.0;
			double x = getWidth() / 20.0;
			double y = type.getBoundsInParent().getMaxY() + getWidth() / 40.0;
			double h = (getHeight() * 18.0 / 20.0 - y) * 3.0 / 4.0;

			layoutInArea(rules, x, y, w, h, 0.0, HPos.LEFT, VPos.TOP);
		}

		protected void layoutFlavor(Node flavor, Node rules) {
			double x = getWidth() * 1.0 / 20.0;
			double w = getWidth() * 18.0 / 20.0;
			double y = rules.getBoundsInParent().getMaxY() + getWidth() / 40.0;
			double h = (getHeight() * 18.0 / 20.0 - y);

			layoutInArea(flavor, x, y, w, h, 0.0, HPos.LEFT, VPos.TOP);
		}

		protected void layoutPT(Node ptbox) {
			double x = getWidth() * 19.0 / 20.0 - ptbox.getLayoutBounds().getWidth();
			double y = getHeight() - getWidth() * 1.0 / 20.0 - ptbox.getLayoutBounds().getHeight();
			double w = ptbox.getLayoutBounds().getWidth();
			double h = ptbox.getLayoutBounds().getHeight();

			layoutInArea(ptbox, x, y, w, h, 0.0, HPos.RIGHT, VPos.TOP);
		}

		@Override
		protected void layoutChildren() {
			super.layoutChildren();

			Node name = nodes.get(Characteristic.Name);
			Node mc = nodes.get(Characteristic.ManaCost);
			Node set = nodes.get(Characteristic.SetCode);
			Node type = nodes.get(Characteristic.TypeLine);
			Node rules = nodes.get(Characteristic.RulesText);
			Node flavor = nodes.get(Characteristic.FlavorText);
			Node pt = nodes.get(Characteristic.PTBox);

			layoutManaCost(mc);
			layoutName(name, mc);
			layoutSetCode(set, name);
			layoutType(type, name, set);
			layoutRules(rules, type);
			layoutFlavor(flavor, rules);
			layoutPT(pt);
		}
	}

	@Override
	public InputStream open(CardFace face) throws IOException {
		File f = new File(new File(PARENT_DIR, String.format("s%s", face.card().set().code())), String.format("%s%d.png", face.name(), face.card().variation()));

		if (f.exists()) {
			return new FileInputStream(f);
		}

		CardRenderLayout layout = new CardRenderLayout(face);

		Task<Void> imageRenderTask = new Task<Void>() {
			@Override
			protected Void call() throws Exception {
				Scene scene = new Scene(layout, WIDTH, HEIGHT, Color.TRANSPARENT);

				if (!f.getParentFile().exists() && !f.mkdirs()) {
					throw new Error("Unable to create parent directories for " + f.getAbsolutePath());
				}

				WritableImage image = scene.snapshot(null);
				ImageIO.write(SwingFXUtils.fromFXImage(image, null), "png", f);
				return null;
			}
		};

		Platform.runLater(imageRenderTask);

		// I don't like blocking here...
		while (!imageRenderTask.isDone()) {
			try {
				Thread.sleep(100);
			} catch (InterruptedException ie) {
				break;
			}
		}

		return new FileInputStream(f);
	}
}
