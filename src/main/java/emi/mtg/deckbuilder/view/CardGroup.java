package emi.mtg.deckbuilder.view;

import com.google.gson.Gson;
import emi.lib.Service;
import emi.lib.mtg.card.Card;
import emi.lib.mtg.characteristic.CardRarity;
import emi.lib.mtg.characteristic.ManaSymbol;
import emi.lib.mtg.data.ImageSource;
import emi.mtg.deckbuilder.model.CardInstance;
import javafx.application.Platform;
import javafx.collections.ListChangeListener;
import javafx.collections.ObservableList;
import javafx.collections.transformation.FilteredList;
import javafx.collections.transformation.SortedList;
import javafx.collections.transformation.TransformationList;
import javafx.concurrent.Task;
import javafx.scene.CacheHint;
import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.image.Image;
import javafx.scene.input.ClipboardContent;
import javafx.scene.input.DataFormat;
import javafx.scene.input.Dragboard;
import javafx.scene.input.TransferMode;
import javafx.scene.paint.Color;

import javax.xml.crypto.dsig.Transform;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.net.URL;
import java.util.*;
import java.util.concurrent.ForkJoinPool;
import java.util.function.Function;

/**
 * Created by Emi on 6/25/2017.
 */
public class CardGroup extends Canvas implements ListChangeListener<CardInstance> {
	public final static Comparator<CardInstance> NAME_SORT = Comparator.comparing(c -> c.card().name());
	public final static Function<CardInstance, String> CMC_GROUP = c -> c.card().manaCost().varies() ? "X" : Integer.toString(c.card().manaCost().convertedCost());
	public final static Comparator<String> CMC_SORT = (s1, s2) -> {
		if ("X".equals(s1)) {
			return "X".equals(s2) ? 0 : 1;
		} else if ("X".equals(s2)) {
			return "X".equals(s1) ? 0 : -1;
		} else {
			return Integer.parseInt(s1) - Integer.parseInt(s2);
		}
	};
	public final static Comparator<CardInstance> COLOR_SORT = (c1, c2) -> {
		if (c1.card().color().size() != c2.card().color().size()) {
			int s1 = c1.card().color().size();
			if (s1 == 0) {
				s1 = emi.lib.mtg.characteristic.Color.values().length + 1;
			}

			int s2 = c2.card().color().size();
			if (s2 == 0) {
				s2 = emi.lib.mtg.characteristic.Color.values().length + 1;
			}

			return s1 - s2;
		}

		for (int i = emi.lib.mtg.characteristic.Color.values().length - 1; i >= 0; --i) {
			emi.lib.mtg.characteristic.Color c = emi.lib.mtg.characteristic.Color.values()[i];
			long n1 = -c1.card().manaCost().symbols().stream().map(ManaSymbol::colors).filter(s -> s.contains(c)).count();
			long n2 = -c2.card().manaCost().symbols().stream().map(ManaSymbol::colors).filter(s -> s.contains(c)).count();

			if (n1 != n2) {
				return (int) (n2 - n1);
			}
		}

		return 0;
	};
	public final static Function<CardInstance, String> RARITY_GROUP = c -> c.card().rarity().toString();
	public final static Comparator<String> RARITY_SORT = Comparator.comparing(CardRarity::forString);

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

	private final static double WIDTH = 300;
	private final static double HEIGHT = 420;
	private final static double PADDING = 15;

	private final static Image CARD_BACK = new Image("file:Back.xlhq.jpg");
	private final static Image CARD_BACK_THUMB = new Image("file:Back.xlhq.jpg", WIDTH, HEIGHT, true, true);

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
			return maxWidth(height);
		}

		@Override
		public double maxWidth(double height) {
			return Double.MAX_VALUE;
		}

		@Override
		public double minHeight(double width) {
			return prefHeight(width);
		}

		@Override
		public double prefHeight(double width) {
			if (width > 0) {
				this.stride = Math.max(1, (int) Math.floor((width - PADDING) / (WIDTH + PADDING)));
			}

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
							URL url = images.find(ci.card());
							Image image = url != null ? new Image(url.toString()) : CARD_BACK;
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
							URL url = images.find(ci.card());
							Image image = url != null ? new Image(url.toString()) : CARD_BACK;
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

	private static <T> ObservableList<T> allSource(TransformationList<T, T> derived) {
		TransformationList<? extends T, ? extends T> source = derived;

		while (source.getSource() != null) {
			if (source.getSource() instanceof TransformationList) {
				source = (TransformationList<? extends T, ? extends T>) source.getSource();
			} else {
				return (ObservableList<T>) source.getSource();
			}
		}

		return null;
	}

	public CardGroup(String group, Gson gson, ImageSource images, FilteredList<CardInstance> cards, Comparator<CardInstance> sort, String engine, TransferMode[] dragModes, TransferMode[] dropModes) {
		super(1024, 1024);

		setCache(true);
		setCacheHint(CacheHint.SCALE);

		this.group = group;
		this.cards = cards.filtered(ci -> ci.tags().contains(group)).sorted(sort);
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
			db.setDragView(thumbnailCache.computeIfAbsent(ci.card(), c -> {
				URL url = images.find(c);
				return url != null ? new Image(url.toString(), WIDTH, HEIGHT, true, true) : CARD_BACK_THUMB;
			}));
			draggingCard = ci;

			de.consume();
		});

		this.setOnDragOver(de -> {
			de.acceptTransferModes(dropModes);
			de.consume();
		});

		this.setOnDragDropped(de -> {
			if (de.getGestureSource() instanceof CardGroup) {
				CardGroup source = (CardGroup) de.getGestureSource();
				CardInstance card = source.draggingCard;
				assert card != null : "CardGroup received a DnD from another CardGroup with no draggingCard...";

				// n.b. .contains checks up the transformation list (I guess!?)
				if (de.getAcceptedTransferMode() == TransferMode.MOVE && this.cards.contains(card)) {
					card.tags().remove(source.group);
					card.tags().add(this.group);
				} else if (de.getAcceptedTransferMode() == TransferMode.MOVE || de.getAcceptedTransferMode() == TransferMode.COPY) {
					CardInstance newCard = new CardInstance(card.card(), card.tags());
					newCard.tags().remove(source.group);
					newCard.tags().add(this.group);
					allSource(this.cards).add(newCard);

					if (de.getAcceptedTransferMode() == TransferMode.MOVE) {
						allSource(source.cards).remove(card);
					}
				} else {
					assert false;
				}

				this.getParent().requestLayout();
				this.scheduleRender();
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
			scheduleRender();

			de.consume();
		});
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

	private volatile boolean renderScheduled = false;
	private volatile boolean noRefilter = false;

	@Override
	public void resize(double width, double height) {
		setWidth(width);
		setHeight(height);
		scheduleRender();
	}

	@Override
	public void onChanged(Change<? extends CardInstance> c) {
		if (!noRefilter) {
			getParent().requestLayout();
			scheduleRender();
		}
	}

	public synchronized void scheduleRender() {
		if (!renderScheduled) {
			renderScheduled = true;
			Platform.runLater(() -> {
				this.render();
				renderScheduled = false;
			});
		}
	}

	protected void render() {
		if (!noRefilter) {
			noRefilter = true;
			try {
				refilter.invoke(this.cards.getSource());
			} catch (IllegalAccessException | InvocationTargetException e) {
				new Throwable("Error while trying to refilter cards for group " + group + " (recoverable)", e).printStackTrace();
			}
			noRefilter = false;
		}

		// TODO: Fix this with some sort of pagination.
		if (engine == null || this.cards.size() > 150) {
			return;
		}

		getGraphicsContext2D().clearRect(0, 0, getWidth(), getHeight());
		engine.render(getGraphicsContext2D());
		getGraphicsContext2D().setStroke(Color.BLACK);
		getGraphicsContext2D().strokeRect(0, 0, getWidth() - 1, getHeight() - 1);
	}
}
