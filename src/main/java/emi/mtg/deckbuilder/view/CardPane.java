package emi.mtg.deckbuilder.view;

import com.sun.javafx.collections.ObservableListWrapper;
import emi.lib.mtg.card.CardFace;
import emi.lib.mtg.characteristic.Color;
import emi.lib.mtg.data.ImageSource;
import emi.mtg.deckbuilder.model.CardInstance;
import emi.mtg.deckbuilder.view.groupings.ConvertedManaCost;
import emi.mtg.deckbuilder.view.sortings.ManaCost;
import emi.mtg.deckbuilder.view.sortings.Name;
import javafx.beans.binding.DoubleBinding;
import javafx.beans.property.DoubleProperty;
import javafx.collections.ObservableList;
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

import java.util.*;
import java.util.function.Predicate;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class CardPane extends BorderPane {
	private static final Pattern OMNIFILTER_PATTERN = Pattern.compile("(?:(?<characteristic>[^ :<>=]+)(?<operator>(?:[<>=:])))?(?<value>[^ \"]+|\"[^\"]*\")");
	private static final String OMNIFILTER_PROMPT = "text:rules o:text cmc>X type:\"supertype cardtype\" t:subtype";

	private static final Predicate<CardInstance> createOmnifilter(String query) {
		Matcher m = OMNIFILTER_PATTERN.matcher(query);

		Predicate<CardInstance> predicate = c -> true;
		while(m.find()) {
			final String gvalue = m.group("value");
			final String value;
			if (gvalue.startsWith("\"")) {
				value = gvalue.substring(1, gvalue.length() - 1);
			} else {
				value = gvalue;
			}

			if (m.group("characteristic") != null) {
				String characteristic = m.group("characteristic");
				boolean negate = characteristic.startsWith("!");
				if (negate) {
					characteristic = characteristic.substring(1);
				}

				Predicate<CardInstance> subPredicate;
				switch (characteristic) {
					case "o":
					case "text":
						subPredicate = c -> Arrays.stream(CardFace.Kind.values()).map(c.card()::face).filter(Objects::nonNull).map(CardFace::text).map(String::toLowerCase).anyMatch(s -> s.contains(value.toLowerCase()));
						break;
					case "c":
					case "color": {
						EnumSet<Color> tmp = Arrays.stream(Color.values()).filter(c -> value.toLowerCase().contains(c.letter.toLowerCase())).collect(() -> EnumSet.noneOf(Color.class), Set::add, Set::addAll);
						EnumSet<Color> colors = !negate ? tmp : EnumSet.complementOf(tmp);

						negate = false;
						subPredicate = c -> colors.containsAll(c.card().color());
						break;
					}
					case "ci":
					case "identity": {
						EnumSet<Color> tmp = Arrays.stream(Color.values()).filter(c -> value.toLowerCase().contains(c.letter.toLowerCase())).collect(() -> EnumSet.noneOf(Color.class), Set::add, Set::addAll);
						EnumSet<Color> colors = !negate ? tmp : EnumSet.complementOf(tmp);

						negate = false;
						subPredicate = c -> colors.containsAll(c.card().colorIdentity());
						break;
					}
					case "t":
					case "type":
						subPredicate = c -> Arrays.stream(CardFace.Kind.values()).map(c.card()::face).filter(Objects::nonNull).map(cf -> cf.type().toString().toLowerCase()).anyMatch(s -> s.contains(value.toLowerCase()));
						break;
					case "set":
					case "s":
					case "edition":
					case "e":
						subPredicate = c -> c.card().set().name().toLowerCase().contains(value.toLowerCase()) || c.card().set().code().toLowerCase().equals(value.toLowerCase());
						break;
					case "setcode":
					case "sc":
						subPredicate = c -> c.card().set().code().toLowerCase().equals(value.toLowerCase());
						break;
					case "cmc":
						int ivalue = Integer.parseInt(value);
						switch (m.group("operator")) {
							case "=":
								subPredicate = c -> c.card().manaCost().convertedCost() == ivalue;
								break;
							case ">":
								subPredicate = c -> c.card().manaCost().convertedCost() > ivalue;
								break;
							case "<":
								subPredicate = c -> c.card().manaCost().convertedCost() < ivalue;
								break;
							default:
								subPredicate = c -> true;
								(new Throwable("Unrecognized operator " + m.group("operator"))).printStackTrace();
								break;
						}
						break;
					default:
						subPredicate = c -> true;
						(new Throwable("Unrecognized characteristic " + m.group("characteristic"))).printStackTrace();
						break;
				}
				if (negate) {
					subPredicate = subPredicate.negate();
				}
				predicate = predicate.and(subPredicate);
			} else {
				predicate = predicate.and(c -> c.card().name().toLowerCase().contains(value.toLowerCase()));
			}
		}

		return predicate;
	}

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

	private final CardView cardView;

	public CardPane(String title, ImageSource images, ObservableList<CardInstance> model, String initEngine) {
		super();

		// TODO: Somehow use these from CardView.
		this.cardView = new CardView(images, model, initEngine, new ConvertedManaCost(), new ManaCost(), new Name());
		setCenter(new CardViewScrollPane(this.cardView));

		Button label = new Button(title);
		label.setFont(Font.font(null, FontWeight.BOLD, -1));

		ContextMenu deckMenu = new ContextMenu();

		Menu groupingMenu = new Menu("Grouping");
		ToggleGroup groupingGroup = new ToggleGroup();
		for (CardView.Grouping grouping : CardView.groupings()) {
			RadioMenuItem item = new RadioMenuItem(grouping.toString());
			item.setOnAction(ae -> {
				this.cardView.group(grouping);
				this.cardView.requestFocus();
			});
			item.setSelected("CMC".equals(grouping.toString()));
			item.setToggleGroup(groupingGroup);
			groupingMenu.getItems().add(item);
		}
		deckMenu.getItems().add(groupingMenu);

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
		deckMenu.getItems().add(displayMenu);

		MenuItem sortButton = new MenuItem("Sort");
		sortButton.setOnAction(ae -> {
			ReorderDialog<CardView.Sorting> dlg = new ReorderDialog<>("Sort", new ObservableListWrapper<>(new ArrayList<>(CardView.sortings())));
			dlg.showAndWait()
					.ifPresent(s -> {
						this.cardView.sort(s.toArray(new CardView.Sorting[s.size()]));
						this.cardView.requestFocus();
					});
		});
		deckMenu.getItems().add(sortButton);

		CustomMenuItem cardScale = new CustomMenuItem();
		Slider cardScaleSlider = new Slider(0.25, 1.5, 1.0);
		this.cardView.cardScaleProperty().bind(cardScaleSlider.valueProperty());
		cardScale.setContent(cardScaleSlider);
		cardScale.setHideOnClick(false);
		deckMenu.getItems().add(cardScale);

		label.setOnAction(ae -> {
			deckMenu.show(label, Side.BOTTOM, 0, 0);
		});

		TextField filter = new TextField();
		filter.setPromptText(OMNIFILTER_PROMPT);
		filter.setPrefWidth(250.0);
		filter.setOnAction(ae -> {
			this.cardView.filter(createOmnifilter(filter.getText()));
			this.cardView.requestFocus();
		});

		HBox controlBar = new HBox(8.0);
		controlBar.setPadding(new Insets(8.0));
		controlBar.setAlignment(Pos.BASELINE_LEFT);
		controlBar.getChildren().add(label);
		controlBar.getChildren().add(filter);
		HBox.setHgrow(label, Priority.NEVER);
		HBox.setHgrow(filter, Priority.ALWAYS);
		this.setTop(controlBar);
	}

	public CardView view() {
		return this.cardView;
	}
}
