package emi.mtg.deckbuilder.view;

import com.sun.javafx.collections.ObservableListWrapper;
import emi.lib.Service;
import emi.lib.mtg.Card;
import emi.mtg.deckbuilder.model.CardInstance;
import javafx.application.Platform;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.DoubleProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.property.SimpleDoubleProperty;
import javafx.collections.ListChangeListener;
import javafx.collections.ObservableList;
import javafx.collections.transformation.FilteredList;
import javafx.collections.transformation.SortedList;
import javafx.geometry.Rectangle2D;
import javafx.geometry.VPos;
import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.image.PixelReader;
import javafx.scene.image.WritableImage;
import javafx.scene.input.*;
import javafx.scene.paint.Color;
import javafx.scene.text.Font;
import javafx.scene.text.TextAlignment;

import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ForkJoinPool;
import java.util.function.Consumer;
import java.util.function.Predicate;
import java.util.stream.Collectors;

public class CardView extends Canvas implements ListChangeListener<CardInstance> {
	private static final double CARD_WIDTH = 220.0;
	private static final double CARD_HEIGHT = 308.0;
	private static final double CARD_PADDING = CARD_WIDTH / 40.0;
	private static final double ROUND_RADIUS = CARD_WIDTH / 8.0;

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

		public MVec2d copy() {
			return new MVec2d(this.x, this.y);
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

		public boolean contains(MVec2d point) {
			return point.x >= pos.x && point.y >= pos.y && point.x <= pos.x + dim.x && point.y <= pos.y + dim.y;
		}
	}

	@Service(CardView.class)
	@Service.Property.String(name="name")
	public interface LayoutEngine {
		void layoutGroups(int[] groupSizes, Bounds[] groupBounds, Bounds[] labelBounds);

		MVec2d coordinatesOf(int card, MVec2d buffer);
		int cardAt(MVec2d point, int groupSize);
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

	public static class ActiveSorting {
		public final Sorting sorting;
		public SimpleBooleanProperty descending;

		public ActiveSorting(Sorting sorting, boolean descending) {
			this.sorting = sorting;
			this.descending = new SimpleBooleanProperty(descending);
		}

		@Override
		public String toString() {
			return sorting.toString();
		}
	}

	private static final Map<String, Service.Loader<LayoutEngine>.Stub> engineMap = new HashMap<>();
	private static final Map<String, Grouping> groupings;
	private static final Map<String, Sorting> sortings;

	public static final List<ActiveSorting> DEFAULT_SORTING, DEFAULT_COLLECTION_SORTING;

	static {
		Service.Loader<LayoutEngine> loader = Service.Loader.load(LayoutEngine.class);

		for (Service.Loader<LayoutEngine>.Stub stub : loader) {
			engineMap.put(stub.string("name"), stub);
		}

		groupings = Collections.unmodifiableMap(Service.Loader.load(Grouping.class).stream()
				.collect(Collectors.toMap(g -> g.string("name"), g -> g.uncheckedInstance())));

		sortings = Collections.unmodifiableMap(Service.Loader.load(Sorting.class).stream()
				.collect(Collectors.toMap(g -> g.string("name"), g -> g.uncheckedInstance())));

		DEFAULT_SORTING = Collections.unmodifiableList(Arrays.asList(
				new ActiveSorting(sortings.get("Mana Cost"), false),
				new ActiveSorting(sortings.get("Name"), false)
		));

		DEFAULT_COLLECTION_SORTING = Collections.unmodifiableList(Arrays.asList(
				new ActiveSorting(sortings.get("Rarity"), true),
				new ActiveSorting(sortings.get("Mana Cost"), false),
				new ActiveSorting(sortings.get("Name"), false)
		));
	}

	public static Set<String> engineNames() {
		return CardView.engineMap.keySet();
	}

	public static Map<String, Grouping> groupings() {
		return CardView.groupings;
	}

	public static Map<String, Sorting> sortings() {
		return CardView.sortings;
	}

	private static final Map<Card.Printing, Image> imageCache = new HashMap<>();
	private static final Map<Card.Printing, Image> thumbnailCache = new HashMap<>();
	private static final Image CARD_BACK = new Image("file:Back.xlhq.jpg", CARD_WIDTH, CARD_HEIGHT, true, true);
	private static final Image CARD_BACK_THUMB = new Image("file:Back.xlhq.jpg", CARD_WIDTH, CARD_HEIGHT, true, true);

	private final Images images;

	private ObservableList<CardInstance> model;
	private FilteredList<CardInstance> filteredModel;
	private SortedList<CardInstance> sortedModel;

	private LayoutEngine engine;
	private Comparator<CardInstance> sort;
	private Predicate<CardInstance> filter;
	private List<ActiveSorting> sortingElements;
	private Grouping grouping;

	private final BooleanProperty reverseGroups;
	private final Map<String, Integer> groupIndexMap;

	private DoubleProperty scrollMinX, scrollMinY, scrollX, scrollY, scrollMaxX, scrollMaxY;

	private boolean panning;
	private double lastDragX, lastDragY;

	private Bounds[] groupBounds, labelBounds;
	private CardList[] cardLists;
	private CardInstance draggingCard, zoomedCard;
	private TransferMode[] dragModes, dropModes;
	private CardZoomPreview zoomPreview;

	private Consumer<CardInstance> doubleClick;

	private DoubleProperty cardScaleProperty;

	public CardView(Images images, ObservableList<CardInstance> model, String engine, Grouping grouping, List<ActiveSorting> sorts) {
		super(1024, 1024);

		setFocusTraversable(true);

		this.images = images;

		this.filter = ci -> true;
		this.sort = (c1, c2) -> 0;

		this.cardScaleProperty = new SimpleDoubleProperty(1.0);
		this.cardScaleProperty.addListener(ce -> layout());

		this.engine = CardView.engineMap.containsKey(engine) ? CardView.engineMap.get(engine).uncheckedInstance(this) : null;

		this.reverseGroups = new SimpleBooleanProperty(false);
		this.reverseGroups.addListener(ce -> layout());
		this.groupIndexMap = new HashMap<>();

		this.scrollMinX = new SimpleDoubleProperty(0.0);
		this.scrollMinY = new SimpleDoubleProperty(0.0);
		this.scrollX = new SimpleDoubleProperty(0.0);
		this.scrollY = new SimpleDoubleProperty(0.0);
		this.scrollMaxX = new SimpleDoubleProperty(100.0);
		this.scrollMaxY = new SimpleDoubleProperty(100.0);

		this.cardLists = null;
		this.draggingCard = null;
		this.dragModes = TransferMode.COPY_OR_MOVE;
		this.dropModes = TransferMode.COPY_OR_MOVE;

		this.doubleClick = ci -> {};

		model(model);
		sort(sorts);
		layout(engine);
		group(grouping);

		scheduleRender();

		this.scrollX.addListener(e -> scheduleRender());
		this.scrollY.addListener(e -> scheduleRender());

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
			db.setDragView(thumbnailCache.getOrDefault(draggingCard.printing(), CARD_BACK_THUMB));
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
					this.model.add(ci); // TODO: Is this a problem...?
					layout();
					break;
				case MOVE:
					source.model.remove(ci);
					this.model.add(ci);
					source.layout();
					layout();
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
			if (me.getButton() == MouseButton.PRIMARY && me.getClickCount() % 2 == 0) {
				CardInstance ci = cardAt(me.getX(), me.getY());

				if (ci != null) {
					this.doubleClick.accept(ci);
					layout();
				}
			}
		});

		setOnScroll(se -> {
			scrollX.set(Math.max(scrollMinX.get(), Math.min(scrollX.get() - se.getDeltaX(), scrollMaxX.get())));
			scrollY.set(Math.max(scrollMinY.get(), Math.min(scrollY.get() - se.getDeltaY(), scrollMaxY.get())));
		});

		setOnMousePressed(me -> {
			if (me.getButton() == MouseButton.MIDDLE || (me.getButton() == MouseButton.PRIMARY && cardAt(me.getX(), me.getY()) == null)) {
				panning = true;
				lastDragX = me.getX();
				lastDragY = me.getY();
				me.consume();
			} else if (me.getButton() == MouseButton.SECONDARY) {
				showPreview(me);
			}
		});

		setOnMouseDragged(me -> {
			if (panning) {
				scrollX.set(Math.max(scrollMinX.get(), Math.min(scrollX.get() - (me.getX() - lastDragX), scrollMaxX.get())));
				scrollY.set(Math.max(scrollMinY.get(), Math.min(scrollY.get() - (me.getY() - lastDragY), scrollMaxY.get())));

				lastDragX = me.getX();
				lastDragY = me.getY();
				me.consume();
			} else if (me.getButton() == MouseButton.SECONDARY) {
				showPreview(me);
			}
		});

		setOnMouseReleased(me -> {
			lastDragX = -1;
			lastDragY = -1;
			panning = false;

			if (zoomPreview != null) {
				zoomPreview.close();
				zoomPreview = null;
			}

			zoomedCard = null;
			scheduleRender();
		});
	}

	private void showPreview(MouseEvent me) {
		CardInstance ci;
		if (me.getPickResult().getIntersectedNode() != this) {
			ci = null;
		} else {
			ci = cardAt(me.getX(), me.getY());
		}

		if (zoomedCard == ci) {
			return;
		}

		zoomedCard = ci;

		if (zoomPreview != null) {
			zoomPreview.close();
		}

		if (zoomedCard != null) {
			MVec2d zoomLoc = cardCoordinatesOf(me.getX(), me.getY());
			if (zoomLoc == null) {
				zoomLoc = new MVec2d(me.getX(), me.getY());
			}
			zoomLoc.plus(me.getScreenX() - me.getX(), me.getScreenY() - me.getY());

			Rectangle2D start = new Rectangle2D(zoomLoc.x, zoomLoc.y, cardWidth(), cardHeight());

			zoomPreview = new CardZoomPreview(start, imageCache.get(zoomedCard.printing()));
		}
	}

	private MVec2d cardCoordinatesOf(double x, double y) {
		if (this.engine == null || sortedModel.size() == 0) {
			return null;
		}

		MVec2d point = new MVec2d(x + scrollX.get(), y + scrollY.get());

		int group = 0;
		for (; group <= groupBounds.length; ++group) {
			if (group == groupBounds.length) {
				return null;
			} else if (groupBounds[group].contains(point)) {
				point.plus(groupBounds[group].pos.copy().negate());
				break;
			}
		}

		CardList cardsInGroup = cardLists[group];

		if (cardsInGroup == null || cardsInGroup.isEmpty()) {
			return null;
		}

		int card = this.engine.cardAt(point, cardsInGroup.size());

		if (card < 0) {
			return null;
		}

		this.engine.coordinatesOf(card, point);
		point.plus(groupBounds[group].pos);
		point.plus(-scrollX.get(), -scrollY.get());
		return point;
	}

	private CardInstance cardAt(double x, double y) {
		if (this.engine == null || sortedModel.size() == 0) {
			return null;
		}

		MVec2d point = new MVec2d(x + scrollX.get(), y + scrollY.get());

		int group = 0;
		for (; group <= groupBounds.length; ++group) {
			if (group == groupBounds.length) {
				return null;
			} else if (groupBounds[group].contains(point)) {
				point.plus(groupBounds[group].pos.copy().negate());
				break;
			}
		}

		CardList cardsInGroup = cardLists[group];

		if (cardsInGroup == null || cardsInGroup.isEmpty()) {
			return null;
		}

		int card = this.engine.cardAt(point, cardsInGroup.size());

		if (card < 0) {
			return null;
		}

		return cardsInGroup.get(card);
	}

	public void model(ObservableList<CardInstance> model) {
		ForkJoinPool.commonPool().submit(() -> {
			ObservableList<CardInstance> m = model;
			if (m == null) {
				m = new ObservableListWrapper<>(new ArrayList<>());
			}

			this.model = m;
			this.filteredModel = this.model.filtered(this.filter);
			this.sortedModel = this.filteredModel.sorted(this.sort);
			this.sortedModel.addListener(this);

			layout();
		});
	}

	public ObservableList<CardInstance> model() {
		return this.model;
	}

	public void layout(String engine) {
		if (CardView.engineMap.containsKey(engine)) {
			this.engine = CardView.engineMap.get(engine).uncheckedInstance(this);
			layout();
		}
	}

	public void filter(Predicate<CardInstance> filter) {
		ForkJoinPool.commonPool().submit(() -> {
			Predicate<CardInstance> f = filter;
			if (f == null) {
				f = c -> true;
			}

			this.filter = f;
			this.filteredModel.setPredicate(this.filter);
		});
	}

	public void sort(List<ActiveSorting> sorts) {
		ForkJoinPool.commonPool().submit(() -> {
			List<ActiveSorting> s = sorts;
			if (s == null) {
				s = Collections.emptyList();
			}

			this.sortingElements = s;

			Comparator<CardInstance> sort = (c1, c2) -> 0;
			for (ActiveSorting element : s) {
				sort = sort.thenComparing(element.descending.get() ? element.sorting.reversed() : element.sorting);
			}

			this.sort = sort;
			this.sortedModel.setComparator(this.sort);
		});
	}

	public List<ActiveSorting> sort() {
		return this.sortingElements;
	}

	public void group(Grouping grouping) {
		if (grouping == null) {
			// TODO: Extract this to an actual grouping? "None"?
			grouping = new Grouping() {
				@Override
				public String[] groups() {
					return new String[] { "All Cards" };
				}

				@Override
				public String extract(CardInstance ci) {
					return "All Cards";
				}

				@Override
				public void add(CardInstance ci, String which) {
					throw new UnsupportedOperationException();
				}

				@Override
				public void remove(CardInstance ci, String which) {
					throw new UnsupportedOperationException();
				}
			};
		}

		this.grouping = grouping;
		groupIndexMap.clear();
		for (int i = 0; i < this.grouping.groups().length; ++i) {
			groupIndexMap.put(this.grouping.groups()[i], i);
		}

		this.groupBounds = new Bounds[grouping.groups().length];
		this.labelBounds = new Bounds[grouping.groups().length];
		for (int i = 0; i < this.groupBounds.length; ++i) {
			this.groupBounds[i] = new Bounds();
			this.labelBounds[i] = new Bounds();
		}

		layout();
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

	public BooleanProperty reverseGroups() {
		return reverseGroups;
	}

	public DoubleProperty cardScaleProperty() {
		return cardScaleProperty;
	}

	public DoubleProperty scrollMinX() {
		return scrollMinX;
	}

	public DoubleProperty scrollX() {
		return scrollX;
	}

	public DoubleProperty scrollMaxX() {
		return scrollMaxX;
	}

	public DoubleProperty scrollMinY() {
		return scrollMinY;
	}

	public DoubleProperty scrollY() {
		return scrollY;
	}

	public DoubleProperty scrollMaxY() {
		return scrollMaxY;
	}

	public double cardWidth() {
		return CARD_WIDTH * cardScaleProperty.doubleValue();
	}

	public double cardHeight() {
		return CARD_HEIGHT * cardScaleProperty.doubleValue();
	}

	public double cardPadding() {
		return CARD_PADDING;
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
		layout();
	}

	@Override
	public void onChanged(Change<? extends CardInstance> c) {
		layout();
	}

	public void scheduleRender() {
		ForkJoinPool.commonPool().submit(this::render);
	}

	class CardList extends ArrayList<CardInstance> {

	}

	private static final ExecutorService IMAGE_LOAD_POOL = Executors.newCachedThreadPool(r -> {
		Thread th = Executors.defaultThreadFactory().newThread(r);
		th.setDaemon(true);
		return th;
	});

	protected synchronized void layout() {
		if (engine == null || grouping == null || model == null || filteredModel == null || sortedModel == null
				|| groupBounds == null || labelBounds == null) {
			return;
		}

		int[] groupSizes = new int[grouping.groups().length];
		cardLists = new CardList[groupSizes.length];

		// One complete pass through the list... TODO: use streams?
		for (CardInstance ci : sortedModel) {
			Integer i = groupIndexMap.get(grouping.extract(ci));
			if (i == null) {
				System.err.println("Warning: Couldn't find group for " + ci.card().name());
				continue;
			}

			if (reverseGroups.get()) {
				i = groupSizes.length - 1 - i;
			}

			if (cardLists[i] == null) {
				cardLists[i] = new CardList();
			}

			cardLists[i].add(ci);
			++groupSizes[i];
		}

		engine.layoutGroups(groupSizes, groupBounds, labelBounds);

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

		scrollMinX.set(low.x);
		scrollMinY.set(low.y);
		scrollMaxX.set(high.x - getWidth());
		scrollMaxY.set(high.y - getHeight());

		scrollX.set(Math.max(low.x, Math.min(scrollX.get(), high.x - getWidth())));
		scrollY.set(Math.max(low.y, Math.min(scrollY.get(), high.y - getHeight())));

		scheduleRender();
	}

	protected synchronized void render() {
		if (engine == null || grouping == null) {
			Platform.runLater(() -> {
				GraphicsContext gfx = getGraphicsContext2D();
				gfx.setFill(Color.WHITE);
				gfx.fillRect(0, 0, getWidth(), getHeight());
				gfx.setTextAlign(TextAlignment.CENTER);
				gfx.setFill(Color.BLACK);
				gfx.setFont(new Font(null, getHeight() / 10.0));
				gfx.fillText("Select a valid display layout/card grouping.", getWidth() / 2, getHeight() / 2, getWidth());
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
				gfx.fillText("No cards to display.", getWidth() / 2, getHeight() / 2, getWidth());
			});
			return;
		}

		if (groupBounds == null || cardLists == null) {
			return; // TODO: What do we do here? Call layout manually...?
		}

		SortedMap<MVec2d, Image> renderMap = new TreeMap<>();
		MVec2d loc = new MVec2d();

		for (int i = 0; i < cardLists.length; ++i) {
			if (cardLists[i] == null) {
				continue;
			}

			final Bounds bounds = groupBounds[i];
			final MVec2d gpos = new MVec2d(bounds.pos).plus(-scrollX.get(), -scrollY.get());

			if (gpos.x < -bounds.dim.x || gpos.x > getWidth() || gpos.y < -bounds.dim.y || gpos.y > getHeight()) {
				continue;
			}

			for (int j = 0; j < cardLists[i].size(); ++j) {
				loc = engine.coordinatesOf(j, loc);
				loc = loc.plus(groupBounds[i].pos).plus(-scrollX.get(), -scrollY.get());

				if (loc.x < -cardWidth() || loc.x > getWidth() || loc.y < -cardHeight() || loc.y > getHeight()) {
					continue;
				}

				final Card.Printing printing = cardLists[i].get(j).printing();
				if (CardView.thumbnailCache.containsKey(printing)) {
					renderMap.put(new MVec2d(loc), CardView.thumbnailCache.get(printing));
				} else {
					renderMap.put(new MVec2d(loc), CARD_BACK);
					CardView.imageCache.put(printing, CARD_BACK);
					CardView.thumbnailCache.put(printing, CARD_BACK);

					IMAGE_LOAD_POOL.submit(() -> {
						Image src = this.images.get(printing);

						if (src != null) {
							PixelReader reader = src.getPixelReader();

							if (reader.getColor(0, 0).getOpacity() <= 0.05) {
								CardView.imageCache.put(printing, src);

								Platform.runLater(() -> {
									ImageView thumb = new ImageView(src);
									thumb.setSmooth(false);
									thumb.setPreserveRatio(true);
									thumb.setFitWidth(CARD_WIDTH);
									thumb.setFitHeight(CARD_HEIGHT);

									CardView.thumbnailCache.put(printing, thumb.snapshot(null, null));
									scheduleRender();
								});
							} else {
								WritableImage dst = new WritableImage((int) src.getWidth(), (int) src.getHeight());
								for (int y = 0; y < src.getHeight(); ++y) {
									for (int x = 0; x < src.getWidth(); ++x) {
										Color c = reader.getColor(x, y);

										// possibly modify color if near corner
										double ux = -1, uy = -1;
										if (x <= ROUND_RADIUS) {
											ux = x;
										} else if (x >= dst.getWidth() - ROUND_RADIUS) {
											ux = dst.getWidth() - x;
										}

										if (y <= ROUND_RADIUS) {
											uy = y;
										} else if (y >= dst.getHeight() - ROUND_RADIUS) {
											uy = dst.getHeight() - y;
										}

										if (ux >= 0 && uy >= 0) {
											double dx = ux - ROUND_RADIUS, dy = uy - ROUND_RADIUS;
											double d = Math.sqrt(dx * dx + dy * dy);
											double dd = Math.max(ROUND_RADIUS - 0.5, Math.min(d, ROUND_RADIUS + 0.5));
											c = c.interpolate(Color.TRANSPARENT, dd - ROUND_RADIUS + 0.5);
										}

										dst.getPixelWriter().setColor(x, y, c);
									}
								}

								CardView.imageCache.put(printing, dst);

								Platform.runLater(() -> {
									ImageView thumb = new ImageView(dst);
									thumb.setSmooth(false);
									thumb.setPreserveRatio(true);
									thumb.setFitWidth(CARD_WIDTH);
									thumb.setFitHeight(CARD_HEIGHT);

									CardView.thumbnailCache.put(printing, dst);
									scheduleRender();
								});
							}

							scheduleRender();
						} else {
							System.err.println("Unable to load image for " + printing.set().code() + "/" + printing.card().name());
							CardView.imageCache.put(printing, CARD_BACK);
							CardView.thumbnailCache.put(printing, CARD_BACK);
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
				gfx.drawImage(img.getValue(), img.getKey().x, img.getKey().y, cardWidth(), cardHeight());
			}

			gfx.setFill(Color.BLACK);
			gfx.setTextAlign(TextAlignment.CENTER);
			gfx.setTextBaseline(VPos.CENTER);
			for (int i = 0; i < labelBounds.length; ++i) {
				if (cardLists[i] == null || cardLists[i].isEmpty()) {
					continue;
				}

				String s = grouping.groups()[reverseGroups.get() ? grouping.groups().length - 1 - i : i];

				gfx.setFont(Font.font(labelBounds[i].dim.y));
				gfx.fillText(String.format("%s (%d)", s, cardLists[i].size()),
						labelBounds[i].pos.x + labelBounds[i].dim.x / 2.0 - scrollX.get(),
						labelBounds[i].pos.y + labelBounds[i].dim.y / 2.0 - scrollY.get(),
						labelBounds[i].dim.x);
			}
		});
	}
}
