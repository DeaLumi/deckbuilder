package org.whitefoxy.mtg.deckbuilder.model;

import java.util.HashSet;
import java.util.Set;

/**
 * Created by Emi on 5/20/2017.
 */
public class DeckList {

	public final String name;
	public final Set<CardInstance> cards;

	public DeckList(String name) {
		this.name = name;
		this.cards = new HashSet<>();
	}
}
