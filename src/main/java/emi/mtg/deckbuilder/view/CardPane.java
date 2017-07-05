package emi.mtg.deckbuilder.view;

import emi.lib.mtg.characteristic.ManaSymbol;
import emi.lib.mtg.data.ImageSource;
import emi.mtg.deckbuilder.model.CardInstance;
import javafx.collections.ObservableList;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.text.Font;

import java.util.Comparator;
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
			long n1 = -c1.card().front().manaCost().symbols().stream().map(ManaSymbol::colors).filter(s -> s.contains(c)).count();
			long n2 = -c2.card().front().manaCost().symbols().stream().map(ManaSymbol::colors).filter(s -> s.contains(c)).count();

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
						subPredicate = c -> c.card().front().text().toLowerCase().contains(value.toLowerCase());
						break;
					case "t":
					case "type":
						subPredicate = c -> c.card().front().type().toString().toLowerCase().contains(value.toLowerCase());
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
								subPredicate = c -> c.card().front().manaCost().convertedCost() == ivalue;
								break;
							case ">":
								subPredicate = c -> c.card().front().manaCost().convertedCost() > ivalue;
								break;
							case "<":
								subPredicate = c -> c.card().front().manaCost().convertedCost() < ivalue;
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

		this.cardView = new CardView(images, model, initEngine, "CMC");
		this.setCenter(this.cardView);

		Label label = new Label(title);
		label.setFont(Font.font(18.0));

		TextField filter = new TextField();
		filter.setPromptText(OMNIFILTER_PROMPT);
		filter.setPrefWidth(250.0);
		filter.setOnAction(ae -> {
			this.cardView.filter(createOmnifilter(filter.getText()));
			this.cardView.requestFocus();
		});

		ComboBox<String> groupingBox = new ComboBox<>();
		groupingBox.setValue("CMC");
		groupingBox.setOnAction(ae -> {
			this.cardView.group(groupingBox.getValue());
			this.cardView.requestFocus();
		});
		for (String grouping : CardView.groupingNames()) {
			groupingBox.getItems().add(grouping);
		}

		ComboBox<String> displayBox = new ComboBox<>();
		displayBox.setValue(initEngine);
		displayBox.setOnAction(ae -> {
			this.cardView.layout(displayBox.getValue());
			this.cardView.requestFocus();
		});
		for (String engine : CardView.engineNames()) {
			displayBox.getItems().add(engine);
		}

		HBox controlBar = new HBox(8.0);
		controlBar.setPadding(new Insets(8.0));
		controlBar.setAlignment(Pos.BASELINE_LEFT);
		controlBar.getChildren().add(label);
		controlBar.getChildren().add(filter);
		controlBar.getChildren().add(new Label("Group by:"));
		controlBar.getChildren().add(groupingBox);
		controlBar.getChildren().add(new Label("Display as:"));
		controlBar.getChildren().add(displayBox);
		HBox.setHgrow(filter, Priority.SOMETIMES);
		this.setTop(controlBar);
	}

	public CardView view() {
		return this.cardView;
	}
}
