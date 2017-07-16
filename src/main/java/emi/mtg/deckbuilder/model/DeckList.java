package emi.mtg.deckbuilder.model;

import com.google.gson.TypeAdapter;
import com.google.gson.TypeAdapterFactory;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonWriter;
import com.sun.javafx.collections.ObservableListWrapper;
import com.sun.javafx.collections.ObservableMapWrapper;
import emi.lib.mtg.card.Card;
import emi.lib.mtg.game.Deck;
import emi.lib.mtg.game.Format;
import emi.lib.mtg.game.Zone;
import javafx.collections.MapChangeListener;
import javafx.collections.ObservableList;
import javafx.collections.ObservableMap;

import java.io.IOException;
import java.util.*;

public class DeckList implements Deck {
	private static class CardInstanceListWrapper extends AbstractList<Card> {
		private List<CardInstance> backing;

		public CardInstanceListWrapper(List<CardInstance> backing) {
			this.backing = backing;
		}

		@Override
		public Card get(int index) {
			return backing.get(index).card();
		}

		@Override
		public int size() {
			return backing.size();
		}
	}

	public static TypeAdapter<Format> createFormatAdapter(Map<String, Format> formats) {
		return new TypeAdapter<Format>() {
			@Override
			public void write(JsonWriter out, Format value) throws IOException {
				if (value == null) {
					out.nullValue();
				} else {
					out.value(formats.entrySet().stream().filter(e -> e.getValue().equals(value)).map(Map.Entry::getKey).findAny().orElse(null));
				}
			}

			@Override
			public Format read(JsonReader in) throws IOException {
				switch (in.peek()) {
					case STRING:
						return formats.get(in.nextString());
					default:
						return null;
				}
			}
		};
	}

	public String name;
	public Format format;
	public String author;
	public String description;
	public Map<Zone, List<CardInstance>> cards;
	public List<CardInstance> sideboard;

	private transient final Map<Zone, CardInstanceListWrapper> cardsWrapper;
	private transient final CardInstanceListWrapper sideboardWrapper;

	public DeckList() {
		this("", "", null, "", new EnumMap<>(Zone.class), new ArrayList<>());
	}

	public DeckList(String name, String author, Format format, String description, Map<Zone, List<CardInstance>> cards, List<CardInstance> sideboard) {
		this.name = name;
		this.author = author;
		this.format = format;
		this.description = description;
		this.cards = cards;
		this.sideboard = sideboard;
		this.sideboardWrapper = new CardInstanceListWrapper(this.sideboard);

		this.cardsWrapper = new HashMap<>();
		for (Zone zone : Zone.values()) {
			this.cards.computeIfAbsent(zone, z -> new ArrayList<>());
			this.cardsWrapper.put(zone, new CardInstanceListWrapper(this.cards.get(zone)));
		}
	}

	@Override
	public String name() {
		return name;
	}

	@Override
	public String author() {
		return author;
	}

	@Override
	public Format format() {
		return format;
	}

	@Override
	public String description() {
		return description;
	}

	@Override
	public Map<Zone, ? extends List<? extends Card>> cards() {
		return this.cardsWrapper;
	}

	@Override
	public List<? extends Card> sideboard() {
		return this.sideboardWrapper;
	}
}
