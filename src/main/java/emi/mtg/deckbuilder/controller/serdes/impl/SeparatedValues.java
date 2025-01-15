package emi.mtg.deckbuilder.controller.serdes.impl;

import emi.lib.mtg.Card;
import emi.lib.mtg.game.Zone;
import emi.mtg.deckbuilder.controller.Context;
import emi.mtg.deckbuilder.controller.serdes.DeckImportExport;
import emi.mtg.deckbuilder.model.CardInstance;
import emi.mtg.deckbuilder.model.DeckList;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BiFunction;

public abstract class SeparatedValues implements DeckImportExport.Textual {
	private enum SVDataFormat implements DataFormat {
		CSV ("csv", "text/csv", "Comma-separated plaintext spreadsheet."),
		TSV ("tsv", "text/tsv", "Tab-separated plaintext spreadsheet."),
		Buylist("tsv", "text/tsv", "Tab-separated plaintext with columns for planning the construction of a paper deck.");

		private final String extension, mime, description;

		SVDataFormat(String extension, String mime, String description) {
			this.extension = extension;
			this.mime = mime;
			this.description = description;
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
			return javafx.scene.input.DataFormat.PLAIN_TEXT;
		}

		@Override
		public Class<?> javaType() {
			return String.class;
		}
	}

	protected abstract void parseLine(DeckList deck, Zone currentZone, String[] values) throws IOException;

	protected abstract LinkedHashMap<String, BiFunction<CardInstance, Integer, String>> zoneData();

	protected abstract char separator();

	@Override
	public DeckList importDeck(Reader from) throws IOException {
		throw new UnsupportedOperationException(); // TODO: Add importer
	}

	@Override
	public void exportDeck(DeckList deck, Writer writer) throws IOException {
		final char separator = separator();
		int row = 1;

		final LinkedHashMap<String, BiFunction<CardInstance, Integer, String>> fns = zoneData();
		boolean first = true;
		for (String key : fns.keySet()) {
			if (first) {
				first = false;
			} else {
				writer.append(separator);
			}
			writer.append(key);
		}
		writer.append('\n');
		++row;

		first = true;
		for (Zone zone : deck.cards().keySet()) {
			if (deck.cards(zone).isEmpty()) continue;

			if (first) {
				first = false;
			} else {
				for (int i = 0; i < fns.size(); ++i) writer.append(separator);
				writer.append('\n');
				++row;
			}

			writer.append(zone.toString());
			for (int i = 0; i < fns.size() - 1; ++i) writer.append(separator);
			writer.append('\n');
			++row;

			Map<CardInstance, AtomicInteger> histo = deck.printHisto(zone);
			for (Map.Entry<CardInstance, AtomicInteger> entry : histo.entrySet()) {
				first = true;
				for (BiFunction<CardInstance, Integer, String> column : fns.values()) {
					if (first) {
						first = false;
					} else {
						writer.append(separator);
					}

					String val = String.format(column.apply(entry.getKey(), entry.getValue().get()), row);
					if (val.indexOf(separator) >= 0 || val.indexOf('\r') >= 0 || val.indexOf('\n') >= 0 || val.indexOf('\"') >= 0) {
						val = val.replaceAll("\"", "\"\"");
						val = "\"" + val + "\"";
					}
					writer.append(val);
				}
				writer.append('\n');
				++row;
			}
		}
	}

	@Override
	public EnumSet<Feature> supportedFeatures() {
		return EnumSet.of(Feature.CardArt, Feature.OtherZones);
	}

	@Override
	public final DataFormat importFormat() {
		return null; // TODO: Add importer
	}

	public static abstract class BasicValues extends SeparatedValues {
		@Override
		protected LinkedHashMap<String, BiFunction<CardInstance, Integer, String>> zoneData() {
			LinkedHashMap<String, BiFunction<CardInstance, Integer, String>> tmp = new LinkedHashMap<>();

			tmp.put("Qty", (pr, count) -> Integer.toString(count));
			tmp.put("Name", (pr, count) -> pr.card().name());
			tmp.put("Set", (pr, count) -> pr.set().code().toUpperCase());
			tmp.put("No.", (pr, count) -> pr.collectorNumber());

			return tmp;
		}

		@Override
		protected void parseLine(DeckList deck, Zone currentZone, String[] values) throws IOException {
			int count = Integer.parseInt(values[0]);

			emi.lib.mtg.Set set = Context.get().data.set(values[2]);
			for (Card.Print pr : set.prints()) {
				if (!pr.collectorNumber().equals(values[3])) continue;
				if (!pr.card().name().equals(values[1])) continue;

				for (int i = 0; i < count; ++i) deck.cards(currentZone).add(new CardInstance(pr));
				return;
			}

			throw new IOException("Unable to find card matching " + Arrays.toString(values) + "; did the data source change?");
		}

	}

	public static class CSV extends BasicValues {
		@Override
		public String toString() {
			return "Comma-Separated Values";
		}

		@Override
		public DataFormat exportFormat() {
			return SVDataFormat.CSV;
		}

		@Override
		protected char separator() {
			return ',';
		}
	}

	public static class TSV extends BasicValues {
		@Override
		public String toString() {
			return "Tab-Separated Values";
		}

		@Override
		public DataFormat exportFormat() {
			return SVDataFormat.TSV;
		}

		@Override
		protected char separator() {
			return '\t';
		}
	}

	public static class BuylistTSV extends SeparatedValues {
		@Override
		public String toString() {
			return "Buylist TSV";
		}

		@Override
		public DataFormat exportFormat() {
			return SVDataFormat.Buylist;
		}

		@Override
		protected LinkedHashMap<String, BiFunction<CardInstance, Integer, String>> zoneData() {
			LinkedHashMap<String, BiFunction<CardInstance, Integer, String>> tmp = new LinkedHashMap<>();

			tmp.put("Qty", (pr, count) -> Integer.toString(count)); // A
			tmp.put("Name", (pr, count) -> pr.card().name()); // B
			tmp.put("Set", (pr, count) -> pr.set().code().toUpperCase()); // C
			tmp.put("No.", (pr, count) -> pr.collectorNumber()); // D
			tmp.put("Cost Per.", (pr, count) -> "$0.00"); // E
			tmp.put("Total", (pr, count) -> "=A%1$d*E%1$d"); // F
			tmp.put("State", (pr, count) -> ""); // G
			tmp.put("Action", (pr, count) -> ""); // H
			tmp.put("Notes", (pr, count) -> ""); // I
			tmp.put("White Proxy", (pr, count) -> "=IF($H%1$d=J$1,$B%1$d,\"\")"); // J
			tmp.put("Color Proxy", (pr, count) -> "=IF($H%1$d=K$1,$B%1$d,\"\")"); // K
			tmp.put("Fancy Proxy", (pr, count) -> "=IF($H%1$d=L$1,$B%1$d,\"\")"); // L
			tmp.put("Buy", (pr, count) -> "=IF($H%1$d=M$1,CONCATENATE($A%1$d, \" \", $B%1$d),\"\")"); // M
			tmp.put("Buy - Price", (pr, count) -> "=IF($M%1$d<>\"\",$F%1$d,\"\")"); // N

			return tmp;
		}

		@Override
		protected void parseLine(DeckList deck, Zone currentZone, String[] values) throws IOException {
			int count = Integer.parseInt(values[0]);

			emi.lib.mtg.Set set = Context.get().data.set(values[2]);
			for (Card.Print pr : set.prints()) {
				if (!pr.collectorNumber().equals(values[3])) continue;
				if (!pr.card().name().equals(values[1])) continue;

				for (int i = 0; i < count; ++i) deck.cards(currentZone).add(new CardInstance(pr));
				return;
			}

			throw new IOException("Unable to find card matching " + Arrays.toString(values) + "; did the data source change?");
		}

		@Override
		protected char separator() {
			return '\t';
		}
	}
}
