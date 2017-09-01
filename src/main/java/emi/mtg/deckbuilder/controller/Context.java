package emi.mtg.deckbuilder.controller;

import emi.lib.Service;
import emi.lib.mtg.DataSource;
import emi.lib.mtg.game.Format;
import emi.lib.mtg.scryfall.ScryfallDataSource;
import emi.mtg.deckbuilder.model.DeckList;
import emi.mtg.deckbuilder.view.Images;

import java.io.IOException;
import java.util.Map;
import java.util.stream.Collectors;

public class Context {
	public static final Map<String, Format> FORMATS = Service.Loader.load(Format.class).stream()
			.collect(Collectors.toMap(s -> s.string("name"), s -> s.uncheckedInstance()));

	public final DataSource data;
	public final Images images;
	public final Tags tags;

	public DeckList deck;

	public Context() throws IOException {
		this.data = new ScryfallDataSource();
		this.images = new Images();
		this.tags = new Tags(this);
		this.deck = new DeckList();
		this.deck.format = FORMATS.get("Standard");
	}
}
