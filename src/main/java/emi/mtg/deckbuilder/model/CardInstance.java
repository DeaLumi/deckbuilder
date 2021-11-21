package emi.mtg.deckbuilder.model;

import emi.lib.mtg.Card;
import emi.lib.mtg.enums.Rarity;
import emi.lib.mtg.game.Format;
import emi.mtg.deckbuilder.controller.Context;

import java.io.Serializable;
import java.time.LocalDate;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class CardInstance implements Card.Printing, Serializable {
	public enum Flags {
		Unlimited,
		Full,
		Invalid,
		Warning,
		Notice
	}

	public transient final EnumSet<Flags> flags = EnumSet.noneOf(Flags.class);
	public transient Format.ValidationResult.CardResult lastValidation = null;
	private final Set<String> tags = new HashSet<>();
	private Card.Printing printing;

	// Declare this so GSON doesn't nullify flags.
	@SuppressWarnings("unused")
	private CardInstance() {
	}

	public CardInstance(Card.Printing printing) {
		this.printing = printing;
	}

	public CardInstance(CardInstance clone) {
		this(clone.printing);
		tags.addAll(clone.tags);
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
	public Rarity rarity() {
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
	public boolean promo() {
		return printing.promo();
	}

	@Override
	public UUID id() {
		return printing.id();
	}

	@Override
	public LocalDate releaseDate() {
		return printing.releaseDate();
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

	@Override
	public String toString() {
		return printingToString(printing);
	}

	public static String printingToString(Card.Printing pr) {
		return String.format("%s (%s) %s", pr.card().name(), pr.set().code().toUpperCase(), pr.collectorNumber());
	}

	private static final Pattern PRINTING_PATTERN = Pattern.compile("^(?<cardName>[^(]+) \\((?<setCode>[A-Z0-9]+)\\) (?<collectorNumber>.+)$");

	public static Card.Printing stringToPrinting(String str) {
		Matcher matcher = PRINTING_PATTERN.matcher(str);
		if (!matcher.find()) throw new IllegalArgumentException("String does not appear to represent a printing: \"" + str + "\"");

		emi.lib.mtg.Set set = Context.get().data.set(matcher.group("setCode"));
		if (set == null) throw new IllegalArgumentException("While parsing printing \"" + str + "\": Unable to match set code " + matcher.group("setCode") + " -- did the data source change?");

		for (Card.Printing pr : set.printings()) {
			if (!matcher.group("collectorNumber").equals(pr.collectorNumber())) continue;
			if (!matcher.group("cardName").equals(pr.card().name())) continue;
			return pr;
		}

		throw new IllegalArgumentException("While parsing printing \"" + str + "\": Unable to match card name " + matcher.group("cardName") + " and collector number " + matcher.group("collectorNumber") + " -- did the data source change?");
	}
}
