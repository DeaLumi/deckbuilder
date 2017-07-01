package emi.mtg.deckbuilder.controller;

import com.google.gson.Gson;
import emi.mtg.deckbuilder.model.DeckList;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;

public class SerdesControl {
	private final Gson gson;

	public SerdesControl(Gson gson) {
		this.gson = gson;
	}

	public void saveDeck(DeckList list, File to) throws IOException {
		FileWriter writer = new FileWriter(to);
		this.gson.toJson(list, DeckList.class, writer);
		writer.close();
	}

	public DeckList loadDeck(File from) throws IOException {
		FileReader reader = new FileReader(from);
		DeckList deckList = this.gson.fromJson(reader, DeckList.class);
		reader.close();
		return deckList;
	}
}
