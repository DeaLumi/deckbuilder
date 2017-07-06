package emi.mtg.deckbuilder.view;

import emi.lib.Service;
import emi.lib.mtg.card.Card;
import emi.lib.mtg.data.ImageSource;
import emi.mtg.deckbuilder.model.CardInstance;
import javafx.application.Platform;
import javafx.collections.ListChangeListener;
import javafx.collections.ObservableList;
import javafx.collections.transformation.FilteredList;
import javafx.collections.transformation.SortedList;
import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.image.Image;
import javafx.scene.input.DataFormat;
import javafx.scene.input.Dragboard;
import javafx.scene.input.MouseButton;
import javafx.scene.input.TransferMode;
import javafx.scene.paint.Color;
import javafx.scene.text.Font;
import javafx.scene.text.TextAlignment;

import java.io.InputStream;
import java.util.*;
import java.util.concurrent.ForkJoinPool;
import java.util.function.Consumer;
import java.util.function.Predicate;
import java.util.stream.Collectors;

public class CardView extends Canvas implements ListChangeListener<CardInstance> {
	// TODO: Turn these into properties that can change? This renderer is FAST!
	public static final double WIDTH = 220.0;
	public static final double HEIGHT = 308.0;
	public static final double PADDING = WIDTH / 40.0;

	public static class MVec2d implements Comparable<MVec2d> {
		public double x, y;

		public MVec2d(double x, double y) {
			set(x, y);
		}

		public MVec2d(MVec2d other) {
			this(other.x, other.y);
		}

		public MVec2d() {
			this(0.0, 0.0);
		}

		public MVec2d set(double x, double y) {
			this.x = x;
			this.y = y;
			return this;
		}

		public MVec2d set(MVec2d other) {
			return set(other.x, other.y);
		}

		public MVec2d plus(double x, double y) {
			this.x += x;
			this.y += y;
			return this;
		}

		public MVec2d plus(MVec2d other) {
			return plus(other.x, other.y);
		}

		public MVec2d negate() {
			this.x = -this.x;
			this.y = -this.y;
			return this;
		}

		@Override
		public int compareTo(MVec2d o) {
			double dy = this.y - o.y;

			if (dy != 0) {
				return (int) Math.signum(dy);
			}

			return (int) Math.signum(this.x - o.x);
		}

		@Override
		public int hashCode() {
			short hi = (short) (Double.doubleToRawLongBits(x) & 0xFFFF);
			short lo = (short) (Double.doubleToRawLongBits(y) & 0xFFFF);
			return (hi << 16) | lo;
		}

		@Override
		public boolean equals(Object obj) {
			if (!(obj instanceof MVec2d)) {
				return false;
			}

			return x == ((MVec2d) obj).x && y == ((MVec2d) obj).y;
		}
	}

	public static class Bounds {
		public MVec2d pos, dim;

		public Bounds() {
			this.pos = new MVec2d();
			this.dim = new MVec2d();
		}
	}

	@Service(CardView.class)
	@Service.Property.String(name="name")
	public interface LayoutEngine {
		Bounds[] layoutGroups(int[] groupSizes);

		MVec2d coordinatesOf(int group, int card, MVec2d buffer);
		int groupAt(MVec2d point);
		int cardAt(MVec2d point, int group, int groupSize);
	}

	@Service
	@Service.Property.String(name="name")
	public interface Grouping {
		String[] groups();
		String extract(CardInstance ci);
		void add(CardInstance ci, String which);
		void remove(CardInstance ci, String which);
	}

	@Service
	@Service.Property.String(name="name")
	public interface Sorting extends Comparator<CardInstance> {
		// nothing to do...
	}

	private static final Map<String, Service.Loader<LayoutEngine>.Stub> engineMap = new HashMap<>();
	private static final Map<String, Grouping> groupingMap;
	private static final Map<String, Sorting> sortingMap;

	static {
		Service.Loader<LayoutEngine> loader = Service.Loader.load(LayoutEngine.class);

		for (Service.Loader<LayoutEngine>.Stub stub : loader) {
			engineMap.put(stub.string("name"), stub);
		}

		groupingMap = Service.Loader.load(Grouping.class).stream()
				.collect(Collectors.toMap(s -> s.string("name"), s -> s.uncheckedInstance()));

		sortingMap = Service.Loader.load(Sorting.class).stream()
				.collect(Collectors.toMap(s -> s.string("name"), s -> s.uncheckedInstance()));
	}

	public static Set<String> engineNames() {
		return CardView.engineMap.keySet();
	}
	public static Set<String> groupingNames() {
		return CardView.groupingMap.keySet();
	}
	public static Set<String> sortingNames() {
		return CardView.sortingMap.keySet();
	}

	private static final Map<Card, Image> imageCache = new HashMap<>();
	private static final Map<Card, Image> thumbnailCache = new HashMap<>();
	private static final Image CARD_BACK = new Image("file:Back.xlhq.jpg", WIDTH, HEIGHT, true, true);
	private static final Image CARD_BACK_THUMB = new Image("file:Back.xlhq.jpg", WIDTH, HEIGHT, true, true);

	private final ImageSource images;

	private final ObservableList<CardInstance> model;
	private final FilteredList<CardInstance> filteredModel;
	private final SortedList<CardInstance> sortedModel;

	private LayoutEngine engine;
	private Predicate<CardInstance> filter;
	private Comparator<CardInstance> sort;
	private Grouping grouping;

	private final Map<String, Integer> groupIndexMap;

	private double scrollX, scrollY;

	private boolean panning;
	private double lastDragX, lastDragY;

	private CardList[] cardLists;
	private CardInstance draggingCard;
	private TransferMode[] dragModes, dropModes;

	private Consumer<CardInstance> doubleClick;

	public CardView(ImageSource images, ObservableList<CardInstance> model, String engine, String grouping) {
		super(1024, 1024);

		setFocusTraversable(true);

		this.images = images;
		this.model = model;
		this.filteredModel = this.model.filtered(this.filter = ci -> true);
		this.sortedModel = this.filteredModel.sorted(this.sort = CardPane.COLOR_SORT.thenComparing(CardPane.NAME_SORT));
		this.sortedModel.addListener(this);

		this.engine = CardView.engineMap.containsKey(engine) ? CardView.engineMap.get(engine).uncheckedInstance(this) : null;
		this.grouping = CardView.groupingMap.get(grouping);

		this.groupIndexMap = new HashMap<>();
		if (this.grouping != null) {
			for (int i = 0; i < this.grouping.groups().length; ++i) {
				this.groupIndexMap.put(this.grouping.groups()[i], i);
			}
		}

		this.scrollX = this.scrollY = 0;

		this.cardLists = null;
		this.draggingCard = null;
		this.dragModes = TransferMode.COPY_OR_MOVE;
		this.dropModes = TransferMode.COPY_OR_MOVE;

		this.doubleClick = ci -> {};

		setOnScroll(se -> {
			scrollX += se.getDeltaX();
			scrollY += se.getDeltaY();
			scheduleRender();
		});

		setOnDragDetected(de -> {
			if (de.getButton() != MouseButton.PRIMARY) {
				return;
			}

			if (this.engine == null) {
				return;
			}

			this.draggingCard = cardAt(de.getX(), de.getY());

			if (this.draggingCard == null) {
				return;
			}

			Dragboard db = this.startDragAndDrop(dragModes);
			db.setDragView(thumbnailCache.getOrDefault(draggingCard.card(), CARD_BACK_THUMB));
			db.setContent(Collections.singletonMap(DataFormat.PLAIN_TEXT, draggingCard.card().name()));

			de.consume();
		});

		setOnDragOver(de -> {
			if (de.getGestureSource() instanceof CardView && ((CardView) de.getGestureSource()).draggingCard != null) {
				de.acceptTransferModes(dropModes);
				de.consume();
			}
		});

		setOnDragDropped(de -> {
			if (!(de.getGestureSource() instanceof CardView)) {
				return; // TODO: Accept drag/drop from other programs...?
			}

			CardView source = (CardView) de.getGestureSource();

			if (source == this) {
				return; // TODO: Group switching within same CardView.
			}

			CardInstance ci = source.draggingCard;

			switch (de.getAcceptedTransferMode()) {
				case COPY:
//					model.add(new CardInstance(ci.card(), ci.tags()));
					model.add(ci); // TODO: Is this a problem...?
					scheduleRender();
					break;
				case MOVE:
					source.model.remove(ci);
					model.add(ci);
					source.scheduleRender();
					scheduleRender();
					break;
				case LINK:
					break;
				default:
					assert false;
					break;
			}

			source.draggingCard = null;

			// TODO: Group switching.
		});

		setOnMousePressed(me -> this.requestFocus());

		setOnMouseClicked(me -> {
			if (me.getClickCount() % 2 == 0) {
				CardInstance ci = cardAt(me.getX(), me.getY());

				if (ci != null) {
					doubleClick.accept(ci);
					scheduleRender();
				}
			}
		});

		setOnMousePressed(me -> {
			if (me.getButton() == MouseButton.MIDDLE || (me.getButton() == MouseButton.PRIMARY && cardAt(me.getX(), me.getY()) == null)) {
				panning = true;
				lastDragX = me.getX();
				lastDragY = me.getY();
				me.consume();
			}
		});

		setOnMouseDragged(me -> {
			if (panning) {
				scrollX += me.getX() - lastDragX;
				scrollY += me.getY() - lastDragY;
				scheduleRender();

				lastDragX = me.getX();
				lastDragY = me.getY();
				me.consume();
			}
		});

		setOnMouseReleased(me -> {
			lastDragX = -1;
			lastDragY = -1;
			panning = false;
		});

		// TODO: Mouse drag panning
	}

	private CardInstance cardAt(double x, double y) {
		MVec2d point = new MVec2d(x - scrollX, y - scrollY);
		int group = this.engine.groupAt(point);

		if (group < 0) {
			return null;
		}

		// TODO: Optimize to "take Nth and count"? Only really reduces memory usage.
		CardInstance[] cardsInGroup = this.sortedModel.stream()
				.filter(ci -> grouping.groups()[group].equals(grouping.extract(ci)))
				.toArray(CardInstance[]::new);

		int card = this.engine.cardAt(point, group, cardsInGroup.length);

		if (card < 0) {
			return null;
		}

		return cardsInGroup[card];
	}

	public void layout(String engine) {
		if (CardView.engineMap.containsKey(engine)) {
			this.engine = CardView.engineMap.get(engine).uncheckedInstance(this);
			scheduleRender();
		}
	}

	public void filter(Predicate<CardInstance> filter) {
		this.filter = filter;
		this.filteredModel.setPredicate(filter);
	}

	public void sort(String sort) {
		if (CardView.sortingMap.containsKey(sort)) {
			this.sort = CardView.sortingMap.get(sort);
			this.sortedModel.setComparator(this.sort);
		}
	}

	public void group(String grouping) {
		if (CardView.groupingMap.containsKey(grouping)) {
			this.grouping = CardView.groupingMap.get(grouping);
			groupIndexMap.clear();
			for (int i = 0; i < this.grouping.groups().length; ++i) {
				groupIndexMap.put(this.grouping.groups()[i], i);
			}
			scheduleRender();
		}
	}

	public void dragModes(TransferMode... dragModes) {
		this.dragModes = dragModes;
	}

	public void dropModes(TransferMode... dropModes) {
		this.dropModes = dropModes;
	}

	public void doubleClick(Consumer<CardInstance> doubleClick) {
		this.doubleClick = doubleClick;
	}

	@Override
	public boolean isResizable() {
		return true;
	}

	@Override
	public double minWidth(double height) {
		return 0.0;
	}

	@Override
	public double prefWidth(double height) {
		return getWidth();
	}

	@Override
	public double maxWidth(double height) {
		return 8192.0;
	}

	@Override
	public double minHeight(double width) {
		return 0.0;
	}

	@Override
	public double prefHeight(double width) {
		return getHeight();
	}

	@Override
	public double maxHeight(double width) {
		return 8192.0;
	}

	@Override
	public void resize(double width, double height) {
		setWidth(width);
		setHeight(height);
		scheduleRender();
	}

	@Override
	public void onChanged(Change<? extends CardInstance> c) {
		scheduleRender();
	}

	public void scheduleRender() {
		ForkJoinPool.commonPool().submit(this::render);
	}

	class CardList extends ArrayList<CardInstance> {

	}

	protected void render() {
		if (engine == null || grouping == null) {
			Platform.runLater(() -> {
				GraphicsContext gfx = getGraphicsContext2D();
				gfx.setFill(Color.WHITE);
				gfx.fillRect(0, 0, getWidth(), getHeight());
				gfx.setTextAlign(TextAlignment.CENTER);
				gfx.setFill(Color.BLACK);
				gfx.setFont(new Font(null, getHeight() / 10.0));
				gfx.fillText("Select a valid display layout/card grouping.", getWidth() / 2, getHeight() / 2);
			});
			return;
		}

		if (sortedModel.isEmpty()) {
			Platform.runLater(() -> {
				GraphicsContext gfx = getGraphicsContext2D();
				gfx.setFill(Color.WHITE);
				gfx.fillRect(0, 0, getWidth(), getHeight());
				gfx.setTextAlign(TextAlignment.CENTER);
				gfx.setFill(Color.BLACK);
				gfx.setFont(new Font(null, getHeight() / 10.0));
				gfx.fillText("No cards to display.", getWidth() / 2, getHeight() / 2);
			});
			return;
		}

		if (sortedModel.size() > 400) {
			Platform.runLater(() -> {
				GraphicsContext gfx = getGraphicsContext2D();
				gfx.setFill(Color.WHITE);
				gfx.fillRect(0, 0, getWidth(), getHeight());
				gfx.setTextAlign(TextAlignment.CENTER);
				gfx.setFill(Color.BLACK);
				gfx.setFont(new Font(null, getHeight() / 10.0));
				gfx.fillText("Too many cards to display.", getWidth() / 2, getHeight() / 2);
			});
			return;
		}

		int[] groupSizes = new int[grouping.groups().length];
		CardList[] cardLists = new CardList[groupSizes.length];

		// One complete pass through the list... TODO: use streams?
		for (CardInstance ci : sortedModel) {
			final int i = groupIndexMap.get(grouping.extract(ci));
			if (cardLists[i] == null) {
				cardLists[i] = new CardList();
			}

			cardLists[i].add(ci);
			++groupSizes[i];
		}

		Bounds[] groupBounds = engine.layoutGroups(groupSizes);

		MVec2d low = new MVec2d(), high = new MVec2d();

		for (int i = 0; i < groupBounds.length; ++i) {
			final Bounds bounds = groupBounds[i];

			if (bounds.pos.x < low.x) {
				low.x = bounds.pos.x;
			}

			if (bounds.pos.y < low.y) {
				low.y = bounds.pos.y;
			}

			if (bounds.pos.x + bounds.dim.x > high.x) {
				high.x = bounds.pos.x + bounds.dim.x;
			}

			if (bounds.pos.y + bounds.dim.y > high.y) {
				high.y = bounds.pos.y + bounds.dim.y;
			}
		}

		// TODO: Create min/max scroll X/Y properties and draw scroll bars at edges.
		scrollX = -Math.max(Math.min(high.x - getWidth(), -scrollX), low.x);
		scrollY = -Math.max(Math.min(high.y - getHeight(), -scrollY), low.y);

		SortedMap<MVec2d, Image> renderMap = new TreeMap<>();
		MVec2d loc = new MVec2d();

		for (int i = 0; i < cardLists.length; ++i) {
			if (cardLists[i] == null) {
				continue;
			}

			final Bounds bounds = groupBounds[i];
			bounds.pos.plus(scrollX, scrollY);

			if (bounds.pos.x < -bounds.dim.x || bounds.pos.x > getWidth() || bounds.pos.y < -bounds.dim.y || bounds.pos.y > getHeight()) {
				continue;
			}

			for (int j = 0; j < cardLists[i].size(); ++j) {
				loc = engine.coordinatesOf(i, j, loc);
				loc = loc.plus(scrollX, scrollY);

				if (loc.x < -WIDTH || loc.x > getWidth() || loc.y < -HEIGHT || loc.y > getHeight()) {
					continue;
				}

				final Card card = cardLists[i].get(j).card();
				if (CardView.imageCache.containsKey(card)) {
					renderMap.put(new MVec2d(loc), CardView.imageCache.get(card));
				} else {
					renderMap.put(new MVec2d(loc), CARD_BACK);
					CardView.imageCache.put(card, CARD_BACK);

					ForkJoinPool.commonPool().submit(() -> {
						InputStream in = this.images.openSafely(card.front());

						if (in != null) {
							CardView.imageCache.put(card, new Image(in, WIDTH*2.0, HEIGHT*2.0, true, true));
							scheduleRender();
						} else {
							System.err.println("Unable to load image for " + card.set().code() + "/" + card.name());
							CardView.imageCache.put(card, CARD_BACK);
						}
					});
				}
			}
		}

		Platform.runLater(() -> {
			GraphicsContext gfx = getGraphicsContext2D();
			gfx.setFill(Color.WHITE);
			gfx.fillRect(0, 0, getWidth(), getHeight());
			for (Map.Entry<MVec2d, Image> img : renderMap.entrySet()) {
				gfx.drawImage(img.getValue(), img.getKey().x, img.getKey().y, WIDTH, HEIGHT);
			}
		});
	}
}
