package emi.mtg.deckbuilder.model;

import emi.lib.mtg.Card;
import emi.lib.mtg.game.Format;
import emi.lib.mtg.game.Zone;
import emi.mtg.deckbuilder.view.components.CardView;
import emi.mtg.deckbuilder.view.groupings.CardTypeGroup;
import emi.mtg.deckbuilder.view.groupings.Rarity;

import java.net.URI;
import java.util.*;
import java.util.stream.Stream;

public class Preferences {
	public Format defaultFormat = Format.Standard;
	public URI updateUri = URI.create("http://emi.sly.io/deckbuilder-nodata.zip");

	public boolean autoUpdateData = true;
	public boolean autoUpdateProgram = true;

	public boolean theFutureIsNow = true;

	public String authorName = "";

	public CardView.Grouping collectionGrouping = Rarity.INSTANCE;
	public List<CardView.ActiveSorting> collectionSorting = CardView.DEFAULT_COLLECTION_SORTING;
	public Map<Zone, CardView.Grouping> zoneGroupings = Collections.singletonMap(Zone.Command, CardTypeGroup.INSTANCE);

	public boolean collapseDuplicates = true;

	// N.B. Preferences get loaded *before* card data, so we can't reference Card.Printings here.
	public HashMap<String, UUID> preferredPrintings = new HashMap<>();

	public enum PreferAge {
		Any,
		Newest,
		Oldest
	};

	public PreferAge preferAge = PreferAge.Any;
	public boolean preferNotPromo = true;
	public boolean preferPhysical = true;

	public Card.Printing preferredPrinting(Card card) {
		Card.Printing preferred = card.printing(preferredPrintings.get(card.fullName()));

		if (preferred != null) return preferred;

		Stream<? extends Card.Printing> stream = card.printings().stream();

		if (preferNotPromo) stream = stream.filter(pr -> !pr.promo());
		if (preferPhysical) stream = stream.filter(pr -> !pr.set().digital());
		if (preferAge != PreferAge.Any) stream = stream.sorted(preferAge == PreferAge.Newest ?
				(a1, a2) -> a2.set().releaseDate().compareTo(a1.set().releaseDate()) :
				(a1, a2) -> a1.set().releaseDate().compareTo(a2.set().releaseDate()));

		return stream.findFirst().orElse(null);
	}

	public Card.Printing anyPrinting(Card card) {
		Card.Printing preferred = preferredPrinting(card);
		if (preferred == null) preferred = card.printings().iterator().next();

		return preferred;
	}
}
