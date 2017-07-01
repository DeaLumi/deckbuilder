package emi.mtg.deckbuilder.model;

import java.util.HashSet;
import java.util.Set;

public class DeckList {

	public final String name;
	public final Set<CardInstance> cards;

	public DeckList(String name) {
		this.name = name;
		this.cards = new HashSet<>();
	}
}
