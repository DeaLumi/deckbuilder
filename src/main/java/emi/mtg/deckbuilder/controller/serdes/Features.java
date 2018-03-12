package emi.mtg.deckbuilder.controller.serdes;

public enum Features {
	DeckName("Deck Name"),
	Author("Author"),
	Description("Description"),
	Format("Intended deck format"),
	CardArt("Specific card printings"),
	OtherZones("Zones (beyond library & sideboard)"),
	Variants("Deck variants (only current will be exported)"),
	Export("Exporting"),
	Import("Importing");

	private final String description;

	Features(String description) {
		this.description = description;
	}

	@Override
	public String toString() {
		return description;
	}
}
