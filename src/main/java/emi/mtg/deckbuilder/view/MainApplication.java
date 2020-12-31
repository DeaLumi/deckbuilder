package emi.mtg.deckbuilder.view;

import emi.lib.mtg.DataSource;
import emi.mtg.deckbuilder.controller.Context;
import emi.mtg.deckbuilder.controller.Updater;
import emi.mtg.deckbuilder.model.DeckList;
import emi.mtg.deckbuilder.view.dialogs.PreferencesDialog;
import emi.mtg.deckbuilder.view.dialogs.TagManagementDialog;
import emi.mtg.deckbuilder.view.util.AlertBuilder;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.event.ActionEvent;
import javafx.geometry.HPos;
import javafx.scene.Group;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.GridPane;
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
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.ForkJoinTask;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;

public class MainApplication extends Application {
	public static final ClassLoader PLUGIN_CLASS_LOADER;

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

	public static final List<DataSource> DATA_SOURCES = findDataSources();
	private static List<DataSource> findDataSources() {
		return Collections.unmodifiableList(StreamSupport.stream(ServiceLoader.load(DataSource.class, PLUGIN_CLASS_LOADER).spliterator(), true)
				.sorted(Comparator.comparing(Object::toString))
				.collect(Collectors.toList()));
	}

	private final HashSet<MainWindow> mainWindows = new HashSet<>();

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
			"Check the plugins/ directory for a data source plugin.",
			"At bare minimum, scryfall-0.0.0.jar should be provided!");

	private static final String DATA_LOAD_ERROR = String.join("\n",
			"An error occurred while loading card data.",
			"Some or all cards may not have been loaded..",
			"",
			"Try updating card data?");

	@Override
	public void start(Stage primaryStage) throws Exception {
		this.hostStage = primaryStage;
		hostStage.setScene(new Scene(new Group()));

		if (Runtime.getRuntime().maxMemory() <= 1024*1024*1024) {
			AlertBuilder.notify(primaryStage)
					.type(Alert.AlertType.WARNING)
					.title("Memory Limit Warning")
					.headerText("Low memory limit detected!")
					.contentText(LOW_MEMORY_LIMIT)
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

		DataSource data;
		if (DATA_SOURCES.size() == 0) {
			AlertBuilder.notify(hostStage)
					.type(Alert.AlertType.ERROR)
					.title("No Data Sources")
					.headerText("No data source is available!")
					.contentText(NO_DATA_SOURCES)
					.showAndWait();

			Platform.exit();
			return;
		} else if (DATA_SOURCES.size() > 1) {
			ChoiceDialog<DataSource> dialog = new ChoiceDialog<>(DATA_SOURCES.get(0), DATA_SOURCES.toArray(new DataSource[0]));
			dialog.initOwner(hostStage);
			dialog.setTitle("Select Data Source");
			dialog.setHeaderText("Multiple data sources available.");
			dialog.setContentText("Please select a data source:");
			data = dialog.showAndWait().orElse(null);

			if (data == null) {
				Platform.exit();
				return;
			}
		} else {
			data = DATA_SOURCES.get(0);
		}

		Context.instantiate(data);

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
				doGraphicalDataUpdate(null);
			}
		}

		doGraphicalLoadData();

		new MainWindow(this, new DeckList("", Context.get().preferences.authorName, Context.get().preferences.defaultFormat, "", Collections.emptyMap())).show();
	}

	public void doGraphicalLoadData() {
		if (!Platform.isFxApplicationThread()) {
			Platform.runLater(this::doGraphicalLoadData);
			return;
		}

		ProgressBar progress = new ProgressBar(0.0);
		progress.setPrefWidth(256.0);

		Alert alert = AlertBuilder.create()
				.owner(hostStage)
				.title("Loading")
				.headerText("Loading card data...")
				.contentNode(progress)
				.get();
		ForkJoinTask<Boolean> future = ForkJoinPool.commonPool().submit(() -> {
			try {
				Context.get().loadData(d -> Platform.runLater(() -> progress.setProgress(d)));
			} catch (IOException ioe) {
				return false;
			} finally {
				Platform.runLater(() -> {
					alert.getButtonTypes().setAll(ButtonType.CLOSE);
					alert.hide();
				});
			}

			return true;
		});
		alert.showAndWait();

		try {
			if (!future.get()) {
				if (AlertBuilder.query(hostStage)
						.type(Alert.AlertType.ERROR)
						.title("Data Load Error")
						.headerText("An error occurred while loading data.")
						.contentText(DATA_LOAD_ERROR)
						.showAndWait().orElse(ButtonType.NO) == ButtonType.YES) {
					doGraphicalDataUpdate(Context.get().data);
					doGraphicalLoadData();
				}
			}
		} catch (InterruptedException | ExecutionException e) {
			throw new RuntimeException(e);
		}
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

		ForkJoinPool.commonPool().submit(() -> {
			try {
				updater.update(d -> Platform.runLater(() -> pbar.setProgress(d)));
			} catch (Exception e) {
				throw new RuntimeException(e);
			} finally {
				Platform.runLater(progressDialog::close);
			}
		});

		progressDialog.showAndWait();
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

		doGraphicalDataUpdate(null);
	}

	public void doGraphicalDataUpdate(DataSource earlyData) {
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

		DataSource data = earlyData == null ? Context.get().data : earlyData;

		ForkJoinPool.commonPool().submit(() -> {
			try {
				if (earlyData == null) Context.get().saveTags();
				if (data.update(d -> Platform.runLater(() -> pbar.setProgress(d)))) {
					Platform.runLater(() -> {
						progressDialog.close();

						for (MainWindow child : mainWindows) {
							child.remodel();
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

	public boolean showPreferences() {
		try {
			PreferencesDialog pd = new PreferencesDialog(Context.get().preferences);
			pd.initOwner(hostStage);

			if(pd.showAndWait().orElse(false)) {
				Context.get().savePreferences();
				return true;
			}
		} catch (IOException e) {
			e.printStackTrace();
			throw new AssertionError(e);
		}

		return false;
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

	public void trimImageDiskCache() {
		GridPane grid = new GridPane();
		grid.setHgap(8.0);
		grid.setVgap(8.0);

		Label unusedLabel = new Label("Max Days w/o Loading:");
		Spinner<Integer> unusedDays = new Spinner<>(1, 365, 180);
		unusedDays.setMaxWidth(Double.MAX_VALUE);

		Label sizeFactorLabel = new Label("Min % of Median Size:");
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
				.type(Alert.AlertType.CONFIRMATION)
				.title("Clean Disk Cache")
				.headerText("Enter cleanup parameters:")
				.contentNode(grid)
				.buttons(ButtonType.OK, ButtonType.CANCEL)
				.get();

		Button ok = (Button) dlg.getDialogPane().lookupButton(ButtonType.OK);
		Button cancel = (Button) dlg.getDialogPane().lookupButton(ButtonType.CANCEL);
		ok.addEventFilter(ActionEvent.ACTION, event -> {
			ForkJoinPool.commonPool().submit(() -> {
				IOException tmpThrown;
				Images.CacheCleanupResults tmpResults;
				try {
					tmpResults = Context.get().images.cleanCache(
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
								.showAndWait();
					} else if (thrown != null) {
						AlertBuilder.notify(hostStage)
								.type(Alert.AlertType.ERROR)
								.title("Cache Cleanup")
								.headerText("Error During Cleanup")
								.contentText("An error occurred while cleaning the cache: " + thrown.getMessage() + "\n")
								.showAndWait();
					} else {
						AlertBuilder.notify(hostStage)
								.type(Alert.AlertType.ERROR)
								.title("Cache Cleanup")
								.headerText("????")
								.contentText("Something very strange happened. Sorry...")
								.showAndWait();
					}

					dlg.close();
				});
			});

			ok.setDisable(true);
			cancel.setDisable(true);
			event.consume();
		});

		dlg.show();
	}
}
