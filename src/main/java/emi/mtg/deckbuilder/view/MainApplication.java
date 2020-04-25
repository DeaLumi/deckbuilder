package emi.mtg.deckbuilder.view;

import emi.mtg.deckbuilder.controller.Context;
import emi.mtg.deckbuilder.controller.Updater;
import emi.mtg.deckbuilder.model.DeckList;
import emi.mtg.deckbuilder.view.dialogs.PreferencesDialog;
import emi.mtg.deckbuilder.view.dialogs.TagManagementDialog;
import emi.mtg.deckbuilder.view.util.AlertBuilder;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.event.ActionEvent;
import javafx.scene.Group;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.text.Text;
import javafx.stage.Stage;

import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.concurrent.ForkJoinPool;

public class MainApplication extends Application {
	public static final ClassLoader PLUGIN_CLASS_LOADER;

	private final HashSet<MainWindow> mainWindows = new HashSet<>();

	static {
		ClassLoader tmp;
		try {
			List<URL> urls = new ArrayList<>();
			for (Path path : Files.newDirectoryStream(Paths.get("plugins/"), "*.jar")) {
				urls.add(path.toUri().toURL());
			}
			tmp = new URLClassLoader(urls.toArray(new URL[urls.size()]), MainWindow.class.getClassLoader());
		} catch (IOException ioe) {
			ioe.printStackTrace();
			System.err.println("Warning: Unable to load any plugins...");
			tmp = new URLClassLoader(new URL[0], MainWindow.class.getClassLoader());
		}
		PLUGIN_CLASS_LOADER = tmp;
	}

	private final Updater updater;
	private Stage hostStage;

	public void closeAllWindows() {
		for (MainWindow child : mainWindows) {
			child.close();
		}
	}

	public void quit() {
		hostStage.close();
	}

	public static void main(String[] args) {
		launch(args);
	}

	public MainApplication() {
		updater = new Updater();
	}

	void registerMainWindow(MainWindow window) {
		mainWindows.add(window);
	}

	void deregisterMainWindow(MainWindow window) {
		mainWindows.remove(window);
		if (mainWindows.isEmpty()) {
			quit();
		}
	}

	@Override
	public void start(Stage primaryStage) throws Exception {
		this.hostStage = primaryStage;
		hostStage.setScene(new Scene(new Group()));

		Thread.setDefaultUncaughtExceptionHandler((x, e) -> {
			boolean deckSaved = true;

			for (MainWindow child : mainWindows) {
				try {
					child.emergencySave();
				} catch (Throwable t) {
					e.addSuppressed(t);
					deckSaved = false;
				}
			}

			e.printStackTrace();
			e.printStackTrace(new PrintWriter(System.out));

			StringWriter stackTrace = new StringWriter();
			e.printStackTrace(new PrintWriter(stackTrace));

			Alert alert = new Alert(Alert.AlertType.ERROR);
			alert.initOwner(hostStage);

			alert.setTitle("Uncaught Exception");
			alert.setHeaderText("An internal error has occurred.");

			alert.setContentText(
					"Something went wrong!\n" +
							"\n" +
							"I have no idea if the application will be able to keep running.\n" +
							(deckSaved ?
									"Your decks have been emergency-saved to 'emergency-save-XXXXXXXX.json' in the deckbuilder directory.\n" :
									"Something went even more wrong while we tried to save your decks. Sorry. D:\n"
							) +
							"If this keeps happening, message me on Twitter @DeaLuminis!\n" +
							"If you're the nerdy type, tech details follow.\n" +
							"\n" +
							"Thread: " + x.getName() + " / " + x.getId() + "\n" +
							"Exception: " + e.getClass().getSimpleName() + ": " + e.getMessage() + "\n" +
							"\n" +
							"Full stack trace written to standard out and standard error (usually err.txt)."
			);

			alert.getDialogPane().setExpandableContent(new ScrollPane(new Text(stackTrace.toString())));

			alert.showAndWait();
		});

		hostStage.setOnCloseRequest(ev -> {
			try {
				Context.get().saveAll();
			} catch (IOException e) {
				throw new RuntimeException(e);
			}
		});

		Alert alert = AlertBuilder.create()
				.owner(hostStage)
				.title("Loading")
				.headerText("Loading card data...")
				.contentText("Please wait a moment!")
				.show();
		Context.instantiate();
		alert.getButtonTypes().setAll(ButtonType.CLOSE);
		alert.hide();

		if (Context.get().preferences.autoUpdateProgram && updater.needsUpdate()) {
			if (AlertBuilder.query(hostStage)
					.title("Auto-Update")
					.headerText("A program update is available.")
					.contentText("Would you like to update?")
					.showAndWait().orElse(ButtonType.NO) == ButtonType.YES) {
				doGraphicalUpdate();
			}
		}

		if (Context.get().preferences.autoUpdateData && Context.get().data.needsUpdate()) {
			if (AlertBuilder.query(hostStage)
					.title("Auto-Update")
					.headerText("New card data may be available.")
					.contentText("Would you like to update?")
					.showAndWait().orElse(ButtonType.NO) == ButtonType.YES) {
				doGraphicalDataUpdate();
			}
		}

		new MainWindow(this, new DeckList("", Context.get().preferences.authorName, Context.get().preferences.defaultFormat, "", Collections.emptyMap())).show();
	}

	public void update() {
		TextInputDialog uriInput = new TextInputDialog(Context.get().preferences.updateUri == null ? "" : Context.get().preferences.updateUri.toString());
		uriInput.setTitle("Update Source");
		uriInput.setHeaderText("Update Server URL");
		uriInput.setContentText("URL:");
		uriInput.getDialogPane().setExpanded(true);

		ProgressBar progress = new ProgressBar(0.0);
		uriInput.getDialogPane().setExpandableContent(progress);
		uriInput.getDialogPane().setPrefWidth(512.0);

		Button btnOk = (Button) uriInput.getDialogPane().lookupButton(ButtonType.OK);
		Button btnCancel = (Button) uriInput.getDialogPane().lookupButton(ButtonType.CANCEL);
		btnOk.addEventFilter(ActionEvent.ACTION, ae -> {
			String newUri = uriInput.getEditor().getText();

			try {
				Context.get().preferences.updateUri = new URI(newUri);
				Context.get().saveAll();

				ForkJoinPool.commonPool().submit(() -> {
					try {
						updater.update(d -> Platform.runLater(() -> progress.setProgress(d)));
					} catch (IOException e) {
						throw new RuntimeException(e);
					}
				});
			} catch (IOException | URISyntaxException e) {
				uriInput.close();
				throw new RuntimeException(e);
			}

			btnCancel.setDisable(true);
			btnOk.setDisable(true);
			ae.consume();
		});

		uriInput.initOwner(hostStage);
		uriInput.showAndWait();
	}

	public void doGraphicalUpdate() {
		Alert progressDialog = new Alert(Alert.AlertType.NONE, "Updating...", ButtonType.CLOSE);
		progressDialog.setHeaderText("Updating...");
		progressDialog.getDialogPane().lookupButton(ButtonType.CLOSE).setDisable(true);

		ProgressBar pbar = new ProgressBar(0.0);
		progressDialog.getDialogPane().setContent(pbar);
		progressDialog.getDialogPane().setPrefWidth(256.0);

		progressDialog.initOwner(hostStage);
		progressDialog.show();

		ForkJoinPool.commonPool().submit(() -> {
			try {
				updater.update(d -> Platform.runLater(() -> pbar.setProgress(d)));
			} catch (Exception e) {
				throw new RuntimeException(e);
			} finally {
				Platform.runLater(progressDialog::close);
			}
		});
	}

	public void updateData() {
		if (!Context.get().data.needsUpdate() && AlertBuilder.query(hostStage)
				.title("Update Data")
				.headerText("Data is fresh.")
				.contentText("Data source seems fresh. Update anyway?")
				.showAndWait()
				.orElse(ButtonType.NO) != ButtonType.YES) {
			return;
		}

		doGraphicalDataUpdate();
	}

	public void doGraphicalDataUpdate() {
		Alert progressDialog = AlertBuilder.create()
				.type(Alert.AlertType.NONE)
				.title("Updating")
				.headerText("Updating...")
				.contentText("")
				.buttons(ButtonType.CLOSE).get();
		progressDialog.getDialogPane().lookupButton(ButtonType.CLOSE).setDisable(true);

		ProgressBar pbar = new ProgressBar(0.0);
		progressDialog.getDialogPane().setContent(pbar);
		progressDialog.getDialogPane().setPrefWidth(256.0);

		ForkJoinPool.commonPool().submit(() -> {
			try {
				Context.get().saveTags();
				if (Context.get().data.update(d -> Platform.runLater(() -> pbar.setProgress(d)))) {
					Platform.runLater(() -> {
						progressDialog.close();

						for (MainWindow child : mainWindows) {
							child.remodel();
						}

						try {
							Context.get().loadTags();
						} catch (IOException ioe) {
							throw new Error(ioe);
						}
					});
				}
			} catch (Throwable e) {
				Platform.runLater(() -> {
					progressDialog.close();
					throw new RuntimeException(e);
				});
			}
		});

		progressDialog.showAndWait();
	}

	public void showPreferences() {
		try {
			PreferencesDialog pd = new PreferencesDialog(Context.get().preferences);
			pd.initOwner(hostStage);

			if(pd.showAndWait().orElse(false)) {
				Context.get().savePreferences();
			}
		} catch (IOException e) {
			e.printStackTrace();
		}
	}

	public void showTagManagementDialog() {
		TagManagementDialog dlg = new TagManagementDialog();
		dlg.initOwner(hostStage);
		dlg.showAndWait();
	}

	public void saveTags() {
		try {
			Context.get().saveTags(); // TODO: Move this to controller?
		} catch (IOException ioe) {
			ioe.printStackTrace(); // TODO: Handle gracefully
		}
	}
}
