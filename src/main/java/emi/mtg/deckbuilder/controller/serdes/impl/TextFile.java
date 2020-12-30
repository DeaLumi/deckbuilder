package emi.mtg.deckbuilder.controller.serdes.impl;

import emi.lib.mtg.Card;
import emi.lib.mtg.game.Format;
import emi.lib.mtg.game.Zone;
import emi.mtg.deckbuilder.controller.Context;
import emi.mtg.deckbuilder.controller.serdes.DeckImportExport;
import emi.mtg.deckbuilder.model.CardInstance;
import emi.mtg.deckbuilder.model.DeckList;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.Writer;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class TextFile implements DeckImportExport {
	private static final Pattern LINE_PATTERN = Pattern.compile("^(?:(?<preCount>\\d+)x? (?<preCardName>.+)|(?<postCardName>.+) x?(?<postCount>\\d+))$");

	@Override
	public String extension() {
		return "txt";
	}

	@Override
	public String toString() {
		return "Plain Text File";
	}

	@Override
	public DeckList importDeck(File from) throws IOException {
		Scanner scanner = new Scanner(from);

		final String name = from.getName().substring(0, from.getName().lastIndexOf('.'));

		Zone zone = Zone.Library;
		Map<Zone, List<CardInstance>> cards = new HashMap<>();

		while (scanner.hasNextLine()) {
			String line = scanner.nextLine();

			if (line.trim().isEmpty()) {
				continue;
			}

			Matcher m = LINE_PATTERN.matcher(line);

			if (!m.matches()) {
				try {
					zone = Zone.valueOf(line.trim().substring(0, line.trim().length() - 1));
					continue;
				} catch (IllegalArgumentException iae) {
					// do nothing
				}

				throw new IOException("Malformed line " + line);
			}

			int count = Integer.parseInt(m.group("preCount") != null ? m.group("preCount") : m.group("postCount"));
			String cardName = m.group("preCardName") != null ? m.group("preCardName") : m.group("postCardName");

			Card card = Context.get().data.cards().stream().filter(c -> c.name().equals(cardName)).findAny().orElse(null);

			if (card == null) {
				throw new IOException("Couldn't find card named \"" + cardName + "\"");
			}

			Card.Printing printing = Context.get().preferences.preferredPrinting(card);
			if (printing == null) printing = card.printings().iterator().next();
			for (int i = 0; i < count; ++i) {
				cards.computeIfAbsent(zone, z -> new ArrayList<>()).add(new CardInstance(printing));
			}
		}

		Format fmt = Arrays.stream(Format.values())
				.filter(f -> f.deckZones().equals(cards.keySet()))
				.findAny()
				.orElse(Context.get().preferences.defaultFormat);

		return new DeckList(name, "", fmt, "", cards);
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
