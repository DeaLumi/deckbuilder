package emi.mtg.deckbuilder.view.components;

import emi.lib.mtg.Card;
import emi.lib.mtg.game.Format;
import emi.lib.mtg.game.Zone;
import emi.mtg.deckbuilder.controller.DeckChanger;
import emi.mtg.deckbuilder.model.CardInstance;
import emi.mtg.deckbuilder.model.DeckList;
import emi.mtg.deckbuilder.model.Preferences;
import emi.mtg.deckbuilder.view.dialogs.PrintingSelectorDialog;
import emi.mtg.deckbuilder.view.groupings.ManaValue;
import emi.mtg.deckbuilder.view.layouts.Piles;
import javafx.beans.binding.Bindings;
import javafx.beans.property.*;
import javafx.collections.FXCollections;
import javafx.collections.ListChangeListener;
import javafx.collections.ObservableList;
import javafx.scene.Node;
import javafx.scene.control.*;

import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class DeckPane extends SplitPane {
	private final EnumMap<Zone, CardPane> paneMap;
	private CardPane cutCardsPane;
	private final DeckList deck;
	private BooleanProperty autoValidate;
	private ObjectProperty<ListChangeListener<CardInstance>> onDeckChanged;
	private BooleanProperty showSideboard, showCutboard;

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

	public BooleanProperty showSideboardProperty() {
		if (showSideboard == null) {
			showSideboard = new BooleanPropertyBase() {
				@Override
				protected void invalidated() {
					if (getShowSideboard()) {
						if (zonePane(Zone.Sideboard) != null && !getItems().contains(zonePane(Zone.Sideboard))) {
							int i = 0;
							Iterator<Map.Entry<Zone, CardPane>> paneIter = paneMap.entrySet().iterator();
							Map.Entry<Zone, CardPane> last = null;
							while (paneIter.hasNext()) {
								Map.Entry<Zone, CardPane> next = paneIter.next();
								if (!getItems().contains(next.getValue())) continue;

								if ((last == null || last.getKey().ordinal() > Zone.Sideboard.ordinal()) && next.getKey().ordinal() > Zone.Sideboard.ordinal()) {
									break;
								}

								last = next;
								++i;
							}

							getItems().add(i, zonePane(Zone.Sideboard));
							setDividerPosition(i > 0 ? i - 1 : 0, i > 0 ? 0.9 : 0.1);
						}
					} else {
						if (zonePane(Zone.Sideboard) != null) getItems().remove(zonePane(Zone.Sideboard));
					}
				}

				@Override
				public Object getBean() {
					return DeckPane.this;
				}

				@Override
				public String getName() {
					return "showSideboard";
				}
			};
		}

		return showSideboard;
	}

	public boolean getShowSideboard() {
		return showSideboard != null && showSideboard.get();
	}

	public void setShowSideboard(boolean showSideboard) {
		showSideboardProperty().set(showSideboard);
	}

	public BooleanProperty showCutboardProperty() {
		if (showCutboard == null) {
			showCutboard = new BooleanPropertyBase() {
				@Override
				protected void invalidated() {
					if (getShowCutboard()) {
						if (!getItems().contains(cutCardsPane)) {
							getItems().add(cutCardsPane);
							setDividerPosition(getItems().size() - 2, 0.9);
						}
					} else {
						getItems().remove(cutCardsPane);
					}
				}

				@Override
				public Object getBean() {
					return DeckPane.this;
				}

				@Override
				public String getName() {
					return "showCutboard";
				}
			};
		}

		return showCutboard;
	}

	public boolean getShowCutboard() {
		return showCutboard != null && showCutboard.get();
	}

	public void setShowCutboard(boolean showCutboard) {
		showCutboardProperty().set(showCutboard);
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

		deck.cutCards().forEach(ci -> ci.flags.clear());
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
						if (Preferences.get().removeToCutboard.get()) {
							DeckChanger.change(
									deck,
									String.format("Cut %s", ci.card().name()),
									l -> {
										l.cards(z).remove(ci);
										l.cutCards().add(ci);
									},
									l -> {
										l.cutCards().remove(ci);
										l.cards(z).add(ci);
									}
							);
						} else {
							DeckChanger.change(
									deck,
									String.format("Remove %s", ci.card().name()),
									l -> l.cards(z).remove(ci), // TODO: These used to be synchronized on the model
									l -> l.cards(z).add(ci)
							);
						}
					});
					pane.view().contextMenu(createDeckContextMenu(pane, z));
					pane.view().collapseDuplicatesProperty().set(Preferences.get().collapseDuplicates);

					return pane;
				})
				.collect(Collectors.toList()));
		setDividerPosition(0, 0.85);
		if (getAutoValidate()) updateCardStates(deck.validate());

		cutCardsPane = new CardPane(
				"Cut Cards",
				deck,
				deck.cutCards(),
				Piles.Factory.INSTANCE,
				Preferences.get().cutboardGrouping.get(),
				CardView.DEFAULT_SORTING
		);
		deck.cutCards().removeListener(deckListChangedListener);
		deck.cutCards().addListener(deckListChangedListener);
		cutCardsPane.view().doubleClick(ci -> {
			DeckChanger.change(
				deck,
				String.format("Remove %s", ci.card().name()),
				l -> l.cutCards().remove(ci), // TODO: These used to be synchronized on the model
				l -> l.cutCards().add(ci)
			);
		});
		// TODO: Add a context menu.
		cutCardsPane.view().collapseDuplicatesProperty().set(Preferences.get().collapseDuplicates);
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
							String.format("Change %d Printing%s", modify.size(), modify.size() > 1 ? "s" : ""),
							doFn,
							undoFn
					);
				}
				pane.view().scheduleRender();
			});
		});

		MenuItem removeAllMenuItem = new MenuItem("Remove All");
		removeAllMenuItem.textProperty().bind(Bindings.when(Preferences.get().removeToCutboard).then("Cut All").otherwise("Remove All"));
		removeAllMenuItem.setOnAction(ae -> {
			final List<CardInstance> cards = new ArrayList<>(menu.cards);
			if (Preferences.get().removeToCutboard.get()) {
				DeckChanger.change(
						deck,
						String.format("Cut %d Card%s", cards.size(), cards.size() > 1 ? "s" : ""),
						l -> {
							l.cards(zone).removeAll(cards);
							l.cutCards().addAll(cards);
						},
						l -> {
							l.cutCards().removeAll(cards);
							l.cards(zone).addAll(cards);
						}
				);
			} else {
				DeckChanger.change(
						deck,
						String.format("Remove %d Card%s", cards.size(), cards.size() > 1 ? "s" : ""),
						l -> l.cards(zone).removeAll(cards),
						l -> l.cards(zone).addAll(cards)
				);
			}
		});

		Menu moveMenu = new Menu("Move To");

		for (Zone other : deck.format().deckZones()) {
			if (other == zone) continue;

			MenuItem moveItem = new MenuItem(other.toString());
			moveItem.setOnAction(ae -> {
				final List<CardInstance> cards = new ArrayList<>(menu.cards);

				DeckChanger.change(
						deck,
						String.format("Move %d Card%s", cards.size(), cards.size() > 1 ? "s" : ""),
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

		if (moveMenu.getItems().size() == 1) {
			MenuItem item = moveMenu.getItems().get(0);
			item.setText("Move to " + item.getText());
			menu.getItems().add(item);
		} else if (!moveMenu.getItems().isEmpty()) {
			menu.getItems().add(moveMenu);
		}

		menu.getItems().addAll(changePrintingMenuItem);
		menu.addTagsMenu();
		menu.getItems().add(removeAllMenuItem);
		menu.addSeparator()
				.addCleanupImages();

		return menu;
	}

	public void rerenderViews() {
		for (CardPane pane : paneMap.values()) pane.view().scheduleRender();
	}
}
