package emi.mtg.deckbuilder.view;

import com.sun.javafx.collections.ObservableListWrapper;
import emi.lib.mtg.card.CardFace;
import emi.lib.mtg.characteristic.ManaSymbol;
import emi.lib.mtg.data.ImageSource;
import emi.mtg.deckbuilder.model.CardInstance;
import emi.mtg.deckbuilder.view.groupings.ConvertedManaCost;
import emi.mtg.deckbuilder.view.sortings.ManaCost;
import emi.mtg.deckbuilder.view.sortings.Name;
import javafx.collections.ObservableList;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.geometry.Side;
import javafx.scene.control.*;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;
import javafx.scene.text.Text;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.function.Predicate;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class CardPane extends BorderPane {
	public final static Comparator<CardInstance> NAME_SORT = Comparator.comparing(c -> c.card().name());
	public final static Comparator<CardInstance> COLOR_SORT = (c1, c2) -> {
		if (c1.card().color().size() != c2.card().color().size()) {
			int s1 = c1.card().color().size();
			if (s1 == 0) {
				s1 = emi.lib.mtg.characteristic.Color.values().length + 1;
			}

			int s2 = c2.card().color().size();
			if (s2 == 0) {
				s2 = emi.lib.mtg.characteristic.Color.values().length + 1;
			}

			return s1 - s2;
		}

		for (int i = emi.lib.mtg.characteristic.Color.values().length - 1; i >= 0; --i) {
			emi.lib.mtg.characteristic.Color c = emi.lib.mtg.characteristic.Color.values()[i];
			long n1 = -c1.card().manaCost().symbols().stream().map(ManaSymbol::colors).filter(s -> s.contains(c)).count();
			long n2 = -c2.card().manaCost().symbols().stream().map(ManaSymbol::colors).filter(s -> s.contains(c)).count();

			if (n1 != n2) {
				return (int) (n2 - n1);
			}
		}

		return 0;
	};

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
						subPredicate = c -> Arrays.stream(CardFace.Kind.values()).map(c.card()::face).map(CardFace::text).map(String::toLowerCase).anyMatch(s -> s.contains(value.toLowerCase()));
						break;
					case "t":
					case "type":
						subPredicate = c -> Arrays.stream(CardFace.Kind.values()).map(c.card()::face).map(cf -> cf.type().toString().toLowerCase()).anyMatch(s -> s.contains(value.toLowerCase()));
						break;
					case "set":
						subPredicate = c -> c.card().set().name().toLowerCase().contains(value.toLowerCase());
						break;
					case "setcode":
					case "sc":
						subPredicate = c -> c.card().set().code().equals(value.toUpperCase());
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
								throw new Error("Unrecognized operator " + m.group("operator"));
						}
						break;
					default:
						throw new Error("Unrecognized characteristic " + m.group("characteristic"));
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

	private final CardView cardView;

	public CardPane(String title, ImageSource images, ObservableList<CardInstance> model, String initEngine) {
		super();

		this.cardView = new CardView(images, model, initEngine, new ConvertedManaCost(), new ManaCost(), new Name());
		this.setCenter(this.cardView);

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
