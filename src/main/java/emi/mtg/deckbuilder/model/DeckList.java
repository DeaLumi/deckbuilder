package emi.mtg.deckbuilder.model;

import com.sun.javafx.collections.ObservableListWrapper;
import com.sun.javafx.collections.ObservableMapWrapper;
import emi.lib.mtg.card.Card;
import emi.lib.mtg.game.Deck;
import emi.lib.mtg.game.Format;
import emi.lib.mtg.game.Zone;
import javafx.collections.MapChangeListener;
import javafx.collections.ObservableList;
import javafx.collections.ObservableMap;

import java.util.*;

public class DeckList implements Deck, MapChangeListener<Zone, ObservableList<CardInstance>> {
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

	public String name;
	public Format format;
	public String author;
	public String description;
	public ObservableMap<Zone, ObservableList<CardInstance>> cards;
	public ObservableList<CardInstance> sideboard;

	private transient final Map<Zone, CardInstanceListWrapper> cardsWrapper;
	private transient final CardInstanceListWrapper sideboardWrapper;

	public DeckList() {
		this("<No Name>", "<No Author>", null, "<No Description>", new ObservableMapWrapper<>(new EnumMap<>(Zone.class)), new ObservableListWrapper<>(new ArrayList<>()));
	}

	public DeckList(String name, String author, Format format, String description, ObservableMap<Zone, ObservableList<CardInstance>> cards, ObservableList<CardInstance> sideboard) {
		this.name = name;
		this.author = author;
		this.format = format;
		this.description = description;
		this.cards = cards;
		this.sideboard = sideboard;
		this.sideboardWrapper = new CardInstanceListWrapper(this.sideboard);
		this.cardsWrapper = new HashMap<>();

		for (Zone zone : Zone.values()) {
			this.cardsWrapper.put(zone, new CardInstanceListWrapper(cards.computeIfAbsent(zone, z -> new ObservableListWrapper<>(new ArrayList<>()))));
		}

		cards.addListener(this);
	}

	@Override
	public void onChanged(Change<? extends Zone, ? extends ObservableList<CardInstance>> change) {
		if (change.wasRemoved()) {
			this.cardsWrapper.remove(change.getKey());
		}

		if (change.wasAdded()) {
			this.cardsWrapper.put(change.getKey(), new CardInstanceListWrapper(change.getValueAdded()));
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
