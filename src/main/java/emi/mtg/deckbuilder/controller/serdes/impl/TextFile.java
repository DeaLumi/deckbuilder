package emi.mtg.deckbuilder.controller.serdes.impl;

import emi.lib.mtg.Card;
import emi.lib.mtg.game.Zone;
import emi.mtg.deckbuilder.model.CardInstance;
import emi.mtg.deckbuilder.model.DeckList;
import emi.mtg.deckbuilder.model.Preferences;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.Writer;
import java.util.*;
import java.util.function.BiConsumer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class TextFile extends NameOnlyImporter {
	private static final Pattern LINE_PATTERN = Pattern.compile("^(?<!// )(?:(?<preCount>\\d+)x? )?(?<cardName>.+)(?: x?(?<postCount>\\d+))?(?![:])$");
	private static final Pattern ZONE_PATTERN = Pattern.compile("^(?:// )?(?<zoneName>[A-Za-z ]+):?$");

	@Override
	public String extension() {
		return "txt";
	}

	@Override
	public String toString() {
		return "Plain Text File";
	}

	public static boolean parseLine(String line, BiConsumer<Card.Printing, Integer> handler) {
		Matcher m = LINE_PATTERN.matcher(line.trim());

		if (!m.matches()) return false;

		int count;
		if (m.group("preCount") != null) {
			count = Integer.parseInt(m.group("preCount"));
		} else if (m.group("postCount") != null) {
			count = Integer.parseInt(m.group("postCount"));
		} else {
			count = 1;
		}

		Card.Printing pr = NameOnlyImporter.findPrinting(m.group("cardName"));

		if (pr == null) return false;

		handler.accept(pr, count);
		return true;
	}

	@Override
	public DeckList importDeck(File from) throws IOException {
		Scanner scanner = new Scanner(from);

		name(from.getName().substring(0, from.getName().lastIndexOf('.')));
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

				String nextZoneName = zm.group("zoneName");
				if ("SB".equals(nextZoneName) || "Outside the Game".equals(nextZoneName)) nextZoneName = "Sideboard";
				if ("Commander".equals(nextZoneName)) nextZoneName = "Command";

				try {
					Zone nextZone = Zone.valueOf(nextZoneName);
					endZone();
					beginZone(nextZone);
				} catch (IllegalArgumentException iae) {
					throw new IOException("Unknown zone " + nextZoneName);
				}
			}
		}
		endZone();

		return completeImport();
	}

	private static void writeList(List<CardInstance> list, Writer writer) throws IOException {
		LinkedList<CardInstance> tmp = new LinkedList<>(list);
		while (!tmp.isEmpty()) {
			Card card = tmp.removeFirst().card();

			int count = 1;
			Iterator<CardInstance> iter = tmp.iterator();
			while (iter.hasNext()) {
				if (iter.next().card().name().equals(card.name())) {
					++count;
					iter.remove();
				}
			}

			writer.append(Integer.toString(count)).append(' ').append(card.name()).append('\n');
		}
	}

	@Override
	public void exportDeck(DeckList deck, File to) throws IOException {
		FileWriter writer = new FileWriter(to);

		for (Zone zone : Zone.values()) {
			if (deck.cards(zone).isEmpty()) {
				continue;
			}

			if (zone != Zone.Library) {
				writer.append('\n').append(zone.name()).append(':').append('\n');
			}

			writeList(deck.cards(zone), writer);
		}

		writer.close();
	}

	@Override
	public EnumSet<Feature> supportedFeatures() {
		return EnumSet.of(Feature.OtherZones, Feature.Import, Feature.Export);
	}
}
