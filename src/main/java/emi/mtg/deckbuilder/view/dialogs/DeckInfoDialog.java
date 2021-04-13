package emi.mtg.deckbuilder.view.dialogs;

import emi.lib.mtg.game.Format;
import emi.mtg.deckbuilder.model.DeckList;
import emi.mtg.deckbuilder.model.Preferences;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.control.*;

import java.io.IOException;

public class DeckInfoDialog extends Dialog<Boolean> {
	@FXML
	private TextField deckNameField;

	@FXML
	private TextField authorField;

	@FXML
	private ComboBox<Format> formatCombo;

	@FXML
	private TextArea descriptionField;

	public DeckInfoDialog(DeckList deck) throws IOException {
		setTitle("Deck Info");

		FXMLLoader loader = new FXMLLoader(getClass().getResource("DeckInfoDialog.fxml"));
		loader.setController(this);
		loader.setRoot(getDialogPane());
		loader.load();
		getDialogPane().setStyle("-fx-base: " + Preferences.get().theme.baseHex());

		deckNameField.setText(deck.nameProperty().getValue());
		authorField.setText(deck.authorProperty().getValue());
		descriptionField.setText(deck.descriptionProperty().getValue());

		formatCombo.getItems().setAll(Format.values());
		formatCombo.getSelectionModel().select(deck.formatProperty().getValue());

		setResultConverter(bt -> {
			if (bt.equals(ButtonType.OK)) {
				deck.nameProperty().setValue(deckNameField.getText());
				deck.authorProperty().setValue(authorField.getText());
				deck.formatProperty().setValue(formatCombo.getValue());
				deck.descriptionProperty().setValue(descriptionField.getText());
				return true;
			} else {
				return false;
			}
		});
	}
}
