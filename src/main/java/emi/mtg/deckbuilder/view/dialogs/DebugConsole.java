package emi.mtg.deckbuilder.view.dialogs;

import emi.mtg.deckbuilder.model.Preferences;
import emi.mtg.deckbuilder.view.MainApplication;
import emi.mtg.deckbuilder.view.util.FxUtils;
import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.scene.control.Dialog;
import javafx.scene.control.TextArea;
import javafx.stage.Modality;

public class DebugConsole extends Dialog<Void> {
    @FXML
    private TextArea log;

    public DebugConsole() {
        FxUtils.FXML(this);
        getDialogPane().setStyle(Preferences.get().theme.style());

        initModality(Modality.NONE);
        setResizable(true);

        Platform.runLater(() -> log.setText(MainApplication.stdboth.get().toString()));
        MainApplication.stdboth.addView(log);
    }
}
