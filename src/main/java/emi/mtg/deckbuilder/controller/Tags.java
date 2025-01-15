package emi.mtg.deckbuilder.controller;

import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonToken;
import com.google.gson.stream.JsonWriter;
import emi.lib.mtg.Card;
import emi.lib.mtg.DataSource;
import emi.mtg.deckbuilder.model.CardInstance;
import emi.mtg.deckbuilder.model.Preferences;
import emi.mtg.deckbuilder.util.PluginUtils;
import emi.mtg.deckbuilder.view.MainApplication;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.function.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class Tags {
	public static class TagGraph<T> {
		// TODO: This whole thing is jank. Should really just return unmodifiable sets, most likely.
		// Or use the parent references.
		private class BimapSet<A, B> implements Set<B> {
			private final A key;
			private final Set<B> set;
			private final Map<A, Set<B>> source;
			private final Map<B, Set<A>> complement;
			private final Supplier<Set> setConstructor;

			public BimapSet(A key, Set<B> set, Map<A, Set<B>> source, Map<B, Set<A>> complement, Supplier<Set> setConstructor) {
				this.key = key;
				this.set = set;
				this.source = source;
				this.complement = complement;
				this.setConstructor = setConstructor;
			}

			@Override
			public boolean add(B b) {
				complement.computeIfAbsent(b, k -> setConstructor.get()).add(key);
				boolean added = set.add(b);

				if (added && source.get(key) != set) {
					source.compute(key, (k, s) -> {
						if (s != null) set.addAll(s);
						return set;
					});
				}

				return added;
			}

			@Override
			public boolean remove(Object o) {
				Set<A> complementary = complement.get(o);
				if (complementary != null) {
					complementary.remove(key);
					if (complementary.isEmpty()) {
						complement.remove(o);
					}
				}

				boolean removed = set.remove(o);
				if (set.isEmpty()) {
					source.remove(key);
				}
				return removed;
			}

			@Override
			public void clear() {
				for (B obj : this) {
					Set<A> complementary = complement.get(obj);
					if (complementary != null) {
						complementary.remove(key);
						if (complementary.isEmpty()) {
							complement.remove(obj);
						}
					}
				}
				set.clear();
				source.remove(key);
			}

			@Override
			public int size() {
				return set.size();
			}

			@Override
			public boolean isEmpty() {
				return set.isEmpty();
			}

			@Override
			public boolean contains(Object o) {
				return set.contains(o);
			}

			@Override
			public Iterator<B> iterator() {
				return set.iterator(); // TODO: Need to override this iterator for remove operation.
			}

			@Override
			public Object[] toArray() {
				return set.toArray();
			}

			@Override
			public <T> T[] toArray(T[] a) {
				return set.toArray(a);
			}

			@Override
			public boolean containsAll(Collection<?> c) {
				return set.containsAll(c);
			}

			@Override
			public boolean addAll(Collection<? extends B> c) {
				boolean result = false;
				for (B b : c) result = this.add(b) || result;
				return result;
			}

			@Override
			public boolean retainAll(Collection<?> c) {
				boolean result = false;
				for (B b : this) if (!c.contains(b)) result = remove(b) || result;
				return result;
			}

			@Override
			public boolean removeAll(Collection<?> c) {
				boolean result = false;
				for (Object b : c) result = remove(b) || result;
				return false;
			}

			@Override
			public boolean equals(Object o) {
				return set.equals(o);
			}

			@Override
			public int hashCode() {
				return set.hashCode();
			}

			@Override
			public Spliterator<B> spliterator() {
				return set.spliterator();
			}

			@Override
			public Stream<B> stream() {
				return set.stream();
			}

			@Override
			public Stream<B> parallelStream() {
				return set.parallelStream();
			}

			@Override
			public void forEach(Consumer<? super B> action) {
				set.forEach(action);
			}
		}

		private final Supplier<Set> setSupplier;
		private final Map<String, Set<T>> tagToObjs;
		private final Map<T, Set<String>> objToTags;

		public TagGraph() {
			this(s -> new HashMap<>(), HashSet::new);
		}

		public TagGraph(Function<Supplier<Set>, Map> mapSupplier, Supplier<Set> setSupplier) {
			this.setSupplier = setSupplier;
			this.tagToObjs = mapSupplier.apply(setSupplier);
			this.objToTags = mapSupplier.apply(setSupplier);
		}

		public Set<String> tags(T obj) {
			return new BimapSet<>(obj, objToTags.computeIfAbsent(obj, k -> setSupplier.get()), objToTags, tagToObjs, setSupplier);
		}

		public Set<T> objects(String tag) {
			return new BimapSet<>(tag, tagToObjs.computeIfAbsent(tag, k -> setSupplier.get()), tagToObjs, objToTags, setSupplier);
		}

		public void tag(T object, String tag) {
			tagToObjs.computeIfAbsent(tag, t -> setSupplier.get()).add(object);
			objToTags.computeIfAbsent(object, o -> setSupplier.get()).add(tag);
		}

		public void untag(T object, String tag) {
			Set<T> objs = tagToObjs.get(tag);
			if (objs != null) {
				objs.remove(object);
				if (objs.isEmpty()) {
					tagToObjs.remove(tag);
				}
			}

			Set<String> tags = objToTags.get(object);
			if (tags != null) {
				tags.remove(tag);
				if (tags.isEmpty()) {
					objToTags.remove(object);
				}
			}
		}

		public void clear() {
			tagToObjs.clear();
			objToTags.clear();
		}
	}

	public interface Provider {
		boolean load(DataSource data, Path path, DoubleConsumer progress) throws IOException;

		Set<String> tags(Card card);

		Set<Card> cards(String tag);

		Set<String> tags(Card.Printing printing);

		Set<Card.Printing> printings(String tag);

		default boolean modifiable() {
			return false;
		}

		default boolean save(Path path, DoubleConsumer progress) throws IOException {
			throw new UnsupportedOperationException();
		}

		default void tag(Card card, String tag) {
			throw new UnsupportedOperationException();
		}

		default void tag(Card.Printing printing, String tag) {
			throw new UnsupportedOperationException();
		}

		default void untag(Card card, String tag) {
			throw new UnsupportedOperationException();
		}

		default void untag(Card.Printing printing, String tag) {
			throw new UnsupportedOperationException();
		}

		interface Graphed extends Provider {
			TagGraph<Card> cardTags();

			TagGraph<Card.Printing> printingTags();

			@Override
			default Set<String> tags(Card card) {
				return cardTags().tags(card);
			}

			@Override
			default Set<Card> cards(String tag) {
				return cardTags().objects(tag);
			}

			@Override
			default Set<String> tags(Card.Printing printing) {
				return printingTags().tags(printing);
			}

			@Override
			default Set<Card.Printing> printings(String tag) {
				return printingTags().objects(tag);
			}

			@Override
			default void tag(Card card, String tag) {
				if (modifiable()) cardTags().tag(card, tag);
			}

			@Override
			default void tag(Card.Printing printing, String tag) {
				if (modifiable()) printingTags().tag(printing, tag);
			}

			@Override
			default void untag(Card card, String tag) {
				if (modifiable()) cardTags().untag(card, tag);
			}

			@Override
			default void untag(Card.Printing printing, String tag) {
				if (modifiable()) printingTags().untag(printing, tag);
			}
		}
	}

	public static class JsonProvider implements Provider.Graphed {
		private static final Path LEGACY_TAGS = MainApplication.JAR_DIR.resolve("tags.json");
		private static final String FILE_NAME = "json-tags.json";

		private final TagGraph<Card> cardTags;
		private final TagGraph<Card.Printing> printingTags;

		public JsonProvider() {
			this.cardTags = new TagGraph<>();
			this.printingTags = new TagGraph<>();
		}

		@Override
		public boolean load(DataSource data, Path path, DoubleConsumer progress) throws IOException {
			final Path filePath = path.resolve(FILE_NAME);
			final Context context = Context.get();

			cardTags.clear();

			Map<String, Set<Card>> cardNameCache = new HashMap<>();
			Map<String, Card.Printing> printingCache = new HashMap<>();
			for (Card card : context.data.cards()) {
				cardNameCache.computeIfAbsent(card.name(), c -> new HashSet<>()).add(card);

				for (Card.Printing pr : card.printings()) {
					printingCache.put(Card.Printing.Reference.format(pr), pr);
				}
			}

			if (Files.exists(filePath)) {
				try (JsonReader reader = context.gson.newJsonReader(Files.newBufferedReader(filePath))) {
					reader.beginObject();
					while (reader.peek() == JsonToken.NAME) {
						String block = reader.nextName();

						if ("cards".equals(block)) {
							readBlock(reader, (tag, cardname) -> {
								Set<Card> cards = cardNameCache.get(cardname);
								if (cards == null || cards.isEmpty()) {
									MainApplication.LOG.err("Tags file %s refers to unknown card name %s -- are we in the right universe?", filePath, cardname);
								} else {
									cards.forEach(c -> cardTags.tag(c, tag));
								}
							});
						} else if ("printings".equals(block)) {
							readBlock(reader, (tag, printingRef) -> {
								try {
									printingTags.tag(CardInstance.stringToPrinting(printingRef), tag);
								} catch (IllegalArgumentException iae) {
									MainApplication.LOG.err("Tags file %s refers to unknown printing %s -- are we in the right universe?", filePath, printingRef);
								}
							});
						} else {
							throw new IOException(String.format("Tags file %s contains an unknown block %s -- this file seems to not be a tags file. Please move it away or fix the file.", filePath, block));
						}
					}
					reader.endObject();
				}
			}

			if (Files.exists(LEGACY_TAGS)) {
				try (JsonReader legacy = context.gson.newJsonReader(Files.newBufferedReader(LEGACY_TAGS))) {
					readBlock(legacy, (tag, cardName) -> {
						Set<Card> cards = cardNameCache.get(cardName);
						if (cards == null) {
							MainApplication.LOG.err("Tags file %s refers to unknown card %s -- are we in the right universe?", LEGACY_TAGS, cardName);
						} else {
							cards.forEach(c -> cardTags.tag(c, tag));
						}
					});

					MainApplication.LOG.log("Migrated legacy tags file %s to standard JSON tags. Saving.", LEGACY_TAGS);
					save(path, d -> {});
					if (!LEGACY_TAGS.equals(filePath)) Files.delete(LEGACY_TAGS);
				}
			}

			return true;
		}

		private void readBlock(JsonReader reader, BiConsumer<String, String> entryHandler) throws IOException {
			reader.beginObject();
			while (reader.peek() == JsonToken.NAME) {
				String tag = reader.nextName();
				reader.beginArray();
				while (reader.peek() == JsonToken.STRING) {
					String obj = reader.nextString();
					entryHandler.accept(tag, obj);
				}
				reader.endArray();
			}
			reader.endObject();
		}

		@Override
		public boolean modifiable() {
			return true;
		}

		@Override
		public boolean save(Path path, DoubleConsumer progress) throws IOException {
			final Context context = Context.get();

			try (JsonWriter writer = context.gson.newJsonWriter(Files.newBufferedWriter(path.resolve(FILE_NAME)))) {
				writer.beginObject();

				writer.name("cards");
				writer.beginObject();
				for (Map.Entry<String, Set<Card>> tagsEntry : cardTags.tagToObjs.entrySet()) { // TODO private access
					writer.name(tagsEntry.getKey());

					writer.beginArray();
					for (Card card : tagsEntry.getValue()) {
						writer.value(card.name());
					}
					writer.endArray();
				}
				writer.endObject();

				writer.name("printings");
				writer.beginObject();
				for (Map.Entry<String, Set<Card.Printing>> tagsEntry : printingTags.tagToObjs.entrySet()) { // TODO private access
					writer.name(tagsEntry.getKey());

					writer.beginArray();
					for (Card.Printing pr : tagsEntry.getValue()) {
						writer.value(Card.Printing.Reference.format(pr));
					}
					writer.endArray();
				}
				writer.endObject();

				writer.endObject();
			}

			return true;
		}

		@Override
		public TagGraph<Card> cardTags() {
			return cardTags;
		}

		@Override
		public TagGraph<Card.Printing> printingTags() {
			return printingTags;
		}
	}

	public static final List<Provider> PROVIDERS = PluginUtils.providers(Provider.class);

	public Set<String> tags(Card card) {
		return PROVIDERS.stream()
				.flatMap(p -> p.tags(card).stream())
				.collect(Collectors.toSet());
	}

	public Set<String> tags(Card.Printing pr) {
		return PROVIDERS.stream()
				.flatMap(p -> p.tags(pr).stream())
				.collect(Collectors.toSet());
	}

	public Set<Card> cards(String tag) {
		return PROVIDERS.stream()
				.flatMap(p -> p.cards(tag).stream())
				.collect(Collectors.toSet());
	}

	public Set<Card.Printing> printings(String tag) {
		return PROVIDERS.stream()
				.flatMap(p -> p.printings(tag).stream())
				.collect(Collectors.toSet());
	}

	public void add(Card card, String tag) {
		PROVIDERS.stream()
				.filter(Provider::modifiable)
				.forEach(p -> p.tag(card, tag));
	}

	public void remove(Card card, String tag) {
		PROVIDERS.stream()
				.filter(Provider::modifiable)
				.forEach(p -> p.untag(card, tag));
	}

	public void load(DataSource data, Path from, DoubleConsumer progress) throws IOException {
		int n = 0;
		for (Provider provider : PROVIDERS) {
			final int in = n;
			provider.load(data, from, d -> progress.accept((in + d) / PROVIDERS.size()));
			++n;
		}
	}

	public void save(Path to, DoubleConsumer progress) throws IOException {
		int n = 0;
		int s = PROVIDERS.size();
		for (Provider provider : PROVIDERS) {
			if (provider.modifiable()) {
				final int in = n, is = s;
				provider.save(to, d -> progress.accept((in + d) / is));
				++n;
			} else {
				--s;
				progress.accept(n / (double) s);
			}
		}
	}

	public static void main(String[] args) throws IOException {
		Preferences prefs = Preferences.instantiate();
		DataSource data;
		try {
			java.lang.reflect.Field dataSourceField = Preferences.class.getDeclaredField("dataSource");
			dataSourceField.setAccessible(true);
			data = (DataSource) dataSourceField.get(prefs);
		} catch (ReflectiveOperationException roe) {
			throw new AssertionError(roe);
		}

		Context.instantiate(data);
		Preferences.get().dataSource.loadData(Preferences.get().dataPath, d -> System.out.printf("\rLoad data: %.2f%%", d * 100.0));
		JsonProvider jsonTags = new JsonProvider();
		jsonTags.load(data, MainApplication.JAR_DIR, d -> System.out.printf("\rLoad tags: %.2f%%", d * 100.0));
		jsonTags.save(MainApplication.JAR_DIR, d -> {});
	}
}
