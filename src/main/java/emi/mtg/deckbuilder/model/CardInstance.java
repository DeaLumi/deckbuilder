package emi.mtg.deckbuilder.model;

import com.google.gson.TypeAdapter;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonWriter;
import emi.lib.mtg.Card;
import emi.lib.mtg.DataSource;

import java.io.IOException;
import java.io.Serializable;
import java.util.*;

public class CardInstance implements Serializable {

	private Card.Printing printing;

	public CardInstance(Card.Printing printing) {
		this.printing = printing;
	}

	public Card card() {
		return printing.card();
	}

	public Card.Printing printing() {
		return printing;
	}

	public void printing(Card.Printing printing) {
		this.printing = printing;
	}
}
