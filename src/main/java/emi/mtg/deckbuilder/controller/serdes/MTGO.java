package emi.mtg.deckbuilder.controller.serdes;

import com.sun.javafx.collections.ObservableListWrapper;
import com.sun.javafx.collections.ObservableMapWrapper;
import emi.lib.Service;
import emi.lib.mtg.card.Card;
import emi.lib.mtg.data.CardSource;
import emi.lib.mtg.game.Zone;
import emi.mtg.deckbuilder.controller.DeckImportExport;
import emi.mtg.deckbuilder.model.CardInstance;
import emi.mtg.deckbuilder.model.DeckList;
import javafx.collections.ObservableList;
import javafx.collections.ObservableMap;

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
	private final CardSource cs;

	public MTGO(CardSource cs) {
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

			Card card = cs.sets().stream()
					.flatMap(s -> s.cards().stream())
					.filter(c -> m.group("name").equals(c.name()))
					.findAny().orElse(null);

			if (card == null) {
				throw new IOException("Couldn't find card named " + m.group("name"));
			}

			CardInstance ci = new CardInstance(card);
			for (int i = 0; i < Integer.parseInt(m.group("num")); ++i) {
				(m.group("sb") == null ? library : sideboard).add(ci);
			}
		}

		Map<Zone, List<CardInstance>> cards = new HashMap<>(Collections.singletonMap(Zone.Library, library));
		return new DeckList(from.getName(), "<No Author>", null, "<No Description>", cards, sideboard);
	}

	private static void writeList(List<? extends Card> list, String prefix, Writer writer) throws IOException {
		LinkedList<? extends Card> tmp = new LinkedList<>(list);
		while (!tmp.isEmpty()) {
			Card card = tmp.removeFirst();

			int count = 1;
			Iterator<? extends Card> iter = tmp.iterator();
			while (iter.hasNext()) {
				if (iter.next().name().equals(card.name())) {
					++count;
					iter.remove();
				}
			}

			writer.append(prefix).append(Integer.toString(count)).append(' ').append(card.name()).append('\n');
		}
	}

	@Override
	public void exportDeck(DeckList deck, File to) throws IOException {
		FileWriter writer = new FileWriter(to);

		for (Map.Entry<Zone, ? extends List<? extends Card>> zone : deck.cards().entrySet()) {
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
