package emi.mtg.deckbuilder.view;

import emi.lib.mtg.game.Format;
import emi.mtg.deckbuilder.model.DeckList;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.control.*;

import java.io.IOException;
import java.util.Collection;

public class DeckInfoDialog extends Dialog<Boolean> {
	@FXML
	private TextField deckNameField;

	@FXML
	private TextField authorField;

	@FXML
	private ComboBox<Format> formatCombo;

	@FXML
	private TextArea descriptionField;

	public DeckInfoDialog(Collection<? extends Format> formats, DeckList deck) throws IOException {
		FXMLLoader loader = new FXMLLoader(getClass().getResource("DeckInfoDialog.fxml"));
		loader.setController(this);
		loader.setRoot(getDialogPane());
		loader.load();

		deckNameField.setText(deck.name);
		authorField.setText(deck.author);
		descriptionField.setText(deck.description);

		formatCombo.getItems().setAll(formats);
		formatCombo.getSelectionModel().select(deck.format);

		setResultConverter(bt -> {
			if (bt.equals(ButtonType.OK)) {
				deck.name = deckNameField.getText();
				deck.author = authorField.getText();
				deck.format = formatCombo.getValue();
				deck.description = descriptionField.getText();
				return true;
			} else {
				return false;
			}
		});
	}
}
