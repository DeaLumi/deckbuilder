package emi.mtg.deckbuilder.model;

import emi.lib.mtg.Card;
import emi.lib.mtg.characteristic.CardRarity;
import emi.mtg.deckbuilder.controller.Context;

import java.io.Serializable;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

public class CardInstance implements Card.Printing, Serializable {
	public enum Flags {
		Unlimited,
		Full,
		Invalid
	}

	public transient final EnumSet<Flags> flags = EnumSet.noneOf(Flags.class);
	private final Set<String> tags = new HashSet<>();
	private Card.Printing printing;

	// Declare this so GSON doesn't nullify flags.
	@SuppressWarnings("unused")
	private CardInstance() {
	}

	public CardInstance(Card.Printing printing) {
		this.printing = printing;
	}

	public Card card() {
		return printing.card();
	}

	public Card.Printing printing() {
		return printing;
	}

	public void printing(Card.Printing printing) {
		this.printing = printing;
	}

	@Override
	public Set<? extends Face> faces() {
		return printing.faces();
	}

	@Override
	public Face face(Card.Face.Kind kind) {
		return printing.face(kind);
	}

	@Override
	public emi.lib.mtg.Set set() {
		return printing.set();
	}

	@Override
	public CardRarity rarity() {
		return printing.rarity();
	}

	@Override
	public Integer multiverseId() {
		return printing.multiverseId();
	}

	@Override
	public int variation() {
		return printing.variation();
	}

	@Override
	public String collectorNumber() {
		return printing.collectorNumber();
	}

	@Override
	public Integer mtgoCatalogId() {
		return printing.mtgoCatalogId();
	}

	@Override
	public UUID id() {
		return printing.id();
	}

	public void refreshInstance() {
		Card.Printing pr = Context.get().data.printing(printing.id());
		if (pr != null && pr != printing) {
			printing = pr;
		}
	}

	public Set<String> tags() {
		return tags;
	}
}
