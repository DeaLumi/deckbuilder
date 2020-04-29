package emi.mtg.deckbuilder.view.dialogs;

import emi.lib.mtg.game.Format;
import emi.mtg.deckbuilder.model.Preferences;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.control.*;

import java.io.IOException;
import java.net.URI;

public class PreferencesDialog extends Dialog<Boolean> {
	@FXML
	private CheckBox collapseDuplicates;

	@FXML
	private TextField authorName;

	@FXML
	private ComboBox<Format> defaultFormat;

	@FXML
	private CheckBox theFutureIsNow;

	@FXML
	private CheckBox autoUpdateData;

	@FXML
	private CheckBox autoUpdateProgram;

	@FXML
	private TextField updateUrlField;

	public PreferencesDialog(Preferences prefs) throws IOException {
		setTitle("Deck Builder Preferences");

		FXMLLoader loader = new FXMLLoader(getClass().getResource("PreferencesDialog.fxml"));
		loader.setControllerFactory(x -> this);
		loader.setRoot(getDialogPane());
		loader.load();

		collapseDuplicates.setSelected(prefs.collapseDuplicates);
		authorName.setText(prefs.authorName);
		defaultFormat.getItems().setAll(Format.values());
		defaultFormat.getSelectionModel().select(prefs.defaultFormat);
		theFutureIsNow.setSelected(prefs.theFutureIsNow);

		autoUpdateData.setSelected(prefs.autoUpdateData);
		autoUpdateProgram.setSelected(prefs.autoUpdateProgram);
		updateUrlField.setText(prefs.updateUri.toString());
		updateUrlField.disableProperty().bind(autoUpdateProgram.selectedProperty().not());

		setResultConverter(bt -> {
			if (bt.equals(ButtonType.OK)) {
				prefs.collapseDuplicates = collapseDuplicates.isSelected();
				prefs.authorName = authorName.getText();
				prefs.defaultFormat = defaultFormat.getValue();
				prefs.theFutureIsNow = theFutureIsNow.isSelected();

				prefs.autoUpdateData = autoUpdateData.isSelected();
				prefs.autoUpdateProgram = autoUpdateProgram.isSelected();
				prefs.updateUri = URI.create(updateUrlField.getText());

				return true;
			} else {
				return false;
			}
		});
	}
}
