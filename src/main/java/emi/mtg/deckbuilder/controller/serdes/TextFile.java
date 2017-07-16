package emi.mtg.deckbuilder.controller.serdes;

import com.sun.javafx.collections.ObservableListWrapper;
import emi.lib.Service;
import emi.lib.mtg.card.Card;
import emi.lib.mtg.data.CardSource;
import emi.lib.mtg.game.Zone;
import emi.mtg.deckbuilder.controller.DeckImportExport;
import emi.mtg.deckbuilder.model.CardInstance;
import emi.mtg.deckbuilder.model.DeckList;
import javafx.collections.ObservableList;

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

	private final CardSource cs;

	public TextFile(CardSource cs) {
		this.cs = cs;
	}

	@Override
	public DeckList importDeck(File from) throws IOException {
		Scanner scanner = new Scanner(from);

		DeckList list = new DeckList();
		list.name = from.getName();

		Zone zone = Zone.Library;
		boolean sideboarding = false;

		while (scanner.hasNextLine()) {
			String line = scanner.nextLine();

			if (line.trim().isEmpty()) {
				continue;
			}

			Matcher m = LINE_PATTERN.matcher(line);

			if (!m.matches()) {
				if ("Sideboard:".equals(line.trim())) {
					sideboarding = true;
					continue;
				}

				try {
					zone = Zone.valueOf(line.trim());
					continue;
				} catch (IllegalArgumentException iae) {
					// do nothing
				}

				throw new IOException("Malformed line " + line);
			}

			int count = Integer.parseInt(m.group("preCount") != null ? m.group("preCount") : m.group("postCount"));
			String cardName = m.group("preCardName") != null ? m.group("preCardName") : m.group("postCardName");

			Card card = cs.sets().stream()
					.flatMap(s -> s.cards().stream())
					.filter(c -> cardName.equals(c.name()))
					.findAny().orElse(null);

			if (card == null) {
				throw new IOException("Couldn't find card named " + cardName);
			}

			CardInstance ci = new CardInstance(card);
			for (int i = 0; i < count; ++i) {
				(sideboarding ? list.sideboard : list.cards.computeIfAbsent(zone, z -> new ObservableListWrapper<>(new ArrayList<>()))).add(ci);
			}
		}

		return list;
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

		for (Map.Entry<Zone, List<CardInstance>> entry : deck.cards.entrySet()) {
			if (entry.getValue() == null || entry.getValue().isEmpty()) {
				continue;
			}

			if (entry.getKey() != Zone.Library) {
				writer.append('\n').append(entry.getKey().name()).append('\n');
			}

			writeList(entry.getValue(), writer);
		}

		if (deck.sideboard != null && !deck.sideboard.isEmpty()) {
			writer.append('\n').append("Sideboard:").append('\n');
			writeList(deck.sideboard, writer);
		}

		writer.close();
	}

	@Override
	public Set<Features> unsupportedFeatures() {
		return EnumSet.allOf(Features.class);
	}
}
