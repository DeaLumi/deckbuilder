package emi.mtg.deckbuilder.model;

import emi.lib.mtg.card.Card;
import emi.lib.mtg.game.Deck;
import emi.lib.mtg.game.Zone;

import java.util.*;
import java.util.stream.Collectors;

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

	public String name;
	public String author;
	public String description;
	public Map<Zone, List<CardInstance>> cards;
	public List<CardInstance> sideboard;

	private transient final CardInstanceListWrapper sideboardWrapper;

	public DeckList() {
		this("<No Name>", "<No Author>", "<No Description>", new EnumMap<>(Zone.class), new ArrayList<>());
	}

	public DeckList(String name, String author, String description, Map<Zone, List<CardInstance>> cards, List<CardInstance> sideboard) {
		this.name = name;
		this.author = author;
		this.description = description;
		this.cards = cards;
		this.sideboard = sideboard;
		this.sideboardWrapper = new CardInstanceListWrapper(this.sideboard);
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
	public String description() {
		return description;
	}

	@Override
	public Map<Zone, List<? extends Card>> cards() {
		return new AbstractMap<Zone, List<? extends Card>>() {
			@Override
			public Set<Entry<Zone, List<? extends Card>>> entrySet() {
				return cards.entrySet().stream()
						.map(e -> new Map.Entry<Zone, List<? extends Card>>() {
							CardInstanceListWrapper wrapped = new CardInstanceListWrapper(e.getValue());

							@Override
							public Zone getKey() {
								return e.getKey();
							}

							@Override
							public List<? extends Card> getValue() {
								return wrapped;
							}

							@Override
							public List<? extends Card> setValue(List<? extends Card> value) {
								throw new UnsupportedOperationException();
							}
						}).collect(Collectors.toSet());
			}
		};
	}

	@Override
	public List<? extends Card> sideboard() {
		return this.sideboardWrapper;
	}
}
