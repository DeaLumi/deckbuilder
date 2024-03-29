package emi.mtg.deckbuilder.view;

import emi.lib.mtg.DataSource;
import emi.mtg.deckbuilder.controller.Context;
import emi.mtg.deckbuilder.controller.Updater;
import emi.mtg.deckbuilder.model.DeckList;
import emi.mtg.deckbuilder.model.Preferences;
import emi.mtg.deckbuilder.model.State;
import emi.mtg.deckbuilder.view.util.AlertBuilder;
import emi.mtg.deckbuilder.view.util.FxUtils;
import emi.mtg.deckbuilder.view.util.MicroMarkdown;
import emi.mtg.deckbuilder.util.PluginUtils;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.event.ActionEvent;
import javafx.geometry.HPos;
import javafx.scene.AccessibleAction;
import javafx.scene.Group;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.Priority;
import javafx.stage.Modality;
import javafx.stage.Screen;
import javafx.stage.Stage;
import javafx.stage.WindowEvent;

import java.io.*;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.concurrent.ForkJoinPool;

public class MainApplication extends Application {
	public static final Path JAR_PATH = PluginUtils.jarPath(MainApplication.class);
	public static final Path JAR_DIR = JAR_PATH.getParent();

	public static final List<DataSource> DATA_SOURCES = PluginUtils.providers(DataSource.class);

	private final HashSet<MainWindow> mainWindows = new HashSet<>();

	private final Updater updater;
	private Stage hostStage;
	private Screen screen;

	public void closeAllWindows() {
		for (MainWindow child : mainWindows) {
			if (child.getOnCloseRequest() != null) {
				WindowEvent closeEvent = new WindowEvent(child, WindowEvent.WINDOW_CLOSE_REQUEST);
				child.getOnCloseRequest().handle(closeEvent);
				if (closeEvent.isConsumed()) return;
			}
			child.close();
		}
	}

	public void quit() {
		hostStage.close();
		System.exit(0);
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
			try {
				Preferences.save();
				State.save();
				Context.get().saveTags();
			} catch (IOException ioe) {
				throw new RuntimeException(ioe);
			} finally {
				quit();
			}
		}
	}

	private Screen discoverScreen() {
		java.awt.PointerInfo pointerInfo = java.awt.MouseInfo.getPointerInfo();

		double scx = pointerInfo.getLocation().getX();
		double scy = pointerInfo.getLocation().getY();

		List<Screen> screens = Screen.getScreensForRectangle(scx, scy, 0, 0);

		if (screens.size() == 1) return screens.get(0);

		return Screen.getPrimary();
	}

	private static final String LOW_MEMORY_LIMIT = String.join("\n",
			"Your maximum memory limit is less than 1 GB! " +
			"You may be running a 32-bit JRE or have configured a low memory limit. " +
			"Between card data and images, Magic consumes a lot of RAM. " +
			"This may cause unexpected issues, and is known to cause no cards to appear.",
			"",
			"If your computer has more than 1 GB of RAM, make sure that:",
			" \u2022 A 64-bit JRE was used to launch the Deckbuilder.",
			" \u2022 You're not running with weird command line parameters.",
			"",
			"You can increase the memory limit through the Java control panel, " +
			"but explaining in-depth here would be a lot of text! I suggest Google. :)",
			"",
			"From here on out, there be monsters...");

	private static final String NO_DATA_SOURCES = String.join("\n",
			"No data sources are available!",
			"",
			"Check the plugins/ directory for a data source plugin. " +
			"At bare minimum, scryfall-0.0.0.jar should be provided!");

	private static final String UPDATE_ERROR = String.join("\n",
			"The update server may be down, or there may be a problem with " +
			"your internet connection. This shouldn't cause problems for your " +
			"deckbuilding, but you won't be able to get bugfixes etc.",
			"",
			"If you're not expecting this, you should let me know, " +
			"in case the server's down and I need to fix that. :^)");

	private static final String DATA_LOAD_ERROR = String.join("\n",
			"An error occurred while loading card data.",
			"Some or all cards may not have been loaded..",
			"",
			"Try updating card data?");

	@Override
	public void start(Stage primaryStage) throws Exception {
		System.setProperty("file.encoding", "UTF-8");

		this.screen = discoverScreen();
		this.hostStage = primaryStage;
		hostStage.setScene(new Scene(new Group()));
		hostStage.getScene().getStylesheets().add(MainApplication.class.getResource("styles.css").toExternalForm());

		Preferences prefs = Preferences.instantiate();
		State state = State.instantiate();

		if (!Files.isDirectory(prefs.dataPath)) {
			Files.createDirectories(prefs.dataPath);
		}

		if (Runtime.getRuntime().maxMemory() <= 1024*1024*1024) {
			AlertBuilder.notify(primaryStage)
					.type(Alert.AlertType.WARNING)
					.title("Memory Limit Warning")
					.headerText("Low memory limit detected!")
					.contentText(LOW_MEMORY_LIMIT)
					.modal(Modality.APPLICATION_MODAL)
					.showAndWait();
		}

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

			StringBuilder condensedTrace = new StringBuilder(String.format("Exception: %s: %s", e.getClass().getSimpleName(), e.getMessage()));
			for (Throwable t = e.getCause(); t != null; t = t.getCause()) {
				condensedTrace.append(String.format("\nCaused by: %s: %s", t.getClass().getSimpleName(), t.getMessage()));

				for (Throwable s : t.getSuppressed()) {
					condensedTrace.append(String.format("\n\tSuppressing: %s: %s", s.getClass().getSimpleName(), s.getMessage()));
				}
			}

			Path file = JAR_PATH.resolveSibling(String.format("exception-%s.log", Instant.now().toString()).replaceAll(":", "-"));
			try {
				PrintWriter writer = new PrintWriter(Files.newBufferedWriter(file));

				writer.println(String.format("At %s on thread %s/%d, an uncaught exception was thrown.", Instant.now(), x.getName(), x.getId()));
				writer.println(deckSaved ? "The user's decks were successfully emergency-saved." : "One or more of the user's decks were not able to be saved.");
				writer.println();

				writer.println("Abbreviated stack trace:");
				writer.println("------------------------");
				writer.println(condensedTrace);
				writer.println();

				writer.println("Complete stack trace:");
				writer.println("---------------------");
				e.printStackTrace(writer);
				writer.close();
			} catch (IOException ioe) {
				e.addSuppressed(ioe);
			}

			final boolean ds = deckSaved;
			Platform.runLater(() -> {
				Alert alert = new Alert(Alert.AlertType.ERROR);
				alert.initOwner(hostStage);
				alert.getDialogPane().setStyle(Preferences.get().theme.style());

				alert.setTitle("Uncaught Exception");
				alert.setHeaderText("An internal error has occurred.");

				alert.setContentText(String.join("\n",
						"Something went wrong!",
						"",
						(ds ?
								"Your decks have been emergency-saved to 'emergency-save-XXXXXXXX.json' beside the Deckbuilder JAR." :
								"One or more of your decks couldn't even be emergency-saved. They may be gone. I'm terribly sorry. D:"
						),
						"",
						"The program may terminate when you close this dialog, or it might seem to work just fine. " +
								"The internal state of the program may have become corrupted, though, and so " +
								"I recommend saving all your decks and restarting the program.",
						"",
						"Detailed error information was saved to " + file.toAbsolutePath().toString() + " " +
								"If you care to, please contact me on Twitter @DeaLuminis and share this file!",
						"",
						"If you're the nerdy type, tech details follow.",
						"",
						"Thread: " + x.getName() + " / " + x.getId(),
						condensedTrace
				));

				TextArea text = new TextArea(stackTrace.toString());
				text.setMinWidth(800.0);
				text.setMinHeight(800.0);
				text.setEditable(false);
				alert.getDialogPane().setExpandableContent(text);

				alert.show();
				FxUtils.transfer(alert, screen);
			});
		});

		try {
			if (prefs.autoUpdateProgram && updater.needsUpdate()) {
				AlertBuilder.query(hostStage)
						.screen(screen)
						.title("Auto-Update")
						.headerText("A program update is available.")
						.contentText("Would you like to update?")
						.longRunning(updater::update, AlertBuilder.Exceptions.Throw)
						.showAndWait();
			}
		} catch (IOException ioe) {
			AlertBuilder.notify(hostStage)
					.screen(screen)
					.type(Alert.AlertType.WARNING)
					.title("Updater Error")
					.headerText("Unable to check for updates.")
					.contentText(UPDATE_ERROR)
					.modal(Modality.APPLICATION_MODAL)
					.showAndWait();
		}

		if (DATA_SOURCES.size() == 0) {
			AlertBuilder.notify(hostStage)
					.screen(screen)
					.type(Alert.AlertType.ERROR)
					.title("No Data Sources")
					.headerText("No data source is available!")
					.contentText(NO_DATA_SOURCES)
					.modal(Modality.APPLICATION_MODAL)
					.showAndWait();

			Platform.exit();
			return;
		}

		selectDataSource();

		prefs.convertOldPreferredPrintings();

		MainWindow window = new MainWindow(this);
		window.addDeck(new DeckList("", prefs.authorName, prefs.defaultFormat, "", Collections.emptyMap()));
		window.show();
		FxUtils.transfer(window, screen);

		if (state.checkUpdated()) {
			showChangeLog();
		}
	}

	private void selectDataSource() {
		Label label = new Label(DATA_SOURCES.size() > 1 ? "Please select a data source:" : "Using data from:");
		ComboBox<DataSource> combo = new ComboBox<>(FXCollections.observableArrayList(DATA_SOURCES.toArray(new DataSource[0])));
		GridPane grid = new GridPane();
		grid.setHgap(10.0);
		grid.setMaxWidth(Double.MAX_VALUE);
		combo.setMaxWidth(Double.MAX_VALUE);
		GridPane.setHgrow(combo, Priority.ALWAYS);
		GridPane.setHgrow(label, Priority.NEVER);
		GridPane.setFillWidth(combo, true);
		grid.add(label, 0, 0);
		grid.add(combo, 1, 0);
		combo.getSelectionModel().selectFirst();

		Alert alert = AlertBuilder.query(hostStage)
				.screen(screen)
				.buttons(ButtonType.OK, ButtonType.CANCEL)
				.title(DATA_SOURCES.size() > 1 ? "Select Data Source" : "Loading Data")
				.headerText(DATA_SOURCES.size() > 1 ? "Select a data source to use." : "Please wait...")
				.contentNode(grid)
				.longRunning(ButtonType.OK,
						dlg -> {
							if (Context.instantiated()) return;

							DataSource data = combo.getSelectionModel().getSelectedItem();

							if (Preferences.get().autoUpdateData && data.needsUpdate(Preferences.get().dataPath)) {
								AlertBuilder.query(dlg.getDialogPane().getScene().getWindow())
										.title("Auto-Update")
										.headerText("New card data available.")
										.contentText("Would you like to update?")
										.longRunning(prg -> data.update(Preferences.get().dataPath, prg))
										.showAndWait();
							}

							try {
								Context.instantiate(data);
								combo.setDisable(true);
							} catch (IOException e) {
								throw new RuntimeException(e);
							}
						},
						prg -> Context.get().loadData(prg),
						dlg -> {
							if (Preferences.get().autoUpdateData) {
								if(AlertBuilder.notify(dlg.getDialogPane().getScene().getWindow())
										.type(Alert.AlertType.ERROR)
										.buttons(ButtonType.YES, ButtonType.NO)
										.title("Data Load Error")
										.headerText("An error occurred while loading data.")
										.contentText(DATA_LOAD_ERROR)
										.longRunning(prg -> Context.get().data.update(Preferences.get().dataPath, prg))
										.showAndWait().orElse(ButtonType.NO) != ButtonType.YES) {
									dlg.close();
								} else {
									dlg.getDialogPane().lookupButton(ButtonType.OK).executeAccessibleAction(AccessibleAction.FIRE);
								}
							}
						},
						AlertBuilder.Exceptions.Throw)
				.get();

		alert.getDialogPane().setPrefWidth(350.0);

		alert.show();
		alert.hide();
		FxUtils.transfer(alert, screen);

		if(DATA_SOURCES.size() == 1) {
			Platform.runLater(() -> alert.getDialogPane().lookupButton(ButtonType.OK).executeAccessibleAction(AccessibleAction.FIRE));
		}

		if (alert.showAndWait().orElse(ButtonType.CANCEL) != ButtonType.OK) {
			System.exit(1);
		}
	}

	public void update() {
		// TODO: Replace this with a longRunning AlertBuilder as in the program startup sequence?

		TextInputDialog uriInput = new TextInputDialog(Preferences.get().updateUri == null ? "" : Preferences.get().updateUri.toString());
		uriInput.setTitle("Update Source");
		uriInput.setHeaderText("Update Server URL");
		uriInput.setContentText("URL:");
		uriInput.getDialogPane().setExpanded(true);
		uriInput.getDialogPane().setStyle(Preferences.get().theme.style());

		ProgressBar progress = new ProgressBar(0.0);
		uriInput.getDialogPane().setExpandableContent(progress);
		uriInput.getDialogPane().setPrefWidth(512.0);

		Button btnOk = (Button) uriInput.getDialogPane().lookupButton(ButtonType.OK);
		Button btnCancel = (Button) uriInput.getDialogPane().lookupButton(ButtonType.CANCEL);
		btnOk.addEventFilter(ActionEvent.ACTION, ae -> {
			String newUri = uriInput.getEditor().getText();

			try {
				Preferences.get().updateUri = new URI(newUri);
				Preferences.save();

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

		uriInput.show();
		uriInput.hide();
		FxUtils.transfer(uriInput, screen);

		uriInput.showAndWait();
	}

	@FunctionalInterface
	interface RunnableWithIOE {
		void run() throws IOException;
	}

	private static Runnable wrapIOE(RunnableWithIOE wrioe) {
		return () -> {
			try {
				wrioe.run();
			} catch (IOException e) {
				throw new RuntimeException(e);
			}
		};
	}

	public void updateData() {
		final DataSource data = Context.get().data;
		if(AlertBuilder.query(hostStage)
				.screen(screen)
				.title("Update Data")
				.headerText(data.needsUpdate(Preferences.get().dataPath) ? "New card data available." : "Data appears fresh.")
				.contentText(data.needsUpdate(Preferences.get().dataPath) ? "Would you like to update?" : "Would you like to update anyway?")
				.longRunning(ButtonType.YES, wrapIOE(Context.get()::saveTags), prg -> data.update(Preferences.get().dataPath, prg), null, AlertBuilder.Exceptions.Defer)
				.showAndWait().orElse(ButtonType.NO) == ButtonType.YES) {

			wrapIOE(Context.get()::loadTags).run();
			for (MainWindow child : mainWindows) {
				child.remodel();
			}
		}
	}

	public void trimImageDiskCache() {
		// TODO: Maybe FXML this whole mess. Make it another Dialog and open it from MainWindow.

		GridPane grid = new GridPane();
		grid.setHgap(8.0);
		grid.setVgap(8.0);

		Label unusedLabel = new Label("Days Since Last Load:");
		Spinner<Integer> unusedDays = new Spinner<>(1, 365, 180);
		unusedDays.setMaxWidth(Double.MAX_VALUE);

		Label sizeFactorLabel = new Label("Below % of Median Size:");
		Slider sizeFactor = new Slider(0.10, 0.85, 0.50);
		sizeFactor.setMaxWidth(Double.MAX_VALUE);
		Label sizeFactorIndicator = new Label();
		sizeFactorIndicator.textProperty().bind(sizeFactor.valueProperty().multiply(100).asString("%.0f%%"));
		FlowPane sizeFactorFlow = new FlowPane(sizeFactor, sizeFactorIndicator);
		sizeFactorFlow.setMaxWidth(Double.MAX_VALUE);
		sizeFactorFlow.setPrefWidth(512.0);

		grid.addRow(0, unusedLabel, unusedDays);
		grid.addRow(1, sizeFactorLabel, sizeFactorFlow);
		GridPane.setHalignment(unusedLabel, HPos.RIGHT);
		GridPane.setHalignment(sizeFactorFlow, HPos.RIGHT);

		ProgressBar progress = new ProgressBar(0.0);
		progress.setMaxWidth(Double.MAX_VALUE);
		grid.add(progress, 0, 2, 2, 1);

		Alert dlg = AlertBuilder.query(hostStage)
				.screen(screen)
				.type(Alert.AlertType.CONFIRMATION)
				.title("Clean Disk Cache")
				.headerText("Enter cleanup parameters:")
				.contentNode(grid)
				.buttons(ButtonType.OK, ButtonType.CANCEL)
				.modal(Modality.APPLICATION_MODAL)
				.get();

		Button ok = (Button) dlg.getDialogPane().lookupButton(ButtonType.OK);
		Button cancel = (Button) dlg.getDialogPane().lookupButton(ButtonType.CANCEL);
		ok.addEventFilter(ActionEvent.ACTION, event -> {
			ForkJoinPool.commonPool().submit(() -> {
				IOException tmpThrown;
				Images.CacheCleanupResults tmpResults;
				try {
					tmpResults = Context.get().images.cleanDiskCache(
							Instant.now().minus(unusedDays.getValue(), ChronoUnit.DAYS),
							sizeFactor.getValue(),
							d -> Platform.runLater(() -> progress.setProgress(d))
					);
					tmpThrown = null;
				} catch (IOException e) {
					tmpResults = null;
					tmpThrown = e;
				}

				final IOException thrown = tmpThrown;
				final Images.CacheCleanupResults results = tmpResults;
				Platform.runLater(() -> {
					if (results != null) {
						AlertBuilder.notify(hostStage)
								.title("Cache Cleanup")
								.headerText("Cleanup successful!")
								.contentText(String.format("%d files deleted totalling %d MB!", results.filesDeleted, results.deletedBytes / (1024 * 1024)))
								.modal(Modality.APPLICATION_MODAL)
								.show();
					} else if (thrown != null) {
						AlertBuilder.notify(hostStage)
								.type(Alert.AlertType.ERROR)
								.title("Cache Cleanup")
								.headerText("Error During Cleanup")
								.contentText("An error occurred while cleaning the cache: " + thrown.getMessage() + "\n")
								.modal(Modality.APPLICATION_MODAL)
								.show();
					} else {
						AlertBuilder.notify(hostStage)
								.type(Alert.AlertType.ERROR)
								.title("Cache Cleanup")
								.headerText("????")
								.contentText("Something very strange happened. Sorry...")
								.modal(Modality.APPLICATION_MODAL)
								.show();
					}

					dlg.close();
				});
			});

			ok.setDisable(true);
			cancel.setDisable(true);
			event.consume();
		});

		dlg.show();
		FxUtils.transfer(dlg, screen);
	}

	public void showChangeLog() {
		URL changelogUrl = MainApplication.class.getClassLoader().getResource("META-INF/changelog.md");
		String html;
		try (BufferedReader reader = new BufferedReader(new InputStreamReader(changelogUrl.openStream(), StandardCharsets.UTF_8))) {
			html = new MicroMarkdown(reader).toString();
		} catch (IOException ioe) {
			html = "<p>Sorry, we weren't able to load the changelog. Reason:</p>\n\n<p>" + ioe.getLocalizedMessage() + "</p>";
		}

		Alert alert = AlertBuilder.notify(hostStage)
				.screen(screen)
				.title("Change Log")
				.headerText("What's New")
				.contentHtml(html)
				.show();
		FxUtils.center(alert, screen);
	}
}
