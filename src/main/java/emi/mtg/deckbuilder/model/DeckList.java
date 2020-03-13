package emi.mtg.deckbuilder.model;

import emi.lib.mtg.game.Deck;
import emi.lib.mtg.game.Format;
import emi.lib.mtg.game.Zone;
import javafx.beans.property.Property;
import javafx.beans.property.SimpleObjectProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;

import java.util.Collections;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

public class DeckList implements Deck {
	private static Map<Zone, ObservableList<CardInstance>> emptyDeck() {
		Map<Zone, ObservableList<CardInstance>> zones = new EnumMap<>(Zone.class);

		for (Zone zone : Zone.values()) {
			zones.put(zone, FXCollections.observableArrayList());
		}

		return zones;
	}

	private Property<String> name = new SimpleStringProperty("");
	private Property<Format> format = new SimpleObjectProperty<>(null);
	private Property<String> author = new SimpleStringProperty("");
	private Property<String> description = new SimpleStringProperty("");

	private Map<Zone, ObservableList<CardInstance>> cards = emptyDeck();

	private DeckList() {

	}

	public DeckList(String name, String author, Format format, String description, Map<Zone, List<CardInstance>> cards) {
		this.name.setValue(name);
		this.author.setValue(author);
		this.format.setValue(format);
		this.description.setValue(description);

		for (Zone zone : Zone.values()) {
			this.cards.get(zone).setAll(cards.getOrDefault(zone, Collections.emptyList()));
		}
	}

	@Override
	public String name() {
		return name.getValue();
	}

	public Property<String> nameProperty() {
		return name;
	}

	@Override
	public String author() {
		return author.getValue();
	}

	public Property<String> authorProperty() {
		return author;
	}

	@Override
	public Format format() {
		return format.getValue();
	}

	public Property<Format> formatProperty() {
		return format;
	}

	@Override
	public String description() {
		return description.getValue();
	}

	public Property<String> descriptionProperty() {
		return description;
	}

	@Override
	public ObservableList<CardInstance> cards(Zone zone) {
		return cards.get(zone);
	}

	public Map<Zone, ObservableList<CardInstance>> cards() {
		return cards;
	}
}
