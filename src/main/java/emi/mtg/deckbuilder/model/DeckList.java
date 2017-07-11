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

	private String name;
	private String author;
	private String description;
	private Map<Zone, List<CardInstance>> cards;
	private List<CardInstance> sideboard;

	private transient final CardInstanceListWrapper sideboardWrapper;

	public DeckList() {
		this("<No Name>", "<No Author>", "<No Description>");
	}

	public DeckList(String name, String author, String description) {
		this.name = name;
		this.author = author;
		this.description = description;
		this.cards = new EnumMap<>(Zone.class);
		this.sideboard = new ArrayList<>();
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
	public Map<Zone, List<Card>> cards() {
		return new AbstractMap<Zone, List<Card>>() {
			@Override
			public Set<Entry<Zone, List<Card>>> entrySet() {
				return cards.entrySet().stream()
						.map(e -> new Map.Entry<Zone, List<Card>>() {
							CardInstanceListWrapper wrapped = new CardInstanceListWrapper(e.getValue());

							@Override
							public Zone getKey() {
								return e.getKey();
							}

							@Override
							public List<Card> getValue() {
								return wrapped;
							}

							@Override
							public List<Card> setValue(List<Card> value) {
								throw new UnsupportedOperationException();
							}
						}).collect(Collectors.toSet());
			}
		};
	}

	@Override
	public List<Card> sideboard() {
		return this.sideboardWrapper;
	}
}
