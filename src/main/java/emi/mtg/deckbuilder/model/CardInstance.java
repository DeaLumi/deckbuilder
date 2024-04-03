package emi.mtg.deckbuilder.model;

import emi.lib.mtg.Card;
import emi.lib.mtg.Mana;
import emi.lib.mtg.TypeLine;
import emi.lib.mtg.enums.Rarity;
import emi.lib.mtg.game.Format;
import emi.mtg.deckbuilder.controller.Context;

import java.io.Serializable;
import java.time.LocalDate;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

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
	public Set<? extends Face> mainFaces() {
		return printing.mainFaces();
	}

	@Override
	public Set<? extends Face> faces(Card.Face face) {
		return printing.faces(face);
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

	public boolean hasTooltip() {
		return Preferences.get().cardInfoTooltips || (Preferences.get().cardTagsTooltips && !tags().isEmpty()) || lastValidation != null;
	}

	public String tooltip() {
		List<String> sections = new ArrayList<>();

		if (Preferences.get().cardInfoTooltips) {
			sections.add(card().faces().stream().map(f -> f.name() + " " + f.manaCost().toString() + " (" + ((int) f.manaValue()) + ")\n" +
					f.type().toString() + "\n\n" +
					f.rules().replaceAll("\n", "\n\n") +
					(f.printedPower().isEmpty() ? (f.printedLoyalty().isEmpty() ? "" : "\n\nStarting Loyalty: " + f.printedLoyalty()) : "\n\nP/T: " + f.printedPower() + "/" + f.printedToughness())
			).collect(Collectors.joining("\n\n//\n\n")));

			sections.add(rarity().toString() + " from " + set().name() + " (" + set().code().toUpperCase() + ")");
		}

		if (Preferences.get().cardTagsTooltips && !tags.isEmpty()) {
			sections.add("Tags: " + String.join(", ", tags));
		}

		if (lastValidation != null) {
			sections.add(lastValidation.toString());
		}

		return String.join("\n\n", sections);
	}

	public PrintingReference reference() {
		return new PrintingReference(this);
	}

	public static class PrintingReference {
		public static final Pattern PATTERN = Pattern.compile("^(?<cardName>[^(]+) \\((?<setCode>[-A-Za-z0-9 ]+)\\) (?<collectorNumber>.+)$");

		public final String cardName, setCode, collectorNumber;

		public static PrintingReference valueOf(String string) {
			Matcher matcher = PATTERN.matcher(string);
			if (!matcher.find()) throw new IllegalArgumentException(String.format("String does not appear to be a printing reference: \"%s\".", string));

			return new PrintingReference(matcher.group("cardName"), matcher.group("setCode"), matcher.group("collectorNumber"));
		}

		public PrintingReference(String cardName, String setCode, String collectorNumber) {
			this.cardName = cardName;
			this.setCode = setCode;
			this.collectorNumber = collectorNumber;
		}

		public PrintingReference(Card.Printing pr) {
			this.cardName = pr.card().name();
			this.setCode = pr.set().code();
			this.collectorNumber = pr.collectorNumber();
		}

		@Override
		public String toString() {
			return String.format("%s (%s) %s", cardName, setCode, collectorNumber);
		}
	}

	public static String printingToString(Card.Printing pr) {
		return new PrintingReference(pr).toString();
	}

	public static Card.Printing stringToPrinting(String str) {
		PrintingReference ref = PrintingReference.valueOf(str);

		emi.lib.mtg.Set set = Context.get().data.set(ref.setCode);
		if (set == null) throw new IllegalArgumentException("While parsing printing \"" + str + "\": Unable to match set code " + ref.setCode + " -- did the data source change?");

		for (Card.Printing pr : set.printings()) {
			if (!ref.collectorNumber.equals(pr.collectorNumber())) continue;
			if (!ref.cardName.equals(pr.card().name())) continue;
			return pr;
		}

		throw new IllegalArgumentException("While parsing printing \"" + str + "\": Unable to match card name " + ref.cardName + " and collector number " + ref.collectorNumber + " -- did the data source change?");
	}
}
