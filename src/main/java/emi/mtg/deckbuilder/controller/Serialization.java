package emi.mtg.deckbuilder.controller;

import com.google.gson.*;
import com.google.gson.reflect.TypeToken;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonToken;
import com.google.gson.stream.JsonWriter;
import emi.lib.mtg.Card;
import emi.lib.mtg.game.Format;
import emi.mtg.deckbuilder.controller.serdes.impl.NameOnlyImporter;
import emi.mtg.deckbuilder.view.components.CardView;
import emi.mtg.deckbuilder.view.groupings.ConvertedManaCost;
import javafx.beans.property.Property;
import javafx.beans.property.SimpleObjectProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;

import java.io.IOException;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.UUID;
import java.util.function.Function;

public class Serialization {
	public static final Gson GSON = new GsonBuilder()
			.setPrettyPrinting()
			.setExclusionStrategies()
			.registerTypeAdapter(CardView.Grouping.class, Serialization.createCardViewGroupingAdapter())
			.registerTypeAdapter(CardView.ActiveSorting.class, Serialization.createActiveSortingTypeAdapter())
			.registerTypeAdapter(Format.class, Serialization.createFormatAdapter())
			.registerTypeAdapter(Path.class, Serialization.createPathTypeAdapter())
			.registerTypeAdapterFactory(Serialization.createPropertyTypeAdapterFactory())
			.registerTypeAdapterFactory(Serialization.createObservableListTypeAdapterFactory())
			.create();

	private static class StringTypeAdapter<T> extends TypeAdapter<T> {
		protected final Function<T, String> toString;
		protected final Function<String, T> fromString;

		public StringTypeAdapter(Function<T, String> toString, Function<String, T> fromString) {
			this.toString = toString;
			this.fromString = fromString;
		}

		@Override
		public void write(JsonWriter out, T value) throws IOException {
			if (value == null) {
				out.nullValue();
			} else {
				out.value(toString.apply(value));
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

			return fromString.apply(v);
		}
	}

	public static TypeAdapter<Format> createFormatAdapter() {
		return new StringTypeAdapter<>(Format::name, s -> {
			if ("EDH".equals(s)) s = "Commander";
			try {
				return Format.valueOf(s);
			} catch (IllegalArgumentException iae) {
				return Format.Freeform;
			}
		});
	}

	public static TypeAdapter<CardView.Grouping> createCardViewGroupingAdapter() {
		return new StringTypeAdapter<>(CardView.Grouping::name, s -> CardView.GROUPINGS.stream()
				.filter(g -> g.name().equals(s))
				.findAny()
				.orElseGet(() -> {
					System.err.println(String.format("Couldn't find grouping factory %s! Did a plugin go away? Defaulting to CMC...", s));
					return ConvertedManaCost.INSTANCE;
				}));
	}

	public static TypeAdapter<Card> createCardAdapter() {
		return new StringTypeAdapter<>(Card::fullName, NameOnlyImporter::findCard);
	}

	public static TypeAdapter<Card.Printing> createCardPrintingAdapter() {
		return new StringTypeAdapter<>(p -> p.id().toString(), i -> Context.get().data.printing(UUID.fromString(i)));
	}

	public static TypeAdapter<Path> createPathTypeAdapter() {
		return new StringTypeAdapter<>(Path::toString, Paths::get);
	}

	public static TypeAdapter<CardView.ActiveSorting> createActiveSortingTypeAdapter() {
		return new StringTypeAdapter<>(
				v -> String.format("%s%s", v.descending.get() ? "+" : "-", v.toString()),
				s -> CardView.SORTINGS.stream()
						.filter(n -> s.substring(1).equals(n.toString()))
						.findAny()
						.map(sorting -> new CardView.ActiveSorting(sorting, s.startsWith("+")))
						.orElse(null)
		);
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
					System.err.println("Can't create property type adapter for un-adapted inner type " + innerType.getTypeName());
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
					System.err.println("Can't create observable list type adapter for un-adapted inner type " + innerType.getTypeName());
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