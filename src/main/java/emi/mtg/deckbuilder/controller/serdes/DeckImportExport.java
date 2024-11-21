package emi.mtg.deckbuilder.controller.serdes;

import emi.mtg.deckbuilder.model.DeckList;
import emi.mtg.deckbuilder.util.PluginUtils;
import emi.mtg.deckbuilder.view.MainApplication;
import javafx.scene.input.Clipboard;
import javafx.scene.input.ClipboardContent;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.stream.Collectors;

public interface DeckImportExport {
	enum Feature {
		DeckName("Deck Name"),
		Author("Author"),
		Description("Description"),
		Format("Intended deck format"),
		CardArt("Specific card printings"),
		OtherZones("Zones (beyond library & sideboard)"),
		Tags("Deck-specific card tags");

		private final String description;

		Feature(String description) {
			this.description = description;
		}

		@Override
		public String toString() {
			return description;
		}
	}

	interface DataFormat {
		String name();

		String extension();

		String mime();

		String description();

		javafx.scene.input.DataFormat fxFormat();

		Class<?> javaType();

		static DataFormat singleton(String name, String extension, String mime, String description, javafx.scene.input.DataFormat fxFormat, Class<?> javaType) {
			return new DataFormat() {
				@Override
				public String name() {
					return name;
				}

				@Override
				public String toString() {
					return name;
				}

				@Override
				public String extension() {
					return extension;
				}

				@Override
				public String mime() {
					return mime;
				}

				@Override
				public String description() {
					return description;
				}

				@Override
				public javafx.scene.input.DataFormat fxFormat() {
					return fxFormat;
				}

				@Override
				public Class<?> javaType() {
					return javaType;
				}
			};
		}
	}

	DataFormat importFormat();

	DataFormat exportFormat();

	DeckList importDeck(Path from) throws IOException;

	void exportDeck(DeckList deck, Path to) throws IOException;

	EnumSet<Feature> supportedFeatures();

	interface Monotype extends DeckImportExport {
		DataFormat format();

		@Override
		default DataFormat importFormat() {
			return format();
		}

		@Override
		default DataFormat exportFormat() {
			return format();
		}
	}

	interface CopyPaste extends DeckImportExport {
		DeckList importDeck(Clipboard from) throws IOException;

		void exportDeck(DeckList deck, ClipboardContent clipboard) throws IOException;
	}

	interface Textual extends DeckImportExport, CopyPaste {
		DeckList importDeck(Reader from) throws IOException;
		void exportDeck(DeckList deck, Writer to) throws IOException;

		default DeckList importDeck(Path from) throws IOException {
			try (Reader source = Files.newBufferedReader(from)) {
				return importDeck(source);
			}
		}

		default void exportDeck(DeckList deck, Path to) throws IOException {
			try (Writer sink = Files.newBufferedWriter(to, StandardCharsets.UTF_8)) {
				exportDeck(deck, sink);
			}
		}

		default DeckList deserializeDeck(String from) throws IOException {
			try (Reader source = new StringReader(from)) {
				DeckList deck = importDeck(source);
				if (!supportedFeatures().contains(Feature.DeckName)) deck.nameProperty().setValue("Imported Deck");
				return deck;
			}
		}

		default String serializeDeck(DeckList deck) throws IOException {
			try (Writer sink = new StringWriter()) {
				exportDeck(deck, sink);
				sink.flush();
				return sink.toString();
			}
		}

		default DeckList importDeck(Clipboard from) throws IOException {
			if (!String.class.isAssignableFrom(importFormat().javaType())) throw new AssertionError("Textual formats must have String-based javaType!");
			if (!from.hasContent(importFormat().fxFormat())) throw new IOException("Clipboard does not contain a deck!");
			return deserializeDeck((String) from.getContent(importFormat().fxFormat()));
		}

		default void exportDeck(DeckList deck, ClipboardContent to) throws IOException {
			to.put(exportFormat().fxFormat(), serializeDeck(deck));
		}
	}

	static void checkLinkage(DeckImportExport serdes) {
		MainApplication.LOG.log("Checking linkage for %s: Input formats = %s, output formats = %s", serdes, serdes.importFormat(), serdes.exportFormat());
	}

	List<DeckImportExport> DECK_FORMAT_PROVIDERS = PluginUtils.providers(DeckImportExport.class, DeckImportExport::checkLinkage);

	List<DeckImportExport.CopyPaste> COPYPASTE_PROVIDERS = Collections.unmodifiableList(DECK_FORMAT_PROVIDERS.stream()
			.filter(s -> s instanceof DeckImportExport.CopyPaste)
			.map(s -> (DeckImportExport.CopyPaste) s)
			.collect(Collectors.toList()));
}
