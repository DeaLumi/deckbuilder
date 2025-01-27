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
	protected static class Element {
		public final Object hash;
		public final Set<Integer> all;
		public final AtomicInteger preferred;

		public Element(Object hash, int preferred) {
			this.hash = hash;
			this.preferred = new AtomicInteger(preferred);
			this.all = new HashSet<>(4);
		}
	}

	public static class SubList<T> extends ObservableListBase<T> {
		private final List<T> source;
		private Element[] elements;
		private int count, total;

		protected SubList(List<T> source, Element[] elements, int count) {
			this.source = source;
			this.elements = elements;
			this.count = count;
			this.total = Arrays.stream(elements, 0, count).mapToInt(e -> e.all.size()).sum();
		}

		protected int reindexOf(int index, int min, int max) {
			// TODO: This does not work.
			int reid = Arrays.binarySearch(elements, min, max, index);
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
				nextRemove(i - delta, removed.get(elements[i - delta].preferred.get() - lowest));
				elements[i - delta] = elements[i];
			}

			count -= delta;

			endChange();
		}

		protected void insertIndices(int index, Element[] newIds) {
			final int delta = newIds.length;
			final int reindex = reindexOf(index);
			Element[] dest;

			if (newIds.length > elements.length - count) {
				dest = new Element[(count + newIds.length) * 3 / 2];
				System.arraycopy(this.elements, 0, dest, 0, reindex);
			} else {
				dest = elements;
			}

			// TODO There's probably an off-by-one error in here.
			for (int i = count - 1; i > reindex + delta; --i) {
				dest[i] = elements[i - delta];
			}

			for (int i = 0; i < delta; ++i) {
				dest[reindex + i] = newIds[i];
			}

			elements = dest;
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
			return source.get(elements[index].preferred.get());
		}

		public int count(int index) {
			return elements[index].all.size();
		}

		public List<T> getAll(int index) {
			return elements[index].all.stream().map(source::get).collect(Collectors.toList());
		}

		@Override
		public int size() {
			return count;
		}

		public int total() {
			return total;
		}
	}

	public final ObservableList<T> source;

	public final ObjectProperty<Predicate<T>> predicate;
	public final ObjectProperty<Function<? super T, Object>> hash;
	public final BooleanProperty globallyUnique;
	public final ObjectProperty<Comparator<? super T>> compare;
	public final ObjectProperty<Function<T, Set<G>>> grouping;

	private final ObservableMap<G, SubList<T>> groups;
	private final ObservableList<T> filtered;

	private final DoubleProperty progress;

	public FilteredGroupedModel(ObservableList<T> source, Predicate<T> predicate, Function<? super T, Object> hash, boolean globallyUnique, Comparator<? super T> compare, Function<T, Set<G>> grouping) {
		this.source = source;
		this.groups = FXCollections.observableMap(new TreeMap<>());
		this.filtered = FXCollections.observableArrayList();
		this.predicate = new SimpleObjectProperty<>(predicate);
		this.hash = new SimpleObjectProperty<>(hash);
		this.globallyUnique = new SimpleBooleanProperty(globallyUnique);
		this.compare = new SimpleObjectProperty<>(compare);
		this.grouping = new SimpleObjectProperty<>(grouping);
		this.progress = new SimpleDoubleProperty(ProgressIndicator.INDETERMINATE_PROGRESS);

		this.source.addListener(this::sourceChanged);

		this.predicate.addListener((prop, oldPredicate, newPredicate) -> {
			regroup(); // TODO skip grouping/hashing?
		});

		this.hash.addListener((prop, oldHash, newHash) -> {
			regroup(); // TODO skip filtering/grouping?
		});

		this.globallyUnique.addListener((prop, oldGlobal, newGlobal) -> {
			regroup(); // TODO skip filtering/grouping?
		});

		this.compare.addListener((prop, oldCompare, newCompare) -> {
			regroup(); // TODO skip filtering/grouping?
		});

		this.grouping.addListener((prop, oldGrouping, newGrouping) -> {
			regroup(); // TODO skip filtering/hashing?
		});

		regroup();
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
			filtered.clear();
			groups.clear();
			return;
		}

		slog.start();

		final Function<? super T, Object> hash = this.hash.get();
		final Comparator<? super T> compare = this.compare.get();
		final boolean global = this.globallyUnique.get();
		final Predicate<T> predicate = this.predicate.get();
		final Function<T, Set<G>> grouping = this.grouping.get();

		int cores = Runtime.getRuntime().availableProcessors();
		int elementsPerCore = elements / cores + 1;

		if (elementsPerCore < MIN_ELEMENTS_PER_CORE) {
			cores = 1;
			elementsPerCore = elements;
		}

		Future[] futures = new Future[cores];
		ConcurrentMap<G, Element[]> regrouped = new ConcurrentHashMap<>();
		ConcurrentMap<Object, Element> globalWitness = global ? new ConcurrentHashMap<>() : null;
		ConcurrentMap<G, ConcurrentMap<Object, Element>> witness = global ? null : new ConcurrentHashMap<>();
		ConcurrentMap<G, AtomicInteger> pointers = new ConcurrentHashMap<>();
		List<T> filtered = Collections.synchronizedList(new ArrayList<>());

		progress.set(0.0);
		AtomicInteger processed = new AtomicInteger(0);

		for (int i = 0; i < cores; ++i) {
			final int start = i * elementsPerCore;
			final int end = Math.min((i + 1) * elementsPerCore, elements);

			futures[i] = EXECUTOR.submit(() -> groupRegion(source, start, end, predicate, filtered, grouping, regrouped, pointers, () -> progress.set(processed.incrementAndGet() / (double) elements), hash, compare, globalWitness, witness));
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

		if (global) {
			// Clean up any nulls from degrouping.
			for (G group : regrouped.keySet()) {
				Element[] els = regrouped.get(group);
				int limit = pointers.get(group).get();
				for (int p = 0, j = 0; j < limit; ++j) {
					if (els[j] == null) {
						pointers.get(group).decrementAndGet();
						continue;
					}
					if (p != j) {
						els[p] = els[j];
					}
					++p;
				}
			}
		}

		// TODO Include shifting in progress?
		progress.set(1.0);

		slog.log("Finished in %.4f seconds; storing.", slog.lap());

		this.filtered.setAll(filtered);

		// TODO generate change events and actually update groups
		// If our grouping has any overlap with the old groups, we could just replace their lists' contents.
		// Although, we can't be certain of their indices anymore.
		//groups.keySet().removeIf(x -> !regrouped.containsKey(x));
		groups.clear();
		for (Map.Entry<G, Element[]> entry : regrouped.entrySet()) {
			groups.put(entry.getKey(), new SubList<>(this.source, entry.getValue(), pointers.get(entry.getKey()).get()));
		}

		slog.log("Stored in %.4f seconds; what's taking so damn long?", slog.lap());

		progress.set(0.0);
	}

	private static <T, G> void groupRegion(List<? extends T> source, int start, int end, Predicate<T> predicate, List<T> filtered, Function<T, Set<G>> grouping, Map<G, Element[]> concreteGroups, ConcurrentMap<G, AtomicInteger> pointers, Runnable increment, Function<? super T, Object> hash, Comparator<? super T> compare, ConcurrentMap<Object, Element> globalWitness, ConcurrentMap<G, ConcurrentMap<Object, Element>> witness) {
		Slog slog = SLOG.child(String.format("%d..%d", start, end));
		slog.start();
		for (int i = start; i < end; ++i) {
			T element = source.get(i);
			increment.run();

			if (!predicate.test(element)) continue;

			filtered.add(element);
			Set<G> groups = grouping.apply(element);
			Object hasho = hash == null ? null : hash.apply(element);
			Element el;

			if (hasho == null) {
				el = new Element(element, i);
				for (G group : groups) {
					int idx = pointers.computeIfAbsent(group, g -> new AtomicInteger(0)).getAndIncrement();
					Element[] arr = concreteGroups.computeIfAbsent(group, g -> new Element[source.size()]);
					arr[idx] = el;
				}
			} else if (globalWitness != null) {
				Set<G> oldGroups = null;
				boolean add = false;

				synchronized (globalWitness) {
					el = globalWitness.get(hasho);
					if (el == null) {
						el = new Element(hasho, i);
						globalWitness.put(hasho, el);
						add = true;
					} else {
						T old = source.get(el.preferred.get());
						if (compare.compare(old, element) > 0) {
							oldGroups = grouping.apply(old);
							el.preferred.set(i);
							add = true;
						}
					}
				}

				if (add) {
					for (G group : groups) {
						if (oldGroups != null && oldGroups.contains(group)) continue;
						int idx = pointers.computeIfAbsent(group, g -> new AtomicInteger(0)).getAndIncrement();
						Element[] arr = concreteGroups.computeIfAbsent(group, g -> new Element[source.size()]);
						arr[idx] = el;
					}

					if (oldGroups != null) {
						for (G group : oldGroups) {
							if (groups.contains(group)) continue;
							Element[] els = concreteGroups.get(group);
							int limit = pointers.get(group).get();
							synchronized (els) {
								for (int j = 0; j < limit; ++j) {
									if (els[j] != null && els[j].hash == hasho) els[j] = null;
								}
							}
						}
					}
				}

				el.all.add(i);
			} else if (witness != null) {
				for (G group : groups) {
					Map<Object, Element> wit = witness.computeIfAbsent(group, g -> new ConcurrentHashMap<>());
					boolean add = false;

					// TODO: Not sure if synchronizing on wit is correct or necessary here.
					synchronized (wit) {
						el = wit.get(hasho);
						if (el == null) {
							el = new Element(hasho, i);
							wit.put(hasho, el);
							add = true;
						} else if (compare.compare(source.get(el.preferred.get()), element) > 0) {
							el.preferred.set(i);
						}
					}

					if (add) {
						int idx = pointers.computeIfAbsent(group, g -> new AtomicInteger(0)).getAndIncrement();
						Element[] arr = concreteGroups.computeIfAbsent(group, g -> new Element[source.size()]);
						arr[idx] = el;
					}

					el.all.add(i);
				}
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

	public ObservableList<T> filtered() {
		return filtered;
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

		FilteredGroupedModel<String, Integer> test = new FilteredGroupedModel<>(numbers, i -> true, i -> i, true, Comparator.comparingInt(a -> a), grouping);

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
