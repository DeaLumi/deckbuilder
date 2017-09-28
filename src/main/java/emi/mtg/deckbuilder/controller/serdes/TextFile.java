package emi.mtg.deckbuilder.controller.serdes;

import emi.lib.Service;
import emi.lib.mtg.Card;
import emi.lib.mtg.game.Zone;
import emi.mtg.deckbuilder.controller.Context;
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

		DeckList list = new DeckList();
		list.nameProperty().setValue(from.getName().substring(0, from.getName().lastIndexOf('.')));
		list.formatProperty().setValue(Context.FORMATS.get("Standard"));

		Zone zone = Zone.Library;

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

			Card card = context.data.card(cardName);

			if (card == null) {
				throw new IOException("Couldn't find card named " + cardName);
			}

			// TODO: Just take the first printing for now...
			CardInstance ci = new CardInstance(card.printings().iterator().next());
			for (int i = 0; i < count; ++i) {
				list.primaryVariant.get().cards(zone).add(ci);
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

		for (Zone zone : Zone.values()) {
			if (deck.primaryVariant.get().cards(zone).isEmpty()) {
				continue;
			}

			if (zone != Zone.Library) {
				writer.append('\n').append(zone.name()).append(':').append('\n');
			}

			writeList(deck.primaryVariant.get().cards(zone), writer);
		}

		writer.close();
	}

	@Override
	public Set<Features> unsupportedFeatures() {
		return EnumSet.of(Features.Author, Features.DeckName, Features.Description, Features.CardArt, Features.Format, Features.Variants);
	}
}
