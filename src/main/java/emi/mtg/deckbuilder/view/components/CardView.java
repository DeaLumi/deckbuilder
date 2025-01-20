package emi.mtg.deckbuilder.view.components;

import emi.lib.mtg.Card;
import emi.mtg.deckbuilder.controller.Context;
import emi.mtg.deckbuilder.controller.DeckChanger;
import emi.mtg.deckbuilder.controller.Tags;
import emi.mtg.deckbuilder.model.CardInstance;
import emi.mtg.deckbuilder.model.DeckList;
import emi.mtg.deckbuilder.model.FilteredGroupedModel;
import emi.mtg.deckbuilder.model.Preferences;
import emi.mtg.deckbuilder.util.UniqueList;
import emi.mtg.deckbuilder.view.Images;
import emi.mtg.deckbuilder.view.dialogs.PrintSelectorDialog;
import emi.mtg.deckbuilder.util.PluginUtils;
import emi.mtg.deckbuilder.view.util.AlertBuilder;
import javafx.application.Platform;
import javafx.beans.property.*;
import javafx.collections.*;
import javafx.collections.transformation.SortedList;
import javafx.geometry.Rectangle2D;
import javafx.geometry.VPos;
import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.control.*;
import javafx.scene.effect.DropShadow;
import javafx.scene.image.Image;
import javafx.scene.input.*;
import javafx.scene.paint.Color;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;
import javafx.scene.text.TextAlignment;
import javafx.stage.Screen;

import java.io.IOException;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ForkJoinPool;
import java.util.function.Consumer;
import java.util.function.Supplier;
import java.util.stream.Collectors;

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

		public boolean empty() {
			return dim.x >= 0 && dim.y >= 0;
		}

		public Bounds plus(Bounds other) {
			final double myHighX = pos.x + dim.x;
			final double myHighY = pos.y + dim.y;

			pos.x = Math.min(pos.x, other.pos.x);
			pos.y = Math.min(pos.y, other.pos.y);

			dim.x = myHighX - pos.x;
			dim.y = myHighY - pos.y;

			return this;
		}
	}

	public interface LayoutEngine {
		interface Factory {
			String name();
			LayoutEngine create(CardView parent);
		}

		void layoutGroups(Bounds boundingBox, Group[] groups, boolean includeEmpty);

		MVec2d coordinatesOf(int card, MVec2d buffer);
		int cardAt(MVec2d point, int groupSize);

		boolean cardInSelection(MVec2d cardPos, MVec2d min, MVec2d max, int groupSize);

		default boolean cardInSelection(int card, MVec2d min, MVec2d max, MVec2d buffer, int groupSize) {
			buffer = coordinatesOf(card, buffer);
			return cardInSelection(buffer, min, max, groupSize);
		}
	}

	public interface Grouping {
		interface Group extends Comparable<Group> {
			default void add(DeckList deck, CardInstance ci) {
				throw new UnsupportedOperationException();
			}

			default void remove(DeckList deck, CardInstance ci) {
				throw new UnsupportedOperationException();
			}

			boolean contains(CardInstance ci);
		}

		String name();

		default boolean supportsModification() {
			return false;
		}

		Set<Group> groups(CardInstance card);
	}

	public interface Sorting extends Comparator<CardInstance> {
		// nothing to do...
	}

	public static class ActiveSorting {
		public static Comparator<CardInstance> merge(List<ActiveSorting> sorts) {
			if (sorts == null || sorts.isEmpty()) throw new IllegalArgumentException("At least one sort must be specified.");

			Comparator<CardInstance> sort = (c1, c2) -> 0;
			for (ActiveSorting element : sorts) {
				sort = sort.thenComparing(element.descending.get() ? element.sorting.reversed() : element.sorting);
			}

			return sort;
		}

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

	public static final Map<Class<? extends LayoutEngine.Factory>, LayoutEngine.Factory> LAYOUT_ENGINES = PluginUtils.providersMap(LayoutEngine.Factory.class);
	public static final Map<Class<? extends Grouping>, Grouping> GROUPINGS = PluginUtils.providersMap(Grouping.class);
	public static final Map<Class<? extends Sorting>, Sorting> SORTINGS = PluginUtils.providersMap(Sorting.class);

	public static final List<ActiveSorting> DEFAULT_SORTING, DEFAULT_COLLECTION_SORTING;

	static {
		Sorting color = SORTINGS.get(emi.mtg.deckbuilder.view.sortings.Color.class),
				mv = SORTINGS.get(emi.mtg.deckbuilder.view.sortings.ManaValue.class),
				manaCost = SORTINGS.get(emi.mtg.deckbuilder.view.sortings.ManaCost.class),
				name = SORTINGS.get(emi.mtg.deckbuilder.view.sortings.Name.class),
				rarity = SORTINGS.get(emi.mtg.deckbuilder.view.sortings.Rarity.class);

		assert color != null;
		assert mv != null;
		assert manaCost != null;
		assert name != null;
		assert rarity != null;

		DEFAULT_SORTING = Collections.unmodifiableList(Arrays.asList(
				new ActiveSorting(color, false),
				new ActiveSorting(mv, false),
				new ActiveSorting(manaCost, false),
				new ActiveSorting(name, false)
		));

		DEFAULT_COLLECTION_SORTING = Collections.unmodifiableList(Arrays.asList(
				new ActiveSorting(rarity, true),
				new ActiveSorting(color, false),
				new ActiveSorting(mv, false),
				new ActiveSorting(manaCost, false),
				new ActiveSorting(name, false)
		));
	}

	private class PanBehavior {
		private final MVec2d panStart, scrollStart;

		public PanBehavior() {
			CardView.this.addEventHandler(MouseEvent.MOUSE_PRESSED, this::mousePressed);
			CardView.this.addEventHandler(MouseEvent.MOUSE_DRAGGED, this::mouseDragged);
			CardView.this.addEventHandler(MouseEvent.MOUSE_RELEASED, this::mouseReleased);
			this.panStart = new MVec2d(Double.NaN, Double.NaN);
			this.scrollStart = new MVec2d(Double.NaN, Double.NaN);
		}

		void mousePressed(MouseEvent me) {
			if (me.getButton() != MouseButton.MIDDLE) return;
			if (CardView.this.hoverCard != null) return;

			CardView.this.requestFocus();
			panStart.set(me.getX(), me.getY());
			scrollStart.set(CardView.this.scrollX.get(), CardView.this.scrollY.get());

			me.consume();
		}

		void mouseDragged(MouseEvent me) {
			if (Double.isNaN(panStart.x) || Double.isNaN(panStart.y)) return;

			CardView.this.scrollX.set(Math.max(CardView.this.scrollMinX.get(), Math.min(scrollStart.x - (me.getX() - panStart.x), CardView.this.scrollMaxX.get())));
			CardView.this.scrollY.set(Math.max(CardView.this.scrollMinY.get(), Math.min(scrollStart.y - (me.getY() - panStart.y), CardView.this.scrollMaxY.get())));

			me.consume();
			CardView.this.scheduleRender();
		}

		void mouseReleased(MouseEvent me) {
			if (Double.isNaN(panStart.x) || Double.isNaN(panStart.y)) return;

			panStart.set(Double.NaN, Double.NaN);
			scrollStart.set(Double.NaN, Double.NaN);

			me.consume();
		}
	}

	private class DragAndDropBehavior {
		private boolean dragging;

		public DragAndDropBehavior() {
			CardView.this.addEventHandler(MouseEvent.MOUSE_PRESSED, this::mousePressed);
			CardView.this.addEventHandler(MouseEvent.DRAG_DETECTED, this::dragDetected);
			CardView.this.addEventHandler(DragEvent.DRAG_OVER, this::dragOver);
			CardView.this.addEventHandler(DragEvent.DRAG_DROPPED, this::dragDropped);
			CardView.this.addEventHandler(DragEvent.DRAG_DONE, this::dragDone);
			this.dragging = false;
		}

		void mousePressed(MouseEvent me) {
			if (me.getButton() != MouseButton.PRIMARY) return;
			if (me.isAltDown()) return;
			if (CardView.this.hoverCard == null) return;

			if (!CardView.this.selectedCards.contains(CardView.this.hoverCard)) {
				if (!me.isControlDown()) CardView.this.selectedCards.clear();
				CardView.this.selectedCards.add(CardView.this.hoverCard);
			}

			CardView.this.requestFocus();
			dragging = true;
			me.consume();
		}

		void dragDetected(MouseEvent me) {
			if (!dragging) return;
			if (me.getButton() != MouseButton.PRIMARY) return;

			CardView.this.selectedCards.retainAll(CardView.this.model.source);

			if (CardView.this.selectedCards.isEmpty()) {
				return;
			}

			Dragboard db = CardView.this.startDragAndDrop(TransferMode.ANY);

			CardInstance view;
			if (CardView.this.hoverCard != null) {
				view = CardView.this.hoverCard;
			} else {
				view = CardView.this.selectedCards.iterator().next();
			}

			if (view != null) {
				Context.get().images.getThumbnail(view.print()).thenApply(img -> {
					db.setDragView(img, -8.0, -8.0);
					return img;
				}).getNow(Images.LOADING_CARD);
			}

			db.setContent(Collections.singletonMap(DataFormat.PLAIN_TEXT, CardView.this.selectedCards.toString()));
			CardView.dragModified = me.isControlDown();
			CardView.dragSource = CardView.this;
			me.consume();
		}

		void dragOver(DragEvent de) {
			if (CardView.dragSource == CardView.this) {
				// TODO Should I check dragging here?
				CardView.this.mouseMoved(de.getX(), de.getY()); // TODO BEHAVIOR

				if (CardView.this.hoverGroup != null && !CardView.this.selectedCards.stream().allMatch(CardView.this.hoverGroup.group::contains)) {
					if (CardView.dragModified) {
						de.acceptTransferModes(TransferMode.LINK);
					} else {
						de.acceptTransferModes(TransferMode.MOVE);
					}
				} else if (CardView.this.hoverGroup != null) {
					de.acceptTransferModes(); // TODO: If we ever allow rearranging within groups.
				} else {
					// Don't accept.
				}
				de.consume();
			} else if (CardView.dragSource != null) {
				CardView.this.mouseMoved(de.getX(), de.getY()); // TODO BEHAVIOR

				if (CardView.dragModified) {
					de.acceptTransferModes(TransferMode.COPY);
				} else {
					de.acceptTransferModes(TransferMode.MOVE);
				}
				de.consume();
			} else {
				// TODO: Accept transfer from other programs/areas?
			}
		}

		void dragDropped(DragEvent de) {
			if (CardView.dragSource == CardView.this) {
				if (CardView.this.grouping.supportsModification()) {
					final List<CardInstance> changed = new ArrayList<>(CardView.this.selectedCards);

					if (CardView.this.deck != null) DeckChanger.startChangeBatch(CardView.this.deck);

					changed.forEach(ci -> CardView.this.hoverGroup.group.add(CardView.this.deck, ci));

					if (de.getAcceptedTransferMode() == TransferMode.MOVE) {
						for (Group group : CardView.this.groupedModel.values()) {
							if (group == hoverGroup) {
								continue;
							}

							for (CardInstance ci : changed) {
								group.group.remove(CardView.this.deck, ci);
							}
						}
					}

					// Hacky hack to force value changed events since cards can't advertise it.
					if (CardView.this.deck != null) {
						DeckChanger.addBatchedChange(
								CardView.this.deck,
								l -> changed.forEach(model::sourceElementChanged),
								l -> changed.forEach(model::sourceElementChanged)
						);

						DeckChanger.endChangeBatch(
								CardView.this.deck,
								"Change Tags"
						);
					} else {
						changed.forEach(model::sourceElementChanged);
					}

					de.setDropCompleted(true);
					de.consume();
				}
			} else if (CardView.dragSource != null) {
				Set<CardInstance> oldCards = new HashSet<>(CardView.dragSource.selectedCards);

				if (de.getAcceptedTransferMode() == TransferMode.MOVE && CardView.dragSource.deck != null) {
					final CardView source = CardView.dragSource;
					final DeckList sourceDeck = source.deck;
					final ObservableList<CardInstance> sourceModel = source.model.source;
					DeckChanger.startChangeBatch(sourceDeck);

					if (source.grouping.supportsModification() && CardView.this.hoverGroup != null && CardView.this.grouping.supportsModification()) {
						for (Grouping.Group oldGroup : source.groupedModel.keySet()) {
							for (CardInstance oldCard : source.selectedCards) {
								oldGroup.remove(sourceDeck, oldCard);
							}
						}
					}

					DeckChanger.addBatchedChange(
							sourceDeck,
							l -> sourceModel.removeAll(oldCards),
							l -> sourceModel.addAll(oldCards)
					);

					if (sourceDeck != CardView.this.deck) DeckChanger.endChangeBatch(sourceDeck, String.format("Remove %d Card%s", oldCards.size(), oldCards.size() > 1 ? "s" : ""));
					source.selectedCards.clear();
				}

				if (CardView.this.deck != null) {
					if (CardView.dragSource.deck != CardView.this.deck) DeckChanger.startChangeBatch(CardView.this.deck);
					Set<CardInstance> newCards = oldCards.stream()
							.map(ci -> {
								CardInstance clone = new CardInstance(ci);

								if (CardView.this.hoverGroup != null && CardView.this.grouping.supportsModification()) {
									CardView.this.hoverGroup.group.add(CardView.this.deck, clone);
								}

								return clone;
							})
							.collect(Collectors.toSet());

					final ObservableList<CardInstance> thisModel = CardView.this.model.source;
					DeckChanger.addBatchedChange(
							CardView.this.deck,
							l -> thisModel.addAll(newCards),
							l -> thisModel.removeAll(newCards)
					);
					DeckChanger.endChangeBatch(CardView.this.deck, String.format("%s %d Card%s", CardView.dragSource.deck != deck ? "Add" : "Move", newCards.size(), newCards.size() > 1 ? "s" : ""));

					CardView.this.selectedCards.clear();
					CardView.this.selectedCards.addAll(newCards);
				}

				de.setDropCompleted(true);
				de.consume();
			} else {
				// TODO: Accept transfer from other programs/areas?
			}

			CardView.this.layout();
		}

		void dragDone(DragEvent de) {
			if (de.isConsumed()) return;

			if (CardView.dragSource != null) {
				CardView.dragSource = null;
				de.consume();
			}
		}
	}

	private class SelectBehavior {
		private boolean selecting;
		private volatile double startX, startY, selectX, selectY, selectW, selectH, selectX2, selectY2;

		public SelectBehavior() {
			CardView.this.addEventHandler(MouseEvent.MOUSE_PRESSED, this::mousePressed);
			CardView.this.addEventHandler(MouseEvent.MOUSE_DRAGGED, this::mouseDragged);
			CardView.this.addEventHandler(MouseEvent.MOUSE_RELEASED, this::mouseReleased);
			this.selecting = false;
			this.startX = this.startY = this.selectX = this.selectY = this.selectW = this.selectH = this.selectX2 = this.selectY2 = Double.NaN;
		}

		private void mousePressed(MouseEvent me) {
			if (me.getButton() != MouseButton.PRIMARY) return;
			if (me.isAltDown()) return;
			if (CardView.this.hoverCard != null) return;

			if (!me.isControlDown()) {
				CardView.this.selectedCards.clear();
			}

			CardView.this.requestFocus();
			this.selecting = true;
			startX = selectX = selectX2 = me.getX() + CardView.this.scrollX.get();
			startY = selectY = selectY2 = me.getY() + CardView.this.scrollY.get();
			selectW = 0;
			selectH = 0;
			me.consume();
		}

		private void mouseDragged(MouseEvent me) {
			if (!selecting) return;

			double absX = me.getX() + CardView.this.scrollX.get(), absY = me.getY() + CardView.this.scrollY.get();

			if (absX < startX) {
				selectX = absX;
				selectX2 = startX;
				selectW = startX - selectX;
			} else {
				selectX = startX;
				selectX2 = absX;
				selectW = absX - startX;
			}

			if (absY < startY) {
				selectY = absY;
				selectY2 = startY;
				selectH = startY - selectY;
			} else {
				selectY = startY;
				selectY2 = absY;
				selectH = absY - startY;
			}

			CardView.this.scheduleRender();
			me.consume(); // TODO: Is this necessary?
		}

		private void mouseReleased(MouseEvent me) {
			if (!selecting) return;

			if (!me.isControlDown()) CardView.this.selectedCards.clear();
			CardView.this.selectedCards.addAll(CardView.this.cardsInBounds(selectX, selectY, selectX2, selectY2));

			selecting = false;
			startX = startY = selectX = selectY = selectW = selectH = Double.NaN;
		}
	}

	private class ZoomBehavior {
		private CardInstance zoomedCard;
		private CardZoomPreview zoomPreview;
		private boolean zoomKeyDown, mouse3Down;

		public ZoomBehavior() {
			CardView.this.addEventHandler(KeyEvent.KEY_PRESSED, this::keyPressed);
			CardView.this.addEventHandler(KeyEvent.KEY_RELEASED, this::keyReleased);
			CardView.this.addEventHandler(MouseEvent.MOUSE_PRESSED, this::mousePressed);
			CardView.this.addEventHandler(MouseEvent.MOUSE_RELEASED, this::mouseReleased);
			CardView.this.addEventHandler(MouseEvent.MOUSE_DRAGGED, this::mouseDraggedOrMoved);
			CardView.this.addEventHandler(MouseEvent.MOUSE_MOVED, this::mouseDraggedOrMoved);
			this.zoomPreview = null;
			this.zoomedCard = null;
			this.zoomKeyDown = this.mouse3Down = false;
		}

		private void keyPressed(KeyEvent ke) {
			if (zoomKeyDown) return;
			if (ke.getCode() != KeyCode.Z) return;
			zoomKeyDown = true;

			if (!mouse3Down) mouseDraggedOrMoved(ke);
			ke.consume();
		}

		private void keyReleased(KeyEvent ke) {
			if (!zoomKeyDown) return;
			if (ke.getCode() != KeyCode.Z) return;
			zoomKeyDown = false;

			if (!mouse3Down) endPreview();
			ke.consume();
		}

		private void mousePressed(MouseEvent me) {
			if (mouse3Down) return;
			if (me.getButton() != MouseButton.MIDDLE) return;
			if (CardView.this.hoverCard == null) return; // Pan instead
			mouse3Down = true;

			if (!zoomKeyDown) mouseDraggedOrMoved(me);
			me.consume();
		}

		private void mouseReleased(MouseEvent me) {
			if (!mouse3Down) return;
			if (me.getButton() != MouseButton.MIDDLE) return;
			mouse3Down = false;

			if (!zoomKeyDown) endPreview();
			me.consume();
		}

		private void mouseDraggedOrMoved(InputEvent ie) {
			if (!zoomKeyDown && !mouse3Down) return;

			showPreview(ie);
			ie.consume();
		}

		private void endPreview() {
			if (zoomPreview != null) {
				zoomPreview.close();
				zoomPreview = null;
				zoomedCard = null;
			}
		}

		private synchronized void showPreview(InputEvent ie) {
			CardInstance ci;
			Group group;
			int idx;
			if (!(ie instanceof MouseEvent)) {
				ci = hoverCard;
				idx = hoverCardGroupIdx;
				group = hoverGroup;
			} else {
				MouseEvent me = (MouseEvent) ie;
				if (me.getPickResult().getIntersectedNode() != CardView.this) {
					ci = null;
					idx = -1;
					group = null;
				} else {
					CardHitResult search = cardAt(me.getX(), me.getY());
					ci = search.card;
					idx = search.index;
					group = search.group;
				}
			}

			if (zoomedCard == ci) {
				return;
			}

			zoomedCard = ci;

			if (zoomPreview != null) {
				zoomPreview.close();
			}

			if (zoomedCard != null) {
				MVec2d zoomLoc = new MVec2d(0.0, 0.0);
				CardView.this.engine.coordinatesOf(idx, zoomLoc);
				zoomLoc.plus(group.groupBounds.pos.x, group.groupBounds.pos.y);
				zoomLoc.plus(-scrollX.get(), -scrollY.get());
				javafx.geometry.Point2D point = CardView.this.localToScreen(0.0, 0.0);
				zoomLoc.plus(point.getX(), point.getY());

				Rectangle2D start = new Rectangle2D(zoomLoc.x, zoomLoc.y, cardWidth(), cardHeight());

				try {
					zoomPreview = new CardZoomPreview(CardView.this, start, zoomedCard.print());
					requestFocus();
				} catch (InterruptedException | ExecutionException e) {
					e.printStackTrace();
				}
			}
		}
	}

	public class Group {
		public final Bounds groupBounds, labelBounds;
		public final Grouping.Group group;
		private final ObservableList<CardInstance> filteredModel;
		private final SortedList<CardInstance> sortedModel;
		private final UniqueList<CardInstance> collapsedModel;

		public Group(Grouping.Group group, ObservableList<CardInstance> modelSource, Comparator<CardInstance> initialSort) {
			this.group = group;
			this.groupBounds = new Bounds();
			this.labelBounds = new Bounds();

			this.filteredModel = modelSource;
			this.sortedModel = this.filteredModel.sorted(initialSort);
			this.collapsedModel = new UniqueList<>(this.sortedModel, CardInstance::print);

			this.collapsedModel.addListener((ListChangeListener<CardInstance>) x -> CardView.this.layout());
		}

		public ObservableList<CardInstance> model() {
			return CardView.this.collapseDuplicatesProperty.get() ? collapsedModel : sortedModel;
		}

		public synchronized void setSort(Comparator<CardInstance> sort) {
			this.sortedModel.setComparator(sort);
		}

		public List<? extends CardInstance> hoverCards(CardInstance ci) {
			if (ci == null) return Collections.emptyList();
			if (!collapseDuplicatesProperty.get()) return Collections.singletonList(ci);

			return collapsedModel.getAll(collapsedModel.indexOf(ci));
		}
	}

	public static class ContextMenu extends javafx.scene.control.ContextMenu {
		public final SetProperty<CardInstance> cards = new SimpleSetProperty<>();
		public final SimpleObjectProperty<CardView.Group> group = new SimpleObjectProperty<>();
		public final SimpleObjectProperty<CardView> view = new SimpleObjectProperty<>();

		public ContextMenu addCopyImage() {
			MenuItem copyImage = new MenuItem("Copy Image");
			copyImage.setOnAction(ae -> {
				if (cards.isEmpty()) return;

				// TODO other faces
				Card.Print.Face face = cards.iterator().next().mainFaces().iterator().next();
				CompletableFuture<Image> img = Context.get().images.getFace(face);

				// Have to farm this to the common pool to avoid deadlock with the rendered image source. (Bleh!)
				ForkJoinPool.commonPool().submit(() -> {
					try {
						ClipboardContent content = new ClipboardContent();
						content.putImage(img.get());
						Platform.runLater(() -> Clipboard.getSystemClipboard().setContent(content));
					} catch (Throwable t) {
						AlertBuilder.create()
								.type(Alert.AlertType.ERROR)
								.title("Error")
								.headerText("An Error Occurred")
								.contentText("While trying to copy the image of " + face.face().name() + ": " + t.getMessage());
					}
				});
			});
			this.getItems().add(copyImage);

			return this;
		}

		public ContextMenu addCleanupImages() {
			MenuItem deleteImages = new MenuItem("Delete Saved Images");
			deleteImages.setOnAction(ae -> {
				try {
					for (CardInstance ci : cards) {
						for (Card.Print pr : ci.card().prints()) {
							Context.get().images.deleteSavedImages(pr);
						}
					}
				} catch (IOException e) {
					e.printStackTrace(); // These aren't fatal as far as I'm concerned...
				}
			});
			this.getItems().add(deleteImages);

			return this;
		}

		public ContextMenu addTagsMenu() {
			Menu tagsMenu = new Menu("Tags");

			this.setOnShowing(e -> {
				ObservableList<MenuItem> tagCBs = FXCollections.observableArrayList();
				tagCBs.setAll(view.get().model.source.stream()
						.map(CardInstance::tags)
						.flatMap(Set::stream)
						.distinct()
						.sorted()
						.map(CheckMenuItem::new)
						.peek(cmi -> cmi.setSelected(this.cards.stream().allMatch(ci -> ci.tags().contains(cmi.getText()))))
						.peek(cmi -> cmi.selectedProperty().addListener(x -> {
							final String tag = cmi.getText();
							final Set<CardInstance> cards = new HashSet<>(this.cards);
							final boolean added = cmi.isSelected();
							final CardView view = this.view.get();

							if (view.deck != null) {
								final Consumer<DeckList> addFn = l -> {
									cards.forEach(ci -> ci.tags().add(tag));
									view.refreshCardGrouping();
								}, removeFn = l -> {
									cards.forEach(ci -> ci.tags().remove(tag));
									view.refreshCardGrouping();
								};

								DeckChanger.change(
										view.deck,
										"Change Tags",
										added ? addFn : removeFn,
										added ? removeFn : addFn
								);
							} else {
								final Tags tags = Context.get().tags;
								if (added) {
									cards.stream()
											.peek(ci -> ci.tags().add(tag))
											.map(CardInstance::card)
											.distinct()
											.forEach(c -> tags.add(c, tag));
								} else {
									cards.stream()
											.peek(ci -> ci.tags().remove(tag))
											.map(CardInstance::card)
											.distinct()
											.forEach(c -> tags.remove(c, tag));
								}
								view.refreshCardGrouping();
							}
						}))
						.collect(Collectors.toList())
				);
				tagCBs.add(new SeparatorMenuItem());

				TextField newTagField = new TextField();
				CustomMenuItem newTagMenuItem = new CustomMenuItem(newTagField);
				newTagMenuItem.setHideOnClick(false);
				newTagField.setPromptText("New tag...");
				newTagField.setOnAction(ae -> {
					if (newTagField.getText().isEmpty()) {
						ae.consume();
						return;
					}

					final String tag = newTagField.getText();
					final Set<CardInstance> cards = new HashSet<>(this.cards);
					final CardView view = this.view.get();

					if (view.deck != null) {
						DeckChanger.change(
								view.deck,
								"Add Tags",
								l -> {
									cards.forEach(ci -> ci.tags().add(tag));
									view.regroup();
								},
								l -> {
									cards.forEach(ci -> ci.tags().remove(tag));
									view.regroup();
								}
						);
					} else {
						final Tags tags = Context.get().tags;
						cards.stream()
								.peek(ci -> ci.tags().add(tag))
								.map(CardInstance::card)
								.distinct()
								.forEach(c -> tags.add(c, tag));
						view.regroup();
					}

					this.hide();
				});

				tagCBs.add(newTagMenuItem);

				tagsMenu.getItems().setAll(tagCBs);
			});

			this.getItems().add(tagsMenu);
			return this;
		}

		public ContextMenu addSeparator() {
			this.getItems().add(new SeparatorMenuItem());
			return this;
		}
	}

	private final DeckList deck;
	final FilteredGroupedModel<Grouping.Group, CardInstance> model;
	final Map<Grouping.Group, Group> groupedModel;

	private LayoutEngine engine;
	private Comparator<CardInstance> sort;
	private List<ActiveSorting> sortingElements;
	private Grouping grouping;

	private final DoubleProperty scrollMinX, scrollMinY, scrollX, scrollY, scrollMaxX, scrollMaxY;

	private final PanBehavior panBehavior;
	private final DragAndDropBehavior dragAndDropBehavior;
	private final SelectBehavior selectBehavior;
	private final ZoomBehavior zoomBehavior;

	private Group hoverGroup;
	private CardInstance hoverCard;
	private volatile int hoverCardGroupIdx;
	public final ObservableSet<CardInstance> selectedCards = FXCollections.observableSet(new HashSet<>());

	private Consumer<CardInstance> doubleClick;
	private ContextMenu contextMenu;

	private final DoubleProperty cardScaleProperty;
	private final BooleanProperty showEmptyGroupsProperty;
	private final BooleanProperty collapseDuplicatesProperty;
	private final BooleanProperty showFlags;

	private static boolean dragModified = false;
	private static CardView dragSource = null;

	private final Tooltip tooltip;

	public CardView(DeckList deck, ObservableList<CardInstance> model, LayoutEngine.Factory layout, Grouping grouping, List<ActiveSorting> sorts) {
		super(1024, 1024);

		setFocusTraversable(true);
		setManaged(true);

		this.deck = deck;

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

		this.showFlags = new SimpleBooleanProperty(true);
		this.showFlags.addListener(ce -> scheduleRender());

		this.hoverCard = null;
		this.hoverGroup = null;
		this.hoverCardGroupIdx = -1;

		this.doubleClick = ci -> {};
		this.contextMenu = null;

		this.sortingElements = sorts;
		this.sort = ActiveSorting.merge(sorts);
		this.grouping = grouping;

		final Grouping.Group unsorted = new Grouping.Group() {
			@Override
			public String toString() {
				return "Unsorted";
			}

			@Override
			public boolean contains(CardInstance ci) {
				return true;
			}

			@Override
			public int compareTo(Grouping.Group o) {
				return 0;
			}
		};

		this.model = new FilteredGroupedModel<>(model, ci -> Collections.singleton(unsorted), ci -> true);

		this.groupedModel = new TreeMap<>();

		final ListChangeListener<? super CardInstance> listListener = change -> layout();

		this.model.addListener((MapChangeListener<? super Grouping.Group, ? super ObservableList<CardInstance>>) mce -> {
			if (mce.wasRemoved()) {
				mce.getValueRemoved().removeListener(listListener);
				this.groupedModel.remove(mce.getKey());
			}

			if (mce.wasAdded()) {
				mce.getValueAdded().addListener(listListener);
				this.groupedModel.put(mce.getKey(), new Group(mce.getKey(), mce.getValueAdded(), sort));
			}

			scheduleLayout();
		});

		this.model.grouping.set(this.grouping::groups);

		this.collapseDuplicatesProperty.addListener((a, b, c) -> layout());

		grouping(grouping);
		layout(layout);

		this.panBehavior = new PanBehavior();
		this.dragAndDropBehavior = new DragAndDropBehavior();
		this.selectBehavior = new SelectBehavior();
		this.zoomBehavior = new ZoomBehavior();

		this.scrollX.addListener(e -> scheduleRender());
		this.scrollY.addListener(e -> scheduleRender());

		this.tooltip = new Tooltip();
		this.tooltip.setMaxWidth(512.0);

		setOnMouseMoved(me -> {
			if (me.isConsumed()) return;

			CardInstance lastHoverCard = hoverCard;

			mouseMoved(me.getX(), me.getY());

			if (hoverCard != null && hoverCard.hasTooltip()) {
				Tooltip.install(this, tooltip);
				if (!tooltip.isShowing() || hoverCard != lastHoverCard) {
					tooltip.setX(me.getScreenX());
					tooltip.setY(me.getScreenY());
					tooltip.setText(hoverCard.tooltip());
				}
			} else {
				tooltip.hide();
				Tooltip.uninstall(this, tooltip);
			}

			me.consume();
		});

		setOnMouseExited(me -> {
			if (me.isConsumed()) return;

			if (hoverGroup != null) {
				hoverGroup = null;
				hoverCard = null;
				hoverCardGroupIdx = -1;

				scheduleRender();
			}

			me.consume();
		});

		setOnMouseClicked(me -> {
			if (me.isConsumed()) return;

			if (me.getButton() == MouseButton.PRIMARY) {
				if (me.isAltDown()) {
					final CardInstance card = hoverCard;
					if (card == null || card.card().prints().size() == 1) {
						return;
					}

					if (deck == null) {
						if (this.model.values().stream().flatMap(List::stream).anyMatch(ci -> ci.card() == card.card() && ci.print() != card.print())) {
							return; // Other versions are already represented. TODO: I hate this. Bind to CardPane's showVersionsSeparately?
						}
					}

					final List<? extends CardInstance> modifyingCards = hoverGroup.hoverCards(card);
					PrintSelectorDialog.show(getScene(), card.card()).ifPresent(pr -> {
						if (deck == null) {
							Preferences.get().preferredPrints.put(card.card().fullName(), new Preferences.PreferredPrint(pr.set().code(), pr.collectorNumber()));
						} else {
							boolean modified = false;
							Consumer<DeckList> doFn = l -> {}, undoFn = l -> {};
							for (CardInstance ci : modifyingCards) {
								if (ci.print() != pr) {
									final Card.Print old = ci.print();
									doFn = doFn.andThen(l -> ci.print(pr)).andThen(l -> this.model.sourceElementChanged(ci));
									undoFn = undoFn.andThen(l -> ci.print(old)).andThen(l -> this.model.sourceElementChanged(ci));
									modified = true;
								}
							}
							if (modified) {
								DeckChanger.change(
										deck,
										String.format("Change %d Print%s", modifyingCards.size(), modifyingCards.size() > 1 ? "s" : ""),
										doFn,
										undoFn
								);
							}
						}

						refreshCardGrouping();
					});
				} else if (me.getClickCount() % 2 == 0) {
					CardInstance ci = cardAt(me.getX(), me.getY()).card;

					if (ci != null) {
						this.doubleClick.accept(ci);
					}
				}
			} else if (me.getButton() == MouseButton.SECONDARY) {
				if (this.contextMenu != null) {
					selectedCards.retainAll(this.model.source);
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

			if (me.isConsumed()) return;

			if (me.getButton() == MouseButton.SECONDARY) {
				if (hoverCard != null && !selectedCards.contains(hoverCard)) {
					if (!me.isControlDown()) {
						selectedCards.clear();
					}
					selectedCards.addAll(hoverGroup.hoverCards(hoverCard));
				}
			}
		});

		setOnMouseDragged(me -> {
			if (me.isConsumed()) return;

			scheduleRender();
		});

		setOnMouseReleased(me -> {
			if (me.isConsumed()) return;

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

		for (Group g : groupedModel.values()) {
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
			hoverCardGroupIdx = -1;
			if (rerender) {
				scheduleRender();
			}

			return;
		}

		rel.plus(hoverGroup.groupBounds.pos.copy().negate());
		int newHoverIdx = this.engine.cardAt(rel, hoverGroup.model().size());
		CardInstance newHoverCard = newHoverIdx >= 0 ? hoverGroup.model().get(newHoverIdx) : null;

		if (newHoverCard != hoverCard) {
			rerender = true;
		}

		hoverCard = newHoverCard;
		hoverCardGroupIdx = newHoverIdx;

		if (rerender) {
			scheduleRender();
		}
	}

	private Set<CardInstance> cardsInBounds(double x1, double y1, double x2, double y2) {
		Set<CardInstance> selectedCards = new HashSet<>();

		MVec2d min = new MVec2d(Math.min(x1, x2), Math.min(y1, y2)),
				max = new MVec2d(Math.max(x1, x2), Math.max(y1, y2));

		MVec2d localMin = new MVec2d(), localMax = new MVec2d(), buffer = new MVec2d();
		for (Group group : groupedModel.values()) {
			localMin.set(group.groupBounds.pos).negate().plus(min);
			localMax.set(group.groupBounds.pos).negate().plus(max);

			for (int j = 0; j < group.model().size(); ++j) {
				if (engine.cardInSelection(j, localMin, localMax, buffer, group.model().size())) {
					selectedCards.addAll(group.hoverCards(group.model().get(j)));
				}
			}
		}

		return selectedCards;
	}

	private static class CardHitResult {
		public final Group group;
		public final int index;
		public final CardInstance card;

		public CardHitResult(Group group, int index, CardInstance card) {
			this.group = group;
			this.index = index;
			this.card = card;
		}
	}

	private CardHitResult cardAt(double x, double y) {
		if (this.engine == null || model.values().stream().mapToLong(List::size).sum() == 0) {
			return new CardHitResult(null, -1, null);
		}

		MVec2d point = new MVec2d(x + scrollX.get(), y + scrollY.get());

		Group group = null;
		for (Group g : groupedModel.values()) {
			if (g.groupBounds.contains(point)) {
				group = g;
				break;
			}
		}

		if (group == null || group.model().isEmpty()) {
			return new CardHitResult(group, -1, null);
		}

		point.plus(group.groupBounds.pos.copy().negate());
		int card = this.engine.cardAt(point, group.model().size());

		if (card < 0) {
			return new CardHitResult(group, -1, null);
		}

		return new CardHitResult(group, card, group.model().get(card));
	}

	public void layout(LayoutEngine.Factory factory) {
		this.engine = factory.create(this);
		layout();
	}

	public void grouping(Grouping grouping) {
		this.grouping = grouping;
		this.model.grouping.set(this.grouping::groups);
		layout();
	}

	public Grouping grouping() {
		return this.grouping;
	}

	public void regroup() {
		grouping(this.grouping);
	}

	public void refreshCardGrouping() {
		this.model.regroup();
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
				this.sort = ActiveSorting.merge(sorts);

				for (Group g : groupedModel.values()) {
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
		return groupedModel.values().stream()
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
		return groupedModel.values().stream()
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
		if (width != getWidth() || height != getHeight()) {
			setWidth(width);
			setHeight(height);
			layout();
		}
	}

	private volatile long renderGeneration = 0, layoutGeneration = 0;

	public synchronized void scheduleRender() {
		final long nextGen = ++renderGeneration;
		ForkJoinPool.commonPool().submit(() -> this.render(nextGen));
	}

	private synchronized void layout(long generation) {
		if (generation < layoutGeneration) {
			return;
		}

		if (engine == null || grouping == null || model == null || groupedModel == null) {
			return;
		}

		Bounds boundingBox = new Bounds();
		engine.layoutGroups(boundingBox, groupedModel.values().toArray(new Group[0]), showEmptyGroupsProperty.get()); // TODO inefficient and unordered

		if (generation < layoutGeneration) {
			return;
		}

		scrollMinX.set(boundingBox.pos.x);
		scrollMinY.set(boundingBox.pos.y);
		scrollMaxX.set(boundingBox.dim.x - getWidth());
		scrollMaxY.set(boundingBox.dim.y - getHeight());

		scrollX.set(Math.max(boundingBox.pos.x, Math.min(scrollX.get(), boundingBox.dim.x - getWidth())));
		scrollY.set(Math.max(boundingBox.pos.y, Math.min(scrollY.get(), boundingBox.dim.y - getHeight())));

		scheduleRender();
	}

	public synchronized void layout() {
		layout(++layoutGeneration);
	}

	public synchronized void scheduleLayout() {
		final long nextGen = ++layoutGeneration;
		Platform.runLater(() -> this.layout(nextGen));
	}

	private enum CardState {
		Full (() -> Color.TRANSPARENT, () -> Color.color(0.0f, 0.0f, 0.0f, 0.5f)),
		Hover (() -> Preferences.get().theme.accent.deriveColor(0.0, 1.0, 1.25, 1.0), () -> Color.TRANSPARENT),
		Selected (() -> Preferences.get().theme.accent.deriveColor(0.0, 1.0, 1.25, 1.0), () -> Preferences.get().theme.accent.deriveColor(0.0, 1.0, 1.25, 0.25)),
		Flagged (() -> Color.RED, () -> Color.TRANSPARENT),
		Warning (() -> Color.ORANGE, () -> Color.TRANSPARENT),
		Notice (() -> Color.LIGHTGREEN, () -> Color.TRANSPARENT);

		public final Supplier<Color> outlineColor, fillColor;

		CardState(Supplier<Color> outlineColor, Supplier<Color> fillColor) {
			this.outlineColor = outlineColor;
			this.fillColor = fillColor;
		}
	}

	private static class RenderMap {
		private static class CardState {
			public final Image img;
			public final EnumSet<CardView.CardState> state;
			public final int count;

			public CardState(Image img, EnumSet<CardView.CardState> states, int count) {
				this.img = img;
				this.state = states;
				this.count = count;
			}
		}

		public final SortedMap<MVec2d, CardState> cards;
		public final Map<Bounds, String> labels;
		public final Bounds hoverGroupBounds;

		public RenderMap() {
			this.cards = new TreeMap<>();
			this.labels = new HashMap<>();
			this.hoverGroupBounds = new Bounds();
		}
	}

	@SuppressWarnings("DuplicateCondition")
	protected synchronized void render(long generation) {
		if (generation < renderGeneration) return;

		if (engine == null || grouping == null) {
			Platform.runLater(() -> {
				GraphicsContext gfx = getGraphicsContext2D();
				gfx.setFill(Preferences.get().theme.base);
				gfx.fillRect(0, 0, getWidth(), getHeight());
				gfx.setTextAlign(TextAlignment.CENTER);
				gfx.setFill(Preferences.get().theme.base.invert());
				gfx.setFont(new Font(null, getHeight() / 10.0));
				gfx.fillText("Select a valid display layout/card grouping.", getWidth() / 2, getHeight() / 2, getWidth());
			});
			return;
		}

		if (model == null || model.values().stream().mapToLong(List::size).sum() == 0) {
			Platform.runLater(() -> {
				GraphicsContext gfx = getGraphicsContext2D();
				gfx.setFill(Preferences.get().theme.base);
				gfx.fillRect(0, 0, getWidth(), getHeight());
				gfx.setTextAlign(TextAlignment.CENTER);
				gfx.setFill(Preferences.get().theme.base.invert());
				gfx.setFont(new Font(null, getHeight() / 10.0));
				gfx.fillText("No cards to display.", getWidth() / 2, getHeight() / 2, getWidth());
			});
			return;
		}

		if (groupedModel == null) {
			return; // TODO: What do we do here? Call layout manually...?
		}

		if (generation < renderGeneration) return;
		RenderMap renderMap = buildRenderMap(false);

		if (generation < renderGeneration) return;
		Platform.runLater(() -> drawRenderMap(generation, renderMap));
	}

	private void drawRenderMap(long generation, RenderMap renderMap) {
		if (generation >= 0 && generation < renderGeneration) return;

		GraphicsContext gfx = getGraphicsContext2D();

		gfx.setFill(Preferences.get().theme.base);
		gfx.fillRect(0, 0, getWidth(), getHeight());

		if (renderMap.hoverGroupBounds.dim.x >= 0 && renderMap.hoverGroupBounds.dim.y >= 0) {
			gfx.setFill(Preferences.get().theme.base.deriveColor(0.0, 1.0, 0.9, 1.0));
			gfx.fillRect(renderMap.hoverGroupBounds.pos.x, renderMap.hoverGroupBounds.pos.y,
					renderMap.hoverGroupBounds.dim.x, renderMap.hoverGroupBounds.dim.y);
		}

		gfx.setFill(Preferences.get().theme.base.invert());
		gfx.setTextAlign(TextAlignment.CENTER);
		gfx.setTextBaseline(VPos.CENTER);
		for (Map.Entry<Bounds, String> label : renderMap.labels.entrySet()) {
			gfx.setFont(Font.font(null, FontWeight.MEDIUM, label.getKey().dim.y));
			gfx.fillText(label.getValue(),
					label.getKey().pos.x + label.getKey().dim.x / 2.0,
					label.getKey().pos.y + label.getKey().dim.y / 2.0,
					label.getKey().dim.x);
		}

		final double cw = cardWidth();
		final double ch = cardHeight();

		for (Map.Entry<MVec2d, RenderMap.CardState> str : renderMap.cards.entrySet()) {
			gfx.drawImage(str.getValue().img, str.getKey().x, str.getKey().y, cw, ch);

			boolean drewFill = false, drewOutline = false;

			for (CardState state : CardView.CardState.values()) {
				if (str.getValue().state.contains(state)) {
					Color fill = state.fillColor.get(), outline = state.outlineColor.get();

					if (!drewFill && fill != Color.TRANSPARENT) {
						drewFill = true;

						gfx.setFill(fill);
						gfx.fillRoundRect(str.getKey().x, str.getKey().y, cw, ch, cw / 8.0, cw / 8.0);
					}

					if (!drewOutline && outline != Color.TRANSPARENT) {
						drewOutline = true;

						gfx.setStroke(outline);
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

		if (selectBehavior.selecting) {
			double x = selectBehavior.selectX - scrollX.get();
			double y = selectBehavior.selectY - scrollY.get();
			double w = selectBehavior.selectW;
			double h = selectBehavior.selectH;

			gfx.setFill(Preferences.get().theme.accent.deriveColor(0, 1.0, 1.25, 0.25));
			gfx.fillRect(x, y, w, h);

			gfx.setStroke(Preferences.get().theme.accent);
			gfx.setFill(Color.TRANSPARENT);
			gfx.setLineWidth(2.0);
			gfx.strokeRect(x, y, w, h);
		}

		// This should never happen.
		if (generation >= Long.MAX_VALUE / 2 && generation == renderGeneration) {
			renderGeneration = 0;
		}
	}

	private final Set<CompletableFuture<Image>> waiting = new HashSet<>();

	private RenderMap buildRenderMap(boolean blocking) {
		RenderMap renderMap = new RenderMap();
		MVec2d loc = new MVec2d(), abs = new MVec2d();
		MVec2d scroll = new MVec2d(-scrollX.get(), -scrollY.get());

		MVec2d dragStart = new MVec2d(selectBehavior.selectX, selectBehavior.selectY),
				dragEnd = new MVec2d(selectBehavior.selectX2, selectBehavior.selectY2),
				dragStartLocal = new MVec2d(), dragEndLocal = new MVec2d(), buffer = new MVec2d();

		if (hoverGroup != null) {
			renderMap.hoverGroupBounds.pos.set(hoverGroup.groupBounds.pos).plus(scroll);
			renderMap.hoverGroupBounds.dim.set(hoverGroup.groupBounds.dim);
			renderMap.hoverGroupBounds.plus(hoverGroup.labelBounds);
		}

		for (Group group : groupedModel.values()) {
			if (!showEmptyGroupsProperty.get() && group.model().isEmpty()) {
				continue;
			}

			final Bounds bounds = group.groupBounds;

			dragStartLocal.set(bounds.pos).negate().plus(dragStart);
			dragEndLocal.set(bounds.pos).negate().plus(dragEnd);

			if (bounds.pos.x == 0 && bounds.pos.y == 0 && (bounds.dim.x == 0 || bounds.dim.y == 0)) {
				continue;
			}

			final Bounds labelBounds = new Bounds();
			labelBounds.pos.set(group.labelBounds.pos).plus(scroll);
			labelBounds.dim.set(group.labelBounds.dim);

			if (labelBounds.pos.x > -labelBounds.dim.x && labelBounds.pos.x < getWidth() && labelBounds.pos.y > -labelBounds.dim.y && labelBounds.pos.y < getHeight()) {
				renderMap.labels.put(labelBounds, String.format("%s (%d)", group.group.toString(), group.sortedModel.size()));
			}

			final MVec2d gpos = new MVec2d(bounds.pos).plus(scroll);

			if (gpos.x < -bounds.dim.x || gpos.x > getWidth() || gpos.y < -bounds.dim.y || gpos.y > getHeight()) {
				continue;
			}

			for (int j = 0; j < group.model().size(); ++j) {
				abs = engine.coordinatesOf(j, abs);
				loc = loc.set(abs).plus(group.groupBounds.pos).plus(scroll);

				if (loc.x < -cardWidth() || loc.x > getWidth() || loc.y < -cardHeight() || loc.y > getHeight()) {
					continue;
				}

				final CardInstance ci = group.model().get(j);
				final Card.Print print = ci.print();

				CompletableFuture<Image> futureImage = Context.get().images.getThumbnail(print);

				if (blocking) {
					try {
						futureImage.get();
					} catch (InterruptedException | ExecutionException e) {
						throw new RuntimeException(e);
					}
				} else if (!futureImage.isDone() && !waiting.contains(futureImage)) {
					waiting.add(futureImage);
					futureImage.thenRun(() -> {
						this.scheduleRender();
						waiting.remove(futureImage);
					});
				}

				EnumSet<CardState> states = EnumSet.noneOf(CardState.class);

				if (showFlags.get()) {
					if (ci.flags.contains(CardInstance.Flags.Invalid)) {
						states.add(CardView.CardState.Flagged);
					}

					if (ci.flags.contains(CardInstance.Flags.Full)) {
						states.add(CardView.CardState.Full);
					}

					if (ci.flags.contains(CardInstance.Flags.Warning)) {
						states.add(CardView.CardState.Warning);
					}

					if (ci.flags.contains(CardInstance.Flags.Notice)) {
						states.add(CardView.CardState.Notice);
					}
				}

				if (hoverCard == ci) {
					states.add(CardView.CardState.Hover);
				}

				if (selectedCards.contains(ci)) {
					states.add(CardView.CardState.Selected);
				} else if (selectBehavior.selecting) {
					if (engine.cardInSelection(abs, dragStartLocal, dragEndLocal, group.model().size())) {
						states.add(CardView.CardState.Selected);
					}
				}

				int count = 1;
				if (collapseDuplicatesProperty.get()) {
					count = group.collapsedModel.count(j);
				}

				renderMap.cards.put(new MVec2d(loc), new RenderMap.CardState(futureImage.getNow(Images.LOADING_CARD), states, count));
			}
		}

		return renderMap;
	}

	public synchronized void renderNow() throws IllegalStateException {
		if (!Platform.isFxApplicationThread()) {
			throw new IllegalStateException("renderNow must be called from the FX Application thread!");
		}

		drawRenderMap(-1, buildRenderMap(true));
	}
}
