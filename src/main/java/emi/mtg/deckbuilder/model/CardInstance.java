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
	public static TypeAdapter<Card.Printing> createCardAdapter(DataSource data) {
		return new TypeAdapter<Card.Printing>() {
			@Override
			public void write(JsonWriter out, Card.Printing value) throws IOException {
				out.value(value.id().toString());
			}

			@Override
			public Card.Printing read(JsonReader in) throws IOException {
				return data.printing(UUID.fromString(in.nextString()));
			}
		};
	}

	private Card.Printing printing;
	private Set<String> tags;

	public CardInstance(Card.Printing printing, Collection<String> tags) {
		this.printing = printing;
		this.tags = new HashSet<>(tags);
	}

	public CardInstance(Card.Printing printing, String... tags) {
		this(printing, Arrays.asList(tags));
	}

	public Card card() {
		return printing.card();
	}

	public Card.Printing printing() {
		return printing;
	}

	public Set<String> tags() {
		return tags;
	}
}
