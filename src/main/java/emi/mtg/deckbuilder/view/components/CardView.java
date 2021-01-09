package emi.mtg.deckbuilder.view.components;

import emi.lib.mtg.Card;
import emi.mtg.deckbuilder.controller.Context;
import emi.mtg.deckbuilder.model.CardInstance;
import emi.mtg.deckbuilder.model.Preferences;
import emi.mtg.deckbuilder.view.Images;
import emi.mtg.deckbuilder.view.MainApplication;
import emi.mtg.deckbuilder.view.dialogs.PrintingSelectorDialog;
import javafx.application.Platform;
import javafx.beans.property.*;
import javafx.collections.FXCollections;
import javafx.collections.ListChangeListener;
import javafx.collections.ObservableList;
import javafx.collections.ObservableSet;
import javafx.collections.transformation.FilteredList;
import javafx.collections.transformation.SortedList;
import javafx.geometry.Rectangle2D;
import javafx.geometry.VPos;
import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.control.Tooltip;
import javafx.scene.effect.DropShadow;
import javafx.scene.image.Image;
import javafx.scene.input.*;
import javafx.scene.paint.Color;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;
import javafx.scene.text.TextAlignment;
import javafx.stage.Screen;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;
import java.util.function.Predicate;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;

public class CardView extends Canvas {
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

	public interface LayoutEngine {
		interface Factory {
			String name();
			LayoutEngine create(CardView parent);
		}

		void layoutGroups(Group[] groups, boolean includeEmpty);

		MVec2d coordinatesOf(int card, MVec2d buffer);
		int cardAt(MVec2d point, int groupSize);
	}

	public interface Grouping {
		interface Group {
			void add(CardInstance ci);
			void remove(CardInstance ci);
			boolean contains(CardInstance ci);
		}

		String name();
		boolean supportsModification();

		Group[] groups(List<CardInstance> model);
	}

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

	public static final List<LayoutEngine.Factory> LAYOUT_ENGINES;
	public static final List<Grouping> GROUPINGS;
	public static final List<Sorting> SORTINGS;

	public static final List<ActiveSorting> DEFAULT_SORTING, DEFAULT_COLLECTION_SORTING;

	static {
		LAYOUT_ENGINES = Collections.unmodifiableList(StreamSupport.stream(ServiceLoader.load(LayoutEngine.Factory.class, MainApplication.PLUGIN_CLASS_LOADER).spliterator(), true)
				.collect(Collectors.toList()));

		GROUPINGS = Collections.unmodifiableList(StreamSupport.stream(ServiceLoader.load(Grouping.class, MainApplication.PLUGIN_CLASS_LOADER).spliterator(), true)
				.collect(Collectors.toList()));

		Sorting color = null, cmc = null, manaCost = null, name = null, rarity = null;

		SORTINGS = Collections.unmodifiableList(StreamSupport.stream(ServiceLoader.load(Sorting.class, MainApplication.PLUGIN_CLASS_LOADER).spliterator(), false)
				.collect(Collectors.toList()));

		for (Sorting sorting : SORTINGS) {
			switch (sorting.toString()) {
				case "Color":
					color = sorting;
					break;
				case "Converted Mana Cost":
					cmc = sorting;
					break;
				case "Mana Cost":
					manaCost = sorting;
					break;
				case "Name":
					name = sorting;
					break;
				case "Rarity":
					rarity = sorting;
					break;
			}
		}

		assert color != null;
		assert cmc != null;
		assert manaCost != null;
		assert name != null;
		assert rarity != null;

		DEFAULT_SORTING = Arrays.asList(
				new ActiveSorting(color, false),
				new ActiveSorting(cmc, false),
				new ActiveSorting(manaCost, false),
				new ActiveSorting(name, false)
		);

		DEFAULT_COLLECTION_SORTING = Arrays.asList(
				new ActiveSorting(rarity, true),
				new ActiveSorting(color, false),
				new ActiveSorting(cmc, false),
				new ActiveSorting(manaCost, false),
				new ActiveSorting(name, false)
		);
	}

	public class Group {
		public final Bounds groupBounds, labelBounds;
		public final Grouping.Group group;
		private final ReadOnlyListWrapper<CardInstance> model;

		public Group(Grouping.Group group, ObservableList<CardInstance> modelSource, Comparator<CardInstance> initialSort) {
			this.group = group;
			this.groupBounds = new Bounds();
			this.labelBounds = new Bounds();

			final SortedList<CardInstance> modelProper = modelSource.filtered(group::contains).sorted(initialSort);
			this.model = new ReadOnlyListWrapper<>(modelProper);
			modelProper.addListener((ListChangeListener<CardInstance>) x -> CardView.this.layout());
		}

		public ObservableList<CardInstance> model() {
			return model.getReadOnlyProperty().get();
		}

		public void setSort(Comparator<CardInstance> sort) {
			assert model.get() instanceof SortedList;
			((SortedList<CardInstance>) model.get()).setComparator(sort);
		}
	}

	public static class ContextMenu extends javafx.scene.control.ContextMenu {
		public final SetProperty<CardInstance> cards = new SimpleSetProperty<>();
		public final SimpleObjectProperty<CardView.Group> group = new SimpleObjectProperty<>();
		public final SimpleObjectProperty<CardView> view = new SimpleObjectProperty<>();
	}

	final ObservableList<CardInstance> model;
	final FilteredList<CardInstance> filteredModel;
	private final FilteredList<CardInstance> collapsedModel;
	private final HashMap<Card, AtomicInteger> collapseAccumulator;
	private volatile Group[] groupedModel;

	private LayoutEngine engine;
	private Comparator<CardInstance> sort;
	private List<ActiveSorting> sortingElements;
	private Grouping grouping;

	private final DoubleProperty scrollMinX, scrollMinY, scrollX, scrollY, scrollMaxX, scrollMaxY;

	private enum DragMode {
		None,
		DragAndDrop,
		Panning,
		Zooming,
		Selecting
	}

	private volatile DragMode dragMode = DragMode.None;
	private volatile double dragStartX, dragStartY;
	private volatile double lastDragX, lastDragY;

	private Group hoverGroup;
	private CardInstance hoverCard;
	public final ObservableSet<CardInstance> selectedCards = FXCollections.observableSet(new HashSet<>());

	private CardInstance zoomedCard;
	private CardZoomPreview zoomPreview;

	private Consumer<CardInstance> doubleClick;
	private ContextMenu contextMenu;

	private final DoubleProperty cardScaleProperty;
	private final BooleanProperty showEmptyGroupsProperty;
	private final BooleanProperty collapseDuplicatesProperty;
	private final BooleanProperty immutableModel;
	private final BooleanProperty showFlags;

	private static boolean dragModified = false;
	private static CardView dragSource = null, dragTarget = null;

	private final Tooltip tooltip = new Tooltip();

	public CardView(ObservableList<CardInstance> model, LayoutEngine.Factory layout, Grouping grouping, List<ActiveSorting> sorts) {
		super(1024, 1024);

		setFocusTraversable(true);
		setManaged(true);

		this.cardScaleProperty = new SimpleDoubleProperty(Screen.getPrimary().getVisualBounds().getWidth() / 1920.0);
		this.cardScaleProperty.addListener(ce -> layout());

		// Collapsing duplicates might require regrouping for deck tags...
		this.collapseDuplicatesProperty = new SimpleBooleanProperty(false);

		this.showEmptyGroupsProperty = new SimpleBooleanProperty(false);
		this.showEmptyGroupsProperty.addListener(ce -> layout());

		this.engine = null;

		this.scrollMinX = new SimpleDoubleProperty(0.0);
		this.scrollMinY = new SimpleDoubleProperty(0.0);
		this.scrollX = new SimpleDoubleProperty(0.0);
		this.scrollY = new SimpleDoubleProperty(0.0);
		this.scrollMaxX = new SimpleDoubleProperty(100.0);
		this.scrollMaxY = new SimpleDoubleProperty(100.0);

		this.immutableModel = new SimpleBooleanProperty(false);
		this.showFlags = new SimpleBooleanProperty(true);
		this.showFlags.addListener(ce -> scheduleRender());

		this.hoverCard = null;
		this.hoverGroup = null;

		this.doubleClick = ci -> {};
		this.contextMenu = null;

		this.collapseAccumulator = new HashMap<>();
		// N.B. can NOT be a lambda due to JavaFX identity checks!
		final Supplier<Predicate<CardInstance>> freshTruePredicate = () -> new Predicate<CardInstance>() {
			@Override
			public boolean test(CardInstance cardInstance) {
				return true;
			}
		};
		final Supplier<Predicate<CardInstance>> freshCollapsePredicate = () -> new Predicate<CardInstance>() {
			{
				collapseAccumulator.clear();
			}

			@Override
			public boolean test(CardInstance ci) {
				return collapseAccumulator.computeIfAbsent(ci.card(), x -> new AtomicInteger()).incrementAndGet() == 1;
			}
		};

		this.sortingElements = sorts;
		this.sort = convertSorts(sorts);
		this.grouping = grouping;

		this.model = model;
		this.filteredModel = model.filtered(freshTruePredicate.get());
		this.collapsedModel = this.filteredModel.filtered(freshTruePredicate.get());

		final Runnable invalidateCollapsedModel = () -> {
			synchronized(this.model) {
				this.collapsedModel.setPredicate((collapseDuplicatesProperty.get() ? freshCollapsePredicate : freshTruePredicate).get());
			}
		};
		this.filteredModel.addListener((ListChangeListener<CardInstance>) lce -> invalidateCollapsedModel.run());
		this.collapseDuplicatesProperty.addListener((a, b, c) -> invalidateCollapsedModel.run());

		grouping(grouping);
		layout(layout);

		this.scrollX.addListener(e -> scheduleRender());
		this.scrollY.addListener(e -> scheduleRender());

		setOnMouseMoved(me -> {
			CardInstance lastHoverCard = hoverCard;

			mouseMoved(me.getX(), me.getY());

			if (hoverCard != null && hoverCard.lastValidation != null) {
				Tooltip.install(this, tooltip);
				if (!tooltip.isShowing() || hoverCard != lastHoverCard) {
					tooltip.setX(me.getScreenX());
					tooltip.setY(me.getScreenY());
					tooltip.setText(hoverCard.lastValidation.toString());
				}
			} else {
				tooltip.hide();
				Tooltip.uninstall(this, tooltip);
			}

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
			if (de.getButton() != MouseButton.PRIMARY) {
				return;
			}

			selectedCards.retainAll(this.model);

			if (selectedCards.isEmpty()) {
				return;
			}

			Dragboard db = this.startDragAndDrop(TransferMode.ANY);

			CardInstance view;
			if (selectedCards.size() == 1) {
				view = selectedCards.iterator().next();
			} else if (hoverCard != null) {
				view = hoverCard;
			} else {
				view = null;
			}

			if (view != null) {
				db.setDragView(Context.get().images.getThumbnail(view.printing()).thenApply(img -> {
					db.setDragView(img);
					return img;
				}).getNow(Images.LOADING_CARD));
			}

			db.setContent(Collections.singletonMap(DataFormat.PLAIN_TEXT, selectedCards.toString()));
			CardView.dragModified = de.isControlDown();
			CardView.dragSource = this;
			de.consume();
		});

		setOnDragOver(de -> {
			mouseMoved(de.getX(), de.getY());

			if (CardView.dragSource == this) {
				if (hoverGroup != null && !selectedCards.stream().allMatch(hoverGroup.group::contains)) {
					if (CardView.dragModified) {
						de.acceptTransferModes(TransferMode.LINK);
					} else {
						de.acceptTransferModes(TransferMode.MOVE);
					}
				} else if (hoverGroup != null) {
					de.acceptTransferModes(); // TODO: If we ever allow rearranging within groups.
				} else {
					// Don't accept.
				}
				de.consume();
			} else if (CardView.dragSource != null) {
				if (CardView.dragModified) {
					de.acceptTransferModes(TransferMode.COPY);
				} else {
					de.acceptTransferModes(TransferMode.MOVE);
				}
				de.consume();
			} else {
				// TODO: Accept transfer from other programs/areas?
			}
		});

		setOnDragDropped(de -> {
			if (CardView.dragSource == this) {
				if (this.grouping.supportsModification()) {
					this.selectedCards.forEach(this.hoverGroup.group::add);

					Set<Card> cards = this.selectedCards.stream().map(CardInstance::card).collect(Collectors.toSet());
					for (int i = 0; i < this.model.size(); ++i) {
						if (cards.contains(this.model.get(i).card())) {
							this.model.set(i, this.model.get(i)); // Refresh groupings
						}
					}
				}
			} else if (CardView.dragSource != null) {
				if (!immutableModel.get()) {
					Set<CardInstance> newCards = dragSource.selectedCards.stream()
							.map(ci -> {
								CardInstance clone = new CardInstance(ci.printing());
								clone.tags().addAll(ci.tags());

								if (this.grouping.supportsModification()) {
									this.hoverGroup.group.add(clone);
								}

								return clone;
							})
							.collect(Collectors.toSet());
					this.model.addAll(newCards);
					this.selectedCards.clear();
					this.selectedCards.addAll(newCards);
				}
			} else {
				// TODO: Accept transfer from other programs/areas?
			}

			layout();

			CardView.dragTarget = this;
			de.setDropCompleted(true);
			de.consume();
		});

		setOnDragDone(de -> {
			if (de.getAcceptedTransferMode() == TransferMode.MOVE) {
				if (CardView.dragTarget == this) {
					if (this.grouping.supportsModification()) {
						Set<Card> modifiedCards = new HashSet<>();
						for (Group group : this.groupedModel) {
							if (group == hoverGroup) {
								continue;
							}

							for (CardInstance ci : selectedCards) {
								group.group.remove(ci);
								modifiedCards.add(ci.card());
							}
						}

						for (int i = 0; i < this.model.size(); ++i) {
							if (modifiedCards.contains(this.model.get(i).card())) {
								this.model.set(i, this.model.get(i));
							}
						}
					}
				} else if (CardView.dragTarget != null) {
					if (!immutableModel.get()) {
						this.model.removeAll(selectedCards);
					}
					selectedCards.clear();
				}
			}

			layout();

			CardView.dragSource = null;
			CardView.dragTarget = null;
			de.consume();
		});

		setOnMouseClicked(me -> {
			if (me.getButton() == MouseButton.PRIMARY) {
				if (me.isAltDown()) {
					final CardInstance card = hoverCard;
					if (card == null || card.card().printings().size() == 1) {
						return;
					}

					if (immutableModel.get()) {
						if (collapsedModel.stream().anyMatch(ci -> ci.card() == card.card() && ci.printing() != card.printing())) {
							return; // Other versions are already represented. TODO: I hate this. Bind to CardPane's showVersionsSeparately?
						}
					}

					final List<CardInstance> modifyingCards = hoverCards(card);
					PrintingSelectorDialog.show(getScene(), card.card()).ifPresent(pr -> {
						if (immutableModel.get()) {
							Preferences.get().preferredPrintings.put(card.card().fullName(), pr.id());
						} else {
							modifyingCards.forEach(x -> x.printing(pr));
						}

						invalidateCollapsedModel.run();
					});
				} else if (me.getClickCount() % 2 == 0) {
					CardInstance ci = cardAt(me.getX(), me.getY());

					if (ci != null) {
						this.doubleClick.accept(ci);
					}
				}
			} else if (me.getButton() == MouseButton.SECONDARY) {
				if (this.contextMenu != null) {
					selectedCards.retainAll(this.model);
					if (!selectedCards.isEmpty()) {
						this.contextMenu.hide();
						this.contextMenu.view.set(this);
						this.contextMenu.cards.set(selectedCards);
						this.contextMenu.group.set(hoverGroup);
						this.contextMenu.show(this, me.getScreenX(), me.getScreenY());
					}
				}
			}
		});

		setOnScroll(se -> {
			scrollX.set(Math.max(scrollMinX.get(), Math.min(scrollX.get() - se.getDeltaX(), scrollMaxX.get())));
			scrollY.set(Math.max(scrollMinY.get(), Math.min(scrollY.get() - se.getDeltaY(), scrollMaxY.get())));
		});

		setOnMousePressed(me -> {
			this.requestFocus();
			mouseMoved(me.getX(), me.getY());

			if (this.contextMenu != null) {
				this.contextMenu.hide();
			}

			dragStartX = lastDragX = me.getX();
			dragStartY = lastDragY = me.getY();

			if (me.getButton() == MouseButton.PRIMARY) {
				if (!me.isAltDown()) {
					if (hoverCard == null) {
						if (!me.isControlDown()) {
							selectedCards.clear();
						}

						dragMode = DragMode.Selecting;
					} else {
						dragMode = DragMode.DragAndDrop;

						if (!selectedCards.contains(hoverCard)) {
							if (!me.isControlDown()) {
								selectedCards.clear();
							}
							selectedCards.add(hoverCard);
						}
					}
				}
			} else if (me.getButton() == MouseButton.SECONDARY) {
				dragMode = DragMode.None;

				if (!selectedCards.contains(hoverCard)) {
					if (!me.isControlDown()) {
						selectedCards.clear();
					}
					selectedCards.addAll(hoverCards(hoverCard));
				}
			} else if (me.getButton() == MouseButton.MIDDLE) {
				if (hoverCard == null) {
					dragMode = DragMode.Panning;
				} else {
					dragMode = DragMode.Zooming;
					showPreview(me);
				}
			}
		});

		setOnMouseDragged(me -> {
			switch (dragMode) {
				case None:
					break;
				case DragAndDrop:
					break;
				case Panning:
					scrollX.set(Math.max(scrollMinX.get(), Math.min(scrollX.get() - (me.getX() - lastDragX), scrollMaxX.get())));
					scrollY.set(Math.max(scrollMinY.get(), Math.min(scrollY.get() - (me.getY() - lastDragY), scrollMaxY.get())));
					break;
				case Selecting:
					break;
				case Zooming:
					showPreview(me);
					break;
			}

			lastDragX = me.getX();
			lastDragY = me.getY();

			scheduleRender();
		});

		setOnMouseReleased(me -> {
			switch (dragMode) {
				case None:
				case DragAndDrop:
				case Panning:
				case Zooming:
					break;
				case Selecting:
					if (!me.isControlDown()) {
						selectedCards.clear();
					}
					selectedCards.addAll(cardsInBounds(dragStartX, dragStartY, me.getX(), me.getY()));
					break;
			}

			lastDragX = -1;
			lastDragY = -1;
			dragStartX = -1;
			dragStartY = -1;
			dragMode = DragMode.None;

			if (zoomPreview != null) {
				zoomPreview.close();
				zoomPreview = null;
				zoomedCard = null;
			}

			scheduleRender();
		});
	}

	private List<CardInstance> hoverCards(CardInstance ci) {
		if (ci == null) return Collections.emptyList();
		if (!collapseDuplicatesProperty.get()) return Collections.singletonList(ci);

		return filteredModel.stream()
				.filter(x -> x.printing() == ci.printing())
				.collect(Collectors.toList());
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
			hoverCard = null;
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

	private synchronized void showPreview(MouseEvent me) {
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

			try {
				zoomPreview = new CardZoomPreview(start, zoomedCard.printing());
			} catch (InterruptedException | ExecutionException e) {
				e.printStackTrace();
			}
		}
	}

	private boolean cardInBounds(MVec2d loc, double x1, double y1, double x2, double y2) {
		return loc.x + cardWidth() >= x1 && loc.x <= x2 && loc.y + cardHeight() >= y1 && loc.y <= y2;
	}

	private Set<CardInstance> cardsInBounds(double x1, double y1, double x2, double y2) {
		Set<CardInstance> selectedCards = new HashSet<>();

		double x1s = Math.min(x1, x2);
		double y1s = Math.min(y1, y2);
		double x2s = Math.max(x1, x2);
		double y2s = Math.max(y1, y2);

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

				if (cardInBounds(loc, x1s, y1s, x2s, y2s)) {
					selectedCards.addAll(hoverCards(groupedModel[i].model.get(j)));
				}
			}
		}

		return selectedCards;
	}

	private MVec2d cardCoordinatesOf(double x, double y) {
		if (this.engine == null || collapsedModel.size() == 0) {
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
		if (this.engine == null || collapsedModel.size() == 0) {
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

	public void layout(LayoutEngine.Factory factory) {
		this.engine = factory.create(this);
		layout();
	}

	public void grouping(Grouping grouping) {
		this.grouping = grouping;
		this.groupedModel = Arrays.stream(this.grouping.groups(this.model))
				.map(g -> new Group(g, this.collapsedModel, this.sort))
				.toArray(Group[]::new);
		layout();
	}

	public Grouping grouping() {
		return this.grouping;
	}

	public void regroup() {
		grouping(this.grouping);
	}

	public void refreshCardGrouping(Collection<CardInstance> modifiedCards) {
		for (int i = 0; i < this.model.size(); ++i) {
			if (modifiedCards.contains(this.model.get(i))) {
				this.model.set(i, this.model.get(i)); // Refresh groupings
			}
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
					g.setSort(this.sort);
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

	public void doubleClick(Consumer<CardInstance> doubleClick) {
		this.doubleClick = doubleClick;
	}

	public void contextMenu(ContextMenu contextMenu) {
		this.contextMenu = contextMenu;
	}

	public DoubleProperty cardScaleProperty() {
		return cardScaleProperty;
	}

	public BooleanProperty collapseDuplicatesProperty() {
		return collapseDuplicatesProperty;
	}

	public BooleanProperty showEmptyGroupsProperty() {
		return showEmptyGroupsProperty;
	}

	public BooleanProperty immutableModelProperty() {
		return immutableModel;
	}

	public BooleanProperty showFlagsProperty() {
		return showFlags;
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
		return Arrays.stream(groupedModel)
				.mapToDouble(g -> Math.max(g.labelBounds.pos.x, g.groupBounds.pos.x) + Math.max(g.labelBounds.dim.x, g.groupBounds.dim.x))
				.max()
				.orElse(getWidth());
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
		return Arrays.stream(groupedModel)
				.mapToDouble(g -> Math.max(g.labelBounds.pos.y, g.groupBounds.pos.y) + Math.max(g.labelBounds.dim.y, g.groupBounds.dim.y))
				.max()
				.orElse(getHeight());
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

	private volatile long renderGeneration = 0;

	public synchronized void scheduleRender() {
		final long nextGen = ++renderGeneration;
		ForkJoinPool.commonPool().submit(() -> this.render(nextGen));
	}

	public synchronized void layout() {
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

	public enum CardState {
		Full (Color.TRANSPARENT, Color.color(0.0f, 0.0f, 0.0f, 0.5f)),
		Hover (Color.GREEN, Color.TRANSPARENT),
		Selected (Color.DODGERBLUE, Color.DODGERBLUE.deriveColor(0.0, 1.0, 1.0, 0.25)),
		Flagged (Color.RED, Color.TRANSPARENT),
		Warning (Color.ORANGE, Color.TRANSPARENT),
		Notice (Color.LIGHTGREEN, Color.TRANSPARENT);

		public final Color outlineColor, fillColor;

		CardState(Color outlineColor, Color fillColor) {
			this.outlineColor = outlineColor;
			this.fillColor = fillColor;
		}
	}

	static class RenderStruct {
		public final Image img;
		public final EnumSet<CardState> state;
		private final int count;

		public RenderStruct(Image img, EnumSet<CardState> states, int count) {
			this.img = img;
			this.state = states;
			this.count = count;
		}
	}

	@SuppressWarnings("DuplicateCondition")
	protected synchronized void render(long generation) {
		if (generation < renderGeneration) return;

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

		if (collapsedModel == null || collapsedModel.isEmpty()) {
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

		if (generation < renderGeneration) return;
		SortedMap<MVec2d, RenderStruct> renderMap = buildRenderMap();

		if (generation < renderGeneration) return;
		Platform.runLater(() -> drawRenderMap(generation, renderMap));
	}

	private void drawRenderMap(long generation, SortedMap<MVec2d, RenderStruct> renderMap) {
		if (generation < renderGeneration) return;

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

			int size;
			if (collapseDuplicatesProperty.get()) {
				size = 0;
				for (CardInstance ci : groupedModel[i].model) {
					size += filteredModel.stream().filter(x -> ci.card() == x.card()).count();
				}
			} else {
				size = groupedModel[i].model.size();
			}

			gfx.setFont(Font.font(groupedModel[i].labelBounds.dim.y));
			gfx.fillText(String.format("%s (%d)", s, size),
					groupedModel[i].labelBounds.pos.x + groupedModel[i].labelBounds.dim.x / 2.0 - scrollX.get(),
					groupedModel[i].labelBounds.pos.y + groupedModel[i].labelBounds.dim.y / 2.0 - scrollY.get(),
					groupedModel[i].labelBounds.dim.x);
		}

		final double cw = cardWidth();
		final double ch = cardHeight();

		for (Map.Entry<MVec2d, RenderStruct> str : renderMap.entrySet()) {
			gfx.drawImage(str.getValue().img, str.getKey().x, str.getKey().y, cw, ch);

			boolean drewFill = false, drewOutline = false;

			for (CardState state : CardState.values()) {
				if (str.getValue().state.contains(state)) {
					if (!drewFill && state.fillColor != Color.TRANSPARENT) {
						drewFill = true;

						gfx.setFill(state.fillColor);
						gfx.fillRoundRect(str.getKey().x, str.getKey().y, cw, ch, cw / 8.0, cw / 8.0);
					}

					if (!drewOutline && state.outlineColor != Color.TRANSPARENT) {
						drewOutline = true;

						gfx.setStroke(state.outlineColor);
						gfx.setLineWidth(6.0);
						gfx.strokeRoundRect(str.getKey().x, str.getKey().y, cw, ch, cw / 12.0, cw / 12.0);
					}
				}
			}

			if (str.getValue().count != 1) {
				gfx.setTextAlign(TextAlignment.RIGHT);
				gfx.setFill(Color.WHITE);
				gfx.setEffect(new DropShadow(8.0, Color.BLACK));
				gfx.setFont(Font.font(null, FontWeight.BOLD,null, ch / 12.0));
				gfx.fillText(String.format("x%d", str.getValue().count),
						str.getKey().x + cw * 0.95,
						str.getKey().y + cw * 0.075,
						cw);
				gfx.setEffect(null);
			}
		}

		if (dragMode == DragMode.Selecting) {
			double x = Math.min(dragStartX, lastDragX);
			double y = Math.min(dragStartY, lastDragY);
			double w = Math.max(dragStartX, lastDragX) - x;
			double h = Math.max(dragStartY, lastDragY) - y;

			gfx.setFill(Color.DODGERBLUE.deriveColor(0.0, 1.0, 1.0, 0.25));
			gfx.fillRect(x, y, w, h);

			gfx.setStroke(Color.DODGERBLUE);
			gfx.setFill(Color.TRANSPARENT);
			gfx.setLineWidth(2.0);
			gfx.strokeRect(x, y, w, h);
		}

		// This should never happen.
		if (generation >= Long.MAX_VALUE / 2 && generation == renderGeneration) {
			renderGeneration = 0;
		}
	}

	private SortedMap<MVec2d, RenderStruct> buildRenderMap() {
		SortedMap<MVec2d, RenderStruct> renderMap = new TreeMap<>();
		MVec2d loc = new MVec2d();

		double dragBoxX1 = Math.min(dragStartX, lastDragX);
		double dragBoxY1 = Math.min(dragStartY, lastDragY);
		double dragBoxX2 = Math.max(dragStartX, lastDragX);
		double dragBoxY2 = Math.max(dragStartY, lastDragY);

		Set<CompletableFuture<Image>> waiting = new HashSet<>();

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

				CompletableFuture<Image> futureImage = Context.get().images.getThumbnail(printing);

				if (!futureImage.isDone()) {
					waiting.add(futureImage);
				}

				EnumSet<CardState> states = EnumSet.noneOf(CardState.class);

				if (showFlags.get()) {
					if (ci.flags.contains(CardInstance.Flags.Invalid)) {
						states.add(CardState.Flagged);
					}

					if (ci.flags.contains(CardInstance.Flags.Full)) {
						states.add(CardState.Full);
					}

					if (ci.flags.contains(CardInstance.Flags.Warning)) {
						states.add(CardState.Warning);
					}

					if (ci.flags.contains(CardInstance.Flags.Notice)) {
						states.add(CardState.Notice);
					}
				}

				if (hoverCard == ci) {
					states.add(CardState.Hover);
				}

				if (selectedCards.contains(ci)) {
					states.add(CardState.Selected);
				} else if (dragMode == DragMode.Selecting) {
					if (cardInBounds(loc, dragBoxX1, dragBoxY1, dragBoxX2, dragBoxY2)) {
						states.add(CardState.Selected);
					}
				}

				int count = 1;
				if (collapseDuplicatesProperty.get() && collapseAccumulator.containsKey(ci.card())) {
					count = collapseAccumulator.get(ci.card()).get();
				}

				renderMap.put(new MVec2d(loc), new RenderStruct(futureImage.getNow(Images.LOADING_CARD), states, count));
			}
		}

		if (!waiting.isEmpty()) {
			CompletableFuture.allOf(waiting.toArray(new CompletableFuture[0])).thenRun(this::scheduleRender);
		}

		return renderMap;
	}

	public synchronized void renderNow() throws IllegalStateException {
		if (!Platform.isFxApplicationThread()) {
			throw new IllegalStateException("renderNow must be called from the FX Application thread!");
		}

		drawRenderMap(-1, buildRenderMap());
	}
}
