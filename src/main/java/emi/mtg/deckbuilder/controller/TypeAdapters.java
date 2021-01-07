package emi.mtg.deckbuilder.controller;

import com.google.gson.Gson;
import com.google.gson.TypeAdapter;
import com.google.gson.TypeAdapterFactory;
import com.google.gson.reflect.TypeToken;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonToken;
import com.google.gson.stream.JsonWriter;
import emi.lib.mtg.Card;
import emi.lib.mtg.DataSource;
import emi.lib.mtg.game.Format;
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

public class TypeAdapters {
	public static TypeAdapter<Format> createFormatAdapter() {
		return new TypeAdapter<Format>() {
			@Override
			public void write(JsonWriter out, Format value) throws IOException {
				if (value == null) {
					out.nullValue();
				} else {
					out.value(value.name());
				}
			}

			@Override
			public Format read(JsonReader in) throws IOException {
				String v;
				switch (in.peek()) {
					case STRING:
						v = in.nextString();
						break;
					case NAME:
						v = in.nextName();
						break;
					default:
						return null;
				}
				if ("EDH".equals(v)) v = "Commander"; // Quick hack for continuity.
				try {
					return Format.valueOf(v);
				} catch (IllegalArgumentException iae) {
					return Format.Freeform;
				}
			}
		};
	}

	public static TypeAdapter<CardView.Grouping> createCardViewGroupingAdapter() {
		return new TypeAdapter<CardView.Grouping>() {
			@Override
			public void write(JsonWriter out, CardView.Grouping value) throws IOException {
				if (value == null) {
					out.nullValue();
				} else {
					out.value(value.name());
				}
			}

			@Override
			public CardView.Grouping read(JsonReader in) throws IOException {
				String v;
				switch (in.peek()) {
					case NAME:
						v = in.nextName();
						break;
					case STRING:
						v = in.nextString();
						break;
					default:
						return null;
				}

				return CardView.GROUPINGS.stream()
						.filter(g -> g.name().equals(v))
						.findAny()
						.orElseGet(() -> {
							System.err.println(String.format("Couldn't find grouping factory %s! Did a plugin go away? Defaulting to CMC...", v));
							return ConvertedManaCost.INSTANCE;
						});
			}
		};
	}

	public static TypeAdapter<Card.Printing> createCardPrintingAdapter(DataSource data) {
		return new TypeAdapter<Card.Printing>() {
			@Override
			public void write(JsonWriter out, Card.Printing value) throws IOException {
				out.value(value.id().toString());
			}

			@Override
			public Card.Printing read(JsonReader in) throws IOException {
				String v;
				switch (in.peek()) {
					case STRING:
						v = in.nextString();
						break;
					case NAME:
						v = in.nextName();
						break;
					default:
						return null;
				}
				return data.printing(UUID.fromString(v));
			}
		};
	}

	public static TypeAdapter<Path> createPathTypeAdapter() {
		return new TypeAdapter<Path>() {
			@Override
			public void write(JsonWriter jsonWriter, Path path) throws IOException {
				if (path == null) {
					jsonWriter.nullValue();
				} else {
					jsonWriter.value(path.toString());
				}
			}

			@Override
			public Path read(JsonReader jsonReader) throws IOException {
				switch (jsonReader.peek()) {
					case NULL:
						return null;
					case STRING:
						return Paths.get(jsonReader.nextString());
					case NAME:
						return Paths.get(jsonReader.nextName());
					default:
						assert false;
						return null;
				}
			}
		};
	}

	public static TypeAdapter<CardView.ActiveSorting> createActiveSortingTypeAdapter() {
		return new TypeAdapter<CardView.ActiveSorting>() {
			@Override
			public void write(JsonWriter out, CardView.ActiveSorting value) throws IOException {
				out.value(String.format("%s%s", value.descending.get() ? "+" : "-", value.toString()));
			}

			@Override
			public CardView.ActiveSorting read(JsonReader in) throws IOException {
				String val;
				switch (in.peek()) {
					case STRING:
						val = in.nextString();
						break;
					case NAME:
						val = in.nextName();
						break;
					default:
						assert false;
						return null;
				}

				boolean descending = val.startsWith("+");
				CardView.Sorting sorting = CardView.SORTINGS.stream()
						.filter(s -> val.substring(1).equals(s.toString()))
						.findAny()
						.orElse(null);

				if (sorting == null) {
					return null;
				} else {
					return new CardView.ActiveSorting(sorting, descending);
				}
			}
		};
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
