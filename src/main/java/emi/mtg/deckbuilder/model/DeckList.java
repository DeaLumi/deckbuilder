package emi.mtg.deckbuilder.model;

import emi.lib.mtg.Card;
import emi.lib.mtg.game.Deck;
import emi.lib.mtg.game.Format;
import emi.lib.mtg.game.Zone;
import javafx.beans.property.*;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;

import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;

public class DeckList implements Deck {
	public static class Change {
		public final String description;
		public final Consumer<DeckList> redo, undo;

		public Change(String description, Consumer<DeckList> redo, Consumer<DeckList> undo) {
			this.description = description;
			this.redo = redo;
			this.undo = undo;
		}

		@Override
		public String toString() {
			return description;
		}
	}

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

	private transient Property<Path> source = new SimpleObjectProperty<>(null);
	private transient BooleanProperty modified = new SimpleBooleanProperty(false);
	private transient ListProperty<Change> undoStack = new SimpleListProperty<>(FXCollections.observableArrayList()), redoStack = new SimpleListProperty<>(FXCollections.observableArrayList());

	private Map<Zone, ObservableList<CardInstance>> cards = emptyDeck();
	private ObservableList<CardInstance> cutCards = FXCollections.observableArrayList();

	private DeckList() {

	}

	public DeckList(String name, String author, Format format, String description, Map<Zone, ? extends List<CardInstance>> cards) {
		this.name.setValue(name);
		this.author.setValue(author);
		this.format.setValue(format);
		this.description.setValue(description);

		for (Zone zone : Zone.values()) {
			if (cards.containsKey(zone)) {
				this.cards.get(zone).setAll(cards.get(zone));
			} else {
				this.cards.get(zone).clear();
			}
		}
	}

	@Override
	public String name() {
		return name.getValue();
	}

	public String fileSafeName() {
		return name.getValue().replaceAll("[^A-Za-z0-9]", "-");
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

	public Path source() {
		return source.getValue();
	}

	public Property<Path> sourceProperty() {
		return source;
	}

	public boolean modified() {
		return modified.get();
	}

	public BooleanProperty modifiedProperty() {
		return modified;
	}

	public ListProperty<Change> undoStack() {
		return undoStack;
	}

	public ListProperty<Change> redoStack() {
		return redoStack;
	}

	@Override
	public ObservableList<CardInstance> cards(Zone zone) {
		return cards.get(zone);
	}

	public Map<Zone, ObservableList<CardInstance>> cards() {
		return cards;
	}

	public ObservableList<CardInstance> cutCards() {
		return cutCards;
	}

	public boolean isEmpty() {
		return cutCards().isEmpty() && cards.values().stream().allMatch(List::isEmpty);
	}

	public Map<CardInstance, AtomicInteger> printHisto(Zone zone) {
		Map<Card.Print, AtomicInteger> tmp = new LinkedHashMap<>();
		Map<CardInstance, AtomicInteger> replace = new HashMap<>();

		for (CardInstance ci : cards(zone)) {
			AtomicInteger i = tmp.get(ci.print());
			if (i == null) {
				i = new AtomicInteger();
				tmp.put(ci.print(), i);
				replace.put(ci, i);
			}
			i.incrementAndGet();
		}

		return replace;
	}

	public Map<CardInstance, AtomicInteger> cardHisto(Zone zone) {
		Map<Card, AtomicInteger> tmp = new LinkedHashMap<>();
		Map<CardInstance, AtomicInteger> replace = new HashMap<>();

		for (CardInstance ci : cards(zone)) {
			AtomicInteger i = tmp.get(ci.card());
			if (i == null) {
				i = new AtomicInteger();
				tmp.put(ci.card(), i);
				replace.put(ci, i);
			}
			i.incrementAndGet();
		}

		return replace;
	}
}
