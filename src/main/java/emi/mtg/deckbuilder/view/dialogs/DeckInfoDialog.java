package emi.mtg.deckbuilder.view.dialogs;

import emi.lib.mtg.game.Format;
import emi.mtg.deckbuilder.model.DeckList;
import emi.mtg.deckbuilder.model.Preferences;
import emi.mtg.deckbuilder.view.util.FxUtils;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.control.*;
import javafx.stage.Window;

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

	public DeckInfoDialog(Window owner, DeckList deck) {
		setTitle("Deck Info");

		FxUtils.FXML(this, getDialogPane());
		getDialogPane().setStyle(Preferences.get().theme.style());
		initOwner(owner);

		deckNameField.setText(deck.nameProperty().getValue());
		authorField.setText(deck.authorProperty().getValue());
		descriptionField.setText(deck.descriptionProperty().getValue());

		formatCombo.getItems().setAll(Format.values());
		formatCombo.getSelectionModel().select(deck.formatProperty().getValue());

		setResultConverter(bt -> {
			if (bt.equals(ButtonType.OK)) {
				boolean modified = false;

				if (!deck.name().equals(deckNameField.getText())) {
					modified = true;
					deck.nameProperty().setValue(deckNameField.getText());
				}

				if (!deck.author().equals(authorField.getText())) {
					modified = true;
					deck.authorProperty().setValue(authorField.getText());
				}

				if (!deck.format().equals(formatCombo.getValue())) {
					modified = true;
					deck.formatProperty().setValue(formatCombo.getValue());
				}

				if (!deck.description().equals(descriptionField.getText())) {
					modified = true;
					deck.descriptionProperty().setValue(descriptionField.getText());
				}

				return modified;
			} else {
				return false;
			}
		});
	}
}
