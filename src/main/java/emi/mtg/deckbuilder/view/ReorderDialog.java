package emi.mtg.deckbuilder.view;

import com.sun.javafx.collections.ObservableListWrapper;
import javafx.beans.binding.Bindings;
import javafx.geometry.Insets;
import javafx.scene.control.*;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.VBox;

import java.util.List;

public class ReorderDialog<T> extends Dialog<List<T>> {
	public ReorderDialog(String title, List<T> items) {
		super();

		double start = System.nanoTime();

		setTitle(title);

		BorderPane contentPane = new BorderPane();
		contentPane.setPadding(new Insets(8.0));

		ListView<T> list = new ListView<>(new ObservableListWrapper<>(items));
		list.getSelectionModel().setSelectionMode(SelectionMode.SINGLE);
		list.setFixedCellSize(24.0);
		list.prefHeightProperty().bind(Bindings.size(list.getItems()).multiply(list.fixedCellSizeProperty()));

		VBox buttons = new VBox(8.0);
		buttons.setPadding(new Insets(0.0, 0.0, 0.0, 8.0));

		Button moveTopButton = new Button("Top");
		moveTopButton.setMaxWidth(Double.MAX_VALUE);

		moveTopButton.setOnAction(ae -> {
			if (list.getSelectionModel().getSelectedItems() == null) {
				return;
			}

			T obj = list.getSelectionModel().getSelectedItem();
			list.getItems().remove(obj);
			list.getItems().add(0, obj);
			list.getSelectionModel().clearAndSelect(0);

			ae.consume();
		});

		Button moveUpButton = new Button("Up");
		moveUpButton.setMaxWidth(Double.MAX_VALUE);

		moveUpButton.setOnAction(ae -> {
			if (list.getSelectionModel().getSelectedItems() == null) {
				return;
			}

			int i = list.getSelectionModel().getSelectedIndex();
			T obj = list.getItems().remove(i);
			list.getItems().add(Math.max(i - 1, 0), obj);
			list.getSelectionModel().clearAndSelect(i - 1);

			ae.consume();
		});

		Button moveDownButton = new Button("Down");
		moveDownButton.setMaxWidth(Double.MAX_VALUE);

		moveDownButton.setOnAction(ae -> {
			if (list.getSelectionModel().getSelectedItems() == null) {
				return;
			}

			int i = list.getSelectionModel().getSelectedIndex();
			T obj = list.getItems().remove(i);
			list.getItems().add(Math.min(i + 1, list.getItems().size()), obj);
			list.getSelectionModel().clearAndSelect(i + 1);

			ae.consume();
		});

		Button moveBottomButton = new Button("Bottom");
		moveBottomButton.setMaxWidth(Double.MAX_VALUE);

		moveBottomButton.setOnAction(ae -> {
			if (list.getSelectionModel().getSelectedItems() == null) {
				return;
			}

			T obj = list.getSelectionModel().getSelectedItem();
			list.getItems().remove(obj);
			list.getItems().add(list.getItems().size(), obj);
			list.getSelectionModel().clearAndSelect(list.getItems().size() - 1);

			ae.consume();
		});

		buttons.getChildren().addAll(moveTopButton, moveUpButton, moveDownButton, moveBottomButton);

		contentPane.setCenter(list);
		contentPane.setRight(buttons);

		getDialogPane().setContent(contentPane);
		getDialogPane().getButtonTypes().addAll(ButtonType.OK, ButtonType.CANCEL);

		setResultConverter(t -> t.getButtonData().isDefaultButton() ? list.getItems() : null);

		System.out.println(String.format("ReorderDialog constructor took %.2f seconds.", (System.nanoTime() - start) / 1e9));
	}
}
