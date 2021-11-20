package emi.mtg.deckbuilder.view.components;

import emi.lib.mtg.Card;
import emi.lib.mtg.enums.CardType;
import emi.mtg.deckbuilder.model.CardInstance;
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

			// SVG for TSP Common from BaconCatBug's Set Symbol Megapack: https://www.slightlymagic.net/forum/viewtopic.php?f=15&t=11086
			loadingIndicator = new SVGPath();
			loadingIndicator.setContent("m 445.30275,167.21201 c -54.81532,19.31186 -88.86828,24.97892 -146.98938,24.57413 -57.17658,-0.31978 -90.58863,-6.18991 -144.4763,-25.4849 -10.33901,-20.42503 -16.73132,-43.88599 -16.73132,-64.17609 l 325.85596,0 c 0,21.87553 -17.65896,65.08686 -17.65896,65.08686 " +
					"m -65.15433,273.75612 c 51.71193,27.20526 85.3095,66.94215 85.3095,115.29769 0,7.11756 -0.65711,6.8477 -2.04082,16.141 l -67.465,0 C 349.56991,537.81414 317.4903,480.89054 317.4903,404.60449 l -0.45471,-8.18013 c 0,0 28.72322,26.42941 63.11351,44.54377 " +
					"M 282.71277,406.8983 c 0,76.30292 -32.16394,133.29398 -78.6136,165.50852 l -67.39753,0 c -1.4505,-6.91516 -2.17575,-6.66217 -2.17575,-13.89779 0,-48.30494 33.7325,-88.09243 85.32636,-115.17963 34.59268,-18.11435 63.18098,-46.97251 63.18098,-46.97251 l -0.32114,10.54141 z " +
					"m 286.97925,-311.75578 0,-57.51391 c 0,-4.03103 -3.15399,-7.31995 -7.18502,-7.31995 l -525.029516,0 c -3.946703,0 -7.168157,3.28892 -7.168157,7.31995 l 0,57.51391 c 0,3.81178 3.221454,7.0501 7.168157,7.0501 l 42.637881,0 -0.06746,5.53213 c 0,65.87957 46.044865,114.57244 103.035925,144.61123 33.07471,17.27104 40.59706,38.28639 40.59706,77.61849 0,38.80924 -8.2476,60.93776 -41.64277,78.46179 -57.12599,29.97133 -103.120254,78.73166 -103.120254,144.54377 0,6.72964 0.472255,13.05448 1.33176,19.44679 l -42.772812,0 c -3.946703,0 -7.168157,3.28892 -7.168157,7.23562 l 0,57.59825 c 0,3.87923 3.221454,7.10069 7.168157,7.10069 l 525.029506,0 c 4.03104,0 7.18503,-3.22146 7.18503,-7.10069 l 0,-57.59825 c 0,-3.9467 -3.15399,-7.23562 -7.18503,-7.23562 l -43.80165,0 c 0.72458,-6.39231 1.31557,-12.71715 1.31557,-19.44679 0,-65.81211 -46.1292,-114.57244 -103.17086,-144.54377 -33.34457,-17.52403 -41.5753,-39.65255 -41.5753,-78.46179 0,-37.8816 8.75358,-59.75713 42.75594,-77.61849 57.05853,-29.97133 103.10339,-78.73166 103.10339,-144.61123 l -0.0675,-5.53213 41.50784,0 c 3.96357,0 7.11756,-3.23832 7.11756,-7.0501");
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

	public CardPane(String title, ObservableList<CardInstance> model, CardView.LayoutEngine.Factory initEngine, CardView.Grouping initGrouping, List<CardView.ActiveSorting> sortings) {
		super();

		this.cardView = new CardView(model, initEngine, initGrouping, sortings);
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
			SortDialog dlg = new SortDialog(this.cardView.sort());
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
				new DeckStatsDialog(this.cardView.filteredModel).show();
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
				Card.Face front = ci.card().face(Card.Face.Kind.Front);
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

	public CardPane(String title, ObservableList<CardInstance> model, CardView.LayoutEngine.Factory layoutEngine, CardView.Grouping grouping) {
		this(title, model, layoutEngine, grouping, CardView.DEFAULT_SORTING);
	}

	public CardPane(String title, ObservableList<CardInstance> model, CardView.LayoutEngine.Factory layoutEngine) {
		this(title, model, layoutEngine, ManaValue.INSTANCE);
	}

	public CardPane(String title, ObservableList<CardInstance> model) {
		this(title, model, Piles.Factory.INSTANCE);
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
