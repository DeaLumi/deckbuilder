package emi.mtg.deckbuilder.model;

import emi.lib.mtg.game.Format;
import emi.lib.mtg.game.Zone;
import emi.mtg.deckbuilder.view.components.CardView;
import emi.mtg.deckbuilder.view.groupings.CardTypeGroup;
import emi.mtg.deckbuilder.view.groupings.Rarity;

import java.net.URI;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class Preferences {
	public HashMap<String, UUID> preferredPrintings = new HashMap<>();
	public Format defaultFormat = Format.Standard;
	public URI updateUri = URI.create("http://emi.sly.io/deckbuilder-nodata.zip");

	public boolean autoUpdateData = true;
	public boolean autoUpdateProgram = false;

	public boolean theFutureIsNow = true;

	public String authorName = "";

	public CardView.Grouping.Factory collectionGrouping = Rarity.Factory.INSTANCE;
	public Map<Zone, CardView.Grouping.Factory> zoneGroupings = Collections.singletonMap(Zone.Command, CardTypeGroup.Factory.INSTANCE);
}
