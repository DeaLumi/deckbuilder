package emi.mtg.deckbuilder.model;

import com.google.gson.TypeAdapter;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonWriter;
import emi.lib.mtg.card.Card;
import emi.lib.mtg.data.CardSource;

import java.io.IOException;
import java.io.Serializable;
import java.util.UUID;

/**
 * Created by Emi on 6/15/2017.
 */
public class CardInstance implements Serializable {
	public static TypeAdapter<Card> createCardAdapter(CardSource cards) {
		return new TypeAdapter<Card>() {
			@Override
			public void write(JsonWriter out, Card value) throws IOException {
				out.value(value.id().toString());
			}

			@Override
			public Card read(JsonReader in) throws IOException {
				return cards.get(UUID.fromString(in.nextString()));
			}
		};
	}

	public final Card card;

	public CardInstance(Card card) {
		this.card = card;
	}
}
