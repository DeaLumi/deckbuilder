package emi.mtg.deckbuilder.model;

import emi.lib.mtg.game.Format;
import emi.lib.mtg.game.impl.formats.Standard;

import java.net.URI;
import java.util.HashMap;
import java.util.UUID;

public class Preferences {
	public HashMap<String, UUID> preferredPrintings = new HashMap<>();
	public Format defaultFormat = new Standard();
	public URI updateUri;

	public boolean autoUpdateData = true;
	public boolean autoUpdateProgram = false;
}
