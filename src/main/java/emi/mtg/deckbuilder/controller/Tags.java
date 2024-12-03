package emi.mtg.deckbuilder.controller;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonToken;
import com.google.gson.stream.JsonWriter;
import emi.lib.mtg.Card;
import emi.mtg.deckbuilder.view.MainApplication;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.stream.Collectors;

public class Tags {
	private static Gson gson = new GsonBuilder()
			.setPrettyPrinting()
			.create();

	private final Context context;
	private final NavigableMap<String, Set<Card>> cardsMap;

	public Tags(Context context) {
		this.context = context;
		this.cardsMap = Collections.synchronizedNavigableMap(new TreeMap<>());
	}

	public SortedSet<String> tags() {
		return cardsMap.navigableKeySet();
	}

	public Set<String> tags(Card card) {
		synchronized (cardsMap) {
			return cardsMap.entrySet().stream()
					.filter(e -> e.getValue().contains(card))
					.map(Map.Entry::getKey)
					.collect(Collectors.toSet());
		}
	}

	public Set<Card> cards(String tag) {
		return cardsMap.computeIfAbsent(tag, t -> new HashSet<>());
	}

	public void add(Card card, String tag) {
		cardsMap.computeIfAbsent(tag, t -> new HashSet<>()).add(card);
	}

	public void add(String tag) {
		cardsMap.putIfAbsent(tag, new HashSet<>());
	}

	public void remove(Card card, String tag) {
		if (cardsMap.containsKey(tag)) {
			cardsMap.get(tag).remove(card);
		}
	}

	public void load(Path from) throws IOException {
		JsonReader reader = gson.newJsonReader(Files.newBufferedReader(from));

		this.cardsMap.clear();

		reader.beginObject();
		while (reader.peek() == JsonToken.NAME) {
			String tag = reader.nextName();

			Set<Card> cards = this.cardsMap.computeIfAbsent(tag, c -> new HashSet<>());

			reader.beginArray();
			while (reader.peek() == JsonToken.STRING) {
				String cardName = reader.nextString();
				Collection<Card> namedCards = context.data.cards().stream().filter(c -> c.name().equals(cardName)).collect(Collectors.toSet());

				if (namedCards.isEmpty()) {
					MainApplication.LOG.err("Warning: Tags file %s refers to unknown card %s -- are we in the right universe?", from, cardName);
				}

				cards.addAll(namedCards);
			}
			reader.endArray();
		}
		reader.endObject();

		reader.close();
	}

	public void save(Path to) throws IOException {
		JsonWriter writer = gson.newJsonWriter(Files.newBufferedWriter(to));

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
