package emi.mtg.deckbuilder.controller;

import emi.lib.Service;
import emi.lib.mtg.data.CardSource;
import emi.mtg.deckbuilder.model.DeckList;
import javafx.stage.FileChooser;

import java.io.File;
import java.io.IOException;

@Service(CardSource.class)
@Service.Property.String(name="name")
@Service.Property.String(name="extension")
public interface DeckImportExport {

	DeckList importDeck(File from) throws IOException;

	void exportDeck(DeckList deck, File to) throws IOException;

}
