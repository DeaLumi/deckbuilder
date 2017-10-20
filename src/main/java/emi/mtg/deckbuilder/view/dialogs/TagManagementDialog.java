package emi.mtg.deckbuilder.view.dialogs;

import emi.mtg.deckbuilder.controller.Context;
import javafx.collections.FXCollections;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.control.*;
import javafx.scene.layout.AnchorPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;

import java.io.IOException;
import java.util.ArrayList;

public class TagManagementDialog extends Dialog<Void> {
	// TODO: Rework this dialog to not change live data...

	@FXML
	private ListView<String> knownTagsList;

	@FXML
	private TextField newTagText;

	@FXML
	private Button newTagBtn;

	@FXML
	protected void removeSelected() {
		context.tags.tags().removeAll(knownTagsList.getSelectionModel().getSelectedItems());
		knownTagsList.getItems().removeAll(knownTagsList.getSelectionModel().getSelectedItems());
	}

	@FXML
	protected void addTag() {
		if (!knownTagsList.getItems().contains(newTagText.getText())) {
			context.tags.add(newTagText.getText());
			knownTagsList.getItems().add(newTagText.getText());
			newTagText.setText("");
		}
	}

	private final Context context;

	public TagManagementDialog(Context context) throws IOException {
		super();

		setTitle("Tags");

		FXMLLoader loader = new FXMLLoader(getClass().getResource("TagManagementDialog.fxml"));
		loader.setController(this);
		loader.setRoot(getDialogPane());
		loader.load();

		this.context = context;

		knownTagsList.setItems(FXCollections.observableArrayList(new ArrayList<>(context.tags.tags())));
		knownTagsList.getSelectionModel().setSelectionMode(SelectionMode.MULTIPLE);

		knownTagsList.setCellFactory(lv -> new ListCell<String>() {
			@Override
			protected void updateItem(String item, boolean empty) {
				super.updateItem(item, empty);

				setText("");

				if (empty) {
					setGraphic(null);
				} else {
					Label label = new Label(item);
					Button button = new Button("-");

					button.setOnAction(ae -> {
						knownTagsList.getItems().remove(item);
						context.tags.tags().remove(item);
					});

					AnchorPane.setLeftAnchor(label, 0.0);
					AnchorPane.setRightAnchor(button, 0.0);

					AnchorPane content = new AnchorPane();
					content.getChildren().addAll(label, button);

					setGraphic(content);
					setContentDisplay(ContentDisplay.RIGHT);
				}
			}
		});

		newTagBtn.disableProperty().bind(newTagText.textProperty().isEmpty());

		HBox.setHgrow(newTagText, Priority.ALWAYS);

		setResultConverter(bt -> {
			try {
				if (bt == ButtonType.APPLY) {
					context.saveTags();
				} else if (bt == ButtonType.CANCEL) {
					context.loadTags();
				}
			} catch (IOException ioe) {
				ioe.printStackTrace();
				// TODO: Handle gracefully
			}

			return null;
		});
	}
}
