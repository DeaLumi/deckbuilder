package emi.mtg.deckbuilder.view.components;

import emi.lib.mtg.Card;
import emi.mtg.deckbuilder.controller.Context;
import emi.mtg.deckbuilder.controller.DeckChanger;
import emi.mtg.deckbuilder.controller.Tags;
import emi.mtg.deckbuilder.model.CardInstance;
import emi.mtg.deckbuilder.model.DeckList;
import emi.mtg.deckbuilder.model.Preferences;
import emi.mtg.deckbuilder.util.UniqueList;
import emi.mtg.deckbuilder.view.Images;
import emi.mtg.deckbuilder.view.dialogs.PrintingSelectorDialog;
import emi.mtg.deckbuilder.util.PluginUtils;
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
import java.util.function.Predicate;
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

		boolean requireRegroup(Group[] existing, ListChangeListener.Change<? extends CardInstance> change);
		Group[] groups(DeckList list, List<CardInstance> model);
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

	public static final List<LayoutEngine.Factory> LAYOUT_ENGINES = PluginUtils.providers(LayoutEngine.Factory.class);
	public static final List<Grouping> GROUPINGS = PluginUtils.providers(Grouping.class);
	public static final List<Sorting> SORTINGS = PluginUtils.providers(Sorting.class);

	public static final List<ActiveSorting> DEFAULT_SORTING, DEFAULT_COLLECTION_SORTING;

	static {
		Sorting color = null, mv = null, manaCost = null, name = null, rarity = null;

		for (Sorting sorting : SORTINGS) {
			switch (sorting.toString()) {
				case "Color":
					color = sorting;
					break;
				case "Mana Value":
					mv = sorting;
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
		assert mv != null;
		assert manaCost != null;
		assert name != null;
		assert rarity != null;

		DEFAULT_SORTING = Arrays.asList(
				new ActiveSorting(color, false),
				new ActiveSorting(mv, false),
				new ActiveSorting(manaCost, false),
				new ActiveSorting(name, false)
		);

		DEFAULT_COLLECTION_SORTING = Arrays.asList(
				new ActiveSorting(rarity, true),
				new ActiveSorting(color, false),
				new ActiveSorting(mv, false),
				new ActiveSorting(manaCost, false),
				new ActiveSorting(name, false)
		);
	}

	public class Group {
		public final Bounds groupBounds, labelBounds;
		public final Grouping.Group group;
		private final FilteredList<CardInstance> filteredModel;
		private final SortedList<CardInstance> sortedModel;
		private final UniqueList<CardInstance> collapsedModel;

		public Group(Grouping.Group group, ObservableList<CardInstance> modelSource, Comparator<CardInstance> initialSort) {
			this.group = group;
			this.groupBounds = new Bounds();
			this.labelBounds = new Bounds();

			this.filteredModel = modelSource.filtered(group::contains);
			this.sortedModel = this.filteredModel.sorted(initialSort);
			this.collapsedModel = new UniqueList<>(this.sortedModel, CardInstance::printing);

			this.collapsedModel.addListener((ListChangeListener<CardInstance>) x -> CardView.this.layout());
		}

		public void refilter() {
			// N.B. This can not be a lambda. We need a new instance.
			this.filteredModel.setPredicate(new Predicate<CardInstance>() {
				@Override
				public boolean test(CardInstance cardInstance) {
					return Group.this.group.contains(cardInstance);
				}
			});
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

		public ContextMenu addCleanupImages() {
			MenuItem deleteImages = new MenuItem("Delete Saved Images");
			deleteImages.setOnAction(ae -> {
				try {
					for (CardInstance ci : cards) {
						for (Card.Printing pr : ci.card().printings()) {
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
				tagCBs.setAll(view.get().model.stream()
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

									if (tags.cards(tag) != null && tags.cards(tag).isEmpty()) {
										tags.tags().remove(tag);
									}
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
						tags.add(tag);
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
	final ObservableList<CardInstance> model;
	final FilteredList<CardInstance> filteredModel;
	private volatile Grouping.Group[] groups;
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
	private final BooleanProperty showFlags;

	private static boolean dragModified = false;
	private static CardView dragSource = null;

	private final Tooltip tooltip = new Tooltip();

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

		this.doubleClick = ci -> {};
		this.contextMenu = null;

		this.sortingElements = sorts;
		this.sort = ActiveSorting.merge(sorts);
		this.grouping = grouping;

		this.model = model;
		this.filteredModel = model.filtered(x -> true);
		this.filteredModel.addListener((ListChangeListener<CardInstance>) lce -> {
			if (this.grouping.requireRegroup(this.groups, lce)) {
				this.regroup();
			}
		});

		this.collapseDuplicatesProperty.addListener((a, b, c) -> layout());

		grouping(grouping);
		layout(layout);

		this.scrollX.addListener(e -> scheduleRender());
		this.scrollY.addListener(e -> scheduleRender());

		setOnMouseMoved(me -> {
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
				Context.get().images.getThumbnail(view.printing()).thenApply(img -> {
					db.setDragView(img, -8.0, -8.0);
					return img;
				}).getNow(Images.LOADING_CARD);
			}

			db.setContent(Collections.singletonMap(DataFormat.PLAIN_TEXT, selectedCards.toString()));
			CardView.dragModified = de.isControlDown();
			CardView.dragSource = this;
			de.consume();
		});

		setOnDragOver(de -> {
			if (CardView.dragSource == this) {
				mouseMoved(de.getX(), de.getY());

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
				mouseMoved(de.getX(), de.getY());

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
					DeckChanger.startChangeBatch(this.deck);

					this.selectedCards.forEach(this.hoverGroup.group::add);

					if (de.getAcceptedTransferMode() == TransferMode.MOVE) {
						for (Group group : this.groupedModel) {
							if (group == hoverGroup) {
								continue;
							}

							for (CardInstance ci : selectedCards) {
								group.group.remove(ci);
							}
						}
					}

					// I know this looks weird, but the `group.add` and `group.remove` above add the actual batched changes.
					DeckChanger.addBatchedChange(
							this.deck,
							l -> { for (Group group : this.groupedModel) group.refilter(); },
							l -> { for (Group group : this.groupedModel) group.refilter(); }
					);

					DeckChanger.endChangeBatch(
							this.deck,
							"Change Tags"
					);

					de.setDropCompleted(true);
					de.consume();
				}
			} else if (CardView.dragSource != null) {
				if (deck != null) {
					Set<CardInstance> oldCards = new HashSet<>(CardView.dragSource.selectedCards);

					if (de.getAcceptedTransferMode() == TransferMode.MOVE && CardView.dragSource.deck != null) {
						final CardView source = CardView.dragSource;
						final DeckList sourceDeck = source.deck;
						final ObservableList<CardInstance> sourceModel = source.model;
						DeckChanger.startChangeBatch(sourceDeck);

						if (source.grouping.supportsModification() && this.hoverGroup != null && this.grouping.supportsModification()) {
							for (Grouping.Group oldGroup : source.groups) {
								for (CardInstance oldCard : source.selectedCards) {
									oldGroup.remove(oldCard);
								}
							}
						}

						DeckChanger.addBatchedChange(
								sourceDeck,
								l -> sourceModel.removeAll(oldCards),
								l -> sourceModel.addAll(oldCards)
						);

						if (sourceDeck != this.deck) DeckChanger.endChangeBatch(sourceDeck, String.format("Remove %d Card%s", oldCards.size(), oldCards.size() > 1 ? "s" : ""));
						source.selectedCards.clear();
					}

					if (dragSource.deck != deck) DeckChanger.startChangeBatch(deck);
					Set<CardInstance> newCards = oldCards.stream()
							.map(ci -> {
								CardInstance clone = new CardInstance(ci);

								if (this.hoverGroup != null && this.grouping.supportsModification()) {
									this.hoverGroup.group.add(clone);
								}

								return clone;
							})
							.collect(Collectors.toSet());

					final ObservableList<CardInstance> thisModel = this.model;
					DeckChanger.addBatchedChange(
							deck,
							l -> thisModel.addAll(newCards),
							l -> thisModel.removeAll(newCards)
					);
					DeckChanger.endChangeBatch(deck, String.format("%s %d Card%s", dragSource.deck != deck ? "Add" : "Move", newCards.size(), newCards.size() > 1 ? "s" : ""));
					this.selectedCards.clear();
					this.selectedCards.addAll(newCards);
				}

				de.setDropCompleted(true);
				de.consume();
			} else {
				// TODO: Accept transfer from other programs/areas?
			}

			layout();
		});

		setOnDragDone(de -> {
			if (CardView.dragSource != null) {
				CardView.dragSource = null;
				de.consume();
			}
		});

		setOnMouseClicked(me -> {
			if (me.getButton() == MouseButton.PRIMARY) {
				if (me.isAltDown()) {
					final CardInstance card = hoverCard;
					if (card == null || card.card().printings().size() == 1) {
						return;
					}

					if (deck == null) {
						if (filteredModel.stream().anyMatch(ci -> ci.card() == card.card() && ci.printing() != card.printing())) {
							return; // Other versions are already represented. TODO: I hate this. Bind to CardPane's showVersionsSeparately?
						}
					}

					final List<? extends CardInstance> modifyingCards = hoverGroup.hoverCards(card);
					PrintingSelectorDialog.show(getScene(), card.card()).ifPresent(pr -> {
						if (deck == null) {
							Preferences.get().preferredPrintings.put(card.card().fullName(), new Preferences.PreferredPrinting(pr.set().code(), pr.collectorNumber()));
						} else {
							boolean modified = false;
							Consumer<DeckList> doFn = l -> {}, undoFn = l -> {};
							for (CardInstance ci : modifyingCards) {
								if (ci.printing() != pr) {
									final Card.Printing old = ci.printing();
									doFn = doFn.andThen(l -> ci.printing(pr));
									undoFn = undoFn.andThen(l -> ci.printing(old));
									modified = true;
								}
							}
							if (modified) {
								// Irritating hack to trigger listeners.
								doFn = doFn.andThen(l -> model.set(0, model.get(0))).andThen(l -> scheduleRender());
								undoFn = undoFn.andThen(l -> model.set(0, model.get(0))).andThen(l -> scheduleRender());

								DeckChanger.change(
										deck,
										String.format("Change %d Printing%s", modifyingCards.size(), modifyingCards.size() > 1 ? "s" : ""),
										doFn,
										undoFn
								);
							}
						}

						refreshCardGrouping();
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

				if (hoverCard != null && !selectedCards.contains(hoverCard)) {
					if (!me.isControlDown()) {
						selectedCards.clear();
					}
					selectedCards.addAll(hoverGroup.hoverCards(hoverCard));
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
		int newHoverIdx = this.engine.cardAt(rel, hoverGroup.model().size());
		CardInstance newHoverCard = newHoverIdx >= 0 ? hoverGroup.model().get(newHoverIdx) : null;

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

			for (int j = 0; j < groupedModel[i].model().size(); ++j) {
				loc = engine.coordinatesOf(j, loc);
				loc = loc.plus(groupedModel[i].groupBounds.pos).plus(-scrollX.get(), -scrollY.get());

				if (loc.x < -cardWidth() || loc.x > getWidth() || loc.y < -cardHeight() || loc.y > getHeight()) {
					continue;
				}

				if (cardInBounds(loc, x1s, y1s, x2s, y2s)) {
					selectedCards.addAll(groupedModel[i].hoverCards(groupedModel[i].model().get(j)));
				}
			}
		}

		return selectedCards;
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

		if (group == null || group.model().isEmpty()) {
			return null;
		}

		point.plus(group.groupBounds.pos.copy().negate());
		int card = this.engine.cardAt(point, group.model().size());

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

		if (group == null || group.model().isEmpty()) {
			return null;
		}

		point.plus(group.groupBounds.pos.copy().negate());
		int card = this.engine.cardAt(point, group.model().size());

		if (card < 0) {
			return null;
		}

		return group.model().get(card);
	}

	public void layout(LayoutEngine.Factory factory) {
		this.engine = factory.create(this);
		layout();
	}

	public void grouping(Grouping grouping) {
		this.grouping = grouping;
		this.groups = this.grouping.groups(this.deck, this.model);
		this.groupedModel = Arrays.stream(this.groups)
				.map(g -> new Group(g, this.filteredModel, this.sort))
				.toArray(Group[]::new);
		layout();
	}

	public Grouping grouping() {
		return this.grouping;
	}

	public void regroup() {
		grouping(this.grouping);
	}

	public void refreshCardGrouping() {
		for (Group group : this.groupedModel) {
			group.refilter();
		}
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
		if (width != getWidth() || height != getHeight()) {
			setWidth(width);
			setHeight(height);
			layout();
		}
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
		Hover (Color.DODGERBLUE, Color.TRANSPARENT),
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

		if (filteredModel == null || filteredModel.isEmpty()) {
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

	private final Set<CompletableFuture<Image>> waiting = new HashSet<>();

	private RenderMap buildRenderMap(boolean blocking) {
		RenderMap renderMap = new RenderMap();
		MVec2d loc = new MVec2d();
		MVec2d scroll = new MVec2d(-scrollX.get(), -scrollY.get());

		double dragBoxX1 = Math.min(dragStartX, lastDragX);
		double dragBoxY1 = Math.min(dragStartY, lastDragY);
		double dragBoxX2 = Math.max(dragStartX, lastDragX);
		double dragBoxY2 = Math.max(dragStartY, lastDragY);

		if (hoverGroup != null) {
			renderMap.hoverGroupBounds.pos.set(hoverGroup.groupBounds.pos).plus(scroll);
			renderMap.hoverGroupBounds.dim.set(hoverGroup.groupBounds.dim);
			renderMap.hoverGroupBounds.plus(hoverGroup.labelBounds);
		}

		for (int i = 0; i < groupedModel.length; ++i) {
			if (groupedModel[i] == null || (!showEmptyGroupsProperty.get() && groupedModel[i].model().isEmpty())) {
				continue;
			}

			final Bounds bounds = groupedModel[i].groupBounds;

			if (bounds.pos.x == 0 && bounds.pos.y == 0 && (bounds.dim.x == 0 || bounds.dim.y == 0)) {
				continue;
			}

			final Bounds labelBounds = new Bounds();
			labelBounds.pos.set(groupedModel[i].labelBounds.pos).plus(scroll);
			labelBounds.dim.set(groupedModel[i].labelBounds.dim);

			if (labelBounds.pos.x > -labelBounds.dim.x && labelBounds.pos.x < getWidth() && labelBounds.pos.y > -labelBounds.dim.y && labelBounds.pos.y < getHeight()) {
				renderMap.labels.put(labelBounds, String.format("%s (%d)", groupedModel[i].group.toString(), groupedModel[i].sortedModel.size()));
			}

			final MVec2d gpos = new MVec2d(bounds.pos).plus(scroll);

			if (gpos.x < -bounds.dim.x || gpos.x > getWidth() || gpos.y < -bounds.dim.y || gpos.y > getHeight()) {
				continue;
			}

			for (int j = 0; j < groupedModel[i].model().size(); ++j) {
				loc = engine.coordinatesOf(j, loc);
				loc = loc.plus(groupedModel[i].groupBounds.pos).plus(scroll);

				if (loc.x < -cardWidth() || loc.x > getWidth() || loc.y < -cardHeight() || loc.y > getHeight()) {
					continue;
				}

				final CardInstance ci = groupedModel[i].model().get(j);
				final Card.Printing printing = ci.printing();

				CompletableFuture<Image> futureImage = Context.get().images.getThumbnail(printing);

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
				} else if (dragMode == DragMode.Selecting) {
					if (cardInBounds(loc, dragBoxX1, dragBoxY1, dragBoxX2, dragBoxY2)) {
						states.add(CardView.CardState.Selected);
					}
				}

				int count = 1;
				if (collapseDuplicatesProperty.get()) {
					count = groupedModel[i].collapsedModel.count(j);
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
