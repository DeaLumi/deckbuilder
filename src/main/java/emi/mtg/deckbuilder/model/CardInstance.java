package emi.mtg.deckbuilder.model;

import emi.lib.mtg.Card;
import emi.lib.mtg.enums.Rarity;
import emi.lib.mtg.game.Format;
import emi.mtg.deckbuilder.controller.Context;

import java.io.Serializable;
import java.time.LocalDate;
import java.util.*;
import java.util.stream.Collectors;

public class CardInstance implements Card.Print, Serializable {
	public enum Flags {
		Unlimited,
		Full,
		Invalid,
		Warning,
		Notice
	}

	public transient final EnumSet<Flags> flags = EnumSet.noneOf(Flags.class);
	public transient Format.Validator.Result.CardResult lastValidation = null;
	private final Set<String> tags = new HashSet<>();
	private Card.Print print;

	// Declare this so GSON doesn't nullify flags.
	@SuppressWarnings("unused")
	private CardInstance() {
	}

	public CardInstance(Card.Print print) {
		this.print = print;
	}

	public CardInstance(CardInstance clone) {
		this(clone.print);
		tags.addAll(clone.tags);
	}

	public Card card() {
		return print.card();
	}

	public Card.Print print() {
		return print;
	}

	public void print(Card.Print printing) {
		this.print = printing;
	}

	@Override
	public Set<? extends Face> faces() {
		return print.faces();
	}

	@Override
	public Set<? extends Face> mainFaces() {
		return print.mainFaces();
	}

	@Override
	public Set<? extends Face> faces(Card.Face face) {
		return print.faces(face);
	}

	@Override
	public emi.lib.mtg.Set set() {
		return print.set();
	}

	@Override
	public Rarity rarity() {
		return print.rarity();
	}

	@Override
	public Integer multiverseId() {
		return print.multiverseId();
	}

	@Override
	public int variation() {
		return print.variation();
	}

	@Override
	public String collectorNumber() {
		return print.collectorNumber();
	}

	@Override
	public Integer mtgoCatalogId() {
		return print.mtgoCatalogId();
	}

	@Override
	public boolean promo() {
		return print.promo();
	}

	@Override
	public Treatment treatment() {
		return print.treatment();
	}

	@Override
	public UUID id() {
		return print.id();
	}

	@Override
	public LocalDate releaseDate() {
		return print.releaseDate();
	}

	public void refreshInstance() {
		Card.Print pr = Context.get().data.print(print.id());
		if (pr != null && pr != print) {
			print = pr;
		}
	}

	public Set<String> tags() {
		return tags;
	}

	@Override
	public String toString() {
		return Card.Print.Reference.format(this);
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

			sections.add(rarity().toString() + " #" +  collectorNumber() + " from " + set().name() + " (" + set().code().toUpperCase() + ")");
		}

		if (Preferences.get().cardTagsTooltips && !tags.isEmpty()) {
			sections.add("Tags: " + String.join(", ", tags));
		}

		if (lastValidation != null) {
			sections.add(lastValidation.toString());
		}

		return String.join("\n\n", sections);
	}

	public static Card.Print stringToPrint(String str) {
		Card.Print.Reference ref = Card.Print.Reference.valueOf(str);

		emi.lib.mtg.Set set = Context.get().data.set(ref.setCode());
		if (set == null) throw new IllegalArgumentException("While parsing printing \"" + str + "\": Unable to match set code " + ref.setCode() + " -- did the data source change?");

		Card.Print pr = set.print(ref.collectorNumber());
		if (pr == null) throw new IllegalArgumentException("While parsing printing \"" + str + "\": Set " + set.name() + " contains no print with collector number " + ref.collectorNumber() + " -- did the data source change?");
		if (!ref.name().equals(pr.card().name())) throw new IllegalArgumentException("While parsing printing \"" + str + "\": Collector number " + ref.collectorNumber() + " matches " + pr.card().fullName() + ", not " + ref.name() + " -- did the data source change?");
		return pr;
	}
}
