package emi.mtg.deckbuilder.view;

import com.google.gson.Gson;
import emi.lib.mtg.data.ImageSource;
import emi.mtg.deckbuilder.model.CardInstance;
import javafx.collections.ListChangeListener;
import javafx.collections.ObservableList;
import javafx.collections.transformation.FilteredList;
import javafx.collections.transformation.SortedList;
import javafx.scene.Node;
import javafx.scene.layout.Pane;

import java.util.*;
import java.util.function.Function;
import java.util.function.Predicate;

/**
 * Created by Emi on 6/21/2017.
 */
public class CardViewManager implements ListChangeListener<CardInstance> {
	public interface ManagedView {
		Pane parentOf(CardInstance instance);

		void adjustLayout();

		CardViewManager manager();
	}

	protected final ManagedView managed;
	protected final ImageSource images;
	protected final Gson gson;
	protected final Map<CardInstance, CardInstanceView> viewMap;
	protected final ObservableList<CardInstance> cards;
	protected final FilteredList<CardInstance> filteredCards;
	protected final SortedList<CardInstance> sortedCards;
	protected Comparator<CardInstance> sort;
	protected Function<CardInstance, String> group;
	protected Comparator<String> groupSort;
	protected Predicate<CardInstance> filter;

	public CardViewManager(ManagedView managed, ImageSource images, Gson gson, ObservableList<CardInstance> cards, Predicate<CardInstance> filter, Comparator<CardInstance> sort, Function<CardInstance, String> group, Comparator<String> groupSort) {
		this.managed = managed;
		this.images = images;
		this.gson = gson;
		this.viewMap = new HashMap<>();

		this.cards = cards;
		this.filteredCards = this.cards.filtered(ci -> true);
		this.sortedCards = this.filteredCards.sorted(NewPilesView.NAME_SORT);
		this.sortedCards.addListener(this);

		this.filter = filter;
		this.sort = sort;
		this.group = group;
		this.groupSort = groupSort;
	}

	public void reconfigure(Predicate<CardInstance> filter, Comparator<CardInstance> sort, Function<CardInstance, String> group, Comparator<String> groupSort) {
		if (filter != null) {
			this.filter = filter;
		}

		if (sort != null) {
			this.sort = sort;
		}

		if (group != null) {
			this.group = group;
		}

		if (groupSort != null) {
			this.groupSort = groupSort;
		}

		Comparator<CardInstance> groupSort2 = (c1, c2) -> this.groupSort.compare(this.group.apply(c1), this.group.apply(c2));
		Comparator<CardInstance> overall = groupSort2.thenComparing(this.sort);

		this.sortedCards.setComparator(overall);
		this.filteredCards.setPredicate(this.filter);
	}

	public final long MAX_CARDS_IN_VIEW = 1000;

	@Override
	public void onChanged(Change<? extends CardInstance> c) {
		if (this.sortedCards.size() > MAX_CARDS_IN_VIEW) {
			System.err.println("Too many cards in " + this.toString());
			return;
		}

		while(c.next()) {
			Map<Pane, List<CardInstanceView>> addRemoveMap = new LinkedHashMap<>();
			c.getRemoved().forEach(ci -> addRemoveMap.computeIfAbsent(managed.parentOf(ci), k -> new ArrayList<>()).add(this.viewMap.remove(ci)));
			addRemoveMap.entrySet().forEach(e -> e.getKey().getChildren().removeAll(e.getValue()));

			addRemoveMap.clear();
			this.sortedCards.stream()
					.filter(ci -> !viewMap.containsKey(ci))
					.forEach(ci -> {
						CardInstanceView view = new CardInstanceView(this.cards, ci, this.images, this.gson);
						viewMap.put(ci, view);
						addRemoveMap.computeIfAbsent(managed.parentOf(ci), k -> new ArrayList<>()).add(view);
					});
			addRemoveMap.entrySet().forEach(e -> e.getKey().getChildren().addAll(e.getValue()));
		}

		managed.adjustLayout();
	}

	public void unloadAll() {
		viewMap.values().forEach(CardInstanceView::unloadImage);
	}

	public void loadAll() {
		viewMap.values().forEach(CardInstanceView::loadImage);
	}
}
