package emi.mtg.deckbuilder.model;

import emi.mtg.deckbuilder.util.Slog;
import javafx.beans.InvalidationListener;
import javafx.beans.property.*;
import javafx.collections.*;
import javafx.scene.control.ProgressIndicator;

import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.*;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

public class FilteredGroupedModel<G extends Comparable<G>, T> implements ObservableMap<G, FilteredGroupedModel.SubList<T>> {
	public static class SubList<T> extends ObservableListBase<T> {
		private final List<T> source;
		private int[] indices;
		private int count;

		public SubList(List<T> source, int[] indices, int count) {
			this.source = source;
			this.indices = indices;
			this.count = count;
		}

		protected int reindexOf(int index, int min, int max) {
			int reid = Arrays.binarySearch(indices, min, max, index);
			if (reid < 0) reid = ~reid;
			return reid;
		}

		protected int reindexOf(int index) {
			return reindexOf(index, 0, count);
		}

		protected void removeIndices(int lowest, int highest, List<T> removed) {
			int lowestReindex = reindexOf(lowest);
			int highestReindex = reindexOf(highest, lowestReindex, count);
			final int delta = highestReindex - lowestReindex;

			beginChange();

			for (int i = highestReindex; i < count; ++i) {
				nextRemove(i - delta, removed.get(indices[i - delta] - lowest));
				indices[i - delta] = indices[i];
			}

			count -= delta;

			endChange();
		}

		protected void insertIndices(int index, int[] newIds) {
			final int delta = newIds.length;
			final int reindex = reindexOf(index);
			int[] dest;

			if (newIds.length > indices.length - count) {
				dest = new int[(count + newIds.length) * 3 / 2];
				System.arraycopy(this.indices, 0, dest, 0, reindex);
			} else {
				dest = indices;
			}

			// TODO There's probably an off-by-one error in here.
			for (int i = count - 1; i > reindex + delta; --i) {
				dest[i] = indices[i - delta];
			}

			for (int i = 0; i < delta; ++i) {
				dest[reindex + i] = newIds[i];
			}

			indices = dest;
			count += delta;

			beginChange();
			nextAdd(reindex, reindex + delta);
			endChange();
		}
		
		protected void elementUpdated(int index) {
			beginChange();
			nextUpdate(reindexOf(index));
			endChange();
		}

		@Override
		public T get(int index) {
			return source.get(indices[index]);
		}

		@Override
		public int size() {
			return count;
		}
	}

	private final ObservableMap<G, SubList<T>> groups;

	public final ObservableList<T> source;
	public final ObjectProperty<Function<T, Set<G>>> grouping;
	public final ObjectProperty<Predicate<T>> predicate;

	private int filteredElements;

	private final DoubleProperty progress;

	// TODO probably need G to be comparable, or to request a comparator for G?
	// This model is doing a lot, but it feels like ordering its keys isn't too much more.
	// But isn't order supposed to be a part of the grouping function, in concept?
	public FilteredGroupedModel(ObservableList<T> source, Function<T, Set<G>> grouping, Predicate<T> predicate) {
		this.source = source;
		this.groups = FXCollections.observableMap(new TreeMap<>());
		this.grouping = new SimpleObjectProperty<>();
		this.predicate = new SimpleObjectProperty<>(ci -> true); // TODO safe to have a value here?
		this.progress = new SimpleDoubleProperty(ProgressIndicator.INDETERMINATE_PROGRESS);

		this.source.addListener(this::sourceChanged);

		this.grouping.addListener((prop, oldGrouping, newGrouping) -> {
			regroup(); // TODO skip filtering(?)
		});

		this.predicate.addListener((prop, oldPredicate, newPredicate) -> {
			regroup(); // TODO skip grouping(?)
		});

		this.grouping.set(grouping);
		this.predicate.set(predicate);
	}

	private static final ExecutorService EXECUTOR = Executors.newFixedThreadPool(Runtime.getRuntime().availableProcessors(), r -> {
		Thread t = Executors.defaultThreadFactory().newThread(r);
		t.setDaemon(true);
		t.setName("FilteredGroupedModel-" + t.getName());
		return t;
	});

	private static final int MIN_ELEMENTS_PER_CORE = 100;

	private static Slog SLOG = new Slog("GroupedModel");

	public ReadOnlyDoubleProperty progress() {
		return progress;
	}

	public void sourceElementChanged(T element) {
		int index = -1;
		for (int i = 0; i < source.size(); ++i) {
			if (source.get(i) == element) {
				index = i;
				break;
			}
		}

		if (index < 0) throw new IllegalArgumentException("Source does not contain element " + element);
		final int i = index;

		sourceChanged(new ListChangeListener.Change<T>(source) {
			private boolean onChange = false;

			@Override
			public boolean next() {
				return !onChange && (onChange = true);
			}

			@Override
			public void reset() {
				onChange = false;
			}

			@Override
			public int getFrom() {
				return i;
			}

			@Override
			public int getTo() {
				return i;
			}

			@Override
			public boolean wasUpdated() {
				return true;
			}

			@Override
			public List<T> getRemoved() {
				return Collections.emptyList();
			}

			@Override
			protected int[] getPermutation() {
				return new int[0];
			}
		});
	}

	public synchronized void regroup() {
		// TODO: Do NOTHING using `groups()` until it's been cleared. The sublists it contains are invalid.
		final Slog slog = SLOG.child("Regroup");
		final int elements = source == null ? 0 : source.size();

		if (elements == 0) {
			progress.set(0.0); // TODO default minimum groups?
			groups.clear();
			return;
		}

		slog.start();

		final Function<T, Set<G>> grouping = this.grouping.get();
		final Predicate<T> predicate = this.predicate.get();

		int cores = Runtime.getRuntime().availableProcessors();
		int elementsPerCore = elements / cores + 1;

		if (elementsPerCore < MIN_ELEMENTS_PER_CORE) {
			cores = 1;
			elementsPerCore = elements;
		}

		Future[] futures = new Future[cores];
		ConcurrentMap<G, int[]> regrouped = new ConcurrentHashMap<>();
		ConcurrentMap<G, AtomicInteger> pointers = new ConcurrentHashMap<>();

		progress.set(0.0);
		AtomicInteger processed = new AtomicInteger(0);

		for (int i = 0; i < cores; ++i) {
			final int start = i * elementsPerCore;
			final int end = Math.min((i + 1) * elementsPerCore, elements);

			futures[i] = EXECUTOR.submit(() -> groupRegion(source, start, end, grouping, predicate, regrouped, pointers, () -> progress.set(processed.incrementAndGet() / (double) elements)));
		}

		for (int i = 0; i < cores; ++i) {
			try {
				futures[i].get();
			} catch (InterruptedException e) {
				slog.err("Exception while joining filter/group thread %d; some elements may be missing. Continuing.", e, i);
			} catch (ExecutionException ee) {
				throw new RuntimeException(ee);
			}
		}

		// TODO Include shifting in progress?
		progress.set(1.0);

		slog.log("Finished in %.4f seconds; storing.", slog.elapsed());

		// TODO generate change events and actually update groups
		// If our grouping has any overlap with the old groups, we could just replace their lists' contents.
		// Although, we can't be certain of their indices anymore.
		groups.keySet().removeIf(x -> !regrouped.containsKey(x));
		filteredElements = 0;
		for (Map.Entry<G, int[]> entry : regrouped.entrySet()) {
			groups.put(entry.getKey(), new SubList<>(this.source, entry.getValue(), pointers.get(entry.getKey()).get()));
			filteredElements += pointers.get(entry.getKey()).get();
		}

		progress.set(0.0);
	}

	private static <T, G> void groupRegion(List<? extends T> source, int start, int end, Function<T, Set<G>> grouping, Predicate<T> predicate, Map<G, int[]> concreteGroups, ConcurrentMap<G, AtomicInteger> pointers, Runnable increment) {
		Slog slog = SLOG.child(String.format("%d..%d", start, end));
		slog.start();
		for (int i = start; i < end; ++i) {
			T element = source.get(i);

			increment.run();

			if (!predicate.test(element)) continue;

			Set<G> groups = grouping.apply(element);

			for (G group : groups) {
				int idx = pointers.computeIfAbsent(group, g -> new AtomicInteger(0)).getAndIncrement();
				int[] arr = concreteGroups.computeIfAbsent(group, g -> new int[source.size()]);
				arr[idx] = i;
			}
		}
		slog.log("Finished in %.4f seconds.", slog.elapsed());
	}

	protected void sourceChanged(ListChangeListener.Change<? extends T> lce) {
		// TODO: In the future, I need to implement these properly. Doing a full regroup is horribly inefficient and
		// TODO: triggers a lot of extra work in the CardView. There's also problematic complications for handling list
		// TODO: change events this way, since the old sublists' indices are invalidated. Finally, handling this
		// TODO: properly would let me avoid sending progress notifications for small list change events.

		while (lce.next()) {
			if (lce.wasPermutated()) {
//				throw new UnsupportedOperationException("Implement handlePermutation");
				regroup();
			}

			if (lce.wasRemoved()) {
//				throw new UnsupportedOperationException("Implement handleRemoved");
				regroup();
			}

			if (lce.wasAdded()) {
//				throw new UnsupportedOperationException("Implement handleAdded");
				regroup();
			}

			if (lce.wasUpdated()) {
//				throw new UnsupportedOperationException("Implement handleUpdated");
				regroup();
			}
		}
	}

	@Override
	public void addListener(MapChangeListener<? super G, ? super SubList<T>> listener) {
		groups.addListener(listener);
	}

	@Override
	public void removeListener(MapChangeListener<? super G, ? super SubList<T>> listener) {
		groups.removeListener(listener);
	}

	@Override
	public int size() {
		return groups.size();
	}

	public int filteredSize() {
		return filteredElements;
	}

	@Override
	public boolean isEmpty() {
		return groups.isEmpty();
	}

	@Override
	public boolean containsKey(Object key) {
		return groups.containsKey(key);
	}

	@Override
	public boolean containsValue(Object value) {
		return groups.containsValue(value);
	}

	@Override
	public SubList<T> get(Object key) {
		return groups.get(key);
	}

	@Override
	public SubList<T> put(G key, SubList<T> value) {
		return null;
	}

	@Override
	public SubList<T> remove(Object key) {
		throw new UnsupportedOperationException();
	}

	@Override
	public void putAll(Map<? extends G, ? extends SubList<T>> m) {
		throw new UnsupportedOperationException();
	}

	@Override
	public void clear() {
		throw new UnsupportedOperationException();
	}

	@Override
	public Set<G> keySet() {
		return groups.keySet();
	}

	@Override
	public Collection<SubList<T>> values() {
		return groups.values();
	}

	@Override
	public Set<Entry<G, SubList<T>>> entrySet() {
		return groups.entrySet();
	}

	@Override
	public boolean equals(Object o) {
		return groups.equals(o);
	}

	@Override
	public int hashCode() {
		return groups.hashCode();
	}

	@Override
	public SubList<T> getOrDefault(Object key, SubList<T> defaultValue) {
		return groups.getOrDefault(key, defaultValue);
	}

	@Override
	public void forEach(BiConsumer<? super G, ? super SubList<T>> action) {
		groups.forEach(action);
	}

	@Override
	public void replaceAll(BiFunction<? super G, ? super SubList<T>, ? extends SubList<T>> function) {
		throw new UnsupportedOperationException();
	}

	@Override
	public SubList<T> putIfAbsent(G key, SubList<T> value) {
		throw new UnsupportedOperationException();
	}

	@Override
	public boolean remove(Object key, Object value) {
		throw new UnsupportedOperationException();
	}

	@Override
	public boolean replace(G key, SubList<T> oldValue, SubList<T> newValue) {
		throw new UnsupportedOperationException();
	}

	@Override
	public SubList<T> replace(G key, SubList<T> value) {
		throw new UnsupportedOperationException();
	}

	@Override
	public SubList<T> computeIfAbsent(G key, Function<? super G, ? extends SubList<T>> mappingFunction) {
		throw new UnsupportedOperationException();
	}

	@Override
	public SubList<T> computeIfPresent(G key, BiFunction<? super G, ? super SubList<T>, ? extends SubList<T>> remappingFunction) {
		throw new UnsupportedOperationException();
	}

	@Override
	public SubList<T> compute(G key, BiFunction<? super G, ? super SubList<T>, ? extends SubList<T>> remappingFunction) {
		throw new UnsupportedOperationException();
	}

	@Override
	public SubList<T> merge(G key, SubList<T> value, BiFunction<? super SubList<T>, ? super SubList<T>, ? extends SubList<T>> remappingFunction) {
		throw new UnsupportedOperationException();
	}

	@Override
	public void addListener(InvalidationListener invalidationListener) {
		groups.addListener(invalidationListener);
	}

	@Override
	public void removeListener(InvalidationListener invalidationListener) {
		groups.removeListener(invalidationListener);
	}

	public static void main(String[] args) {
		ObservableList<Integer> numbers = FXCollections.observableArrayList(IntStream.range(0, 5000).boxed().toArray(Integer[]::new));

		Predicate<Integer> isEven = n -> n % 2 == 0;
		Predicate<Integer> isOdd = n -> n % 2 == 1;
		Predicate<Integer> isPrime = n -> {
			if (n < 2) return false;
			if (n == 2 || n == 3 || n == 5) return true;
			if (n % 2 == 0 || n % 3 == 0 || n % 5 == 0) return false;
			final long root = 1 + (long) Math.sqrt(n);
			for (int i = 6; i < root; ++i) if (n % i == 0) return false;
			return true;
		};

		Function<Integer, Set<String>> grouping = n -> {
			Set<String> tmp = new HashSet<>();
			if (isEven.test(n)) tmp.add("Even");
			if (isOdd.test(n)) tmp.add("Odd");
			if (isPrime.test(n)) tmp.add("Prime");
			return Collections.unmodifiableSet(tmp);
		};

		FilteredGroupedModel<String, Integer> test = new FilteredGroupedModel<>(numbers, grouping, i -> true);

		Consumer<Predicate<Integer>> checkConsistency = filter -> {
			for (int i : numbers) {
				if (!filter.test(i)) continue;
				if (isEven.test(i)) assert test.get("Even").contains(i) : i + " was not grouped as Even.";
				if (isOdd.test(i)) assert test.get("Odd").contains(i) : i + " was not grouped as Odd.";
				if (isPrime.test(i)) assert test.get("Prime").contains(i) : i + " was not grouped as Prime.";
			}

			for (String key : test.keySet()) {
				for (int val : test.get(key)) {
					assert filter.test(val) : val + " was included despite failing predicate.";

					switch (key) {
						case "Even":
							assert isEven.test(val) : val + " was mis-grouped as Even.";
							break;
						case "Odd":
							assert isOdd.test(val) : val + " was mis-grouped as Odd.";
							break;
						case "Prime":
							assert isPrime.test(val) : val + " was mis-grouped as Prime.";
							break;
					}
				}
			}
		};

		assert test.containsKey("Even") && test.containsKey("Odd") && test.containsKey("Prime") : "Not all groups present!";
		checkConsistency.accept(i -> true);
		test.predicate.set(i -> i >= 123 && i <= 128);
		checkConsistency.accept(i -> i >= 123 && i <= 128);

		test.predicate.set(i -> true);
		checkConsistency.accept(i -> true);

		numbers.remove(125);
		checkConsistency.accept(i -> i != 125);
	}
}
