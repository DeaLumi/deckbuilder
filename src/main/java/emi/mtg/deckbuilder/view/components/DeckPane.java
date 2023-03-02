package emi.mtg.deckbuilder.view.components;

import emi.lib.mtg.Card;
import emi.lib.mtg.game.Format;
import emi.lib.mtg.game.Zone;
import emi.mtg.deckbuilder.controller.DeckChanger;
import emi.mtg.deckbuilder.controller.serdes.impl.TextFile;
import emi.mtg.deckbuilder.model.CardInstance;
import emi.mtg.deckbuilder.model.DeckList;
import emi.mtg.deckbuilder.model.Preferences;
import emi.mtg.deckbuilder.view.dialogs.PrintingSelectorDialog;
import emi.mtg.deckbuilder.view.groupings.ManaValue;
import emi.mtg.deckbuilder.view.layouts.Piles;
import emi.mtg.deckbuilder.view.util.AlertBuilder;
import javafx.beans.binding.Bindings;
import javafx.beans.property.*;
import javafx.collections.FXCollections;
import javafx.collections.ListChangeListener;
import javafx.collections.ObservableList;
import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.stage.Modality;

import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class DeckPane extends SplitPane {
	private final EnumMap<Zone, CardPane> paneMap;
	private final DeckList deck;
	private BooleanProperty autoValidate;
	private ObjectProperty<ListChangeListener<CardInstance>> onDeckChanged;

	private final ListChangeListener<CardInstance> deckListChangedListener;

	public DeckPane(DeckList deck) {
		super();
		this.paneMap = new EnumMap<>(Zone.class);
		this.deck = deck;

		deckListChangedListener =  lce -> {
			if (getAutoValidate()) updateCardStates(deck.validate());
			deck.modifiedProperty().set(true);
			if (onDeckChanged.get() != null) onDeckChanged.get().onChanged(lce);
		};

		applyDeck();
	}

	public DeckList deck() {
		return deck;
	}

	public BooleanProperty autoValidateProperty() {
		if (autoValidate == null) {
			autoValidate = new BooleanPropertyBase() {
				@Override
				protected void invalidated() {
					updateCardStates(getAutoValidate() ? deck.validate() : null);
				}

				@Override
				public Object getBean() {
					return DeckPane.this;
				}

				@Override
				public String getName() {
					return "autoValidate";
				}
			};
		}

		return autoValidate;
	}

	public boolean getAutoValidate() {
		return autoValidate != null && autoValidate.get();
	}

	public void setAutoValidate(boolean autoValidate) {
		autoValidateProperty().set(autoValidate);
	}

	public ObjectProperty<ListChangeListener<CardInstance>> onDeckChangedProperty() {
		if (onDeckChanged == null) {
			onDeckChanged = new SimpleObjectProperty<>(null);
		}
		return onDeckChanged;
	}

	public ListChangeListener<CardInstance> getOnDeckChanged() {
		return onDeckChanged != null ? onDeckChanged.get() : null;
	}

	public void setOnDeckChanged(ListChangeListener<CardInstance> listener) {
		onDeckChangedProperty().set(listener);
	}

	public void updateCardStates(Format.ValidationResult result) {
		Stream<CardInstance> stream = deck.cards().values().stream()
				.flatMap(ObservableList::stream);

		stream.forEach(ci -> {
			Format.ValidationResult.CardResult cr = ci.lastValidation = (result == null ? null : result.cards.get(ci));

			if (cr == null) {
				ci.flags.clear();
				return;
			}

			if (cr.errors.isEmpty())
				ci.flags.remove(CardInstance.Flags.Invalid);
			else
				ci.flags.add(CardInstance.Flags.Invalid);

			if (cr.warnings.isEmpty())
				ci.flags.remove(CardInstance.Flags.Warning);
			else
				ci.flags.add(CardInstance.Flags.Warning);

			if (cr.notices.isEmpty())
				ci.flags.remove(CardInstance.Flags.Notice);
			else
				ci.flags.add(CardInstance.Flags.Notice);
		});

		for (Node zonePane : getItems()) {
			if (zonePane instanceof CardPane) {
				((CardPane) zonePane).view().scheduleRender();
			}
		}
	}

	public Set<Card> fullCards() {
		Map<Card, AtomicInteger> histo = new HashMap<>();
		Set<Card> fullCards = new HashSet<>();

		deck.cards().values().stream()
				.flatMap(ObservableList::stream)
				.map(CardInstance::card)
				.forEach(c -> {
					if (histo.computeIfAbsent(c, x -> new AtomicInteger(0)).incrementAndGet() >= deck.format().maxCopies) {
						fullCards.add(c);
					}
				});

		return fullCards;
	}

	public CardPane zonePane(Zone zone) {
		return paneMap.get(zone);
	}

	public void applyDeck() {
		paneMap.clear();
		getItems().setAll(deck.format().deckZones().stream()
				.map(z -> {
					CardPane pane = new CardPane(
							deck,
							z,
							Piles.Factory.INSTANCE,
							Preferences.get().zoneGroupings.getOrDefault(z, ManaValue.INSTANCE),
							CardView.DEFAULT_SORTING
					);

					paneMap.put(z, pane);
					deck.cards(z).removeListener(deckListChangedListener);
					deck.cards(z).addListener(deckListChangedListener);
					pane.view().doubleClick(ci -> {
						DeckChanger.change(
								deck,
								String.format("Remove %s from %s", ci.card().name(), z.name()),
								l -> l.cards(z).remove(ci), // TODO: These used to be synchronized on the model
								l -> l.cards(z).add(ci)
						);
					});
					pane.view().contextMenu(createDeckContextMenu(pane, z));
					pane.view().collapseDuplicatesProperty().set(Preferences.get().collapseDuplicates);

					return pane;
				})
				.collect(Collectors.toList()));
		setDividerPosition(0, 0.85);
		if (getAutoValidate()) updateCardStates(deck.validate());
	}

	public void closing() {
		paneMap.keySet().stream()
				.map(deck::cards)
				.forEach(l -> l.removeListener(deckListChangedListener));
	}

	private CardView.ContextMenu createDeckContextMenu(CardPane pane, Zone zone) {
		CardView.ContextMenu menu = new CardView.ContextMenu();

		MenuItem changePrintingMenuItem = new MenuItem("Choose Printing");
		changePrintingMenuItem.visibleProperty().bind(Bindings.createBooleanBinding(() -> {
			Set<Card> cards = menu.cards.stream().map(CardInstance::card).collect(Collectors.toSet());
			return cards.size() == 1 && cards.iterator().next().printings().size() > 1;
		}, menu.cards));
		changePrintingMenuItem.setOnAction(ae -> {
			if (menu.cards.isEmpty()) {
				return;
			}

			Set<Card> cards = menu.cards.stream().map(CardInstance::card).collect(Collectors.toSet());
			if (cards.size() != 1) {
				return;
			}

			final Card card = cards.iterator().next();
			final Set<CardInstance> modify = new HashSet<>(menu.cards.get());
			PrintingSelectorDialog.show(getScene(), card).ifPresent(pr -> {
				boolean modified = false;
				Consumer<DeckList> doFn = l -> {}, undoFn = l -> {};
				for (CardInstance ci : modify) {
					if (ci.printing() != pr) {
						final Card.Printing old = ci.printing();
						doFn = doFn.andThen(l -> ci.printing(pr));
						undoFn = undoFn.andThen(l -> ci.printing(old));
						modified = true;
					}
				}
				if (modified) {
					// Irritating hack to trigger listeners.
					doFn = doFn.andThen(l -> l.modifiedProperty().set(true));
					undoFn = undoFn.andThen(l -> l.modifiedProperty().set(true));

					DeckChanger.change(
							deck,
							String.format("Use %s from %s", card.name(), pr.set().name()),
							doFn,
							undoFn
					);
				}
				pane.view().scheduleRender();
			});
		});

		MenuItem removeAllMenuItem = new MenuItem("Remove All");
		removeAllMenuItem.setOnAction(ae -> {
			final List<CardInstance> cards = new ArrayList<>(menu.cards);
			DeckChanger.change(
					deck,
					String.format("Remove %s Card%s from %s", cards.size(), cards.size() > 1 ? "s" : "", zone),
					l -> l.cards(zone).removeAll(cards),
					l -> l.cards(zone).addAll(cards)
			);
		});

		Menu moveMenu = new Menu("Move To");

		for (Zone other : deck.format().deckZones()) {
			if (other == zone) continue;

			MenuItem moveItem = new MenuItem(other.toString());
			moveItem.setOnAction(ae -> {
				final List<CardInstance> cards = new ArrayList<>(menu.cards);

				DeckChanger.change(
						deck,
						String.format("Move %d Card%s to %s", cards.size(), cards.size() > 1 ? "s" : "", other),
						l -> {
							l.cards(zone).removeAll(cards);
							l.cards(other).addAll(cards);
						},
						l -> {
							l.cards(other).removeAll(cards);
							l.cards(zone).removeAll(cards);
						}
				);
			});

			moveMenu.getItems().add(moveItem);
		}

		Menu tagsMenu = new Menu("Tags");

		menu.setOnShowing(e -> {
			ObservableList<MenuItem> tagCBs = FXCollections.observableArrayList();
			tagCBs.setAll(deck.cards(zone).stream()
					.map(CardInstance::tags)
					.flatMap(Set::stream)
					.distinct()
					.sorted()
					.map(CheckMenuItem::new)
					.peek(cmi -> cmi.setSelected(menu.cards.stream().allMatch(ci -> ci.tags().contains(cmi.getText()))))
					.peek(cmi -> cmi.selectedProperty().addListener(x -> {
						final String tag = cmi.getText();
						final Set<CardInstance> cards = new HashSet<>(menu.cards);
						final String add = cards.size() > 1 ? String.format("Tag %d Cards as %s", cards.size(), tag) : String.format("Tag %s as %s", cards.iterator().next().card().name(), tag);
						final boolean added = cmi.isSelected();
						final CardView view = menu.view.get();
						final Consumer<DeckList> addFn = l -> {
							cards.forEach(ci -> ci.tags().add(tag));
							view.refreshCardGrouping();
						}, removeFn = l -> {
							cards.forEach(ci -> ci.tags().remove(tag));
							view.refreshCardGrouping();
						};

						DeckChanger.change(
								deck,
								add,
								added ? addFn : removeFn,
								added ? removeFn : addFn
						);
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

				final String tag = newTagField.getText();
				final Set<CardInstance> cards = new HashSet<>(menu.cards);
				final CardView view = menu.view.get();

				DeckChanger.change(
						deck,
						cards.size() > 1 ? String.format("Tag %d Cards as %s", cards.size(), tag) : String.format("Tag %s as %s", cards.iterator().next().card().name(), tag),
						l -> {
							cards.forEach(ci -> ci.tags().add(tag));
							view.regroup();
						},
						l -> {
							cards.forEach(ci -> ci.tags().remove(tag));
							view.regroup();
						}
				);

				menu.hide();
			});

			tagCBs.add(newTagMenuItem);

			tagsMenu.getItems().setAll(tagCBs);
		});

		menu.getItems().addAll(changePrintingMenuItem, tagsMenu);

		if (moveMenu.getItems().size() == 1) {
			MenuItem item = moveMenu.getItems().get(0);
			item.setText("Move to " + item.getText());
			menu.getItems().add(item);
		} else if (!moveMenu.getItems().isEmpty()) {
			menu.getItems().add(moveMenu);
		}

		menu.getItems().add(removeAllMenuItem);

		return menu;
	}

	public void rerenderViews() {
		for (CardPane pane : paneMap.values()) pane.view().scheduleRender();
	}
}
