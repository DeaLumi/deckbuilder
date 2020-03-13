package emi.mtg.deckbuilder.controller.serdes.impl;

import emi.lib.Service;
import emi.lib.mtg.Card;
import emi.lib.mtg.game.Zone;
import emi.mtg.deckbuilder.controller.Context;
import emi.mtg.deckbuilder.controller.serdes.DeckImportExport;
import emi.mtg.deckbuilder.controller.serdes.Features;
import emi.mtg.deckbuilder.model.CardInstance;
import emi.mtg.deckbuilder.model.DeckList;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.Writer;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service.Provider(DeckImportExport.class)
@Service.Property.String(name="name", value="Plain Text File")
@Service.Property.String(name="extension", value="txt")
public class TextFile implements DeckImportExport {
	private static final Pattern LINE_PATTERN = Pattern.compile("^(?:(?<preCount>\\d+)x? (?<preCardName>.+)|(?<postCardName>.+) x?(?<postCount>\\d+))$");

	private final Context context;

	public TextFile(Context context) {
		this.context = context;
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

			Card card = context.data.cards().stream().filter(c -> c.name().equals(cardName)).findAny().orElse(null);

			if (card == null) {
				throw new IOException("Couldn't find card named \"" + cardName + "\"");
			}

			// TODO: Just take the first printing for now...
			Card.Printing printing = card.printings().iterator().next();
			for (int i = 0; i < count; ++i) {
				cards.computeIfAbsent(zone, z -> new ArrayList<>()).add(new CardInstance(printing));
			}
		}

		return new DeckList(name, "", null, "", cards);
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
	public Set<Features> unsupportedFeatures() {
		return EnumSet.of(Features.Author, Features.DeckName, Features.Description, Features.CardArt, Features.Format, Features.Variants);
	}
}
