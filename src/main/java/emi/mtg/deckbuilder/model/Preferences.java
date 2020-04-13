package emi.mtg.deckbuilder.model;

import emi.lib.mtg.game.Format;

import java.net.URI;
import java.util.HashMap;
import java.util.UUID;

public class Preferences {
	public HashMap<String, UUID> preferredPrintings = new HashMap<>();
	public Format defaultFormat = Format.Standard;
	public URI updateUri = URI.create("http://emi.sly.io/deckbuilder-nodata.zip");

	public boolean autoUpdateData = true;
	public boolean autoUpdateProgram = false;

	public boolean theFutureIsNow = true;

	public String authorName = "";
}
