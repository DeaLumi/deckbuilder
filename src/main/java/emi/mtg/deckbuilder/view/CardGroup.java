package emi.mtg.deckbuilder.view;

import emi.lib.Service;
import emi.lib.mtg.card.Card;
import emi.lib.mtg.data.ImageSource;
import emi.mtg.deckbuilder.model.CardInstance;
import javafx.collections.ListChangeListener;
import javafx.collections.ObservableList;
import javafx.collections.transformation.FilteredList;
import javafx.collections.transformation.SortedList;
import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.image.Image;
import javafx.scene.input.TransferMode;
import javafx.scene.paint.Color;

import java.util.Comparator;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

/**
 * Created by Emi on 6/25/2017.
 */
public class CardGroup extends Canvas implements ListChangeListener<CardInstance> {
	@Service({ImageSource.class, ObservableList.class})
	@Service.Property.String(name="name")
	public interface LayoutEngine {
		CardInstance cardAt(double x, double y);
		double width(double height);
		double height(double width);
		void render(GraphicsContext graphics);
	}

	@Service.Provider(CardGroup.LayoutEngine.class)
	@Service.Property.String(name="name", value="Flow")
	public static class Flow implements LayoutEngine {
		private final static Map<Card, Image> imageCache = new HashMap<>();

		private final static double WIDTH = 200;
		private final static double HEIGHT = 280;
		private final static double PADDING = 10;

		private final ImageSource images;
		private final ObservableList<CardInstance> cards;

		private int stride;

		public Flow(ImageSource images, ObservableList<CardInstance> cards) {
			this.images = images;
			this.cards = cards;
		}

		@Override
		public CardInstance cardAt(double x, double y) {
			double fRow = y / (HEIGHT + PADDING);
			int row = (int) fRow;
			double yInRow = (fRow - row) * (HEIGHT + PADDING);

			if (yInRow > HEIGHT) {
				return null;
			}

			double fCol = x / (WIDTH + PADDING);
			int col = (int) fCol;
			double xInCol = (fCol - col) * (WIDTH + PADDING);

			if (xInCol > WIDTH) {
				return null;
			}

			int i = row * stride + col;

			return i < cards.size() ? cards.get(i) : null;
		}

		@Override
		public double width(double height) {
			return Double.MAX_VALUE;
		}

		@Override
		public double height(double width) {
			return Math.ceil((double) cards.size() / (double) stride) * (HEIGHT + PADDING) + PADDING;
		}

		@Override
		public void render(GraphicsContext graphics) {
			stride = (int) Math.floor((graphics.getCanvas().getWidth() - PADDING) / (WIDTH + PADDING));

			if (stride <= 0) {
				return;
			}

			double x = PADDING, y = PADDING;

			for (int i = 0; i < cards.size(); ++i) {
				if (i != 0 && i % stride == 0) {
					x = PADDING;
					y += PADDING + HEIGHT;
				}

				graphics.drawImage(imageCache.computeIfAbsent(cards.get(i).card(), c -> {
					return new Image(images.find(c).toString()); // TODO: Flesh this out... no lazy loading sucks.
				}), x, y, WIDTH, HEIGHT);

				x += PADDING + WIDTH;
			}
		}
	}

	private static final Map<String, Service.Loader<LayoutEngine>.Stub> layoutEngines;

	static {
		layoutEngines = new HashMap<>();
		Service.Loader<LayoutEngine> loader = Service.Loader.load(LayoutEngine.class);

		for (Service.Loader<LayoutEngine>.Stub stub : loader) {
			layoutEngines.put(stub.string("name"), stub);
		}
	}

	public static Set<String> layoutEngineNames() {
		return layoutEngines.keySet();
	}

	private final String group;
	private final SortedList<CardInstance> cards;
	private final TransferMode[] dragModes, dropModes;
	private LayoutEngine engine;

	public CardGroup(String group, ImageSource images, FilteredList<CardInstance> cards, Comparator<CardInstance> sort, String engine, TransferMode[] dragModes, TransferMode[] dropModes) {
		super(1024, 1024);

		getGraphicsContext2D().setFill(Color.MAGENTA);
		getGraphicsContext2D().fill();

		this.group = group;
		this.cards = cards.sorted(sort);
		this.cards.addListener(this);
		this.dragModes = dragModes;
		this.dropModes = dropModes;
		this.engine = layoutEngines.containsKey(engine) ? layoutEngines.get(engine).uncheckedInstance(images, this.cards) : null;

		this.render();

		this.setOnMouseClicked(me -> {
			this.render();
		});
	}

	@Override
	public void onChanged(Change<? extends CardInstance> c) {
		render();
	}

	@Override
	public boolean isResizable() {
		return true;
	}

	@Override
	public double minWidth(double height) {
		return 0;
	}

	@Override
	public double prefWidth(double height) {
		return engine.width(height);
	}

	@Override
	public double maxWidth(double height) {
		return Double.MAX_VALUE;
	}

	@Override
	public double minHeight(double width) {
		return 0;
	}

	@Override
	public double prefHeight(double width) {
		return engine.height(width);
	}

	@Override
	public double maxHeight(double width) {
		return Double.MAX_VALUE;
	}

	@Override
	public void resize(double width, double height) {
		setWidth(width);
		setHeight(height);
		render();
		System.out.println(String.format("%s: %f x %f", group, width, height));
	}

	public void render() {
		if (engine == null) {
			return;
		}

		getGraphicsContext2D().clearRect(0, 0, getWidth(), getHeight());
		engine.render(getGraphicsContext2D());
		getGraphicsContext2D().setStroke(Color.BLACK);
		getGraphicsContext2D().strokeRect(0, 0, getWidth() - 1, getHeight() - 1);
	}
}
