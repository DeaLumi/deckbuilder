package emi.mtg.deckbuilder.controller;

import emi.lib.mtg.DataSource;
import emi.lib.mtg.game.Format;
import emi.lib.mtg.scryfall.ScryfallDataSource;
import emi.mtg.deckbuilder.model.DeckList;
import emi.mtg.deckbuilder.view.Images;

import java.io.IOException;

public class Context {
	public final DataSource data;
	public final Images images;
	public final Tags tags;

	public Format format;
	public DeckList deck;

	public Context() throws IOException {
		this.data = new ScryfallDataSource();
		this.images = new Images();
		this.tags = new Tags(this);
		this.format = null;
	}
}
