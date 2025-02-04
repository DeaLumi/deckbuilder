package emi.mtg.deckbuilder.view.dialogs;

import emi.lib.mtg.game.Format;
import emi.mtg.deckbuilder.controller.DeckChanger;
import emi.mtg.deckbuilder.model.DeckList;
import emi.mtg.deckbuilder.model.Preferences;
import emi.mtg.deckbuilder.view.util.FxUtils;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.stage.Window;

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
				String doString = null;

				final String oldName = deck.name(), oldAuthor = deck.author(), oldDesc = deck.description();
				final Format oldFormat = deck.format();

				final String newName = deckNameField.getText(), newAuthor = authorField.getText(), newDesc = descriptionField.getText();
				final Format newFormat = formatCombo.getValue();

				if (!oldName.equals(newName)) {
					doString = modified ? null : "Rename Deck";
					modified = true;
				}

				if (!oldAuthor.equals(newAuthor)) {
					doString = modified ? null : "Change Deck Author";
					modified = true;
				}

				if (!oldDesc.equals(newDesc)) {
					doString = modified ? null : "Update Deck Description";
					modified = true;
				}

				if (!oldFormat.equals(newFormat)) {
					doString = modified ? null : "Change Deck Format";
					modified = true;
				}

				if (modified) {
					DeckChanger.change(
							deck,
							doString != null ? doString : "Update Deck Info",
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
