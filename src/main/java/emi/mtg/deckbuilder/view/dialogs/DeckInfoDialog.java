package emi.mtg.deckbuilder.view.dialogs;

import emi.lib.mtg.game.Format;
import emi.mtg.deckbuilder.controller.DeckChanger;
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
				String doString = null, undoString = null;

				final String oldName = deck.name(), oldAuthor = deck.author(), oldDesc = deck.description();
				final Format oldFormat = deck.format();

				final String newName = deckNameField.getText(), newAuthor = authorField.getText(), newDesc = descriptionField.getText();
				final Format newFormat = formatCombo.getValue();

				if (!oldName.equals(newName)) {
					doString = modified ? null : String.format("Change Deck Name to %s", newName);
					undoString = modified ? null : String.format("Restore Deck Name to %s", oldName);
					modified = true;
				}

				if (!oldAuthor.equals(newAuthor)) {
					doString = modified ? null : String.format("Change Deck Author to %s", newAuthor);
					undoString = modified ? null : String.format("Restore Deck Author to %s", oldAuthor);
					modified = true;
				}

				if (!oldDesc.equals(newDesc)) {
					doString = modified ? null : "Update Deck Description";
					undoString = modified ? null : "Revert Deck Description";
					modified = true;
				}

				if (!oldFormat.equals(newFormat)) {
					doString = modified ? null : String.format("Change Deck Format to %s", newFormat);
					undoString = modified ? null : String.format("Restore Deck Format to %s", oldFormat);
					modified = true;
				}

				if (modified) {
					DeckChanger.change(
							deck,
							doString != null ? doString : "Update Deck Info",
							undoString != null ? undoString : "Revert Deck Info",
							l -> {
								l.nameProperty().setValue(newName);
								l.authorProperty().setValue(newAuthor);
								l.formatProperty().setValue(newFormat);
								l.descriptionProperty().setValue(newDesc);
							},
							l -> {
								l.nameProperty().setValue(oldName);
								l.authorProperty().setValue(oldAuthor);
								l.formatProperty().setValue(oldFormat);
								l.descriptionProperty().setValue(oldDesc);
							}
					);
				}

				return modified;
			} else {
				return false;
			}
		});
	}
}
