package emi.mtg.deckbuilder.controller.serdes;

import emi.lib.Service;
import emi.lib.mtg.Card;
import emi.lib.mtg.DataSource;
import emi.lib.mtg.game.Format;
import emi.lib.mtg.game.Zone;
import emi.mtg.deckbuilder.controller.DeckImportExport;
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
@Service.Property.String(name="name", value="MTGO")
@Service.Property.String(name="extension", value="dec")
public class MTGO implements DeckImportExport {
	private final DataSource cs;

	public MTGO(DataSource cs, Map<String, Format> formats) {
		this.cs = cs;
	}

	private static final Pattern LINE_PATTERN = Pattern.compile("^(?<sb>SB:\\s*)?(?<num>\\d+) (?<name>.+)$");

	@Override
	public DeckList importDeck(File from) throws IOException {
		Scanner scanner = new Scanner(from);

		List<CardInstance> library = new ArrayList<>(),
				sideboard = new ArrayList<>();

		while(scanner.hasNextLine()) {
			String line = scanner.nextLine();

			if (line.trim().isEmpty()) {
				continue;
			}

			Matcher m = LINE_PATTERN.matcher(line);

			if (!m.find()) {
				throw new IOException("Malformed line " + line);
			}

			Card card = cs.card(m.group("name"));

			if (card == null) {
				throw new IOException("Couldn't find card named " + m.group("name"));
			}

			// TODO: Just take the first printing for now...
			CardInstance ci = new CardInstance(card.printings().iterator().next());
			for (int i = 0; i < Integer.parseInt(m.group("num")); ++i) {
				(m.group("sb") == null ? library : sideboard).add(ci);
			}
		}

		Map<Zone, List<CardInstance>> cards = new HashMap<>(Collections.singletonMap(Zone.Library, library));
		return new DeckList(from.getName(), "<No Author>", null, "<No Description>", cards, sideboard);
	}

	private static void writeList(List<? extends Card.Printing> list, String prefix, Writer writer) throws IOException {
		LinkedList<? extends Card.Printing> tmp = new LinkedList<>(list);
		while (!tmp.isEmpty()) {
			Card.Printing printing = tmp.removeFirst();

			int count = 1;
			Iterator<? extends Card.Printing> iter = tmp.iterator();
			while (iter.hasNext()) {
				if (iter.next().card().name().equals(printing.card().name())) {
					++count;
					iter.remove();
				}
			}

			writer.append(prefix).append(Integer.toString(count)).append(' ').append(printing.card().name()).append('\n');
		}
	}

	@Override
	public void exportDeck(DeckList deck, File to) throws IOException {
		FileWriter writer = new FileWriter(to);

		for (Map.Entry<Zone, ? extends List<? extends Card.Printing>> zone : deck.cards().entrySet()) {
			writeList(zone.getValue(), zone.getKey() == Zone.Library ? "" : "SB:  ", writer);
		}

		if (deck.sideboard() != null) {
			writeList(deck.sideboard(), "SB:  ", writer);
		}

		writer.close();
	}

	@Override
	public Set<Features> unsupportedFeatures() {
		return EnumSet.allOf(Features.class);
	}
}
