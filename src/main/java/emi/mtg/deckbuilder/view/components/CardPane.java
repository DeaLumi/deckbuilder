package emi.mtg.deckbuilder.view.components;

import emi.lib.mtg.Card;
import emi.lib.mtg.enums.CardType;
import emi.lib.mtg.game.Zone;
import emi.mtg.deckbuilder.model.CardInstance;
import emi.mtg.deckbuilder.model.DeckList;
import emi.mtg.deckbuilder.model.Preferences;
import emi.mtg.deckbuilder.view.dialogs.DeckStatsDialog;
import emi.mtg.deckbuilder.view.dialogs.SortDialog;
import emi.mtg.deckbuilder.view.groupings.ManaValue;
import emi.mtg.deckbuilder.view.layouts.Piles;
import emi.mtg.deckbuilder.view.search.SearchProvider;
import javafx.application.Platform;
import javafx.beans.binding.DoubleBinding;
import javafx.beans.property.*;
import javafx.collections.ListChangeListener;
import javafx.collections.ObservableList;
import javafx.event.ActionEvent;
import javafx.geometry.Insets;
import javafx.geometry.Orientation;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.StackPane;
import javafx.scene.paint.Color;
import javafx.scene.shape.Rectangle;
import javafx.scene.shape.SVGPath;

import java.io.IOException;
import java.util.*;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;
import java.util.function.Predicate;
import java.util.function.ToIntFunction;
import java.util.stream.Collectors;

public class CardPane extends BorderPane {
	private static class CardViewScrollPane extends StackPane {
		private static class VisibleAmountBinding extends DoubleBinding {
			private final DoubleProperty height, min, max;

			public VisibleAmountBinding(DoubleProperty height, DoubleProperty min, DoubleProperty max) {
				this.bind(height, min, max);
				this.height = height;
				this.min = min;
				this.max = max;
			}

			@Override
			protected double computeValue() {
				double h = this.height.get();
				double min = this.min.get();
				double max = this.max.get();

				return h/(max + h - min)*(max - min);
			}
		}

		private final CardView view;
		private final ScrollBar hbar, vbar;
		private final Rectangle fill, loadingDim;
		private final SVGPath loadingIndicator;

		private final BooleanProperty loading;

		public CardViewScrollPane(CardView view) {
			this.view = view;

			this.loading = new SimpleBooleanProperty(false);

			vbar = new ScrollBar();
			vbar.setOrientation(Orientation.VERTICAL);
			vbar.minProperty().bind(view.scrollMinY());
			vbar.valueProperty().bindBidirectional(view.scrollY());
			vbar.maxProperty().bind(view.scrollMaxY());
			vbar.visibleAmountProperty().bind(new VisibleAmountBinding(view.heightProperty(), view.scrollMinY(), view.scrollMaxY()));
			vbar.visibleProperty().bind(vbar.maxProperty().greaterThan(vbar.minProperty()));

			hbar = new ScrollBar();
			hbar.setOrientation(Orientation.HORIZONTAL);
			hbar.minProperty().bind(view.scrollMinX());
			hbar.valueProperty().bindBidirectional(view.scrollX());
			hbar.maxProperty().bind(view.scrollMaxX());
			hbar.visibleAmountProperty().bind(new VisibleAmountBinding(view.widthProperty(), view.scrollMinX(), view.scrollMaxX()));
			hbar.visibleProperty().bind(hbar.maxProperty().greaterThan(hbar.minProperty()));

			fill = new Rectangle(12.0, 12.0);
			fill.setFill(Preferences.get().theme.base);
			fill.visibleProperty().bind(vbar.visibleProperty().and(hbar.visibleProperty()));

			loadingDim = new Rectangle(0, 0, Color.gray(0.25));
			loadingDim.setOpacity(0.5);
			loadingDim.visibleProperty().bind(loading);
			loadingDim.setManaged(false);

			// SVG for TSR from Keyrune: https://keyrune.andrewgioia.com
			loadingIndicator = new SVGPath();
			loadingIndicator.setContent("M16.262 16.419c-0.282 0.576-0.511 1.177-0.747 1.773-0.147 0.36-0.22 0.759-0.149 1.146 0.083 0.484 0.358 0.911 0.692 1.262 0.168 0.185 0.361 0.346 0.558 0.5 0.609 0.56 1.091 1.266 1.352 2.054 0.241 0.714 0.301 1.482 0.233 2.23-0.047 0.527-0.159 1.047-0.3 1.556 0.363 0 0.726-0.001 1.089 0 0.141 0.003 0.297 0.012 0.407 0.111 0.089 0.073 0.129 0.187 0.149 0.298 0.103 0.6 0.201 1.201 0.305 1.801 0.023 0.144 0.012 0.303-0.077 0.424-0.089 0.113-0.236 0.155-0.373 0.173-0.801 0.045-1.604 0.080-2.407 0.051-0.714-0.011-1.427-0.072-2.138-0.136-0.658-0.081-1.318-0.162-1.968-0.294-0.735-0.134-1.467-0.292-2.188-0.491-0.648-0.184-1.296-0.373-1.929-0.606-1.822-0.649-3.571-1.507-5.191-2.563-0.85-0.548-1.656-1.162-2.432-1.809-0.292-0.243-0.577-0.495-0.854-0.754-0.128-0.116-0.272-0.245-0.291-0.428-0.017-0.133 0.069-0.248 0.156-0.337 0.409-0.413 0.832-0.813 1.252-1.216 0.161-0.147 0.31-0.329 0.53-0.385 0.181-0.048 0.369 0.038 0.492 0.17 0.237 0.24 0.471 0.483 0.728 0.702 0.152-0.524 0.383-1.028 0.703-1.472 0.611-0.874 1.534-1.491 2.526-1.855 0.594-0.235 1.221-0.369 1.848-0.478-1.843-0.479-3.686-0.953-5.53-1.428 4.518 0 9.036 0.001 13.554 0zM13.394 18.75c-0.299 0.596-0.557 1.215-0.73 1.86-0.288 1.020-0.362 2.103-0.186 3.149 0.073 0.431 0.175 0.862 0.358 1.261 0.184 0.421 0.46 0.802 0.8 1.112 0.554 0.491 1.28 0.783 2.019 0.816 0.271-0.518 0.455-1.091 0.465-1.68 0.001-0.436-0.074-0.874-0.233-1.281-0.189-0.501-0.478-0.96-0.816-1.373-0.177-0.228-0.389-0.426-0.564-0.655-0.322-0.397-0.595-0.836-0.797-1.306-0.255-0.597-0.369-1.255-0.317-1.903zM11.747 18.047c-0.194 0.283-0.413 0.551-0.674 0.774-0.319 0.289-0.693 0.512-1.079 0.698-0.583 0.272-1.218 0.402-1.847 0.515-0.473 0.109-0.943 0.25-1.373 0.479-0.457 0.238-0.874 0.564-1.182 0.979-0.364 0.47-0.571 1.043-0.677 1.623 0.57 0.393 1.264 0.622 1.958 0.597 0.593-0.010 1.173-0.204 1.676-0.512 0.817-0.522 1.496-1.246 2.014-2.062 0.598-0.938 0.968-2.005 1.184-3.092zM11.59 5.402l0.089-0.169c2.416 0.248 4.805 0.805 7.008 1.85 1.001 0.465 1.964 1.013 2.87 1.644-0.102 0.545-0.272 1.092-0.606 1.542-0.263 0.362-0.65 0.638-1.087 0.746-0.35 0.095-0.719 0.108-1.078 0.062-1.214-0.273-2.422-0.577-3.608-0.953-0.783-0.258-1.561-0.545-2.291-0.931-0.248-0.145-0.494-0.296-0.719-0.475-0.374-0.312-0.678-0.717-0.836-1.181-0.181-0.518-0.149-1.094 0.034-1.607 0.108-0.299 0.039-0.122 0.223-0.527zM9.141 2.192v0c-1.226 0.031-0.701 0.006-1.576 0.056-0.168 0.007-0.355 0.047-0.465 0.186-0.092 0.124-0.093 0.289-0.070 0.436 0.101 0.599 0.202 1.199 0.304 1.798 0.019 0.119 0.074 0.237 0.175 0.306 0.113 0.080 0.256 0.083 0.389 0.087h1.078c-0.235 0.827-0.379 1.691-0.313 2.553 0.045 0.646 0.215 1.284 0.507 1.863 0.275 0.536 0.65 1.019 1.093 1.426 0.195 0.155 0.386 0.315 0.554 0.5 0.351 0.367 0.635 0.824 0.7 1.336 0.050 0.335-0.007 0.678-0.128 0.992-0.244 0.625-0.493 1.248-0.774 1.858 4.517-0.001 9.033-0.001 13.55-0-1.84-0.482-3.681-0.958-5.525-1.428 0.708-0.124 1.418-0.285 2.078-0.576 0.471-0.194 0.917-0.451 1.325-0.756 0.39-0.301 0.732-0.665 1.013-1.070 0.295-0.43 0.513-0.911 0.66-1.41 0.191 0.166 0.373 0.342 0.554 0.519 0.133 0.135 0.254 0.306 0.45 0.352 0.2 0.060 0.398-0.060 0.54-0.19 0.497-0.474 0.992-0.95 1.478-1.434 0.080-0.084 0.151-0.196 0.134-0.317-0.017-0.142-0.112-0.257-0.213-0.351-1.291-1.207-2.714-2.273-4.232-3.177-1.018-0.592-2.067-1.133-3.16-1.573-1.091-0.46-2.219-0.827-3.363-1.129-1.868-0.479-3.785-0.767-5.712-0.838-0.35-0.013-0.701-0.012-1.051-0.017z");
			loadingIndicator.setFill(Color.gray(0.75));
			loadingIndicator.setOpacity(0.75);
			loadingIndicator.visibleProperty().bind(loading);
			loadingIndicator.setManaged(false);

			Platform.runLater(this::layoutChildren);

			getChildren().addAll(view, vbar, hbar, fill, loadingDim, loadingIndicator);
		}

		@Override
		protected void layoutChildren() {
			view.resizeRelocate(0, 0, getWidth(), getHeight());

			if (loading.get()) {
				loadingDim.setWidth(getWidth());
				loadingDim.setHeight(getHeight());
				loadingDim.resizeRelocate(0, 0, getWidth(), getHeight());
				double indicatorScale = 0.75 * getHeight() / loadingIndicator.prefHeight(-1);
				loadingIndicator.setScaleX(indicatorScale);
				loadingIndicator.setScaleY(indicatorScale);
				loadingIndicator.relocate(getWidth() / 2 - loadingIndicator.prefWidth(-1) / 2, getHeight() / 2 - loadingIndicator.prefHeight(-1) / 2);
			}

			if (vbar.isVisible() && hbar.isVisible()) {
				double vsbWidth = vbar.prefWidth(-1);
				double hsbHeight = hbar.prefHeight(-1);
				double innerX = getWidth() - vsbWidth;
				double innerY = getHeight() - hsbHeight;
				vbar.resizeRelocate(innerX, 0, vsbWidth, innerY);
				hbar.resizeRelocate(0, innerY, innerX, hsbHeight);

				fill.relocate(innerX, innerY);
				fill.setWidth(vsbWidth);
				fill.setHeight(hsbHeight);
			} else if (vbar.isVisible()) {
				vbar.resizeRelocate(getWidth() - vbar.prefWidth(-1), 0, vbar.prefWidth(-1), getHeight());
			} else if (hbar.isVisible()) {
				hbar.resizeRelocate(0, getHeight() - hbar.prefHeight(-1), getWidth(), hbar.prefHeight(-1));
			}
		}

		public BooleanProperty loading() {
			return loading;
		}
	}

	private static Predicate<CardInstance> uniquenessPredicate() {
		Map<Card, UUID> prefPrintCache = new HashMap<>();

		return ci -> {
			Card.Printing preferred = Preferences.get().preferredPrinting(ci.card());
			if (preferred != null) return preferred.id().equals(ci.id());

			// TODO: This use of id() is entirely internal and might be able to stay.
			synchronized(prefPrintCache) {
				return ci.id().equals(prefPrintCache.computeIfAbsent(ci.card(), fn -> ci.card().printings().iterator().next().id()));
			}
		};
	}

	private final static Predicate<CardInstance> STANDARD_CARDS = c -> c.card().faces().stream().flatMap(f -> f.type().cardTypes().stream()).allMatch(CardType::constructed);

	private final MenuButton deckMenuButton;
	private final TextField filter;
	private final AutoCompleter filterAutoComplete;
	private final Tooltip filterErrorTooltip;
	private final CardView cardView;
	private final CardViewScrollPane scrollPane;
	private final Label deckStats;
	private final CheckMenuItem showIllegalCards;
	private final CheckMenuItem showVersionsSeparately;
	private final CheckMenuItem collapseDuplicates;
	private final CheckMenuItem findOtherCards;
	private final ToggleButton autoToggle;

	private final Consumer<Preferences> prefsListener;

	public final ObjectProperty<Consumer<CardInstance>> autoAction = new SimpleObjectProperty<>(null);
	public final BooleanProperty autoEnabled = new SimpleBooleanProperty(true);
	public final BooleanProperty showingIllegalCards = new SimpleBooleanProperty(true);
	public final BooleanProperty showingVersionsSeparately = new SimpleBooleanProperty(true);

	public CardPane(String title, DeckList deck, ObservableList<CardInstance> model, CardView.LayoutEngine.Factory initEngine, CardView.Grouping initGrouping, List<CardView.ActiveSorting> sortings) {
		super();

		this.cardView = new CardView(deck, model, initEngine, initGrouping, sortings);
		this.scrollPane = new CardViewScrollPane(this.cardView);
		setCenter(this.scrollPane);

		this.deckMenuButton = new MenuButton(title);
		this.deckMenuButton.setMinSize(Button.USE_PREF_SIZE, Button.USE_PREF_SIZE);

		Menu groupingMenu = new Menu("Grouping");
		ToggleGroup groupingGroup = new ToggleGroup();
		for (CardView.Grouping grouping : CardView.GROUPINGS) {
			RadioMenuItem item = new RadioMenuItem(grouping.name());
			item.setOnAction(ae -> {
				this.cardView.grouping(grouping);
				this.cardView.requestFocus();
			});
			item.setSelected(initGrouping.getClass().equals(grouping.getClass()));
			item.setToggleGroup(groupingGroup);
			groupingMenu.getItems().add(item);
		}
		groupingMenu.getItems().add(new SeparatorMenuItem());

		CheckMenuItem showEmptyGroups = new CheckMenuItem("Show Empty Groups");
		showEmptyGroups.selectedProperty().bindBidirectional(this.cardView.showEmptyGroupsProperty());
		groupingMenu.getItems().add(showEmptyGroups);

		Menu displayMenu = new Menu("Display");
		ToggleGroup displayGroup = new ToggleGroup();
		for (CardView.LayoutEngine.Factory display : CardView.LAYOUT_ENGINES) {
			RadioMenuItem item = new RadioMenuItem(display.name());
			item.setOnAction(ae -> {
				this.cardView.layout(display);
				this.cardView.requestFocus();
			});
			item.setSelected(initEngine.getClass().equals(display.getClass()));
			item.setToggleGroup(displayGroup);
			displayMenu.getItems().add(item);
		}

		MenuItem sortButton = new MenuItem("Sort");
		sortButton.setOnAction(ae -> {
			SortDialog dlg = new SortDialog(getScene().getWindow(), this.cardView.sort());
			dlg.initOwner(CardPane.this.getScene().getWindow());
			dlg.showAndWait()
					.ifPresent(s -> {
						this.cardView.sort(s);
						this.cardView.requestFocus();
					});
		});

		CustomMenuItem cardScale = new CustomMenuItem();
		Slider cardScaleSlider = new Slider(0.25, 1.5, 1.0);
		cardScaleSlider.setMajorTickUnit(0.05);
		cardScaleSlider.setBlockIncrement(0.05);
		cardScaleSlider.setMinorTickCount(0);
		cardScaleSlider.setSnapToTicks(true);
		Label cardScaleText = new Label("100%");
		cardScaleText.textProperty().bind(cardScaleSlider.valueProperty().multiply(100).asString("%.0f%%"));
		this.cardView.cardScaleProperty().bind(cardScaleSlider.valueProperty());
		cardScale.setContent(new HBox(cardScaleSlider, cardScaleText));
		cardScale.setHideOnClick(false);

		MenuItem statisticsButton = new MenuItem("Statistics");
		statisticsButton.setOnAction(ae -> {
			try {
				new DeckStatsDialog(getScene().getWindow(), this.cardView.filteredModel).show();
			} catch (IOException e) {
				throw new RuntimeException(e); // TODO: Handle gracefully
			}
		});

		findOtherCards = new CheckMenuItem("Show Nontraditional Cards");
		findOtherCards.setSelected(false);

		showIllegalCards = new CheckMenuItem("Show Invalid Cards");
		showIllegalCards.selectedProperty().bindBidirectional(this.showingIllegalCards);

		showVersionsSeparately = new CheckMenuItem("Show Versions Separately");
		showVersionsSeparately.selectedProperty().bindBidirectional(this.showingVersionsSeparately);

		collapseDuplicates = new CheckMenuItem("Collapse Duplicates");
		collapseDuplicates.selectedProperty().bindBidirectional(cardView.collapseDuplicatesProperty());

		deckMenuButton.getItems().setAll(
				groupingMenu,
				displayMenu,
				sortButton,
				cardScale,
				new SeparatorMenuItem(),
				statisticsButton,
				new SeparatorMenuItem(),
				showIllegalCards,
				findOtherCards,
				showVersionsSeparately,
				collapseDuplicates
		);

		filter = new TextField();
		filter.setPromptText(Preferences.get().searchProvider.name() + "...");
		filter.setMinSize(TextField.USE_PREF_SIZE, TextField.USE_PREF_SIZE);
		Preferences.listen(prefsListener = prefs -> filter.setPromptText(prefs.searchProvider.name() + "..."));

		filterAutoComplete = new AutoCompleter(filter, name -> {
			if (name.length() > 3) {
				return model.stream()
						.map(ci -> ci.card().name())
						.filter(n -> n.toLowerCase().contains(name.toLowerCase()))
						.distinct()
						.sorted(Comparator.comparingInt((ToIntFunction<String>) n -> n.toLowerCase().indexOf(name.toLowerCase())).thenComparing(Comparator.naturalOrder()))
						.collect(Collectors.toList());
			} else {
				return Collections.emptyList();
			}
		}, name -> filter.fireEvent(new ActionEvent(filter, filter)));

		filterAutoComplete.enabledProperty().bind(autoEnabled);

		filterErrorTooltip = new Tooltip();
		filter.setTooltip(filterErrorTooltip);
		Tooltip.uninstall(filter, filterErrorTooltip);
		filter.getTooltip().setText("");

		autoToggle = new ToggleButton("Auto");
		autoToggle.visibleProperty().bind(autoAction.isNotNull());
		autoToggle.managedProperty().bind(autoToggle.visibleProperty());
		autoEnabled.bindBidirectional(autoToggle.selectedProperty());

		findOtherCards.setOnAction(this::updateFilterFx);
		showIllegalCards.setOnAction(this::updateFilterFx);
		showVersionsSeparately.setOnAction(this::updateFilterFx);
		filter.setOnAction(this::updateFilterFx);

		deckStats = new Label("");
		model.addListener((ListChangeListener<CardInstance>) lce -> ForkJoinPool.commonPool().submit(this::updateStats));

		HBox controlBar = new HBox(4.0);
		controlBar.setPadding(new Insets(4.0));
		controlBar.setAlignment(Pos.CENTER_LEFT);
		controlBar.getChildren().add(deckMenuButton);
		controlBar.getChildren().add(filter);
		controlBar.getChildren().add(autoToggle);
		controlBar.getChildren().add(deckStats);
		HBox.setHgrow(deckMenuButton, Priority.NEVER);
		HBox.setHgrow(filter, Priority.SOMETIMES);
		HBox.setHgrow(deckStats, Priority.NEVER);
		this.setTop(controlBar);

		cardView.filteredModel.setPredicate(calculateFilter());
		ForkJoinPool.commonPool().submit(this::updateStats);
	}

	public String title() {
		return deckMenuButton.getText();
	}

	private synchronized void updateStats() {
		final long total = cardView.filteredModel.size();
		AtomicLong land = new AtomicLong(0), creature = new AtomicLong(0), other = new AtomicLong(0);

		try {
			for (CardInstance ci : cardView.filteredModel) {
				Card.Face front = ci.card().front();
				if (front != null && front.type().is(CardType.Creature)) {
					creature.incrementAndGet();
				} else if (front != null && front.type().is(CardType.Land)) {
					land.incrementAndGet();
				} else {
					other.incrementAndGet();
				}
			}
		} catch (NoSuchElementException | ConcurrentModificationException e) {
			return;
		}

		StringBuilder str = new StringBuilder();

		if (total != 0) {
			str.append(total).append(" Card").append(total == 1 ? "" : "s").append(": ");

			boolean comma = append(str, false, land, "Land");
			comma = append(str, comma, creature, "Creature");
			append(str, comma, other, "Other");
		}

		Platform.runLater(() -> deckStats.setText(str.toString()));
	}

	private static boolean append(StringBuilder str, boolean comma, AtomicLong qty, String qtyName) {
		if (qty.get() > 0) {
			if (comma) {
				str.append(", ");
			}

			str.append(qty.get()).append(' ').append(qtyName);

			if (qty.get() != 1) {
				str.append('s');
			}

			return true;
		} else {
			return comma;
		}
	}

	private Predicate<CardInstance> calculateFilter() throws IllegalArgumentException {
		Predicate<CardInstance> compositeFilter;
		if (filter.getText().isEmpty()) {
			compositeFilter = c -> true;
		} else {
			SearchProvider provider = Preferences.get().searchProvider;
			compositeFilter = provider.parse(filter.getText());
		}

		if (!findOtherCards.isSelected()) {
			compositeFilter = compositeFilter.and(STANDARD_CARDS);
		}

		if (!showIllegalCards.isSelected()) {
			compositeFilter = compositeFilter.and(c -> !c.flags.contains(CardInstance.Flags.Invalid));
		}

		if (!showVersionsSeparately.isSelected()) {
			compositeFilter = compositeFilter.and(uniquenessPredicate());
		}

		return compositeFilter;
	}

	private void updateFilterFx(ActionEvent ae) {
		if (!Platform.isFxApplicationThread()) {
			throw new IllegalStateException("updateFilterFx must only be called from the FX Application thread!");
		}

		ForkJoinPool.commonPool().submit(this::updateFilter);
	}

	public void updateFilter() {
		if (Platform.isFxApplicationThread()) {
			new IllegalStateException("updateFilter() called from FX application thread!").printStackTrace(System.err);
			ForkJoinPool.commonPool().submit(this::updateFilter);
			return;
		}

		final Predicate<CardInstance> finalFilter;
		try {
			finalFilter = calculateFilter();
		} catch (IllegalArgumentException iae) {
			Platform.runLater(() -> {
				Tooltip.install(filter, filterErrorTooltip);
				filter.getTooltip().setText(iae.getMessage());
				filter.setStyle("-fx-control-inner-background: #ff8080;");
			});
			return;
		}

		if (filter.getStyle().contains("#ff8080")) {
			Platform.runLater(() -> {
				Tooltip.uninstall(filter, filterErrorTooltip);
				filter.getTooltip().setText("");
				filter.setStyle("");
			});
		}

		changeModel(x -> this.cardView.filteredModel.setPredicate(finalFilter));

		final boolean clear;
		final Node focusTarget;

		if (autoAction.get() != null && this.cardView.filteredModel.size() <= 1 && autoToggle.isSelected()) {
			if (!this.cardView.filteredModel.isEmpty()) {
				autoAction.get().accept(this.cardView.filteredModel.get(0));
				clear = true;
			} else {
				clear = false;
			}

			focusTarget = filter;
		} else {
			clear = false;
			focusTarget = cardView;
		}

		Platform.runLater(() -> {
			if (clear) filter.setText("");
			focusTarget.requestFocus();
		});

		updateStats();
	}

	public CardPane(DeckList deck, Zone zone, CardView.LayoutEngine.Factory initEngine, CardView.Grouping initGrouping, List<CardView.ActiveSorting> sortings) {
		this(zone.name(), deck, deck.cards(zone), initEngine, initGrouping, sortings);
	}

	public CardPane(String title, ObservableList<CardInstance> model, CardView.LayoutEngine.Factory layoutEngine, CardView.Grouping grouping, List<CardView.ActiveSorting> sortings) {
		this(title, null, model, layoutEngine, grouping, sortings);
	}

	public CardPane(String title, ObservableList<CardInstance> model, CardView.LayoutEngine.Factory layoutEngine) {
		this(title, model, layoutEngine, ManaValue.INSTANCE, CardView.DEFAULT_SORTING);
	}

	public CardView view() {
		return this.cardView;
	}

	public void changeModel(Consumer<ObservableList<CardInstance>> mutator) {
		synchronized(this.cardView.model) {
			mutator.accept(this.cardView.model);
		}
	}

	public TextField filter() {
		return filter;
	}

	public BooleanProperty loading() {
		return scrollPane.loading();
	}
}
