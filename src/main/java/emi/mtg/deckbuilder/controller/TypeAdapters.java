package emi.mtg.deckbuilder.controller;

import com.google.gson.TypeAdapter;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonWriter;
import emi.lib.mtg.Card;
import emi.lib.mtg.DataSource;
import emi.lib.mtg.game.Format;

import java.io.IOException;
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
				return data.printing(UUID.fromString(in.nextString()));
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
}
