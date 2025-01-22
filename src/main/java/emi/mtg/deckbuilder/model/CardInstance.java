package emi.mtg.deckbuilder.model;

import com.google.gson.annotations.SerializedName;
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
	@SerializedName(value="print", alternate={ "printing" })
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
		StringBuilder builder = new StringBuilder(256);

		if (Preferences.get().cardInfoTooltips) {
			boolean first = true;

			for (Card.Face face : card().faces()) {
				if (first) {
					first = false;
				} else {
					builder.append("\n\n//\n\n");
				}

				builder.append(face.name());
				if (!face.manaCost().isEmpty()) builder.append(' ').append(face.manaCost().toString());
				builder.append(" (").append(face.manaValue()).append(')');
				builder.append('\n').append(face.type());
				if (!face.rules().isEmpty()) builder.append("\n\n").append(face.rules().replaceAll("\n", "\n\n"));
				if (face.ptldBox().isEmpty()) continue;
				builder.append("\n");
				if (!face.printedPower().isEmpty()) builder.append("\nP/T: ").append(face.printedPower()).append('/').append(face.printedToughness());
				if (!face.printedLoyalty().isEmpty()) builder.append("\nStarting Loyalty: ").append(face.printedLoyalty());
				if (!face.printedDefense().isEmpty()) builder.append("\nDefense: ").append(face.printedDefense());
			}

			builder.append("\n\n")
					.append(rarity())
					.append(" #").append(collectorNumber())
					.append(" from ").append(set().name()).append(" (").append(set().code().toUpperCase())
					.append("), var. ").append(variation()).append(promo() ? ", promo" : ", non-promo")
					.append(", released ").append(releaseDate());
		}

		if (Preferences.get().cardTagsTooltips && !tags.isEmpty()) {
			builder.append("\n\nTags: ").append(String.join(", ", tags));
		}

		if (lastValidation != null) {
			builder.append("\n\n").append(lastValidation);
		}

		return builder.toString();
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
