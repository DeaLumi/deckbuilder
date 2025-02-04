package emi.mtg.deckbuilder.util;

import javafx.beans.property.ObjectProperty;
import javafx.beans.property.ObjectPropertyBase;
import javafx.collections.ListChangeListener;
import javafx.collections.ObservableList;
import javafx.collections.transformation.TransformationList;

import java.util.*;
import java.util.function.BinaryOperator;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Collectors;

// TODO: This could be a UniqueSortedList if I were more clever.
// Since refilter is using a hashmap, it's not actually horrifically slow,
// but it *is* all additional time over sorting that we don't need to take.
public class UniqueList<T> extends TransformationList<T, T> {
	private static class Element<T> {
		public final ArrayList<Integer> sourceIndices;
		public int preferredIndex;
		public final Object identity;
		public final T object;

		public Element(T object, Object identity) {
			this.sourceIndices = new ArrayList<>(4);
			this.preferredIndex = -1;
			this.object = object;
			this.identity = identity;
		}
	}

	private final ArrayList<Element<T>> filtered;
	private final Map<Object, Element<T>> witness;
	private final Map<Integer, Element<T>> reverse;

	/**
	 * Creates a new Transformation list wrapped around the source list.
	 *
	 * @param source the wrapped list
	 * @param hashExtractor a function which returns, for a given element, that element's "identity".
	 * @param comparer Given two elements, returns which one is preferable to keep. Others will be dropped.
	 */
	public UniqueList(ObservableList<? extends T> source, Function<T, Object> hashExtractor, BinaryOperator<? super T> comparer) {
		super(source);

		this.filtered = new ArrayList<>();
		this.witness = new HashMap<>();
		this.reverse = new HashMap<>();

		comparerProperty().set(comparer);
		extractorProperty().set(hashExtractor);
	}

	public UniqueList(ObservableList<? extends T> source, Function<T, Object> extractor) {
		this(source, extractor, (a, b) -> a);
	}

	public UniqueList(ObservableList<? extends T> source) {
		this(source, x -> x, (a, b) -> a);
	}

	private ObjectProperty<BinaryOperator<? super T>> comparer;

	public final ObjectProperty<BinaryOperator<? super T>> comparerProperty() {
		if (comparer == null) {
			comparer = new ObjectPropertyBase<BinaryOperator<? super T>>() {
				@Override
				protected void invalidated() {
					refilter();
				}

				@Override
				public Object getBean() {
					return UniqueList.this;
				}

				@Override
				public String getName() {
					return "comparator";
				}
			};
		}

		return comparer;
	}

	private ObjectProperty<Function<T, Object>> extractor;

	public final ObjectProperty<Function<T, Object>> extractorProperty() {
		if (extractor == null) {
			extractor = new ObjectPropertyBase<Function<T, Object>>() {
				@Override
				protected void invalidated() {
					refilter();
				}

				@Override
				public Object getBean() {
					return UniqueList.this;
				}

				@Override
				public String getName() {
					return "extractor";
				}
			};
		}

		return extractor;
	}

	protected void refilter() {
		// This is to prevent refiltering when we're still creating our properties in constructor.
		if (comparer == null || extractor == null) return;

		if (hasListeners()) {
			beginChange();
			nextRemove(0, filtered.stream().map(x -> x.object).collect(Collectors.toList()));
		}

		filtered.clear();
		witness.clear();
		reverse.clear();

		final Function<T, Object> extract = extractor.get();
		final BinaryOperator<? super T> prefer = comparer.get();

		int i = 0;
		for (final T next : getSource()) {
			final Object key = extract.apply(next);
			final Element<T> elem;

			if (witness.containsKey(key)) {
				elem = witness.get(key);
			} else {
				elem = new Element<T>(next, key);
				filtered.add(elem);
				witness.put(key, elem);
			}

			if (elem.preferredIndex < 0 || prefer.apply(getSource().get(elem.preferredIndex), next) == next) {
				elem.preferredIndex = i;
			}

			elem.sourceIndices.add(i);
			reverse.put(i, elem);

			++i;
		}

		if (hasListeners()) {
			nextAdd(0, filtered.size());
			endChange();
		}
	}

	// TODO: In theory I could implement this cleverly. Actually map changes in the source list to changes in the
	// derived list. But refilter() is O(n) and mapping source list changes is complicated and easy to get wrong. So for
	// now, I'm satisfied with this behavior triggering a full refresh of the list.
	// (If I ever make a list derived from the grouped model over in CardView, or make use of UniqueList in other
	// circumstances, it will be important to revisit this decision.)
	@Override
	protected void sourceChanged(ListChangeListener.Change<? extends T> c) {
		refilter();
	}

	@Override
	public int getSourceIndex(int index) {
		final Element<T> elem = filtered.get(index);

		return elem.preferredIndex >= 0 ? elem.preferredIndex : elem.sourceIndices.get(0);
	}

	@Override
	public T get(int index) {
		return getSource().get(getSourceIndex(index));
	}

	// TODO: This could be faster...?
	public List<? extends T> getAll(int index) {
		return filtered.get(index).sourceIndices.stream()
				.map(getSource()::get)
				.collect(Collectors.toList());
	}

	public int count(int index) {
		return filtered.get(index).sourceIndices.size();
	}

	@Override
	public int size() {
		return filtered.size();
	}
}
