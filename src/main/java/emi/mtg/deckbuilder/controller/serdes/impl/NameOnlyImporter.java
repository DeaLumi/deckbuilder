package emi.mtg.deckbuilder.controller.serdes.impl;

import emi.lib.mtg.Card;
import emi.lib.mtg.DataSource;
import emi.lib.mtg.game.Format;
import emi.lib.mtg.game.Zone;
import emi.mtg.deckbuilder.controller.Context;
import emi.mtg.deckbuilder.controller.serdes.DeckImportExport;
import emi.mtg.deckbuilder.model.CardInstance;
import emi.mtg.deckbuilder.model.DeckList;

import java.io.IOException;
import java.util.*;

public abstract class NameOnlyImporter implements DeckImportExport {
	protected static Card findCard(DataSource data, Map<String, Card> cache, Map<String, Card> fullCache, String name) {
		Card card = cache.get(name);
		if (card == null) {
			card = fullCache.get(name);
		}

		if (card == null) {
			for (Card candidate : data.cards()) {
				if (cache.containsKey(candidate.name())) {
					continue;
				}

				cache.put(candidate.name(), candidate);
				fullCache.put(candidate.fullName(), candidate);

				if (candidate.name().equals(name) || candidate.fullName().equals(name)) {
					card = candidate;
					break;
				}
			}
		}

		return card;
	}

	private final Map<String, Card> cache, fullCache;
	protected final Map<Zone, List<CardInstance>> deckCards;
	protected Zone zone;
	protected String name, author, description;
	protected Format format;

	protected NameOnlyImporter() {
		this.cache = new HashMap<>();
		this.fullCache = new HashMap<>();
		this.deckCards = new EnumMap<>(Zone.class);
		this.zone = Zone.Library;
		this.name = "";
		this.author = "";
		this.description = "";
		this.format = null;
	}

	protected void name(String name) {
		this.name = name;
	}

	protected void author(String author) {
		this.author = author;
	}

	protected void description(String description) {
		this.description = description;
	}

	protected void format(Format format) {
		this.format = format;
	}

	protected void beginImport() {
		this.deckCards.clear();
		this.zone = Zone.Library;
		this.name = "";
		this.author = "";
		this.description = "";
		this.format = null;
	}

	protected void beginZone(Zone zone) {
		this.zone = zone;
		this.deckCards.put(zone, new ArrayList<>());
	}

	protected void addCard(String name) throws IOException {
		addCard(name, 1);
	}

	protected void addCard(String name, int quantity) throws IOException {
		Card card = findCard(Context.get().data, cache, fullCache, name);

		if (card == null) {
			throw new IOException(String.format("The deck refers to an unidentifiable card \"%s\"", name));
		}

		Card.Printing pr = Context.get().preferences.anyPrinting(card);
		for (int k = 0; k < quantity; ++k) {
			deckCards.get(zone).add(new CardInstance(pr));
		}
	}

	protected void endZone() {
		if (deckCards.get(zone).isEmpty()) {
			deckCards.remove(zone);
		}
	}

	protected DeckList completeImport() {
		DeckList deck = new DeckList(name, author, format != null ? format : Format.Freeform, description, this.deckCards);

		if (format == null) {
			Format best = Format.Freeform;
			float bestErrors = Integer.MAX_VALUE;
			for (Format candidate : Format.values()) {
				if (!candidate.deckZones().containsAll(deckCards.keySet())) continue;

				Format.ValidationResult res = candidate.validate(deck);
				int errors = res.deckErrors.size() +
						res.zoneErrors.values().stream().mapToInt(Set::size).sum() +
						res.cards.values().stream().mapToInt(cr -> cr.errors.size()).sum();

				if (errors < bestErrors) {
					best = candidate;
					bestErrors = errors;
				}
			}

			deck.formatProperty().setValue(best);
		}

		return deck;
	}
}
