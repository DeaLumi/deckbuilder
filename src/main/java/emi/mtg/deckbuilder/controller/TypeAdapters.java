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
import javafx.beans.property.Property;
import javafx.beans.property.SimpleObjectProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;

import java.io.IOException;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Map;
import java.util.UUID;

public class TypeAdapters {
	public static TypeAdapter<Format> createFormatAdapter(Map<String, Format> formats) {
		return new TypeAdapter<Format>() {
			@Override
			public void write(JsonWriter out, Format value) throws IOException {
				if (value == null) {
					out.nullValue();
				} else {
					out.value(formats.entrySet().stream().filter(e -> e.getValue().equals(value)).map(Map.Entry::getKey).findAny().orElse(null));
				}
			}

			@Override
			public Format read(JsonReader in) throws IOException {
				switch (in.peek()) {
					case STRING:
						return formats.get(in.nextString());
					default:
						return null;
				}
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
				UUID id = UUID.fromString(in.nextString());
				Card.Printing pr = data.printing(id);
				return pr;
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
