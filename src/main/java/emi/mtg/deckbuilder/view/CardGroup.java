package emi.mtg.deckbuilder.view;

import com.google.gson.Gson;
import emi.lib.Service;
import emi.lib.mtg.card.Card;
import emi.lib.mtg.data.ImageSource;
import emi.mtg.deckbuilder.model.CardInstance;
import javafx.application.Platform;
import javafx.collections.ListChangeListener;
import javafx.collections.ObservableList;
import javafx.collections.transformation.FilteredList;
import javafx.collections.transformation.SortedList;
import javafx.concurrent.Task;
import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.image.Image;
import javafx.scene.input.ClipboardContent;
import javafx.scene.input.DataFormat;
import javafx.scene.input.Dragboard;
import javafx.scene.input.TransferMode;
import javafx.scene.paint.Color;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.*;
import java.util.concurrent.ForkJoinPool;

/**
 * Created by Emi on 6/25/2017.
 */
public class CardGroup extends Canvas implements ListChangeListener<CardInstance> {
	@Service({ImageSource.class, ObservableList.class})
	@Service.Property.String(name="name")
	public interface LayoutEngine {
		double minWidth(double height);
		double prefWidth(double height);
		double maxWidth(double height);
		double minHeight(double width);
		double prefHeight(double width);
		double maxHeight(double width);
		CardInstance cardAt(double x, double y);
		void render(GraphicsContext graphics);
	}

	private final static Map<Card, Image> imageCache = new HashMap<>();
	private final static Map<Card, Image> thumbnailCache = new HashMap<>();

	private final static double WIDTH = 200;
	private final static double HEIGHT = 280;
	private final static double PADDING = 10;

	@Service.Provider(CardGroup.LayoutEngine.class)
	@Service.Property.String(name="name", value="Flow")
	public static class Flow implements LayoutEngine {
		private final ImageSource images;
		private final ObservableList<CardInstance> cards;

		private int stride;

		public Flow(ImageSource images, ObservableList<CardInstance> cards) {
			this.images = images;
			this.cards = cards;
			this.stride = 1;
		}

		@Override
		public double minWidth(double height) {
			return PADDING + WIDTH + PADDING;
		}

		@Override
		public double prefWidth(double height) {
			return PADDING + (WIDTH + PADDING) * cards.size();
		}

		@Override
		public double maxWidth(double height) {
			return prefWidth(height);
		}

		@Override
		public double minHeight(double width) {
			return prefHeight(width);
		}

		@Override
		public double prefHeight(double width) {
			return PADDING + Math.ceil((double) cards.size() / (double) stride) * (HEIGHT + PADDING);
		}

		@Override
		public double maxHeight(double width) {
			return prefHeight(width);
		}

		@Override
		public CardInstance cardAt(double x, double y) {
			double fRow = (y - PADDING) / (HEIGHT + PADDING);
			int row = (int) fRow;
			double yInRow = (fRow - row) * (HEIGHT + PADDING);

			if (yInRow < 0 || yInRow > HEIGHT) {
				return null;
			}

			double fCol = (x - PADDING) / (WIDTH + PADDING);
			int col = (int) fCol;
			double xInCol = (fCol - col) * (WIDTH + PADDING);

			if (xInCol < 0 || xInCol > WIDTH) {
				return null;
			}

			int i = row * stride + col;

			return i < cards.size() ? cards.get(i) : null;
		}

		@Override
		public void render(GraphicsContext graphics) {
			int renderStride = Math.max(1, (int) Math.floor((graphics.getCanvas().getWidth() - PADDING) / (WIDTH + PADDING)));

			if (renderStride != stride) {
				this.stride = renderStride;

				if (graphics.getCanvas().getParent() != null) {
					graphics.getCanvas().getParent().layout();
					return;
				}
			}

			double x = PADDING, y = PADDING;

			for (int i = 0; i < cards.size(); ++i) {
				if (i != 0 && i % stride == 0) {
					x = PADDING;
					y += PADDING + HEIGHT;
				}

				final CardInstance ci = cards.get(i);
				if (imageCache.containsKey(ci.card())) {
					graphics.drawImage(imageCache.get(ci.card()), x, y, WIDTH, HEIGHT);
				} else {
					Task<Image> loadImageTask = new Task<Image>() {
						@Override
						protected Image call() throws Exception {
							Image image = new Image(images.find(ci.card()).toString());
							imageCache.put(ci.card(), image);
							return image;
						}
					};
					loadImageTask.setOnSucceeded(wse -> Platform.runLater(() -> {
						int newI = this.cards.indexOf(ci);

						if (newI >= 0) {
							int row = newI / this.stride;
							int col = newI % this.stride;

							double newX = PADDING + col * (WIDTH + PADDING);
							double newY = PADDING + row * (HEIGHT + PADDING);

							graphics.drawImage(loadImageTask.getValue(), newX, newY, WIDTH, HEIGHT);
						}
					}));
					ForkJoinPool.commonPool().submit(loadImageTask);
				}

				x += PADDING + WIDTH;
			}
		}
	}

	@Service.Provider(LayoutEngine.class)
	@Service.Property.String(name="name", value="Piles")
	public static class Pile implements LayoutEngine {
		private final static double SPACING_FACTOR = 15.0 / 100.0;

		private final ImageSource images;
		private final ObservableList<CardInstance> cards;

		public Pile(ImageSource images, ObservableList<CardInstance> cards) {
			this.images = images;
			this.cards = cards;
		}

		@Override
		public double minWidth(double height) {
			return prefWidth(height);
		}

		@Override
		public double prefWidth(double height) {
			return PADDING + WIDTH + PADDING;
		}

		@Override
		public double maxWidth(double height) {
			return prefWidth(height);
		}

		@Override
		public double minHeight(double width) {
			return prefHeight(width);
		}

		@Override
		public double prefHeight(double width) {
			if (cards.size() == 0) {
				return PADDING + PADDING;
			} else {
				return PADDING + (HEIGHT * SPACING_FACTOR) * (cards.size() - 1) + HEIGHT + PADDING;
			}
		}

		@Override
		public double maxHeight(double width) {
			return prefHeight(width);
		}

		@Override
		public CardInstance cardAt(double x, double y) {
			if (x < PADDING || x > prefWidth(-1) - PADDING || y < PADDING || y > prefHeight(-1) - PADDING) {
				return null;
			}

			int i = Math.min((int) Math.floor((y - PADDING) / (HEIGHT * SPACING_FACTOR)), cards.size() - 1);

			return cards.get(i);
		}

		@Override
		public void render(GraphicsContext graphics) {
			double y = PADDING;

			for (int i = 0; i < cards.size(); ++i) {
				final CardInstance ci = cards.get(i);
				if (imageCache.containsKey(ci.card())) {
					graphics.drawImage(imageCache.get(ci.card()), PADDING, y, WIDTH, HEIGHT);
				} else {
					Task<Image> loadImageTask = new Task<Image>() {
						@Override
						protected Image call() throws Exception {
							Image image = new Image(images.find(ci.card()).toString());
							imageCache.put(ci.card(), image);
							return image;
						}
					};
					loadImageTask.setOnSucceeded(wse -> Platform.runLater(() -> {
						int newI = this.cards.indexOf(ci);

						if (newI >= 0) {
							double newY = PADDING + newI * (HEIGHT * SPACING_FACTOR);
							double h = (newI == this.cards.size() - 1) ? 1.0 : SPACING_FACTOR;

							graphics.drawImage(loadImageTask.getValue(),
									0, 0, loadImageTask.getValue().getWidth(), loadImageTask.getValue().getHeight() * h,
									PADDING, newY, WIDTH, HEIGHT * h);
						}
					}));
					ForkJoinPool.commonPool().submit(loadImageTask);
				}

				y += HEIGHT * SPACING_FACTOR;
			}
		}
	}

	private static Method refilter;

	static {
		Method refilterProxy;
		try {
			refilterProxy = FilteredList.class.getDeclaredMethod("refilter");
			refilterProxy.setAccessible(true);
		} catch (NoSuchMethodException nsme) {
			throw new Error("Couldn't find 'refilter' method on FilteredList.class... has JavaFX changed?");
		}
		refilter = refilterProxy;
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
	private CardInstance draggingCard;

	public CardGroup(String group, Gson gson, ImageSource images, FilteredList<CardInstance> cards, Comparator<CardInstance> sort, String engine, TransferMode[] dragModes, TransferMode[] dropModes) {
		super(1024, 1024);

		getGraphicsContext2D().setFill(Color.MAGENTA);
		getGraphicsContext2D().fill();

		this.group = group;
		this.cards = cards.sorted(sort);
		this.cards.addListener(this);
		this.dragModes = dragModes;
		this.dropModes = dropModes;
		this.engine = layoutEngines.containsKey(engine) ? layoutEngines.get(engine).uncheckedInstance(images, this.cards) : null;

		this.setOnDragDetected(de -> {
			if (this.engine == null) {
				return;
			}

			CardInstance ci = this.engine.cardAt(de.getX(), de.getY());

			if (ci == null) {
				return;
			}

			Dragboard db = this.startDragAndDrop(dragModes);

			ClipboardContent content = new ClipboardContent();
			content.put(DataFormat.PLAIN_TEXT, gson.toJson(ci, CardInstance.class));
			db.setContent(content);
			db.setDragView(thumbnailCache.computeIfAbsent(ci.card(), c -> new Image(images.find(c).toString(), 200, 280, true, true)));
			draggingCard = ci;

			de.consume();
		});

		this.setOnDragOver(de -> {
			de.acceptTransferModes(dropModes);
			de.consume();
		});

		this.setOnDragDropped(de -> {
			if (de.getGestureSource() instanceof CardGroup) {
				CardInstance card = ((CardGroup) de.getGestureSource()).draggingCard;
				assert card != null : "CardGroup received a DnD from another CardGroup with no draggingCard...";

				switch (de.getAcceptedTransferMode()) {
					case COPY:
						card = new CardInstance(card.card(), card.tags());
						cards.add(card);
						// intentional fallthrough
					case MOVE:
						card.tags().remove(((CardGroup) de.getGestureSource()).group);
						card.tags().add(group);
						break;
					default:
						assert false : "TODO: Implement other transfer modes...";
				}

				getParent().requestLayout();
				de.setDropCompleted(true);
			}

			de.consume();
		});

		this.setOnDragDone(de -> {
			if (draggingCard == null) {
				return;
			}

			draggingCard = null;
			getParent().requestLayout();

			de.consume();
		});
	}

	@Override
	public void onChanged(Change<? extends CardInstance> c) {
		getParent().requestLayout();
	}

	@Override
	public boolean isResizable() {
		return true;
	}

	@Override
	public double minWidth(double height) {
		return engine.minWidth(height < 0 ? getHeight() : height);
	}

	@Override
	public double prefWidth(double height) {
		return engine.prefWidth(height < 0 ? getHeight() : height);
	}

	@Override
	public double maxWidth(double height) {
		return engine.maxWidth(height < 0 ? getHeight() : height);
	}

	@Override
	public double minHeight(double width) {
		return engine.minHeight(width < 0 ? getWidth() : width);
	}

	@Override
	public double prefHeight(double width) {
		return engine.prefHeight(width < 0 ? getWidth() : width);
	}

	@Override
	public double maxHeight(double width) {
		return engine.maxHeight(width < 0 ? getWidth() : width);
	}

	@Override
	public void resize(double width, double height) {
		setWidth(width);
		setHeight(height);
		render();
	}

	private boolean noRerender = false;

	public void render() {
		if (!noRerender) {
			noRerender = true;

			try {
				refilter.invoke(this.cards.getSource());
			} catch (IllegalAccessException | InvocationTargetException e) {
				new Throwable("Error while trying to refilter cards for group " + group + " (recoverable)", e).printStackTrace();
			}

			noRerender = false;
		}

		if (engine == null) {
			return;
		}

		getGraphicsContext2D().clearRect(0, 0, getWidth(), getHeight());
		engine.render(getGraphicsContext2D());
		getGraphicsContext2D().setStroke(Color.BLACK);
		getGraphicsContext2D().strokeRect(0, 0, getWidth() - 1, getHeight() - 1);
	}
}
