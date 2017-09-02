package emi.mtg.deckbuilder.view;

import emi.lib.Service;
import emi.lib.mtg.Card;
import emi.mtg.deckbuilder.controller.Context;
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
import javafx.scene.input.*;
import javafx.scene.paint.Color;
import javafx.scene.text.Font;
import javafx.scene.text.TextAlignment;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ForkJoinPool;
import java.util.function.Consumer;
import java.util.function.Predicate;
import java.util.stream.Collectors;

public class CardView extends Canvas implements ListChangeListener<CardInstance> {

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
		void layoutGroups(Group[] groups, boolean includeEmpty);

		MVec2d coordinatesOf(int card, MVec2d buffer);
		int cardAt(MVec2d point, int groupSize);
	}

	@Service(Context.class)
	@Service.Property.String(name="name")
	public interface Grouping {
		interface Group {
			void add(CardInstance ci);
			void remove(CardInstance ci);
			boolean contains(CardInstance ci);
		}

		Group[] groups();

		boolean supportsModification();
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
	private static final Map<String, Service.Loader<Grouping>.Stub> groupingsMap = new HashMap<>();
	private static final Map<String, Sorting> sortings;

	public static final List<ActiveSorting> DEFAULT_SORTING, DEFAULT_COLLECTION_SORTING;

	static {
		Service.Loader<LayoutEngine> loader = Service.Loader.load(LayoutEngine.class);

		for (Service.Loader<LayoutEngine>.Stub stub : loader) {
			engineMap.put(stub.string("name"), stub);
		}

		for (Service.Loader<Grouping>.Stub stub : Service.Loader.load(Grouping.class)) {
			groupingsMap.put(stub.string("name"), stub);
		}

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

	public static Set<String> groupings() {
		return CardView.groupingsMap.keySet();
	}

	public static Map<String, Sorting> sortings() {
		return CardView.sortings;
	}

	public class Group {
		public final Bounds groupBounds, labelBounds;
		public final Grouping.Group group;
		public SortedList<CardInstance> model;

		public Group(Grouping.Group group, ObservableList<CardInstance> modelSource, Comparator<CardInstance> initialSort) {
			this.group = group;
			this.groupBounds = new Bounds();
			this.labelBounds = new Bounds();
			this.model = modelSource.filtered(group::contains).sorted(initialSort);

			this.model.addListener(CardView.this);
		}
	}

	private final Context context;

	private ObservableList<CardInstance> model;
	private FilteredList<CardInstance> filteredModel;
	private Group[] groupedModel;

	private LayoutEngine engine;
	private Comparator<CardInstance> sort;
	private Predicate<CardInstance> filter;
	private List<ActiveSorting> sortingElements;
	private Grouping grouping;

	private DoubleProperty scrollMinX, scrollMinY, scrollX, scrollY, scrollMaxX, scrollMaxY;

	private boolean panning;
	private double lastDragX, lastDragY;

	private Group hoverGroup;
	private CardInstance hoverCard;

	private Group dragGroup;
	private CardInstance dragCard;
	private TransferMode[] dragModes, dropModes;
	private boolean forceMove, forceCopy;

	private CardInstance zoomedCard;
	private CardZoomPreview zoomPreview;

	private Consumer<CardInstance> doubleClick;

	private DoubleProperty cardScaleProperty;
	private BooleanProperty showEmptyGroupsProperty;

	public CardView(Context context, ObservableList<CardInstance> model, String engine, String grouping, List<ActiveSorting> sorts) {
		super(1024, 1024);

		setFocusTraversable(true);

		this.context = context;

		this.filter = ci -> true;
		this.sortingElements = sorts;
		this.sort = convertSorts(sorts);
		this.grouping = groupingsMap.get(grouping).uncheckedInstance(context);

		this.cardScaleProperty = new SimpleDoubleProperty(1.0);
		this.cardScaleProperty.addListener(ce -> layout());

		this.showEmptyGroupsProperty = new SimpleBooleanProperty(false);
		this.showEmptyGroupsProperty.addListener(ce -> layout());

		this.engine = CardView.engineMap.containsKey(engine) ? CardView.engineMap.get(engine).uncheckedInstance(this) : null;

		this.scrollMinX = new SimpleDoubleProperty(0.0);
		this.scrollMinY = new SimpleDoubleProperty(0.0);
		this.scrollX = new SimpleDoubleProperty(0.0);
		this.scrollY = new SimpleDoubleProperty(0.0);
		this.scrollMaxX = new SimpleDoubleProperty(100.0);
		this.scrollMaxY = new SimpleDoubleProperty(100.0);

		this.dragModes = TransferMode.COPY_OR_MOVE;
		this.dropModes = TransferMode.COPY_OR_MOVE;

		this.hoverCard = this.dragCard = null;
		this.hoverGroup = this.dragGroup = null;

		this.doubleClick = ci -> {};

		model(model, true);

		layout(engine);

		this.scrollX.addListener(e -> scheduleRender());
		this.scrollY.addListener(e -> scheduleRender());

		setOnMouseMoved(me -> {
			mouseMoved(me.getX(), me.getY());

			me.consume();
		});

		setOnMouseExited(me -> {
			if (hoverGroup != null) {
				hoverGroup = null;
				hoverCard = null;

				scheduleRender();
			}

			me.consume();
		});

		setOnDragDetected(de -> {
			mouseMoved(de.getX(), de.getY());

			if (de.getButton() != MouseButton.PRIMARY) {
				return;
			}

			this.dragCard = this.hoverCard;
			this.dragGroup = this.hoverGroup;

			if (this.dragCard == null) {
				de.consume();
				return;
			}

			// TODO: This is a workaround: JDK-8148025
			this.forceMove = !de.isShiftDown();
			this.forceCopy = de.isControlDown();

			Dragboard db = this.startDragAndDrop(TransferMode.COPY_OR_MOVE);
			try {
				context.images.getThumbnail(this.dragCard.printing()).thenAccept(db::setDragView).get();
			} catch (ExecutionException | InterruptedException e) {
				e.printStackTrace();
				db.setDragView(Images.CARD_BACK_THUMB);
			}
			db.setContent(Collections.singletonMap(DataFormat.PLAIN_TEXT, this.dragCard.card().name()));

			de.consume();
		});

		setOnDragOver(de -> {
			mouseMoved(de.getX(), de.getY());

			if (de.getGestureSource() == this) {
				if (this.hoverGroup != this.dragGroup) {
					if (this.forceCopy) {
						de.acceptTransferModes(TransferMode.COPY);
					} else if (this.forceMove) {
						de.acceptTransferModes(TransferMode.MOVE);
					} else {
						de.acceptTransferModes(TransferMode.COPY_OR_MOVE);
					}
				} else {
					de.acceptTransferModes(); // TODO: If we ever allow rearranging gCanopy Vistaroups.
				}

				de.consume();
			} else if (de.getGestureSource() instanceof CardView) {
				if (this.forceCopy) {
					de.acceptTransferModes(TransferMode.COPY);
				} else if (this.forceMove) {
					de.acceptTransferModes(TransferMode.MOVE);
				} else {
					// Manually calculate set intersection, since we didn't enforce dragModes in DragDetected.
					EnumSet<TransferMode> dropModes = EnumSet.allOf(TransferMode.class);
					dropModes.retainAll(Arrays.asList(this.dropModes));
					dropModes.retainAll(Arrays.asList(((CardView) de.getGestureSource()).dragModes));
					de.acceptTransferModes(dropModes.toArray(new TransferMode[dropModes.size()]));
				}

				de.consume();
			} // TODO: Accept transfer from other programs/areas?
		});

		setOnDragDropped(de -> {
			if (!(de.getGestureSource() instanceof CardView)) {
				return; // TODO: Accept transfer from other programs/areas?
			}

			CardView source = (CardView) de.getGestureSource();
			CardInstance card = source.dragCard;

			if (source != this) {
				this.model.add(new CardInstance(card.printing()));
			} else {
				if (this.grouping.supportsModification()) {
					this.hoverGroup.group.add(card);

					for (int i = 0; i < this.model.size(); ++i) {
						if (this.model.get(i).card() == card.card()) {
							this.model.set(i, this.model.get(i));
						}
					}
				}
			}

			layout();

			de.acceptTransferModes(de.getAcceptedTransferMode());
			de.setDropCompleted(true);
			de.consume();
		});

		setOnDragDone(de -> {
			if (de.getAcceptedTransferMode() == TransferMode.MOVE) {
				if (de.getGestureTarget() != this) {
					this.model.remove(this.dragCard);
				} else {
					if (this.grouping.supportsModification()) {
						this.dragGroup.group.remove(this.dragCard);

						for (int i = 0; i < this.model.size(); ++i) {
							if (this.model.get(i).card() == this.dragCard.card()) {
								this.model.set(i, this.model.get(i));
							}
						}
					}
				}

				layout();
			}

			this.dragCard = null;
			this.dragGroup = null;

			de.consume();
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

	private void mouseMoved(double x, double y) {
		if (groupedModel == null) {
			return;
		}

		boolean rerender = false;

		MVec2d rel = new MVec2d(x + scrollX.get(), y + scrollY.get());

		Group newHoverGroup = null;

		for (Group g : groupedModel) {
			if (g == null) {
				continue;
			}

			if (g.groupBounds != null && g.groupBounds.contains(rel) || g.labelBounds != null && g.labelBounds.contains(rel)) {
				newHoverGroup = g;
				break;
			}
		}

		if (newHoverGroup != hoverGroup) {
			rerender = true;
		}

		hoverGroup = newHoverGroup;

		if (newHoverGroup == null) {
			if (rerender) {
				scheduleRender();
			}

			return;
		}

		rel.plus(hoverGroup.groupBounds.pos.copy().negate());
		int newHoverIdx = this.engine.cardAt(rel, hoverGroup.model.size());
		CardInstance newHoverCard = newHoverIdx >= 0 ? hoverGroup.model.get(newHoverIdx) : null;

		if (newHoverCard != hoverCard) {
			rerender = true;
		}

		hoverCard = newHoverCard;

		if (rerender) {
			scheduleRender();
		}
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

			zoomPreview = new CardZoomPreview(start, context.images.get(zoomedCard.printing()).getNow(Images.CARD_BACK));
		}
	}

	private MVec2d cardCoordinatesOf(double x, double y) {
		if (this.engine == null || filteredModel.size() == 0) {
			return null;
		}

		MVec2d point = new MVec2d(x + scrollX.get(), y + scrollY.get());

		Group group = null;
		for (Group g : groupedModel) {
			if (g.groupBounds.contains(point)) {
				group = g;
				break;
			}
		}

		if (group == null || group.model.isEmpty()) {
			return null;
		}

		point.plus(group.groupBounds.pos.copy().negate());
		int card = this.engine.cardAt(point, group.model.size());

		if (card < 0) {
			return null;
		}

		this.engine.coordinatesOf(card, point);
		point.plus(group.groupBounds.pos);
		point.plus(-scrollX.get(), -scrollY.get());
		return point;
	}

	private CardInstance cardAt(double x, double y) {
		if (this.engine == null || filteredModel.size() == 0) {
			return null;
		}

		MVec2d point = new MVec2d(x + scrollX.get(), y + scrollY.get());

		Group group = null;
		for (Group g : groupedModel) {
			if (g.groupBounds.contains(point)) {
				group = g;
				break;
			}
		}

		if (group == null || group.model.isEmpty()) {
			return null;
		}

		point.plus(group.groupBounds.pos.copy().negate());
		int card = this.engine.cardAt(point, group.model.size());

		if (card < 0) {
			return null;
		}

		return group.model.get(card);
	}

	public void layout(String engine) {
		if (CardView.engineMap.containsKey(engine)) {
			this.engine = CardView.engineMap.get(engine).uncheckedInstance(this);
			layout();
		}
	}

	// TODO: A lot of this shouldn't be changeable. Change to builder model?
	// TODO: Clean all this bullshit up and use futures?

	public synchronized void model(ObservableList<CardInstance> model, boolean sync) {
		if (!sync) {
			ForkJoinPool.commonPool().submit(() -> model(model, true));
		} else {
			this.model = model;
			this.filter(this.filter, true);
		}
	}

	public void model(ObservableList<CardInstance> model) {
		model(model, false);
	}

	public ObservableList<CardInstance> model() {
		return this.model;
	}

	public FilteredList<CardInstance> filteredModel() { return this.filteredModel; }

	private synchronized void filter(Predicate<CardInstance> filter, boolean sync) {
		if (!sync) {
			ForkJoinPool.commonPool().submit(() -> filter(filter, true));
		} else {
			this.filter = filter;
			this.filteredModel = this.model.filtered(this.filter);
			this.group(this.grouping, true);
		}
	}

	public void filter(Predicate<CardInstance> filter) {
		filter(filter, false);
	}

	private synchronized void group(Grouping grouping, boolean sync) {
		if (!sync) {
			ForkJoinPool.commonPool().submit(() -> group(grouping, true));
		} else {
			this.grouping = grouping;

			this.groupedModel = new Group[this.grouping.groups().length];
			for (int i = 0; i < this.grouping.groups().length; ++i) {
				this.groupedModel[i] = new Group(this.grouping.groups()[i], this.filteredModel, this.sort);
			}

			layout();
		}
	}

	public void group(String grouping) {
		if (CardView.groupingsMap.containsKey(grouping)) {
			group(CardView.groupingsMap.get(grouping).uncheckedInstance(this.context), false);
		}
	}

	private Comparator<CardInstance> convertSorts(List<ActiveSorting> sorts) {
		List<ActiveSorting> s = sorts;
		if (s == null) {
			s = Collections.emptyList();
		}

		Comparator<CardInstance> sort = (c1, c2) -> 0;
		for (ActiveSorting element : s) {
			sort = sort.thenComparing(element.descending.get() ? element.sorting.reversed() : element.sorting);
		}

		return sort;
	}

	private synchronized void sort(List<ActiveSorting> sorts, boolean sync) {
		if (!sync) {
			final List<ActiveSorting> finalized = sorts;
			ForkJoinPool.commonPool().submit(() -> sort(finalized, true));
		} else {
			synchronized(this) {
				if (sorts == null) {
					sorts = Collections.emptyList();
				}

				this.sortingElements = sorts;
				this.sort = convertSorts(sorts);

				for (Group g : groupedModel) {
					g.model.setComparator(this.sort);
				}

				scheduleRender();
			}
		}
	}

	public void sort(List<ActiveSorting> sorts) {
		sort(sorts, false);
	}

	public List<ActiveSorting> sort() {
		return this.sortingElements;
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

	public DoubleProperty cardScaleProperty() {
		return cardScaleProperty;
	}

	public BooleanProperty showEmptyGroupsProperty() {
		return showEmptyGroupsProperty;
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
		return Images.CARD_WIDTH * cardScaleProperty.doubleValue();
	}

	public double cardHeight() {
		return Images.CARD_HEIGHT * cardScaleProperty.doubleValue();
	}

	public double cardPadding() {
		return Images.CARD_PADDING;
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

	protected synchronized void layout() {
		if (engine == null || grouping == null || model == null || groupedModel == null) {
			return;
		}

		engine.layoutGroups(groupedModel, showEmptyGroupsProperty.get());

		MVec2d low = new MVec2d(), high = new MVec2d();

		for (int i = 0; i < groupedModel.length; ++i) {
			final Bounds bounds = groupedModel[i].groupBounds;

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

	private enum CardState {
		Hover,
		Flagged
	}

	class RenderStruct {
		public final Image img;
		public final EnumSet<CardState> state;

		public RenderStruct(Image img) {
			this.img = img;
			this.state = EnumSet.noneOf(CardState.class);
		}

		public RenderStruct(Image img, EnumSet<CardState> states) {
			this.img = img;
			this.state = states;
		}

		public RenderStruct(Image img, CardState state, CardState... states) {
			this.img = img;
			this.state = EnumSet.of(state, states);
		}
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

		if (filteredModel == null || filteredModel.isEmpty()) {
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

		if (groupedModel == null) {
			return; // TODO: What do we do here? Call layout manually...?
		}

		SortedMap<MVec2d, RenderStruct> renderMap = new TreeMap<>();
		MVec2d loc = new MVec2d();

		for (int i = 0; i < groupedModel.length; ++i) {
			if (groupedModel[i] == null) {
				continue;
			}

			final Bounds bounds = groupedModel[i].groupBounds;
			final MVec2d gpos = new MVec2d(bounds.pos).plus(-scrollX.get(), -scrollY.get());

			if (gpos.x < -bounds.dim.x || gpos.x > getWidth() || gpos.y < -bounds.dim.y || gpos.y > getHeight()) {
				continue;
			}

			for (int j = 0; j < groupedModel[i].model.size(); ++j) {
				loc = engine.coordinatesOf(j, loc);
				loc = loc.plus(groupedModel[i].groupBounds.pos).plus(-scrollX.get(), -scrollY.get());

				if (loc.x < -cardWidth() || loc.x > getWidth() || loc.y < -cardHeight() || loc.y > getHeight()) {
					continue;
				}

				final CardInstance ci = groupedModel[i].model.get(j);
				final Card.Printing printing = ci.printing();

				CompletableFuture<Image> futureImage = this.context.images.getThumbnail(printing);

				if (futureImage.isDone()) {
					EnumSet<CardState> states = EnumSet.noneOf(CardState.class);

					if (hoverCard == ci) {
						states.add(CardState.Hover);
					}

					if (context.deck != null && context.deck.format != null && !context.deck.format.cardIsLegal(ci.card())) {
						states.add(CardState.Flagged);
					}

					renderMap.put(new MVec2d(loc), new RenderStruct(futureImage.getNow(Images.CARD_BACK), states));
				} else {
					futureImage.thenRun(this::scheduleRender);
				}
			}
		}

		Platform.runLater(() -> {
			GraphicsContext gfx = getGraphicsContext2D();

			gfx.setFill(Color.WHITE);
			gfx.fillRect(0, 0, getWidth(), getHeight());

			if (hoverGroup != null) {
				gfx.setFill(Color.color(0.9, 0.9, 0.9));
				gfx.fillRect(hoverGroup.groupBounds.pos.x - scrollX.get(), hoverGroup.groupBounds.pos.y - scrollY.get(),
						hoverGroup.groupBounds.dim.x, hoverGroup.groupBounds.dim.y);
			}

			gfx.setFill(Color.BLACK);
			gfx.setTextAlign(TextAlignment.CENTER);
			gfx.setTextBaseline(VPos.CENTER);
			for (int i = 0; i < groupedModel.length; ++i) {
				if (groupedModel[i] == null || (!showEmptyGroupsProperty.get() && groupedModel[i].model.isEmpty())) {
					continue;
				}

				String s = groupedModel[i].group.toString();

				gfx.setFont(Font.font(groupedModel[i].labelBounds.dim.y));
				gfx.fillText(String.format("%s (%d)", s, groupedModel[i].model.size()),
						groupedModel[i].labelBounds.pos.x + groupedModel[i].labelBounds.dim.x / 2.0 - scrollX.get(),
						groupedModel[i].labelBounds.pos.y + groupedModel[i].labelBounds.dim.y / 2.0 - scrollY.get(),
						groupedModel[i].labelBounds.dim.x);
			}

			final double w = cardWidth();
			final double h = cardHeight();
			final double p = cardPadding();

			for (Map.Entry<MVec2d, RenderStruct> str : renderMap.entrySet()) {
				if (str.getValue().state.contains(CardState.Hover)) {
					gfx.setFill(Color.color(0.7, 0.7, 0.7));
					gfx.fillRoundRect(str.getKey().x - p, str.getKey().y - p, p + w + p, p + h + p, w / 8.0, w / 8.0);
				}

				gfx.drawImage(str.getValue().img, str.getKey().x, str.getKey().y, cardWidth(), cardHeight());

				if (str.getValue().state.contains(CardState.Flagged)) {
					gfx.setStroke(Color.RED);
					gfx.setLineWidth(4.0);
					gfx.strokeRoundRect(str.getKey().x, str.getKey().y, w, h, w / 12.0, w / 12.0);
				}
			}
		});
	}
}
