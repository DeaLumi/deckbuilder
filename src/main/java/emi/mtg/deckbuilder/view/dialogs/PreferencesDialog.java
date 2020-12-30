package emi.mtg.deckbuilder.view.dialogs;

import emi.lib.mtg.game.Format;
import emi.lib.mtg.game.Zone;
import emi.mtg.deckbuilder.model.Preferences;
import emi.mtg.deckbuilder.view.components.CardView;
import emi.mtg.deckbuilder.view.groupings.ConvertedManaCost;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.control.*;

import java.io.IOException;
import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public class PreferencesDialog extends Dialog<Boolean> {
	@FXML
	private ToggleButton preferOldest;

	@FXML
	private ToggleButton preferNewest;

	@FXML
	private CheckBox preferNotPromo;

	@FXML
	private CheckBox preferPhysical;

	@FXML
	private CheckBox collapseDuplicates;

	@FXML
	private ComboBox<CardView.Grouping> collectionGrouping;

	private List<CardView.ActiveSorting> collectionSorting;

	@FXML
	private ComboBox<CardView.Grouping> libraryGrouping;

	@FXML
	private ComboBox<CardView.Grouping> sideboardGrouping;

	@FXML
	private ComboBox<CardView.Grouping> commandGrouping;

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

		preferOldest.setSelected(prefs.preferAge == Preferences.PreferAge.Oldest);
		preferNewest.setSelected(prefs.preferAge == Preferences.PreferAge.Newest);
		preferNotPromo.setSelected(prefs.preferNotPromo);
		preferPhysical.setSelected(prefs.preferPhysical);

		collectionGrouping.getItems().setAll(CardView.GROUPINGS);
		libraryGrouping.getItems().setAll(CardView.GROUPINGS);
		sideboardGrouping.getItems().setAll(CardView.GROUPINGS);
		commandGrouping.getItems().setAll(CardView.GROUPINGS);

		collapseDuplicates.setSelected(prefs.collapseDuplicates);
		collectionGrouping.getSelectionModel().select(prefs.collectionGrouping);
		collectionSorting = new ArrayList<>(prefs.collectionSorting);
		libraryGrouping.getSelectionModel().select(prefs.zoneGroupings.getOrDefault(Zone.Library, ConvertedManaCost.INSTANCE));
		sideboardGrouping.getSelectionModel().select(prefs.zoneGroupings.getOrDefault(Zone.Sideboard, ConvertedManaCost.INSTANCE));
		commandGrouping.getSelectionModel().select(prefs.zoneGroupings.getOrDefault(Zone.Command, ConvertedManaCost.INSTANCE));
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
				if (preferOldest.isSelected()) {
					prefs.preferAge = Preferences.PreferAge.Oldest;
				} else if (preferNewest.isSelected()) {
					prefs.preferAge = Preferences.PreferAge.Newest;
				} else {
					prefs.preferAge = Preferences.PreferAge.Any;
				}
				prefs.preferNotPromo = preferNotPromo.isSelected();
				prefs.preferPhysical = preferPhysical.isSelected();

				prefs.collapseDuplicates = collapseDuplicates.isSelected();
				prefs.collectionGrouping = collectionGrouping.getValue();
				prefs.collectionSorting = collectionSorting;
				prefs.zoneGroupings.put(Zone.Library, libraryGrouping.getValue());
				prefs.zoneGroupings.put(Zone.Sideboard, sideboardGrouping.getValue());
				prefs.zoneGroupings.put(Zone.Command, commandGrouping.getValue());
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

	@FXML
	protected void configureCollectionSorting() {
		SortDialog sort = new SortDialog(collectionSorting);
		sort.initOwner(this.getOwner());
		Optional<List<CardView.ActiveSorting>> result = sort.showAndWait();

		if (result.isPresent()) {
			collectionSorting.clear();
			collectionSorting.addAll(result.get());
		}
	}
}
