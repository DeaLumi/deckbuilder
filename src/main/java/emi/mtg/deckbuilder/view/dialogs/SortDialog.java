package emi.mtg.deckbuilder.view.dialogs;

import emi.mtg.deckbuilder.model.Preferences;
import emi.mtg.deckbuilder.view.components.CardView;
import emi.mtg.deckbuilder.view.util.FxUtils;
import javafx.collections.FXCollections;
import javafx.fxml.FXML;
import javafx.scene.control.ButtonBar;
import javafx.scene.control.Dialog;
import javafx.scene.control.ListView;
import javafx.scene.control.cell.CheckBoxListCell;
import javafx.stage.Window;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

public class SortDialog extends Dialog<List<CardView.ActiveSorting>> {
	@FXML
	private ListView<CardView.Sorting> sourceList;

	@FXML
	private ListView<CardView.ActiveSorting> activeList;

	@FXML
	protected void addSort() {
		if (sourceList.getSelectionModel().isEmpty()) {
			return;
		}

		CardView.Sorting sort = sourceList.getItems().remove(sourceList.getSelectionModel().getSelectedIndex());
		sourceList.getSelectionModel().clearSelection();
		activeList.getItems().add(new CardView.ActiveSorting(sort, false));
		activeList.getSelectionModel().clearAndSelect(activeList.getItems().size() - 1);
	}

	@FXML
	protected void removeSort() {
		if (activeList.getSelectionModel().isEmpty()) {
			return;
		}

		CardView.ActiveSorting sort = activeList.getItems().remove(activeList.getSelectionModel().getSelectedIndex());
		activeList.getSelectionModel().clearSelection();
		sourceList.getItems().add(sort.sorting);
		sourceList.getSelectionModel().clearAndSelect(sourceList.getItems().size() - 1);
	}

	@FXML
	protected void moveTop() {
		moveSort(-Integer.MAX_VALUE);
	}

	@FXML
	protected void moveUp() {
		moveSort(-1);
	}

	@FXML
	protected void moveDown() {
		moveSort(1);
	}

	@FXML
	protected void moveBottom() {
		moveSort(Integer.MAX_VALUE);
	}

	protected void moveSort(int delta) {
		if (activeList.getSelectionModel().getSelectedIndices().size() != 1) {
			return;
		}

		int idx = activeList.getSelectionModel().getSelectedIndex();
		CardView.ActiveSorting sort = activeList.getItems().remove(idx);

		idx = Math.max(0, Math.min(idx + delta, activeList.getItems().size()));
		activeList.getItems().add(idx, sort);
		activeList.getSelectionModel().clearAndSelect(idx);
	}

	public SortDialog(Window host, List<CardView.ActiveSorting> currentSorts) {
		super();

		FxUtils.FXML(this, getDialogPane());
		getDialogPane().setStyle(Preferences.get().theme.style());
		initOwner(host);

		List<CardView.Sorting> source = new ArrayList<>(CardView.SORTINGS.values());
		source.removeAll(currentSorts.stream().map(s -> s.sorting).collect(Collectors.toSet()));
		sourceList.setItems(FXCollections.observableList(source));

		List<CardView.ActiveSorting> model = new ArrayList<>();
		for (CardView.ActiveSorting active : currentSorts) {
			model.add(new CardView.ActiveSorting(active.sorting, active.descending.get()));
		}

		activeList.setItems(FXCollections.observableList(model));
		activeList.setCellFactory(CheckBoxListCell.forListView(s -> s.descending));

		setTitle("Sort");

		setResultConverter(t -> t.getButtonData() == ButtonBar.ButtonData.OK_DONE ? model : null);
	}
}
