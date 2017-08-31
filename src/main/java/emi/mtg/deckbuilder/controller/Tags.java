package emi.mtg.deckbuilder.controller;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonToken;
import com.google.gson.stream.JsonWriter;
import emi.lib.mtg.Card;
import emi.lib.mtg.DataSource;

import java.io.*;
import java.util.*;
import java.util.stream.Collectors;

public class Tags {
	private static Gson gson = new GsonBuilder()
			.setPrettyPrinting()
			.create();

	private NavigableMap<String, Set<Card>> cardsMap;

	public Tags() {
		this.cardsMap = new TreeMap<>();
	}

	public SortedSet<String> tags() {
		return cardsMap.navigableKeySet();
	}

	public Set<String> tags(Card card) {
		return cardsMap.entrySet().stream()
				.filter(e -> e.getValue().contains(card))
				.map(Map.Entry::getKey)
				.collect(Collectors.toSet());
	}

	public Set<Card> cards(String tag) {
		return cardsMap.computeIfAbsent(tag, t -> new HashSet<>());
	}

	public void add(Card card, String tag) {
		cardsMap.computeIfAbsent(tag, t -> new HashSet<>()).add(card);
	}

	public void remove(Card card, String tag) {
		if (cardsMap.containsKey(tag)) {
			cardsMap.get(tag).remove(card);
		}
	}

	public void load(DataSource data, File from) throws IOException {
		FileInputStream fis = new FileInputStream(from);
		JsonReader reader = gson.newJsonReader(new InputStreamReader(fis));

		this.cardsMap.clear();

		reader.beginObject();
		while (reader.peek() == JsonToken.NAME) {
			String tag = reader.nextName();

			Set<Card> cards = this.cardsMap.computeIfAbsent(tag, c -> new HashSet<>());

			reader.beginArray();
			while (reader.peek() == JsonToken.STRING) {
				String cardName = reader.nextString();
				Card card = data.card(cardName);

				if (card == null) {
					System.err.println("Warning: Tags file " + from.getAbsolutePath() + " refers to unknown card " + cardName + " -- are we in the right universe?");
					System.err.flush();
					continue;
				}

				cards.add(card);
			}
			reader.endArray();

			System.out.flush();
		}
		reader.endObject();

		reader.close();
	}

	public void save(File to) throws IOException {
		FileOutputStream fis = new FileOutputStream(to);
		JsonWriter writer = new Gson().newJsonWriter(new OutputStreamWriter(fis));

		writer.beginObject();
		for (Map.Entry<String, Set<Card>> tagsEntry : cardsMap.entrySet()) {
			writer.name(tagsEntry.getKey());

			writer.beginArray();
			for (Card tag : tagsEntry.getValue()) {
				writer.value(tag.name());
			}
			writer.endArray();
		}
		writer.endObject();

		writer.close();
	}
}
