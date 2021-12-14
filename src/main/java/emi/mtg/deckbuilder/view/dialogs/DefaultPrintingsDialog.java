package emi.mtg.deckbuilder.view.dialogs;

import emi.lib.mtg.Set;
import emi.mtg.deckbuilder.controller.Context;
import emi.mtg.deckbuilder.model.Preferences;
import emi.mtg.deckbuilder.view.util.FxUtils;
import javafx.beans.value.ObservableValue;
import javafx.fxml.FXML;
import javafx.geometry.Side;
import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.stage.Window;

import java.util.*;
import java.util.stream.Collectors;

public class DefaultPrintingsDialog extends Dialog<Preferences.DefaultPrintings> {
	@FXML
	protected ListView<emi.lib.mtg.Set> ignoredSets;

	@FXML
	protected ListView<emi.lib.mtg.Set> allSets;

	@FXML
	protected ListView<emi.lib.mtg.Set> preferredSets;

	protected TextField searchBox;
	protected ContextMenu searchContextMenu;

	protected void sort(ListView<emi.lib.mtg.Set> which) {
		which.getItems().sort(Comparator.comparing(Set::releaseDate).reversed());
	}

	protected void transferSelected(ListView<emi.lib.mtg.Set> from, ListView<emi.lib.mtg.Set> to) {
		if (from.getSelectionModel().isEmpty()) return;

		to.getItems().addAll(from.getSelectionModel().getSelectedItems());
		from.getItems().removeAll(from.getSelectionModel().getSelectedItems());

		if (to.getSelectionModel().isEmpty() && from.getSelectionModel().getSelectedIndices().size() == 1) {
			to.getSelectionModel().select(from.getSelectionModel().getSelectedItem());
		}

		from.getSelectionModel().clearSelection();
	}

	@FXML
	protected void ignoreSets() {
		transferSelected(allSets, ignoredSets);
		sort(ignoredSets);
	}

	@FXML
	protected void unIgnoreSets() {
		transferSelected(ignoredSets, allSets);
		sort(allSets);
	}

	@FXML
	protected void preferSets() {
		transferSelected(allSets, preferredSets);
	}

	@FXML
	protected void unPreferSets() {
		transferSelected(preferredSets, allSets);
		sort(allSets);
	}

	protected void movePreference(int delta) {
		if (preferredSets.getSelectionModel().getSelectedIndices().size() != 1) {
			return;
		}

		int idx = preferredSets.getSelectionModel().getSelectedIndex();
		emi.lib.mtg.Set set = preferredSets.getItems().remove(idx);

		idx = Math.max(0, Math.min(idx + delta, preferredSets.getItems().size()));
		preferredSets.getItems().add(idx, set);
		preferredSets.getSelectionModel().clearAndSelect(idx);
	}

	@FXML
	protected void moveTop() {
		movePreference(-Integer.MAX_VALUE);
	}

	@FXML
	protected void moveUp() {
		movePreference(-1);
	}

	@FXML
	protected void moveDown() {
		movePreference(1);
	}

	@FXML
	protected void moveBottom() {
		movePreference(Integer.MAX_VALUE);
	}

	@FXML
	protected void ignoreDigital() {
		List<Set> transfer = allSets.getItems().stream().filter(Set::digital).collect(Collectors.toList());
		allSets.getItems().removeAll(transfer);
		ignoredSets.getItems().addAll(transfer);
		sort(ignoredSets);
	}

	@FXML
	protected void ignoreRemasters() {
		List<Set> transfer = allSets.getItems().stream().filter(s -> s.type() == Set.Type.Remaster).collect(Collectors.toList());
		allSets.getItems().removeAll(transfer);
		ignoredSets.getItems().addAll(transfer);
		sort(ignoredSets);
	}

	@FXML
	protected void ignorePromos() {
		List<Set> transfer = allSets.getItems().stream().filter(s -> s.type() == Set.Type.Promo).collect(Collectors.toList());
		allSets.getItems().removeAll(transfer);
		ignoredSets.getItems().addAll(transfer);
		sort(ignoredSets);
	}

	@FXML
	protected void ignoreOther() {
		List<Set> transfer = allSets.getItems().stream().filter(s -> s.type() == Set.Type.Other).collect(Collectors.toList());
		allSets.getItems().removeAll(transfer);
		ignoredSets.getItems().addAll(transfer);
		sort(ignoredSets);
	}

	@FXML
	protected void unIgnoreAll() {
		allSets.getItems().addAll(ignoredSets.getItems());
		ignoredSets.getItems().clear();
		sort(allSets);
	}

	@FXML
	protected void unPreferAll() {
		allSets.getItems().addAll(preferredSets.getItems());
		preferredSets.getItems().clear();
		sort(allSets);
	}

	public DefaultPrintingsDialog(Window host, Preferences.DefaultPrintings source) {
		setTitle("Default Printings");
		initOwner(host);
		getDialogPane().getButtonTypes().setAll(ButtonType.OK, ButtonType.CANCEL);
		FxUtils.FXML(this, getDialogPane());
		getDialogPane().setStyle(Preferences.get().theme.style());

		List<Set> allSets = new ArrayList<>(Context.get().data.sets());

		List<Set> ignoredSets = new ArrayList<>();
		List<Set> preferredSets = new ArrayList<>();

		Iterator<Set> iter = allSets.iterator();
		while (iter.hasNext()) {
			Set set = iter.next();
			if (source.ignoredSets.contains(set.code())) {
				ignoredSets.add(set);
				iter.remove();
			} else if (source.preferredSets.contains(set.code())) {
				preferredSets.add(set);
				iter.remove();
			}
		}

		preferredSets.sort(Comparator.comparingInt(s -> source.prefPriority(s.code())));

		this.ignoredSets.getItems().setAll(ignoredSets);
		this.allSets.getItems().setAll(allSets);
		this.preferredSets.getItems().setAll(preferredSets);

		sort(this.allSets);
		sort(this.ignoredSets);

		this.ignoredSets.getSelectionModel().setSelectionMode(SelectionMode.MULTIPLE);
		this.allSets.getSelectionModel().setSelectionMode(SelectionMode.MULTIPLE);

		this.searchBox = new TextField();
		this.searchBox.setPromptText("Enter search term...");
		this.searchBox.setOnKeyPressed(this::filterKeyPressed);
		this.searchBox.textProperty().addListener(this::filterTextChanged);
		this.searchContextMenu = new ContextMenu(new CustomMenuItem(this.searchBox));

		this.ignoredSets.setOnKeyPressed(this::listViewOnKeyPressed);
		this.allSets.setOnKeyPressed(this::listViewOnKeyPressed);
		this.preferredSets.setOnKeyPressed(this::listViewOnKeyPressed);

		setResultConverter(bt -> {
			if (bt == ButtonType.OK) {
				return new Preferences.DefaultPrintings(this.preferredSets.getItems().stream().map(s -> s.code().toLowerCase()).collect(Collectors.toList()), this.ignoredSets.getItems().stream().map(s -> s.code().toLowerCase()).collect(Collectors.toSet()));
			} else {
				return source;
			}
		});
	}

	private void listViewOnKeyPressed(KeyEvent ke) {
		if (this.searchContextMenu.isShowing()) return;
		if (!ke.getCode().isLetterKey()) return;
		if (ke.getSource() != this.ignoredSets && ke.getSource() != this.allSets && ke.getSource() != this.preferredSets) return;

		this.searchContextMenu.show((Node) ke.getSource(), Side.TOP, 0, 0);
		this.searchBox.setText(ke.getCharacter());
		this.searchBox.setUserData(ke.getSource());
		this.searchBox.requestFocus();
		ke.consume();
	}

	private void filterKeyPressed(KeyEvent ke) {
		if (ke.getCode() != KeyCode.ESCAPE && ke.getCode() != KeyCode.ENTER) return;

		this.searchBox.setUserData(null);
		this.searchContextMenu.hide();
		ke.consume();
	}

	private void filterTextChanged(ObservableValue<? extends String> text, String old, String newValue) {
		if (this.searchBox.getUserData() == null || !(this.searchBox.getUserData() instanceof ListView)) return;

		ListView<emi.lib.mtg.Set> setList = (ListView<emi.lib.mtg.Set>) this.searchBox.getUserData();

		final String search = newValue.toLowerCase();
		emi.lib.mtg.Set set = setList.getItems().stream()
				.filter(s -> s.name().toLowerCase().contains(search) || s.code().toLowerCase().contains(search))
				.findFirst()
				.orElse(null);

		if (set == null) return;

		setList.getSelectionModel().clearSelection();
		setList.getSelectionModel().select(set);
		setList.scrollTo(set);
	}
}
