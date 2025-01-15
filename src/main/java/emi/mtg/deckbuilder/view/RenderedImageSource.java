package emi.mtg.deckbuilder.view;

import emi.lib.mtg.Card;
import emi.lib.mtg.ImageSource;
import javafx.application.Platform;
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

import java.awt.image.BufferedImage;
import java.io.IOException;
import java.util.EnumMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;

public class RenderedImageSource implements ImageSource {
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

		public CardRenderLayout(Card.Print.Face face) {
			setBorder(new Border(new BorderStroke(Color.BLACK, BorderStrokeStyle.SOLID, new CornerRadii(WIDTH / 20.0), new BorderWidths(WIDTH / 30.0))));

			Color bgColor;
			if (face.face().color().size() > 1) {
				bgColor = Color.PALEGOLDENROD;
			} else if (face.face().color().isEmpty()) {
				bgColor = Color.LIGHTGRAY;
			} else {
				switch (face.face().color().iterator().next()) {
					case White:
						bgColor = Color.WHITE;
						break;
					case Blue:
						bgColor = Color.LIGHTBLUE;
						break;
					case Black:
						bgColor = Color.DARKGRAY;
						break;
					case Red:
						bgColor = Color.LIGHTPINK;
						break;
					case Green:
						bgColor = Color.PALEGREEN;
						break;
					case Colorless:
						bgColor = Color.LIGHTGRAY;
						break;
					default:
						throw new IllegalStateException();
				}
			}

			setBackground(new Background(new BackgroundFill(bgColor, new CornerRadii(WIDTH / 15.0), null)));

			{
				Label name = new Label(face.face().name());
				name.setFont(NAME_FONT);
				getChildren().add(name);
				nodes.put(Characteristic.Name, name);
			}

			{
				Label mc = new Label(face.face().manaCost().toString());
				mc.setFont(NAME_FONT);
				getChildren().add(mc);
				nodes.put(Characteristic.ManaCost, mc);
			}

			{
				Label type = new Label(face.face().type().toString());
				type.setFont(NAME_FONT);
				getChildren().add(type);
				nodes.put(Characteristic.TypeLine, type);
			}

			{
				Label set = new Label(String.format("%s-%s", face.print().set().code(), face.print().rarity().name().substring(0, 1)));
				set.setFont(NAME_FONT);
				getChildren().add(set);
				nodes.put(Characteristic.SetCode, set);
			}

			{
				Label rules = new Label(face.face().rules());
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
				if (!face.face().printedPower().isEmpty() && !face.face().printedToughness().isEmpty()) {
					pt = new Label(String.format("%s / %s", face.face().printedPower(), face.face().printedToughness()));
				} else if (!face.face().printedLoyalty().isEmpty()) {
					pt = new Label(face.face().printedLoyalty());
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
	public int priority() {
		return 0;
	}

	@Override
	public BufferedImage open(Card.Print.Face face) throws IOException {
		CardRenderLayout layout = new CardRenderLayout(face);

		CompletableFuture<WritableImage> render = new CompletableFuture<>();

		Platform.runLater(() -> {
			Scene scene = new Scene(layout, WIDTH, HEIGHT, Color.TRANSPARENT);
			WritableImage image = scene.snapshot(null);
			render.complete(image);
		});

		try {
			return SwingFXUtils.fromFXImage(render.get(), null); // TODO: This is pretty wasteful.
		} catch (InterruptedException e) {
			return null; // We're dying anyway?
		} catch (ExecutionException e) {
			throw new IOException(e);
		}
	}

	@Override
	public BufferedImage open(Card.Print print) throws IOException {
		if (print.card().mainFaces().isEmpty()) {
			return null;
		} else {
			return open(print.faces(print.card().mainFaces().iterator().next()).iterator().next());
		}
	}

	@Override
	public boolean cacheable() {
		return false;
	}
}
