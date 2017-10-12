package emi.mtg.deckbuilder.controller.serdes;

import emi.lib.Service;
import emi.mtg.deckbuilder.controller.Context;
import emi.mtg.deckbuilder.model.DeckList;

import java.io.File;
import java.io.IOException;
import java.util.Set;

@Service(Context.class)
@Service.Property.String(name="name")
@Service.Property.String(name="extension")
public interface VariantImportExport {
	DeckList.Variant importVariant(DeckList decklist, File from) throws IOException;

	void exportVariant(DeckList.Variant variant, File to) throws IOException;

	Set<Features> unsupportedFeatures();
}
