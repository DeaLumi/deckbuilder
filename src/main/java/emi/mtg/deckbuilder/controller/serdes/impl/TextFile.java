package emi.mtg.deckbuilder.controller.serdes.impl;

import emi.lib.mtg.Card;
import emi.lib.mtg.game.Zone;
import emi.mtg.deckbuilder.model.CardInstance;
import emi.mtg.deckbuilder.model.DeckList;
import emi.mtg.deckbuilder.model.Preferences;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BiConsumer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public abstract class TextFile extends NameOnlyImporter {
	private static final Pattern LINE_PATTERN = Pattern.compile("^(?<!// )(?:(?<preCount>\\d+)x? )?(?<cardName>[-,A-Za-z0-9 '/]+)(?: \\((?<setCode>[A-Za-z0-9]+)\\)(?: (?<collectorNumber>[A-Za-z0-9]+))?| x(?<postCount>\\d+))?(?![:])$");
	private static final Pattern ZONE_PATTERN = Pattern.compile("^(?:// )?(?<zoneName>[A-Za-z ]+):?$");

	protected abstract boolean preservePrintings();

	protected abstract String zoneToName(Zone zone);

	protected abstract Zone nameToZone(String name);

	public boolean parseLine(String line, BiConsumer<Card.Printing, Integer> handler) {
		Matcher m = LINE_PATTERN.matcher(line.trim());

		if (!m.matches()) return false;

		// Spurious matches -- fortunately none of these are card names... for now.
		if (nameToZone(m.group("cardName")) != null) return false;

		int count;
		if (m.group("preCount") != null) {
			count = Integer.parseInt(m.group("preCount"));
		} else if (m.group("postCount") != null) {
			count = Integer.parseInt(m.group("postCount"));
		} else {
			count = 1;
		}

		String setCode = null, collectorNumber = null;

		if (m.group("setCode") != null) {
			setCode = m.group("setCode");
		}

		if (m.group("collectorNumber") != null) {
			collectorNumber = m.group("collectorNumber");
		}

		Card.Printing pr = NameOnlyImporter.findPrinting(m.group("cardName"), setCode, collectorNumber);

		if (pr == null) return false;

		handler.accept(pr, count);
		return true;
	}

	@Override
	public DeckList importDeck(Path from) throws IOException {
		Scanner scanner = new Scanner(Files.newBufferedReader(from));
		DeckList deck = readDeck(scanner);
		scanner.close();
		deck.nameProperty().setValue(from.getFileName().toString().substring(0, from.getFileName().toString().lastIndexOf('.')));
		return deck;
	}

	public DeckList stringToDeck(String from) throws IOException {
		Scanner scanner = new Scanner(from);
		DeckList deck = readDeck(scanner);
		scanner.close();
		return deck;
	}

	protected DeckList readDeck(Scanner scanner) throws IOException {
		beginImport();
		name("Imported Deck");
		author(Preferences.get().authorName);
		description("");

		beginZone(Zone.Library);

		while (scanner.hasNextLine()) {
			String line = scanner.nextLine();

			if (line.trim().isEmpty()) {
				continue;
			}

			if (!parseLine(line, this::addCard)) {
				Matcher zm = ZONE_PATTERN.matcher(line.trim());
				if (!zm.matches()) {
					throw new IOException("Malformed line or unrecognized card: \"" + line + "\"");
				}

				Zone nextZone = nameToZone(zm.group("zoneName"));
				if (nextZone == null) {
					throw new IOException("Text file references unknown zone \"" + zm.group("zoneName") + "\"");
				}

				endZone();
				beginZone(nextZone);
			}
		}
		endZone();

		return completeImport();
	}

	protected void writeList(List<CardInstance> list, Writer writer) throws IOException {
		Map<Card.Printing, AtomicInteger> qtyMap = new HashMap<>();

		listing: for (CardInstance ci : list) {
			if (!preservePrintings()) {
				for (Card.Printing pr : ci.card().printings()) {
					if (qtyMap.containsKey(pr)) {
						qtyMap.get(pr).incrementAndGet();
						continue listing;
					}
				}
			}

			qtyMap.computeIfAbsent(ci.printing(), x -> new AtomicInteger()).incrementAndGet();
		}

		for (Map.Entry<Card.Printing, AtomicInteger> entry : qtyMap.entrySet()) {
			writer.append(Integer.toString(entry.getValue().get())).append(' ').append(entry.getKey().card().name());
			if (preservePrintings()) writer.append(" (").append(entry.getKey().set().code().toUpperCase()).append(") ").append(entry.getKey().collectorNumber());
			writer.append('\n');
		}
	}

	protected void writeDeck(DeckList deck, Writer writer) throws IOException {
		for (Zone zone : Zone.values()) {
			if (deck.cards(zone).isEmpty()) {
				continue;
			}

			if (zone != Zone.Library) {
				writer.append('\n').append(zoneToName(zone)).append('\n');
			}

			writeList(deck.cards(zone), writer);
		}
	}

	public String deckToString(DeckList deck) throws IOException {
		StringWriter writer = new StringWriter();
		writeDeck(deck, writer);
		writer.close();
		return writer.toString();
	}

	@Override
	public void exportDeck(DeckList deck, Path to) throws IOException {
		BufferedWriter writer = Files.newBufferedWriter(to);
		writeDeck(deck, writer);
		writer.close();
	}

	public static class PlainText extends TextFile implements Monotype {
		@Override
		public String toString() {
			return "Plain Text File";
		}

		@Override
		public String extension() {
			return "txt";
		}

		@Override
		public EnumSet<Feature> supportedFeatures() {
			return EnumSet.of(Feature.CardArt, Feature.OtherZones);
		}

		@Override
		protected boolean preservePrintings() {
			return true;
		}

		@Override
		protected String zoneToName(Zone zone) {
			return zone.name() + ":";
		}

		@Override
		protected Zone nameToZone(String name) {
			try {
				return Zone.valueOf(name);
			} catch (IllegalArgumentException iae) {
				return null;
			}
		}
	}

	public static class Arena extends TextFile implements Monotype {
		public static Arena INSTANCE = new Arena();

		@Override
		public String toString() {
			return "Magic: the Gathering: Arena";
		}

		@Override
		public String extension() {
			return "txt";
		}

		@Override
		public EnumSet<Feature> supportedFeatures() {
			return EnumSet.of(Feature.CardArt, Feature.OtherZones);
		}

		@Override
		protected boolean preservePrintings() {
			return true;
		}

		@Override
		protected String zoneToName(Zone zone) {
			switch (zone) {
				case Library: return "Deck";
				case Command: return "Commander";
				case Sideboard: return "Sideboard";
				default: return null;
			}
		}

		@Override
		protected Zone nameToZone(String name) {
			switch (name) {
				case "Deck": return Zone.Library;
				case "Commander": return Zone.Command;
				case "Sideboard": return Zone.Sideboard;
				default: return null;
			}
		}
	}
}
