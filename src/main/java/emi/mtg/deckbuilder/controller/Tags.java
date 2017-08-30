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

	private Map<Card, Set<String>> tagsMap;

	public Tags() {
		this.tagsMap = new HashMap<>();
	}

	public SortedSet<String> tags() {
		return tagsMap.values().stream()
				.collect(TreeSet::new, Set::addAll, Set::addAll);
	}

	public Set<String> tags(Card card) {
		return tagsMap.getOrDefault(card, Collections.emptySet());
	}

	public Set<Card> cards(String tag) {
		return tagsMap.entrySet().stream()
				.filter(e -> e.getValue().contains(tag))
				.map(Map.Entry::getKey)
				.collect(Collectors.toSet());
	}

	public void load(DataSource data, File from) throws IOException {
		FileInputStream fis = new FileInputStream(from);
		JsonReader reader = gson.newJsonReader(new InputStreamReader(fis));

		this.tagsMap.clear();

		reader.beginObject();
		while (reader.peek() == JsonToken.NAME) {
			String cardName = reader.nextName();
			Card card = data.card(cardName);

			if (card == null) {
				System.err.println("Warning: Tags file " + from.getAbsolutePath() + " refers to unknown card " + cardName + " -- are we in the right universe?");
				System.err.flush();
				continue;
			}

			Set<String> tags = this.tagsMap.computeIfAbsent(card, c -> new HashSet<>());

			System.out.print("Tagging " + card.fullName() + " with ");

			reader.beginArray();
			while (reader.peek() == JsonToken.STRING) {
				String n = reader.nextString();
				tags.add(n);
				System.out.print(n + ", ");
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
		for (Map.Entry<Card, Set<String>> tagsEntry : tagsMap.entrySet()) {
			writer.name(tagsEntry.getKey().name());

			writer.beginArray();
			for (String tag : tagsEntry.getValue()) {
				writer.value(tag);
			}
			writer.endArray();
		}
		writer.endObject();

		writer.close();
	}
}
