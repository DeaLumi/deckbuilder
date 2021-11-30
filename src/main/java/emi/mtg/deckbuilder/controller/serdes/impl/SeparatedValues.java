package emi.mtg.deckbuilder.controller.serdes.impl;

import emi.lib.mtg.Card;
import emi.lib.mtg.game.Zone;
import emi.mtg.deckbuilder.controller.Context;
import emi.mtg.deckbuilder.controller.serdes.DeckImportExport;
import emi.mtg.deckbuilder.model.CardInstance;
import emi.mtg.deckbuilder.model.DeckList;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BiFunction;

public abstract class SeparatedValues implements DeckImportExport {
	protected abstract void parseLine(DeckList deck, Zone currentZone, String[] values) throws IOException;

	protected abstract LinkedHashMap<String, BiFunction<CardInstance, Integer, String>> zoneData();

	protected abstract char separator();

	@Override
	public DeckList importDeck(Path from) throws IOException {
		return null; // TODO: Add importer
	}

	@Override
	public void exportDeck(DeckList deck, Path to) throws IOException {
		BufferedWriter writer = Files.newBufferedWriter(to);

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

			Map<CardInstance, AtomicInteger> histo = deck.printingHisto(zone);
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

		writer.close();
	}

	@Override
	public EnumSet<Feature> supportedFeatures() {
		return EnumSet.of(Feature.CardArt, Feature.OtherZones);
	}

	@Override
	public final List<String> importExtensions() {
		return Collections.emptyList(); // TODO: Add importer
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
			for (Card.Printing pr : set.printings()) {
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
		public List<String> exportExtensions() {
			return Collections.singletonList("csv");
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
		public List<String> exportExtensions() {
			return Collections.singletonList("tsv");
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
		public List<String> exportExtensions() {
			return Collections.singletonList("tsv");
		}

		@Override
		protected LinkedHashMap<String, BiFunction<CardInstance, Integer, String>> zoneData() {
			LinkedHashMap<String, BiFunction<CardInstance, Integer, String>> tmp = new LinkedHashMap<>();

			tmp.put("Qty", (pr, count) -> Integer.toString(count));
			tmp.put("Name", (pr, count) -> pr.card().name());
			tmp.put("Set", (pr, count) -> pr.set().code().toUpperCase());
			tmp.put("No.", (pr, count) -> pr.collectorNumber());
			tmp.put("Cost Per.", (pr, count) -> "$0.00");
			tmp.put("Total", (pr, count) -> "=A%1$d*E%1$d");
			tmp.put("State", (pr, count) -> "");
			tmp.put("Action", (pr, count) -> "");
			tmp.put("Notes", (pr, count) -> "");
			tmp.put("White Proxy", (pr, count) -> "=IF($H%1$d=J$1,$B%1$d,\"\")");
			tmp.put("Color Proxy", (pr, count) -> "=IF($H%1$d=K$1,$B%1$d,\"\")");
			tmp.put("Fancy Proxy", (pr, count) -> "=IF($H%1$d=L$1,$B%1$d,\"\")");
			tmp.put("Buy", (pr, count) -> "=IF($H%1$d=M$1,$B%1$d,\"\")");
			tmp.put("Buy - Price", (pr, count) -> "=IF($H%1$d<>\"\",$F%1$d,\"\")");

			return tmp;
		}

		@Override
		protected void parseLine(DeckList deck, Zone currentZone, String[] values) throws IOException {
			int count = Integer.parseInt(values[0]);

			emi.lib.mtg.Set set = Context.get().data.set(values[2]);
			for (Card.Printing pr : set.printings()) {
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
