package emi.mtg.deckbuilder.model;

import com.google.gson.TypeAdapter;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonWriter;
import emi.lib.mtg.card.CardFace;
import emi.lib.mtg.data.CardSource;

import java.io.IOException;
import java.io.Serializable;
import java.util.*;

public class CardInstance implements Serializable {
	public static TypeAdapter<CardFace> createCardAdapter(CardSource cards) {
		return new TypeAdapter<CardFace>() {
			@Override
			public void write(JsonWriter out, CardFace value) throws IOException {
				out.value(value.id().toString());
			}

			@Override
			public CardFace read(JsonReader in) throws IOException {
				return cards.get(UUID.fromString(in.nextString()));
			}
		};
	}

	private CardFace card;
	private Set<String> tags;

	public CardInstance(CardFace card, Collection<String> tags) {
		this.card = card;
		this.tags = new HashSet<>(tags);
	}

	public CardInstance(CardFace card, String... tags) {
		this(card, Arrays.asList(tags));
	}

	public CardFace card() {
		return card;
	}

	public Set<String> tags() {
		return tags;
	}
}
