package emi.mtg.deckbuilder.model;

import emi.lib.mtg.game.Format;
import emi.lib.mtg.game.Zone;
import emi.mtg.deckbuilder.view.components.CardView;
import emi.mtg.deckbuilder.view.groupings.CardTypeGroup;
import emi.mtg.deckbuilder.view.groupings.Rarity;

import java.net.URI;
import java.util.*;

public class Preferences {
	public HashMap<String, UUID> preferredPrintings = new HashMap<>();
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
}
