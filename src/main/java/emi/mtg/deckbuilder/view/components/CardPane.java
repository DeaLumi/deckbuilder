package emi.mtg.deckbuilder.view.components;

import emi.lib.mtg.Card;
import emi.lib.mtg.characteristic.CardType;
import emi.mtg.deckbuilder.controller.Context;
import emi.mtg.deckbuilder.model.CardInstance;
import emi.mtg.deckbuilder.view.dialogs.SortDialog;
import emi.mtg.deckbuilder.view.omnifilter.Omnifilter;
import javafx.application.Platform;
import javafx.beans.binding.DoubleBinding;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.DoubleProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.collections.ObservableList;
import javafx.event.ActionEvent;
import javafx.event.EventHandler;
import javafx.geometry.Insets;
import javafx.geometry.Orientation;
import javafx.geometry.Pos;
import javafx.geometry.Side;
import javafx.scene.control.*;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.StackPane;
import javafx.scene.shape.Rectangle;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;

import java.util.List;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Predicate;

public class CardPane extends BorderPane {
	private static class CardViewScrollPane extends StackPane {
		private static class VisibleAmountBinding extends DoubleBinding {
			private DoubleProperty height, min, max;

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
		private final Rectangle fill;

		public CardViewScrollPane(CardView view) {
			this.view = view;

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
			fill.setFill(javafx.scene.paint.Color.grayRgb(232));
			fill.visibleProperty().bind(vbar.visibleProperty().and(hbar.visibleProperty()));

			getChildren().addAll(view, vbar, hbar, fill);
		}

		@Override
		protected void layoutChildren() {
			view.resizeRelocate(0, 0, getWidth(), getHeight());

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
	}

	private final static Predicate<CardInstance> STANDARD_CARDS = c -> c.card().faces().stream().allMatch(f -> CardType.CONSTRUCTED_TYPES.containsAll(f.type().cardTypes()));

	private final EventHandler<ActionEvent> updateFilter;
	private final CardView cardView;

	public final BooleanProperty showIllegalCards = new SimpleBooleanProperty(true);

	public CardPane(String title, Context context, ObservableList<CardInstance> model, String initEngine, List<CardView.ActiveSorting> sortings) {
		super();

		this.cardView = new CardView(context, model, initEngine, "CMC", sortings);
		setCenter(new CardViewScrollPane(this.cardView));

		Button label = new Button(title);
		label.setFont(Font.font(null, FontWeight.BOLD, -1));

		Menu groupingMenu = new Menu("Grouping");
		ToggleGroup groupingGroup = new ToggleGroup();
		for (String grouping : CardView.groupings()) {
			RadioMenuItem item = new RadioMenuItem(grouping);
			item.setOnAction(ae -> {
				this.cardView.group(grouping);
				this.cardView.requestFocus();
			});
			item.setSelected("CMC".equals(grouping));
			item.setToggleGroup(groupingGroup);
			groupingMenu.getItems().add(item);
		}
		groupingMenu.getItems().add(new SeparatorMenuItem());

		CheckMenuItem showEmptyGroups = new CheckMenuItem("Show Empty Groups");
		showEmptyGroups.selectedProperty().bindBidirectional(this.cardView.showEmptyGroupsProperty());
		groupingMenu.getItems().add(showEmptyGroups);

		Menu displayMenu = new Menu("Display");
		ToggleGroup displayGroup = new ToggleGroup();
		for (String display : CardView.engineNames()) {
			RadioMenuItem item = new RadioMenuItem(display);
			item.setOnAction(ae -> {
				this.cardView.layout(display);
				this.cardView.requestFocus();
			});
			item.setSelected(initEngine.equals(display));
			item.setToggleGroup(displayGroup);
			displayMenu.getItems().add(item);
		}

		MenuItem sortButton = new MenuItem("Sort");
		sortButton.setOnAction(ae -> {
			SortDialog dlg = new SortDialog(this.cardView.sort());
			dlg.showAndWait()
					.ifPresent(s -> {
						this.cardView.sort(s);
						this.cardView.requestFocus();
					});
		});

		CustomMenuItem cardScale = new CustomMenuItem();
		Slider cardScaleSlider = new Slider(0.25, 1.5, 1.0);
		this.cardView.cardScaleProperty().bind(cardScaleSlider.valueProperty());
		cardScale.setContent(cardScaleSlider);
		cardScale.setHideOnClick(false);

		CheckMenuItem findOtherCards = new CheckMenuItem("Show Nontraditional Cards");
		findOtherCards.setSelected(false);

		CheckMenuItem showIllegalCards = new CheckMenuItem("Show Illegal Cards");
		showIllegalCards.selectedProperty().bindBidirectional(this.showIllegalCards);

		ContextMenu deckMenu = new ContextMenu();

		deckMenu.getItems().add(groupingMenu);
		deckMenu.getItems().add(displayMenu);
		deckMenu.getItems().add(sortButton);
		deckMenu.getItems().add(cardScale);
		deckMenu.getItems().add(new SeparatorMenuItem());
		deckMenu.getItems().add(showIllegalCards);
		deckMenu.getItems().add(findOtherCards);

		label.setOnAction(ae -> {
			if (deckMenu.isShowing()) {
				deckMenu.hide();
			} else {
				deckMenu.show(label, Side.BOTTOM, 0, 0);
			}
		});

		final TextField filter = new TextField();
		filter.setPromptText("Omnifilter...");
		filter.setPrefWidth(250.0);

		this.updateFilter = ae -> {
			Predicate<CardInstance> compositeFilter = Omnifilter.parse(context, filter.getText());

			if (!findOtherCards.isSelected()) {
				compositeFilter = compositeFilter.and(STANDARD_CARDS);
			}

			if (!showIllegalCards.isSelected()) {
				compositeFilter = compositeFilter.and(c -> context.deck.format.cardIsLegal(c.card()));
			}

			this.cardView.filter(compositeFilter);
			this.cardView.requestFocus();
		};

		findOtherCards.setOnAction(updateFilter);
		showIllegalCards.setOnAction(updateFilter);
		filter.setOnAction(updateFilter);

		Label deckStats = new Label("? Land / ? Creature / ? Other");

		// TODO: EW. EW. EW. EW. EW.
		Thread statRefreshThread = new Thread(() -> {
			while (!Thread.interrupted()) {
				try {
					Thread.sleep(1000);
				} catch (InterruptedException ie) {
					break;
				}

				AtomicLong land = new AtomicLong(0), creature = new AtomicLong(0), other = new AtomicLong(0);

				for (CardInstance ci : cardView.filteredModel()) {
					for (Card.Face f : ci.card().faces()) {
						if (f.type().cardTypes().contains(CardType.Creature)) {
							creature.incrementAndGet();
						} else if (f.type().cardTypes().contains(CardType.Land)) {
							land.incrementAndGet();
						} else {
							other.incrementAndGet();
						}
					}
				}

				Platform.runLater(() -> deckStats.setText(String.format("%d Land / %d Creature / %d Other", land.get(), creature.get(), other.get())));
			}
		}, "Stat Refresh Thread");
		statRefreshThread.setDaemon(true);
		statRefreshThread.start();

		HBox controlBar = new HBox(8.0);
		controlBar.setPadding(new Insets(8.0));
		controlBar.setAlignment(Pos.BASELINE_LEFT);
		controlBar.getChildren().add(label);
		controlBar.getChildren().add(filter);
		controlBar.getChildren().add(deckStats);
		HBox.setHgrow(label, Priority.NEVER);
		HBox.setHgrow(filter, Priority.SOMETIMES);
		HBox.setHgrow(deckStats, Priority.NEVER);
		this.setTop(controlBar);

		updateFilter();
	}

	public void updateFilter() {
		this.updateFilter.handle(null);
	}

	public CardPane(String title, Context context, ObservableList<CardInstance> model, String layoutEngine) {
		this(title, context, model, layoutEngine, CardView.DEFAULT_SORTING);
	}

	public CardPane(String title, Context context, ObservableList<CardInstance> model) {
		this(title, context, model, "Piles", CardView.DEFAULT_SORTING);
	}

	public ObservableList<CardInstance> model() {
		return this.cardView.model();
	}

	public CardView view() {
		return this.cardView;
	}
}