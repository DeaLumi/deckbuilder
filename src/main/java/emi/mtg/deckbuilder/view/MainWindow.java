package emi.mtg.deckbuilder.view;

import emi.lib.mtg.Card;
import emi.lib.mtg.DataSource;
import emi.lib.mtg.game.Format;
import emi.lib.mtg.game.Zone;
import emi.mtg.deckbuilder.controller.Context;
import emi.mtg.deckbuilder.controller.Tags;
import emi.mtg.deckbuilder.controller.serdes.DeckImportExport;
import emi.mtg.deckbuilder.controller.serdes.impl.ImageExporter;
import emi.mtg.deckbuilder.controller.serdes.impl.Json;
import emi.mtg.deckbuilder.model.CardInstance;
import emi.mtg.deckbuilder.model.DeckList;
import emi.mtg.deckbuilder.model.Preferences;
import emi.mtg.deckbuilder.model.State;
import emi.mtg.deckbuilder.view.components.CardPane;
import emi.mtg.deckbuilder.view.components.CardView;
import emi.mtg.deckbuilder.view.components.DeckPane;
import emi.mtg.deckbuilder.view.components.DeckTab;
import emi.mtg.deckbuilder.view.dialogs.DeckInfoDialog;
import emi.mtg.deckbuilder.view.dialogs.PreferencesDialog;
import emi.mtg.deckbuilder.view.dialogs.PrintingSelectorDialog;
import emi.mtg.deckbuilder.view.groupings.ManaValue;
import emi.mtg.deckbuilder.view.layouts.FlowGrid;
import emi.mtg.deckbuilder.view.search.SearchProvider;
import emi.mtg.deckbuilder.view.util.AlertBuilder;
import javafx.beans.binding.Bindings;
import javafx.collections.FXCollections;
import javafx.collections.ListChangeListener;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.geometry.Bounds;
import javafx.scene.Group;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.image.WritableImage;
import javafx.scene.input.*;
import javafx.scene.layout.BorderPane;
import javafx.scene.paint.Color;
import javafx.scene.shape.*;
import javafx.stage.FileChooser;
import javafx.stage.Modality;
import javafx.stage.Stage;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.*;
import java.util.concurrent.ForkJoinPool;
import java.util.function.Consumer;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class MainWindow extends Stage {
	@FXML
	private SplitPane collectionSplitter;

	@FXML
	private Menu newDeckMenu;

	@FXML
	private Menu openRecentDeckMenu;

	@FXML
	private TabPane deckTabs;

	@FXML
	private CheckMenuItem autoValidateDeck;

	@FXML
	private Menu debugMenu;

	private CardPane collection;

	private FileChooser primaryFileChooser;
	private DeckImportExport primarySerdes;

	private Map<FileChooser.ExtensionFilter, DeckImportExport> importSerdes, exportSerdes;
	private FileChooser serdesFileChooser;
	private final MainApplication owner;
	private final Consumer<Preferences> prefsListener;

	private final ListChangeListener<Object> mruChangedListener = e -> {
		this.openRecentDeckMenu.getItems().setAll(State.get().recentDecks.stream()
				.map(path -> {
					MenuItem item = new MenuItem(path.toAbsolutePath().toString());
					item.setOnAction(ae -> openDeck(path));
					return item;
				})
				.collect(Collectors.toList()));
	};

	public MainWindow(MainApplication owner) {
		super();

		this.owner = owner;
		this.owner.registerMainWindow(this);

		BorderPane root = new BorderPane();
		FXMLLoader loader = new FXMLLoader();
		loader.setRoot(root);
		loader.setControllerFactory(cls -> this);
		try {
			loader.load(getClass().getResourceAsStream(getClass().getSimpleName() + ".fxml"));
		} catch (IOException ioe) {
			throw new AssertionError(ioe);
		}

		setTitle("Deck Builder v0.0.0");
		setScene(new Scene(root, 1024, 1024));
		getScene().getStylesheets().add(MainApplication.class.getResource("styles.css").toExternalForm());
		root.setStyle(Preferences.get().theme.style());

		setupUI();
		setupImportExport();

		setOnCloseRequest(we -> {
			if (!offerSaveIfModifiedAll()) {
				we.consume();
				return;
			}

			State.get().recentDecks.removeListener(mruChangedListener);
			owner.deregisterMainWindow(this);
		});

		collection.loading().set(true);

		Preferences.listen(prefsListener = this::preferencesChanged);

		ForkJoinPool.commonPool().submit(() -> {
			collection.filter().setText(Preferences.get().defaultQuery);
			collection.updateFilter();
			collection.changeModel(x -> x.setAll(collectionModel(Context.get().data)));
			collection.loading().set(false);
		});
	}

	@FXML
	protected void createEmergency() {
		throw new RuntimeException("Pattern Analysis: Blood Type = Blue!");
	}

	@FXML
	protected void flushImageCaches() {
		Context.get().images.flushMemoryCaches();
	}

	void emergencySave() throws IOException {
		List<IOException> exceptions = new ArrayList<>();
		allDecks().forEach(deck -> {
			try {
				primarySerdes.exportDeck(deck, Files.createTempFile(
						MainApplication.JAR_DIR,
						String.format("emergency-save-%s-", deck.name()), ".json"));
			} catch (IOException ioe) {
				exceptions.add(ioe);
			}
		});

		if (exceptions.size() == 0) return;
		if (exceptions.size() == 1) throw exceptions.get(0);

		IOException thrown = new IOException("Multiple exceptions encountered during emergency save.");
		exceptions.forEach(thrown::addSuppressed);
		throw thrown;
	}

	public Stream<DeckTab> allTabs() {
		return deckTabs.getTabs().stream()
				.map(t -> (DeckTab) t);
	}

	public Stream<DeckPane> allPanes() {
		return allTabs().map(DeckTab::pane);
	}

	public Stream<DeckList> allDecks() {
		return allPanes().map(DeckPane::deck);
	}

	public DeckTab activeTab() {
		return (DeckTab) deckTabs.getSelectionModel().getSelectedItem();
	}

	public DeckPane activeDeckPane() {
		DeckTab activeTab = activeTab();
		return activeTab == null ? null : activeTab.pane();
	}

	public DeckList activeDeck() {
		DeckPane pane = activeDeckPane();
		return pane == null ? null : pane.deck();
	}

	private void flagCollectionCardLegality(CardInstance ci) {
		Format format = activeDeck() == null ? Preferences.get().defaultFormat : activeDeck().format();

		ci.flags.clear();
		ci.flags.add(CardInstance.Flags.Unlimited);
		switch (ci.card().legality(format)) {
			case Legal:
			case Restricted:
				break;
			case Banned:
				ci.flags.add(CardInstance.Flags.Invalid);
				break;
			case NotLegal:
				if (Preferences.get().theFutureIsNow && (ci.card().legality(Format.Future) == Card.Legality.Legal || ci.card().printings().stream().allMatch(pr -> pr.releaseDate().isAfter(LocalDate.now())))) {
					ci.flags.add(CardInstance.Flags.Warning);
				} else {
					ci.flags.add(CardInstance.Flags.Invalid);
				}
				break;
			case Unknown:
				ci.flags.add(CardInstance.Flags.Warning);
				break;
		}
	}

	private ObservableList<CardInstance> collectionModel(DataSource cs) {
		return FXCollections.observableList(cs.printings().stream()
				.map(CardInstance::new)
				.peek(ci -> ci.flags.add(CardInstance.Flags.Unlimited))
				.peek(ci -> ci.tags().addAll(Context.get().tags.tags(ci.card())))
				.peek(this::flagCollectionCardLegality)
				.collect(Collectors.toList()));
	}

	private CardView.ContextMenu createCollectionContextMenu() {
		CardView.ContextMenu menu = new CardView.ContextMenu();

		MenuItem changePrintingMenuItem = new MenuItem("Prefer Printing");
		changePrintingMenuItem.visibleProperty().bind(collection.showingVersionsSeparately.not()
				.and(Bindings.createBooleanBinding(() -> menu.cards.size() == 1 && menu.cards.iterator().next().card().printings().size() > 1, menu.cards)));
		changePrintingMenuItem.setOnAction(ae -> {
			if (collection.showingVersionsSeparately.get()) {
				return;
			}

			if (menu.cards.isEmpty()) {
				return;
			}

			Set<Card> cards = menu.cards.stream().map(CardInstance::card).collect(Collectors.toSet());
			if (cards.size() != 1) {
				return;
			}

			final Card card = cards.iterator().next();

			if (card.printings().size() <= 1) {
				return;
			}

			PrintingSelectorDialog.show(getScene(), card).ifPresent(pr -> {
				Preferences.get().preferredPrintings.put(card.fullName(), new Preferences.PreferredPrinting(pr.set().code(), pr.collectorNumber()));
				ForkJoinPool.commonPool().submit(collection::updateFilter);
			});
		});

		Menu tagsMenu = new Menu("Tags");

		menu.showingProperty().addListener(showing -> {
			final Tags tags = Context.get().tags;
			ObservableList<MenuItem> tagCBs = FXCollections.observableArrayList();
			tagCBs.setAll(tags.tags().stream()
					.map(CheckMenuItem::new)
					.peek(cmi -> cmi.setSelected(menu.cards.stream().allMatch(ci -> tags.tags(ci.card()).contains(cmi.getText()))))
					.peek(cmi -> cmi.selectedProperty().addListener((cmiObj, wasSelected, isSelected) -> {
						if (isSelected) {
							menu.cards.forEach(ci -> {
								ci.tags().add(cmi.getText());
								tags.add(ci.card(), cmi.getText());
							});
						} else {
							menu.cards.forEach(ci -> {
								ci.tags().remove(cmi.getText());
								tags.remove(ci.card(), cmi.getText());
							});

							if (tags.cards(cmi.getText()) == null || tags.cards(cmi.getText()).isEmpty()) {
								tags.tags().remove(cmi.getText());
							}
						}
						collection.view().refreshCardGrouping();
					}))
					.collect(Collectors.toList())
			);
			tagCBs.add(new SeparatorMenuItem());

			TextField newTagField = new TextField();
			CustomMenuItem newTagMenuItem = new CustomMenuItem(newTagField);
			newTagMenuItem.setHideOnClick(false);
			newTagField.setPromptText("New tag...");
			newTagField.setOnAction(ae -> {
				if (newTagField.getText().isEmpty()) {
					ae.consume();
					return;
				}

				tags.add(newTagField.getText());
				menu.cards.forEach(ci -> tags.add(ci.card(), newTagField.getText()));
				collection.view().regroup();
				menu.hide();
			});

			tagCBs.add(newTagMenuItem);

			tagsMenu.getItems().setAll(tagCBs);
		});

		Menu fillMenu = new Menu("Fill");
		fillMenu.visibleProperty().bind(menu.cards.emptyProperty().not());

		for (Zone zone : Zone.values()) {
			MenuItem fillZoneMenuItem = new MenuItem(zone.name());
			fillZoneMenuItem.visibleProperty().bind(Bindings.createBooleanBinding(() -> activeDeck() != null && activeDeck().format().deckZones().contains(zone), menu.showingProperty()));
			fillZoneMenuItem.setOnAction(ae -> {
				activeDeckPane().zonePane(zone).changeModel(model -> {
					for (CardInstance source : menu.cards) {
						long count = activeDeck().format().maxCopies - model.parallelStream().filter(ci -> ci.card().equals(source.card())).count(); // TODO: Should count all zones.
						for (int i = 0; i < count; ++i) {
							model.add(new CardInstance(source));
						}
					}
				});
			});
			fillMenu.getItems().add(fillZoneMenuItem);
		}

		MenuItem deleteImages = new MenuItem("Delete Saved Images");
		deleteImages.setOnAction(ae -> {
			menu.cards.forEach(ci -> {
				try {
					Context.get().images.deleteSavedImages(ci.printing());
				} catch (IOException e) {
					e.printStackTrace(); // These aren't fatal as far as I'm concerned...
				}
			});
		});

		menu.getItems().addAll(changePrintingMenuItem, tagsMenu, fillMenu, deleteImages);

		return menu;
	}

	private void setupUI() {
		debugMenu.setVisible(Preferences.get().showDebugOptions);

		collection = new CardPane("Collection",
				FXCollections.observableArrayList(),
				FlowGrid.Factory.INSTANCE,
				Preferences.get().collectionGrouping,
				Preferences.get().collectionSorting);
		collection.view().immutableModelProperty().set(true);
		collection.view().doubleClick(ci -> activeDeckPane().zonePane(Zone.Library).changeModel(x -> x.add(new CardInstance(ci))));
		collection.autoAction.set(ci -> activeDeckPane().zonePane(Zone.Library).changeModel(x -> x.add(new CardInstance(ci))));

		collection.view().contextMenu(createCollectionContextMenu());

		collection.showingIllegalCards.set(false);
		collection.showingVersionsSeparately.set(false);

		this.collectionSplitter.getItems().add(0, collection);

		autoValidateDeck.setSelected(true);
		allPanes().forEach(pane -> {
			pane.autoValidateProperty().bind(autoValidateDeck.selectedProperty());
			pane.setOnDeckChanged(lce -> updateCollectionState());
		});

		deckTabs.setSide(javafx.geometry.Side.TOP);
		deckTabs.getStyleClass().add("flashy-tabs");
		deckTabs.setTabClosingPolicy(TabPane.TabClosingPolicy.ALL_TABS);
		deckTabs.setRotateGraphic(true);
		deckTabs.getSelectionModel().selectedItemProperty().addListener((obs, oldTab, newTab) -> {
			DeckPane old = oldTab == null ? null : (DeckPane) oldTab.getContent();
			DeckPane newp = newTab == null ? null : (DeckPane) newTab.getContent();

			ForkJoinPool.commonPool().submit(() -> {
				if (old != null && newp != null && !old.deck().format().equals(newp.deck().format())) collection.updateFilter();
				updateCollectionState();
				collection.view().scheduleRender();
			});

			MainWindow.this.titleProperty().unbind();
			if (newp == null) {
				MainWindow.this.setTitle("Deckbuilder v0.0.0");
			} else {
				MainWindow.this.titleProperty().bind(Bindings.createStringBinding(() -> {
					if (newp.deck().name().isEmpty()) {
						return "Unnamed Deck - Deckbuilder v0.0.0";
					} else {
						return newp.deck().name() + " - Deckbuilder v0.0.0";
					}
				}, newp.deck().nameProperty()));
			}
		});

		deckTabs.setOnDragOver(de -> {
			if (!de.getDragboard().hasContent(DeckTab.DRAGGED_TAB)) return;
			if (DeckTab.draggedTab == null) return;

			de.acceptTransferModes(TransferMode.MOVE);
			de.consume();
		});

		final Rectangle feedbackRect = new Rectangle(), feedbackOutline = new Rectangle();
		final Group feedbackGroup = new Group(feedbackRect, feedbackOutline);
		final BorderPane root = (BorderPane) getScene().getRoot();

		feedbackRect.setFill(Color.gray(0.25));
		feedbackRect.setOpacity(0.5);

		feedbackOutline.setStroke(Color.gray(0.75));
		feedbackOutline.setStrokeWidth(4.0);
		feedbackOutline.setStrokeLineJoin(StrokeLineJoin.ROUND);
		feedbackOutline.setStrokeType(StrokeType.CENTERED);
		feedbackOutline.setStrokeLineCap(StrokeLineCap.ROUND);
		feedbackOutline.setOpacity(0.75);
		feedbackOutline.setFill(Color.TRANSPARENT);

		for (Node child : feedbackGroup.getChildrenUnmodifiable()) {
			child.setManaged(false);
			child.setMouseTransparent(true);
		}

		deckTabs.setOnDragEntered(de -> {
			if (!de.getDragboard().hasContent(DeckTab.DRAGGED_TAB)) return;
			if (DeckTab.draggedTab == null) return;
			if (deckTabs.getTabs().contains(DeckTab.draggedTab)) return;

			Bounds bounds = root.screenToLocal(deckTabs.localToScreen(deckTabs.getLayoutBounds()));
			feedbackRect.setX(bounds.getMinX());
			feedbackRect.setY(bounds.getMinY());
			feedbackRect.setWidth(bounds.getWidth());
			feedbackRect.setHeight(bounds.getHeight());

			double mins = Math.min(bounds.getWidth(), bounds.getHeight());
			double dash = (2*(bounds.getWidth() - mins * 0.1) + 2*(bounds.getHeight() - mins * 0.1)) / 101;
			feedbackOutline.getStrokeDashArray().setAll(dash * 0.5, dash * 0.5);
			feedbackOutline.setStrokeDashOffset(dash * 0.25);
			feedbackOutline.setX(bounds.getMinX() + mins * 0.05);
			feedbackOutline.setY(bounds.getMinY() + mins * 0.05);
			feedbackOutline.setWidth(bounds.getWidth() - mins * 0.1);
			feedbackOutline.setHeight(bounds.getHeight() - mins * 0.1);

			root.getChildren().add(feedbackGroup);
		});

		deckTabs.setOnDragExited(de -> {
			if (!de.getDragboard().hasContent(DeckTab.DRAGGED_TAB)) return;
			if (DeckTab.draggedTab == null) return;

			root.getChildren().remove(feedbackGroup);
		});

		deckTabs.setOnDragDropped(de -> {
			if (!de.getDragboard().hasContent(DeckTab.DRAGGED_TAB)) return;
			if (DeckTab.draggedTab == null) return;

			if (!deckTabs.getTabs().contains(DeckTab.draggedTab)) {
				addDeck(DeckTab.draggedTab.pane().deck());
				DeckTab.draggedTab.reallyForceClose();
			}

			de.setDropCompleted(true);
			de.consume();
		});

		deckTabs.setOnDragDone(de -> {
			if (!de.isDropCompleted() && de.getAcceptedTransferMode() == null && DeckTab.draggedTab != null && deckTabs.getTabs().contains(DeckTab.draggedTab)) {
				undock(DeckTab.draggedTab);
			}

			if (deckTabs.getTabs().isEmpty()) {
				close();
			}

			DeckTab.draggedTab = null;
			de.consume();
		});
	}

	private void setupImportExport() {
		this.primarySerdes = new Json();

		this.primaryFileChooser = new FileChooser();
		this.primaryFileChooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("JSON Files (*.json)", "*.json"));

		for (Format format : Format.values()) {
			MenuItem item = new MenuItem(format.name());
			item.setOnAction(ae -> openDeckPane(new DeckList("", Preferences.get().authorName, format, "", Collections.emptyMap())));
			item.setUserData(format);

			if (format == Preferences.get().defaultFormat) {
				item.setAccelerator(new KeyCodeCombination(KeyCode.N, KeyCombination.SHORTCUT_DOWN));
			}

			this.newDeckMenu.getItems().add(item);
		}

		State.get().recentDecks.addListener(mruChangedListener);
		mruChangedListener.onChanged(null);

		this.serdesFileChooser = new FileChooser();
		this.importSerdes = new HashMap<>();
		this.exportSerdes = new HashMap<>();

		for (DeckImportExport s : DeckImportExport.DECK_FORMAT_PROVIDERS) {
			if (!s.importExtensions().isEmpty()) {
				this.importSerdes.put(new FileChooser.ExtensionFilter(s.toString(), s.importExtensions().stream().map(e -> "*." + e).collect(Collectors.toList())), s);
			}
			if (!s.exportExtensions().isEmpty()) {
				this.exportSerdes.put(new FileChooser.ExtensionFilter(s.toString(), s.exportExtensions().stream().map(e -> "*." + e).collect(Collectors.toList())), s);
			}
		}
	}

	public MainWindow undock(DeckTab tab) {
		if (!deckTabs.getTabs().contains(tab)) return null;
		if (deckTabs.getTabs().size() == 1) return null;

		MainWindow window = new MainWindow(MainWindow.this.owner);
		window.addDeck(tab.pane().deck());
		window.show();
		window.setX(getX() + 24.0);
		window.setY(getY() + 24.0);
		tab.reallyForceClose();

		return window;
	}

	public DeckTab addDeck(DeckList deck) {
		DeckPane pane = new DeckPane(deck);
		pane.autoValidateProperty().bind(autoValidateDeck.selectedProperty());
		pane.setOnDeckChanged(lce -> updateCollectionState());
		DeckTab tab = new DeckTab(pane);

		tab.closableProperty().bind(Bindings.size(deckTabs.getTabs()).greaterThan(1));

		tab.setOnCloseRequest(ce -> {
			if (deck.modified()) {
				deckTabs.getSelectionModel().select(tab);
				if (!offerSaveIfModified(deck)) {
					ce.consume();
				}
			}
		});

		deckTabs.getTabs().add(tab);
		deckTabs.getSelectionModel().select(tab);

		return tab;
	}

	public boolean openDeckPane(DeckList forDeck) {
		// We always open a new tab if we don't have any tabs open.
		Preferences.WindowBehavior behavior = !allTabs().findAny().isPresent() ? Preferences.WindowBehavior.NewTab : Preferences.get().windowBehavior;
		if (behavior == Preferences.WindowBehavior.AlwaysAsk) {
			CheckBox rememberCb = new CheckBox("Remember This Choice");
			Alert alert = AlertBuilder.query(this)
					.title("Window Behavior")
					.headerText("Where should this deck be opened?")
					.contentText("You can choose to replace the current window (after being prompted to save any changes), open a new tab for this deck, or open a new window for this deck.")
					.buttons(
							new ButtonType(Preferences.WindowBehavior.NewTab.toString()),
							new ButtonType(Preferences.WindowBehavior.NewWindow.toString()),
							new ButtonType(Preferences.WindowBehavior.ReplaceCurrent.toString()),
							ButtonType.CANCEL
					).get();
			alert.getDialogPane().setExpandableContent(rememberCb);
			alert.getDialogPane().setExpanded(true);
			ButtonType bt = alert.showAndWait().orElse(ButtonType.CANCEL);

			if (bt == ButtonType.CANCEL) return false;

			behavior = Arrays.stream(Preferences.WindowBehavior.values()).filter(v -> v.toString().equals(bt.getText())).findAny().orElseThrow(() -> new NoSuchElementException("???"));

			if (rememberCb.isSelected()) {
				Preferences.get().windowBehavior = behavior;
			}
		}

		switch (behavior) {
			case ReplaceCurrent:
				if (!activeTab().forceClose()) return false; // Intentional fallthrough
			case NewTab:
				addDeck(forDeck);
				break;
			case NewWindow:
				MainWindow window = new MainWindow(this.owner);
				window.addDeck(forDeck);
				window.setX(getX() + 24.0);
				window.setY(getY() + 24.0);
				window.show();
				break;
			default:
				throw new IllegalStateException("Behavior shouldn't still be ask...");
		}

		return true;
	}

	private void openDeck(Path from) {
		if (from == null) return;

		try {
			DeckList list = primarySerdes.importDeck(from);

			if (list.cards() == null || list.format() == null) {
				AlertBuilder.notify(this)
						.type(Alert.AlertType.ERROR)
						.title("Invalid Deck File")
						.headerText("The chosen file couldn't be loaded.")
						.contentText("It may be an older format, or it may not be a Deckbuilder save file.")
						.modal(Modality.WINDOW_MODAL)
						.show();
				return;
			}

			list.sourceProperty().setValue(from);
			if (openDeckPane(list)) {
				State.get().lastLoadDirectory = from.getParent();
				State.get().addRecentDeck(from);
			}
		} catch (IOException ioe) {
			ioe.printStackTrace();
			AlertBuilder.notify(this)
					.type(Alert.AlertType.ERROR)
					.title("Open Error")
					.headerText("An error occurred while opening:")
					.contentText(ioe.getMessage())
					.modal(Modality.WINDOW_MODAL)
					.show();
		}
	}

	@FXML
	protected void openDeck() {
		if (State.get().lastLoadDirectory != null) primaryFileChooser.setInitialDirectory(State.get().lastLoadDirectory.toFile());
		File from = primaryFileChooser.showOpenDialog(this);

		if (from == null) {
			return;
		}

		openDeck(from.toPath());
	}

	protected boolean offerSaveIfModifiedAll() {
		return allDecks().allMatch(this::offerSaveIfModified);
	}

	protected boolean offerSaveIfModified(DeckList deck) {
		if (deck.modified()) {
			ButtonType type = AlertBuilder.query(this)
					.type(Alert.AlertType.WARNING)
					.title("Deck Modified")
					.headerText((deck.name().isEmpty() ? "Unnamed Deck" : deck.name()) + " has been modified.")
					.contentText("Would you like to save this deck?")
					.buttons(ButtonType.YES, ButtonType.NO, ButtonType.CANCEL)
					.modal(Modality.WINDOW_MODAL)
					.showAndWait().orElse(ButtonType.CANCEL);

			if (type == ButtonType.CANCEL) {
				return false;
			}

			if (type == ButtonType.YES) {
				return doSaveDeck(deck);
			}
		}

		return true;
	}

	@FXML
	protected void saveDeck() {
		doSaveDeck(activeDeck());
	}

	protected boolean doSaveDeck(DeckList deck) {
		if (deck.source() == null) {
			return doSaveDeckAs(deck, true);
		} else {
			return saveDeck(deck, deck.source(), true);
		}
	}

	@FXML
	protected void saveDeckAs() {
		doSaveDeckAs(activeDeck(), true);
	}

	@FXML
	protected void saveDeckCopy() {
		doSaveDeckAs(activeDeck(), false);
	}

	protected boolean doSaveDeckAs(DeckList deck, boolean setSource) {
		if (State.get().lastSaveDirectory != null) primaryFileChooser.setInitialDirectory(State.get().lastSaveDirectory.toFile());
		File to = primaryFileChooser.showSaveDialog(this);

		if (to == null) {
			return false;
		}

		return saveDeck(deck, to.toPath(), setSource);
	}

	private boolean saveDeck(DeckList deck, Path to, boolean setSource) {
		try {
			primarySerdes.exportDeck(deck, to);

			if (setSource) {
				deck.modifiedProperty().set(false);
				deck.sourceProperty().setValue(to);
				State.get().lastSaveDirectory = activeDeck().source().getParent();
				State.get().addRecentDeck(to);
			}

			return true;
		} catch (IOException ioe) {
			ioe.printStackTrace();
			AlertBuilder.notify(this)
					.type(Alert.AlertType.ERROR)
					.title("Save Error")
					.headerText("An error occurred while saving:")
					.contentText(ioe.getMessage())
					.modal(Modality.WINDOW_MODAL)
					.showAndWait();
			return false;
		}
	}

	private boolean warnAboutSerdes(DeckImportExport serdes) {
		EnumSet<DeckImportExport.Feature> unsupportedFeatures = EnumSet.complementOf(serdes.supportedFeatures());
		if (unsupportedFeatures.isEmpty() && !serdes.importExtensions().isEmpty() && !serdes.exportExtensions().isEmpty()) return false;

		StringBuilder builder = new StringBuilder();

		builder.append("The file format you selected doesn't support the following features:\n");

		for (DeckImportExport.Feature feature : unsupportedFeatures) {
			builder.append(" \u2022 ").append(feature.toString()).append('\n');
		}

		if (serdes.importExtensions().isEmpty()) {
			builder.append('\n').append("Additionally, you will not be able to re-import from this file.");
		} else if (serdes.exportExtensions().isEmpty()) {
			builder.append('\n').append("Additionally, you will not be able to re-export to this file.");
		}

		builder.append('\n').append("Is this okay?");

		return AlertBuilder.query(this)
				.title("Warning")
				.headerText("Some information may be lost:")
				.contentText(builder.toString())
				.modal(Modality.WINDOW_MODAL)
				.showAndWait()
				.orElse(ButtonType.NO) != ButtonType.YES;
	}

	@FXML
	protected void showDeckInfoDialog() {
		final DeckPane pane = activeDeckPane();
		final Format oldFormat = pane.deck().format();
		DeckInfoDialog did = new DeckInfoDialog(this, pane.deck());
		did.initOwner(this);

		if(did.showAndWait().orElse(false) && !oldFormat.equals(pane.deck().format())) {
			pane.applyDeck();
		}
	}

	@FXML
	protected void actionQuit() {
		owner.closeAllWindows();
	}

	private static final String TIPS_AND_TRICKS = String.join("\n",
			"The UI of this program is really dense! Here are some bits on some subtle but powerful features!",
			"",
			"General stuff:",
			"\u2022 Double-click on a deck tab to change the deck's name.",
			"\u2022 Right click on a deck tab for a handy menu.",
			"\u2022 Toggle on 'Auto' mode to immediately add any single-result searches to the deck.",
			"",
			"Card versions:",
			"\u2022 Alt+Click on cards to show all printings.",
			"\u2022 Double-click a printing to change the version of the card you clicked on.",
			"",
			"Tags:",
			"\u2022 Right click a card to assign tags.",
			"\u2022 Change any view to Grouping -> Tags to group cards by their tags.",
			"\u2022 While grouped by tags, drag cards to their tag groups to assign tags.",
			"\u2022 You can even Control+Drag to assign multiple tags to a card.",
			"\u2022 Search for cards by tag with the 'tag' filter: \"tag:wrath\"",
			"\u2022 Tags assigned to cards in the collection are saved globally and transfer one-way into decks.",
			"\u2022 Tags are also saved and can be edited in decks, independently of the collection.",
			"",
			"I never claimed to be good at UI design! :^)");

	@FXML
	protected void showTipsAndTricks() {
		AlertBuilder.notify(this)
				.title("Program Usage")
				.headerText("Tips and Tricks")
				.contentText(TIPS_AND_TRICKS)
				.showAndWait();
	}

	@FXML
	protected void showSearchHelp() throws IOException {
		SearchProvider provider = Preferences.get().searchProvider;

		AlertBuilder.notify(this)
				.title("Search Help")
				.headerText(provider.name() + ": Usage")
				.contentMarkdown(provider.usage())
				.showAndWait();
	}

	@FXML
	protected void showChangelog() throws IOException {
		owner.showChangeLog();
	}

	private static final String ABOUT_TEXT = String.join("\n",
			"Developer: Emi (dealuminis@gmail.com)",
			"Data & Images: Scryfall (www.scryfall.com)",
			"Time Spiral SVG: Keyrune (keyrune.andrewgioia.com)",
			"",
			"Source code is almost available on GitHub, I swear.",
			"Feel free to DM me with feedback/issues on Twitter!",
			"",
			"Special thanks:",
			"- MagnetMan, for generously indulging my madness time and time again.",
			"- SpookySquid, for good ideas and listening to me yell.",
			"- Fangs, for listening to me yell and good ideas.",
			"- Vash, Akvar, nobodi, and many others, for feedback and testing.");

	@FXML
	protected void showAboutDialog() {
		AlertBuilder.notify(this)
				.title("About Deck Builder")
				.headerText("Deck Builder v0.0.0")
				.contentText(ABOUT_TEXT)
				.modal(Modality.WINDOW_MODAL)
				.showAndWait();
	}

	private void updateCollectionState() {
		Set<Card> fullCards = activeDeckPane().fullCards();
		collection.changeModel(x -> x.forEach(ci -> {
			flagCollectionCardLegality(ci);
			if (fullCards.contains(ci.card())) {
				ci.flags.add(CardInstance.Flags.Full);
			} else {
				ci.flags.remove(CardInstance.Flags.Full);
			}
		}));
		collection.view().scheduleRender();
	}

	private void updateCardStates(Format.ValidationResult result) {
		activeDeckPane().updateCardStates(result);
		updateCollectionState();
	}

	@FXML
	protected void validateDeckAndNotify() {
		Format.ValidationResult result = activeDeck().validate();
		updateCardStates(result);

		if (result.deckErrors.isEmpty() &&
				result.zoneErrors.values().stream().allMatch(Set::isEmpty) &&
				result.cards.values().stream()
						.allMatch(cr -> cr.errors.isEmpty() && cr.warnings.isEmpty() && cr.notices.isEmpty())) {
			AlertBuilder.notify(this)
					.title("Deck Validation")
					.headerText("Deck is valid.")
					.contentText("No validation messages to report!")
					.modal(Modality.WINDOW_MODAL)
					.show();
		} else {
			StringBuilder msg = new StringBuilder();
			for (String err : result.deckErrors) {
				msg.append("\u2022 ").append(err).append("\n");
			}

			for (Map.Entry<Zone, Set<String>> zone : result.zoneErrors.entrySet()) {
				if (zone.getValue().isEmpty()) continue;

				msg.append("\n").append(zone.getKey().name()).append(":\n");
				for (String err : zone.getValue()) {
					msg.append("\u2022 ").append(err).append("\n");
				}
			}

			Set<String> cardErrors = result.cards.values().stream()
					.flatMap(cr -> cr.errors.stream())
					.collect(Collectors.toSet());
			if (!cardErrors.isEmpty()) {
				msg.append("\nCard errors:\n");
				for (String err : cardErrors) {
					msg.append("\u2022 ").append(err).append("\n");
				}
			}

			Set<String> cardWarnings = result.cards.values().stream()
					.flatMap(cr -> cr.warnings.stream())
					.collect(Collectors.toSet());
			if (!cardWarnings.isEmpty()) {
				msg.append("\nCard warnings:\n");
				for (String err : cardWarnings) {
					msg.append("\u2022 ").append(err).append("\n");
				}
			}

			Set<String> cardNotices = result.cards.values().stream()
					.flatMap(cr -> cr.notices.stream())
					.collect(Collectors.toSet());
			if (!cardNotices.isEmpty()) {
				msg.append("\nCard notices:\n");
				for (String err : cardNotices) {
					msg.append("\u2022 ").append(err).append("\n");
				}
			}

			Alert.AlertType type;
			if (!result.deckErrors.isEmpty() || !result.zoneErrors.isEmpty() || !cardErrors.isEmpty()) {
				type = Alert.AlertType.ERROR;
			} else if (!cardWarnings.isEmpty()) {
				type = Alert.AlertType.WARNING;
			} else {
				type = Alert.AlertType.INFORMATION;
			}

			AlertBuilder.notify(this)
					.type(type)
					.title("Deck Validation")
					.headerText("Validation messages:")
					.contentText(msg.toString().trim())
					.modal(Modality.WINDOW_MODAL)
					.show();
		}
	}

	@FXML
	protected void showPreferencesDialog() {
		PreferencesDialog pd = new PreferencesDialog(this);

		if (pd.showAndWait().orElse(ButtonType.CANCEL) == ButtonType.OK) {
			Preferences.get().changed();
		}
	}

	void preferencesChanged(Preferences prefs) {
		getScene().getRoot().setStyle(Preferences.get().theme.style());

		ForkJoinPool.commonPool().submit(collection::updateFilter);
		collection.view().scheduleRender();
		allPanes().forEach(DeckPane::rerenderViews);

		for (MenuItem mi : newDeckMenu.getItems()) {
			if (mi.getUserData() == prefs.defaultFormat) {
				mi.setAccelerator(new KeyCodeCombination(KeyCode.N, KeyCombination.SHORTCUT_DOWN));
			} else {
				mi.setAccelerator(null);
			}
		}

		debugMenu.setVisible(Preferences.get().showDebugOptions);
	}

	@FXML
	protected void updateDeckbuilder() {
		owner.update();
	}

	@FXML
	protected void updateData() {
		owner.updateData();
	}

	@FXML
	protected void remodel() {
		collection.changeModel(x -> x.setAll(collectionModel(Context.get().data)));

		// We need to fix all the card instances in the current deck. They're hooked to old objects.
		allDecks().flatMap(deck -> deck.cards().values().stream())
				.flatMap(ObservableList::stream)
				.forEach(CardInstance::refreshInstance);

		updateCardStates(autoValidateDeck.isSelected() ? activeDeck().validate() : null);
	}

	@FXML
	protected void importDeck() {
		serdesFileChooser.getExtensionFilters().setAll(importSerdes.entrySet().stream()
				.sorted(Comparator.comparing(e -> e.getValue().toString()))
				.map(Map.Entry::getKey)
				.collect(Collectors.toList()));
		if (State.get().lastImportDirectory != null) serdesFileChooser.setInitialDirectory(State.get().lastImportDirectory.toFile());
		File f = serdesFileChooser.showOpenDialog(this);

		if (f == null) {
			return;
		}

		DeckImportExport importer = importSerdes.get(serdesFileChooser.getSelectedExtensionFilter());

		if (warnAboutSerdes(importer)) {
			return;
		}

		try {
			DeckList list = importer.importDeck(f.toPath());
			if (openDeckPane(list)) {
				State.get().lastImportDirectory = f.toPath().getParent();
			}
		} catch (IOException ioe) {
			ioe.printStackTrace();
			AlertBuilder.notify(this)
					.type(Alert.AlertType.ERROR)
					.title("Import Error")
					.headerText("An error occurred while importing:")
					.contentText(ioe.getMessage())
					.modal(Modality.WINDOW_MODAL)
					.show();
		}
	}

	@FXML
	protected void exportDeck() {
		exportDeck(activeDeck());
	}

	public void exportDeck(DeckList deck) {
		serdesFileChooser.getExtensionFilters().setAll(exportSerdes.entrySet().stream()
				.sorted(Comparator.comparing(e -> e.getValue().toString()))
				.map(Map.Entry::getKey)
				.collect(Collectors.toList()));
		if (State.get().lastExportDirectory != null) serdesFileChooser.setInitialDirectory(State.get().lastExportDirectory.toFile());
		File f = serdesFileChooser.showSaveDialog(this);

		if (f == null) {
			return;
		}

		DeckImportExport exporter = exportSerdes.get(serdesFileChooser.getSelectedExtensionFilter());

		if (warnAboutSerdes(exporter)) {
			return;
		}

		try {
			exporter.exportDeck(deck, f.toPath());
			State.get().lastExportDirectory = f.toPath().getParent();
		} catch (IOException ioe) {
			ioe.printStackTrace();
			AlertBuilder.notify(this)
					.type(Alert.AlertType.ERROR)
					.title("Export Error")
					.headerText("An error occurred while exporting:")
					.contentText(ioe.getMessage())
					.modal(Modality.WINDOW_MODAL)
					.showAndWait();
		}
	}

	@FXML
	protected void copyDecklist() throws IOException {
		ClipboardContent content = new ClipboardContent();
		content.put(DataFormat.PLAIN_TEXT, Preferences.get().copyPasteFormat.serializeDeck(activeDeck()));

		if (!Clipboard.getSystemClipboard().setContent(content)) {
			AlertBuilder.notify(MainWindow.this)
					.type(Alert.AlertType.ERROR)
					.title("Copy Failed")
					.headerText("Unable to copy deck to clipboard.")
					.contentText("No idea why, sorry -- maybe try again? Or clear your clipboard?")
					.showAndWait();
		}
	}

	@FXML
	protected void pasteDecklist() {
		if (!Clipboard.getSystemClipboard().hasString()) {
			AlertBuilder.notify(MainWindow.this)
					.type(Alert.AlertType.ERROR)
					.title("No Clipboard Content")
					.headerText("Nothing to paste!")
					.contentText("Your clipboard appears to be empty.")
					.showAndWait();
			return;
		}

		try {
			DeckList list = Preferences.get().copyPasteFormat.deserializeDeck(Clipboard.getSystemClipboard().getString());
			list.modifiedProperty().set(true);
			openDeckPane(list);
		} catch (IOException ioe) {
			AlertBuilder.notify(MainWindow.this)
					.type(Alert.AlertType.ERROR)
					.title("Paste Failed")
					.headerText("An error occurred while importing.")
					.contentText(ioe.getMessage())
					.showAndWait();
		}
	}

	@FXML
	protected void copyImageToClipboard() throws IOException {
		ClipboardContent content = new ClipboardContent();

		final DeckPane pane = activeDeckPane();
		WritableImage img = ImageExporter.deckToImage(pane.deck(), (zone, view) -> {
			view.layout(new FlowGrid.Factory());
			view.grouping(Preferences.get().zoneGroupings.getOrDefault(zone, new ManaValue()));
			view.showFlagsProperty().set(false);
			view.collapseDuplicatesProperty().set(pane.zonePane(zone).view().collapseDuplicatesProperty().get());
			view.cardScaleProperty().set(pane.zonePane(zone).view().cardScaleProperty().get());
			view.resize(2500.0, 2500.0);
		});

		content.put(DataFormat.IMAGE, img);

		if (!Clipboard.getSystemClipboard().setContent(content)) {
			AlertBuilder.notify(MainWindow.this)
					.title("Copy Failed")
					.headerText("Unable to copy deck to clipboard.")
					.contentText("No idea why, sorry -- maybe try again? Or clear your clipboard?")
					.showAndWait();
		}
	}

	@FXML
	protected void trimImageDiskCache() {
		owner.trimImageDiskCache();
	}
}
