package emi.mtg.deckbuilder.model;

import emi.lib.mtg.card.Card;

import java.io.Serializable;

/**
 * Created by Emi on 6/15/2017.
 */
public class CardInstance implements Serializable {
	public final Card card;

	public CardInstance(Card card) {
		this.card = card;
	}
}
