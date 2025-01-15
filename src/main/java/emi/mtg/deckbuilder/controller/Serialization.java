package emi.mtg.deckbuilder.controller;

import com.google.gson.*;
import com.google.gson.reflect.TypeToken;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonToken;
import com.google.gson.stream.JsonWriter;
import emi.lib.mtg.Card;
import emi.lib.mtg.DataSource;
import emi.lib.mtg.game.Format;
import emi.mtg.deckbuilder.controller.serdes.DeckImportExport;
import emi.mtg.deckbuilder.controller.serdes.impl.NameOnlyImporter;
import emi.mtg.deckbuilder.model.CardInstance;
import emi.mtg.deckbuilder.model.Preferences;
import emi.mtg.deckbuilder.util.InstanceMap;
import emi.mtg.deckbuilder.util.Slog;
import emi.mtg.deckbuilder.view.MainApplication;
import emi.mtg.deckbuilder.view.components.CardView;
import emi.mtg.deckbuilder.view.search.SearchProvider;
import javafx.beans.property.Property;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;

import java.io.IOException;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.NoSuchElementException;
import java.util.UUID;
import java.util.function.Function;

public class Serialization {
	public static final Gson GSON;
	static {
		GsonBuilder builder = new GsonBuilder()
				.setPrettyPrinting()
				.setExclusionStrategies()
				.registerTypeAdapter(Instant.class, Serialization.createInstantAdapter())
				.registerTypeAdapter(SearchProvider.class, Serialization.createSearchProviderAdapter())
				.registerTypeAdapter(CardView.Grouping.class, Serialization.createCardViewGroupingAdapter())
				.registerTypeAdapter(CardView.ActiveSorting.class, Serialization.createActiveSortingTypeAdapter())
				.registerTypeAdapter(Format.class, Serialization.createFormatAdapter())
				.registerTypeAdapter(DeckImportExport.CopyPaste.class, Serialization.createCopyPasteSerdesAdapter())
				.registerTypeAdapter(SimpleBooleanProperty.class, Serialization.createBooleanPropertyTypeAdapter())
				.registerTypeAdapter(DataSource.class, Serialization.createDataSourceNameAdapter())
				.registerTypeHierarchyAdapter(Path.class, Serialization.createPathTypeAdapter())
				.registerTypeAdapterFactory(Serialization.createInstanceMapTypeAdapterFactory())
				.registerTypeAdapterFactory(Serialization.createPropertyTypeAdapterFactory())
				.registerTypeAdapterFactory(Serialization.createObservableListTypeAdapterFactory())
				.registerTypeAdapterFactory(Serialization.createPreferredPrintAdapterFactory());

		Preferences.registerPluginTypeAdapters(builder);

		GSON = builder.create();
	};
	public static final Slog LOG = MainApplication.LOG.child("Serialization");

	public abstract static class StringTypeAdapter<T> extends TypeAdapter<T> {
		protected abstract String toString(T value) throws IOException;

		protected abstract T fromString(String value) throws IOException;

		@Override
		public void write(JsonWriter out, T value) throws IOException {
			if (value == null) {
				out.nullValue();
			} else {
				out.value(toString(value));
			}
		}

		@Override
		public T read(JsonReader in) throws IOException {
			String v;
			switch (in.peek()) {
				case STRING:
					v = in.nextString();
					break;
				case NAME:
					v = in.nextName();
					break;
				default:
					throw new IOException("JSON file error: Expected string or name, got " + in.peek().name());
			}

			return fromString(v);
		}
	}

	private static class FunctionalStringTypeAdapter<T> extends StringTypeAdapter<T> {
		protected final Function<T, String> toString;
		protected final Function<String, T> fromString;

		public FunctionalStringTypeAdapter(Function<T, String> toString, Function<String, T> fromString) {
			this.toString = toString;
			this.fromString = fromString;
		}

		@Override
		protected String toString(T value) throws IOException {
			return toString.apply(value);
		}

		@Override
		protected T fromString(String value) throws IOException {
			return fromString.apply(value);
		}
	}

	public static TypeAdapter<Instant> createInstantAdapter() {
		return new FunctionalStringTypeAdapter<>(t -> DateTimeFormatter.RFC_1123_DATE_TIME.format(t.atOffset(ZoneOffset.UTC)), s -> Instant.from(DateTimeFormatter.RFC_1123_DATE_TIME.parse(s)));
	}

	public static TypeAdapter<SearchProvider> createSearchProviderAdapter() {
		return new FunctionalStringTypeAdapter<>(SearchProvider::name, SearchProvider.SEARCH_PROVIDERS::get);
	}

	public static TypeAdapter<Format> createFormatAdapter() {
		return new FunctionalStringTypeAdapter<>(Format::name, s -> {
			if ("EDH".equals(s)) s = "Commander";
			try {
				return Format.valueOf(s);
			} catch (IllegalArgumentException iae) {
				return Format.Freeform;
			}
		});
	}

	public static TypeAdapter<DeckImportExport.CopyPaste> createCopyPasteSerdesAdapter() {
		return new FunctionalStringTypeAdapter<>(Object::toString, s -> DeckImportExport.COPYPASTE_PROVIDERS.stream()
				.filter(serdes -> serdes.toString().equals(s))
				.findAny()
				.orElse(null));
	}

	public static TypeAdapter<CardView.Grouping> createCardViewGroupingAdapter() {
		return new FunctionalStringTypeAdapter<>(CardView.Grouping::name, s -> CardView.GROUPINGS.stream()
				.filter(g -> "Converted Mana Cost".equals(s) ? g instanceof emi.mtg.deckbuilder.view.groupings.ManaValue : g.name().equals(s))
				.findAny()
				.orElseGet(() -> {
					LOG.err("Couldn't find grouping factory %s! Did a plugin go away? Defaulting to CMC...", s);
					return emi.mtg.deckbuilder.view.groupings.ManaValue.INSTANCE;
				}));
	}

	public static TypeAdapter<Card> createCardAdapter() {
		return new FunctionalStringTypeAdapter<>(Card::fullName, NameOnlyImporter::findCard);
	}

	public static TypeAdapterFactory createCardPrintAdapterFactory() {
		return new TypeAdapterFactory() {
			@Override
			public <T> TypeAdapter<T> create(Gson gson, TypeToken<T> type) {
				if (CardInstance.class.isAssignableFrom(type.getRawType())) {
					return gson.getDelegateAdapter(this, type);
				} else if (Card.Print.class.isAssignableFrom(type.getRawType())) {
					return new StringTypeAdapter<T>() {
						@Override
						public T fromString(String i) throws IOException {
							try {
								Card.Print.Reference ref = Card.Print.Reference.valueOf(i);
								emi.lib.mtg.Set set = Context.get().data.set(ref.setCode());

								if (set != null) {
									emi.lib.mtg.Card.Print pr = set.print(ref.collectorNumber());
									if (pr != null && ref.name().equals(pr.card().name())) return (T) pr;

									LOG.err("No exact match for %s in %s; trying by card name.%n", ref, set.name());

									// Either the set has no printing by that collector number, or the printing by that
									// collector number is of a different card. Either way, we're in the rough. Try to
									// find any card with the same name in the set.
									pr = set.prints().stream()
											.filter(pr2 -> ref.name().equals(pr2.card().name()))
											.findAny()
											.orElse(null);

									if (pr != null) return (T) pr;
								}

								LOG.err("Unable to locate set/card matching %s; trying by card name only.%n", ref);

								// Either the set wasn't matched or had no card by this name.
								// Locate the card the hard way, and return the preferred printing.
								emi.lib.mtg.Card card = Context.get().data.cards().stream()
										.filter(c -> ref.name().equals(c.name()))
										.findAny()
										.orElse(null);

								if (card != null) {
									return (T) Preferences.get().preferredPrint(card);
								}

								throw new IOException("Unable to find any card/printing matching " + ref + "; are we in the right universe?");
							} catch (IllegalArgumentException iae) {
								return (T) Context.get().data.print(UUID.fromString(i));
							}
						}

						@Override
						protected String toString(T pr) throws IOException {
							return Card.Print.Reference.format((Card.Print) pr);
						}
					};
				} else {
					return null;
				}
			}
		};
	}

	public static TypeAdapterFactory createPreferredPrintAdapterFactory() {
		return new TypeAdapterFactory() {
			@Override
			public <T> TypeAdapter<T> create(Gson gson, TypeToken<T> type) {
				if (!Preferences.PreferredPrint.class.isAssignableFrom(type.getRawType())) return null;

				TypeAdapter<Preferences.PreferredPrint> reflective = gson.getDelegateAdapter(this, (TypeToken<Preferences.PreferredPrint>) type);

				return (TypeAdapter<T>) new TypeAdapter<Preferences.PreferredPrint>() {
					@Override
					public void write(JsonWriter out, Preferences.PreferredPrint value) throws IOException {
						reflective.write(out, value);
					}

					@Override
					public Preferences.PreferredPrint read(JsonReader in) throws IOException {
						String val;
						if (in.peek() == JsonToken.STRING) {
							val = in.nextString();
						} else if (in.peek() == JsonToken.NAME) {
							val = in.nextName();
						} else {
							val = null;
						}

						if (val != null) {
							Preferences.deferredPreferredPrints.add(UUID.fromString(val));
							return null;
						}

						return reflective.read(in);
					}
				};
			}
		};
	}

	public static TypeAdapter<Path> createPathTypeAdapter() {
		return new FunctionalStringTypeAdapter<>(Path::toString, Paths::get);
	}

	public static TypeAdapter<CardView.ActiveSorting> createActiveSortingTypeAdapter() {
		return new FunctionalStringTypeAdapter<>(
				v -> String.format("%s%s", v.descending.get() ? "+" : "-", v.toString()),
				s -> CardView.SORTINGS.stream()
						.filter(n -> s.endsWith("Converted Mana Cost") ? n instanceof emi.mtg.deckbuilder.view.sortings.ManaValue : s.substring(1).equals(n.toString()))
						.findAny()
						.map(sorting -> new CardView.ActiveSorting(sorting, s.startsWith("+")))
						.orElse(null)
		);
	}

	private static TypeAdapter<SimpleBooleanProperty> createBooleanPropertyTypeAdapter() {
		return new TypeAdapter<SimpleBooleanProperty>() {
			@Override
			public void write(JsonWriter out, SimpleBooleanProperty value) throws IOException {
				out.value(value.get());
			}

			@Override
			public SimpleBooleanProperty read(JsonReader in) throws IOException {
				return new SimpleBooleanProperty(in.nextBoolean());
			}
		};
	}

	public static TypeAdapter<DataSource> createDataSourceNameAdapter() {
		return new TypeAdapter<DataSource>() {

			@Override
			public void write(JsonWriter out, DataSource value) throws IOException {
				out.value(value.toString());
			}

			@Override
			public DataSource read(JsonReader in) throws IOException {
				String value;
				switch (in.peek()) {
					case NAME:
						value = in.nextName();
						break;
					case STRING:
						value = in.nextString();
						break;
					case NULL:
						return null;
					default:
						throw new IOException("DataSource must be a string or name!");
				}

				if (value == null || value.isEmpty()) return null;

				for (DataSource source : MainApplication.DATA_SOURCES) {
					if (value.equals(source.toString())) {
						return source;
					}
				}

				throw new NoSuchElementException("Unknown data source " + value + " -- have your plugins changed?");
			}
		};
	}

	public static TypeAdapterFactory createInstanceMapTypeAdapterFactory() {
		return new InstanceMap.TypeAdapterFactory();
	}

	public static TypeAdapterFactory createPropertyTypeAdapterFactory() {
		return new TypeAdapterFactory() {
			@Override
			public <T> TypeAdapter<T> create(Gson gson, TypeToken<T> typeToken) {
				if (!Property.class.isAssignableFrom(typeToken.getRawType())) {
					return null;
				}

				if (!(typeToken.getType() instanceof ParameterizedType)) {
					return null; // TODO: Raw object property...?
				}

				ParameterizedType type = (ParameterizedType) typeToken.getType();
				Type innerType = type.getActualTypeArguments()[0];
				TypeAdapter<?> inner = gson.getAdapter(TypeToken.get(innerType));

				if (inner == null) {
					LOG.err("Can't create property type adapter for un-adapted inner type " + innerType.getTypeName());
					return null; // Can't deserialize without inner type...
				}

				return (TypeAdapter<T>) createTypeAdapter(inner);
			}

			private <T> TypeAdapter<Property<T>> createTypeAdapter(TypeAdapter<T> inner) {
				return new TypeAdapter<Property<T>>() {
					@Override
					public void write(JsonWriter out, Property<T> value) throws IOException {
						inner.write(out, value.getValue());
					}

					@Override
					public Property<T> read(JsonReader in) throws IOException {
						return new SimpleObjectProperty<>(inner.read(in));
					}
				};
			}
		};
	}

	public static TypeAdapterFactory createObservableListTypeAdapterFactory() {
		return new TypeAdapterFactory() {
			@Override
			public <T> TypeAdapter<T> create(Gson gson, TypeToken<T> typeToken) {
				if (!ObservableList.class.isAssignableFrom(typeToken.getRawType())) {
					return null;
				}

				if (!(typeToken.getType() instanceof ParameterizedType)) {
					return null; // TODO: Raw object property...?
				}

				ParameterizedType type = (ParameterizedType) typeToken.getType();
				Type innerType = type.getActualTypeArguments()[0];
				TypeAdapter<?> adapter = gson.getAdapter(TypeToken.get(innerType));

				if (adapter == null) {
					LOG.err("Can't create observable list type adapter for un-adapted inner type " + innerType.getTypeName());
					return null; // Can't deserialize without inner type...
				}

				return (TypeAdapter<T>) createTypeAdapter(adapter);
			}

			private <T> TypeAdapter<ObservableList<T>> createTypeAdapter(TypeAdapter<T> inner) {
				return new TypeAdapter<ObservableList<T>>() {
					@Override
					public void write(JsonWriter out, ObservableList<T> value) throws IOException {
						out.beginArray();

						for (T el : value) {
							inner.write(out, el);
						}

						out.endArray();
					}

					@Override
					public ObservableList<T> read(JsonReader in) throws IOException {
						in.beginArray();

						ObservableList<T> list = FXCollections.observableArrayList();

						while (in.peek() != JsonToken.END_ARRAY) {
							list.add(inner.read(in));
						}

						in.endArray();

						return list;
					}
				};
			}
		};
	}
}
