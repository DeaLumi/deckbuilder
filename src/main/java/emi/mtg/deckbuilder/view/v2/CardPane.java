package emi.mtg.deckbuilder.view.v2;

import emi.lib.mtg.data.ImageSource;
import emi.mtg.deckbuilder.model.CardInstance;
import emi.mtg.deckbuilder.view.CardGroup;
import javafx.collections.ObservableList;
import javafx.scene.control.ComboBox;
import javafx.scene.control.TextField;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;

import java.util.Comparator;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class CardPane extends BorderPane {
    private final CardView cardView;

    private static final Pattern OMNIFILTER_PATTERN = Pattern.compile("(?:(?<characteristic>[^:]+)(?<operator>(?:[<>]=?|=|:)))?(?<value>[^ \"]+|\"[^\"]*\")");

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
                        subPredicate = c -> c.card().text().toLowerCase().contains(value.toLowerCase());
                        break;
                    case "t":
                    case "type":
                        subPredicate = c -> c.card().type().toString().toLowerCase().contains(value.toLowerCase());
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
                            case ">=":
                                subPredicate = c -> c.card().manaCost().convertedCost() >= ivalue;
                                break;
                            case "<":
                                subPredicate = c -> c.card().manaCost().convertedCost() < ivalue;
                                break;
                            case "<=":
                                subPredicate = c -> c.card().manaCost().convertedCost() <= ivalue;
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

    public CardPane(ImageSource images, ObservableList<CardInstance> model, String initEngine) {
        HBox controlBar = new HBox();

        this.cardView = new CardView(images, model);
        this.cardView.filter(c -> true);
        this.cardView.sort(CardGroup.COLOR_SORT.thenComparing(CardGroup.NAME_SORT));
        this.cardView.group(new String[] { "0", "1", "2", "3", "4", "5", "6", "7", "8", "9", "10", "11", "12", "13", "14", "15", "X" }, CardGroup.CMC_GROUP);
        this.cardView.layout(initEngine);
        this.setCenter(this.cardView);

        ComboBox<String> displayBox = new ComboBox<>();
        displayBox.setValue("Flow");
        displayBox.setOnAction(ae -> this.cardView.layout(displayBox.getValue()));
        for (String engine : CardView.engineNames()) {
            displayBox.getItems().add(engine);
        }

        TextField filter = new TextField();
        filter.setPromptText("Omnifilter...");
        filter.setPrefWidth(250.0);
        filter.setOnAction(ae -> {
            this.cardView.filter(createOmnifilter(filter.getText()));
        });

        controlBar.getChildren().add(filter);
        controlBar.getChildren().add(displayBox);
        this.setTop(controlBar);
    }

    public void filter(Predicate<CardInstance> filter) {
        this.cardView.filter(filter);
    }

    public void sort(Comparator<CardInstance> sort) {
        this.cardView.sort(sort);
    }

    public void group(String[] groups, Function<CardInstance, String> groupExtractor) {
        this.cardView.group(groups, groupExtractor);
    }
}
