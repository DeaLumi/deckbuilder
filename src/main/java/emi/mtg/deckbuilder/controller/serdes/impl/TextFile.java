package emi.mtg.deckbuilder.controller.serdes.impl;

import emi.lib.mtg.Card;
import emi.lib.mtg.game.Zone;
import emi.mtg.deckbuilder.controller.Context;
import emi.mtg.deckbuilder.controller.serdes.DeckImportExport;
import emi.mtg.deckbuilder.model.CardInstance;
import emi.mtg.deckbuilder.model.DeckList;
import emi.mtg.deckbuilder.model.Preferences;
import emi.mtg.deckbuilder.view.util.AlertBuilder;
import emi.mtg.deckbuilder.view.util.FxUtils;
import javafx.beans.binding.Bindings;
import javafx.fxml.FXML;
import javafx.scene.control.Alert;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.stage.Modality;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BiConsumer;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

public abstract class TextFile extends NameOnlyImporter implements DeckImportExport.Textual {
	private enum TextDataFormat implements DataFormat {
		PlainText ("txt", "text/plain", "Simple list of card names and quantities, e.g. \"4 Island\""),
		Arena ("txt", "text/plain", "MtG: Arena text format, consisting of lines like \"4 Island (UST) 213\""),
		UserDefined ("txt", "text/plain", "User-defined plaintext format. You'll be prompted for how to format the line."),
		;

		private final String extension, mime, description;

		TextDataFormat(String extension, String mime, String description) {
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

	private static final Pattern LINE_PATTERN = Pattern.compile("^(?<!// )(?:(?<preCount>\\d+)x? )?(?<cardName>[-,A-Za-z0-9 '/]+)(?: \\((?<setCode>[-A-Za-z0-9]+)\\)(?: (?<collectorNumber>[-A-Za-z0-9]+))?| x(?<postCount>\\d+))?(?![:])$");
	private static final Pattern ZONE_PATTERN = Pattern.compile("^(?:// )?(?<zoneName>[A-Za-z ]+):?$");

	protected abstract boolean preservePrints();

	protected abstract String zoneToName(Zone zone);

	protected abstract Zone nameToZone(String name);

	protected String formatCardLine(int quantity, Card.Print pr) {
		if (preservePrints()) {
			return String.format("%1$d %2$s (%3$s) %4$s", quantity, pr.card().name(), pr.set().code().toUpperCase(), pr.collectorNumber());
		} else {
			return String.format("%1$d %2$s", quantity, pr.card().name());
		}
	}

	public boolean parseCardLine(String line, BiConsumer<Card.Print, Integer> handler) {
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

		Card.Print pr = NameOnlyImporter.findPrint(m.group("cardName"), setCode, collectorNumber);

		if (pr == null) return false;

		handler.accept(pr, count);
		return true;
	}

	@Override
	public DeckList importDeck(Reader from) throws IOException {
		Scanner scanner = new Scanner(from);
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

			if (!parseCardLine(line, this::addCard)) {
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
		Map<Card.Print, AtomicInteger> qtyMap = new HashMap<>();

		listing: for (CardInstance ci : list) {
			if (!preservePrints()) {
				for (Card.Print pr : ci.card().prints()) {
					if (qtyMap.containsKey(pr)) {
						qtyMap.get(pr).incrementAndGet();
						continue listing;
					}
				}
			}

			qtyMap.computeIfAbsent(ci.print(), x -> new AtomicInteger()).incrementAndGet();
		}

		for (Map.Entry<Card.Print, AtomicInteger> entry : qtyMap.entrySet()) {
			writer.append(formatCardLine(entry.getValue().get(), entry.getKey())).append('\n');
		}
	}

	@Override
	public void exportDeck(DeckList deck, Writer writer) throws IOException {
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

	public static class PlainText extends TextFile implements Monotype {
		@Override
		public String toString() {
			return "Plain Text File";
		}

		@Override
		public DataFormat format() {
			return TextDataFormat.PlainText;
		}

		@Override
		public EnumSet<Feature> supportedFeatures() {
			return EnumSet.of(Feature.OtherZones);
		}

		@Override
		protected boolean preservePrints() {
			return false;
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
		public DataFormat format() {
			return TextDataFormat.Arena;
		}

		@Override
		public EnumSet<Feature> supportedFeatures() {
			return EnumSet.of(Feature.CardArt, Feature.OtherZones);
		}

		@Override
		protected boolean preservePrints() {
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

	public static class UserDefined extends TextFile {
		private enum Keyword {
			CardName ("name", false, pr -> pr.card().name(), "The card's ordinary name, not including back sides."),
			CardFullName ("fullname", false, pr -> pr.card().fullName(), "The card's full name, including all faces."),
			SetCode ("set", true, pr -> pr.set().code().toUpperCase(), "The print's three- or four-letter set code, in all caps."),
			SetName ("setname", true, pr -> pr.set().name(), "The print's set's full name."),
			CollectorNumber ("no", true, Card.Print::collectorNumber, "The print's collector number."),
			;

			public static final Map<String, Keyword> KEYWORD_MAP = Collections.unmodifiableMap(Arrays.stream(Keyword.values())
					.collect(Collectors.toMap(k -> k.key, k -> k)));

			public final String key;
			public final boolean preservesPrints;
			public final Function<Card.Print, String> formatter;
			public final String desc;

			Keyword(String key, boolean preservesPrints, Function<Card.Print, String> formatter, String desc) {
				this.key = key;
				this.preservesPrints = preservesPrints;
				this.formatter = formatter;
				this.desc = desc;
			}
		}

		private static final String HELP_TEXT = String.join("\n",
				"The following keywords are automatically replaced:\n" +
						"\u2022 {qty} \u2014 The number of copies of this card or print.\n" +
						Arrays.stream(Keyword.values())
								.map(k -> String.format("\u2022 %s{%s} \u2014 %s", k.preservesPrints ? "*" : "", k.key, k.desc))
								.collect(Collectors.joining("\n")),
						"\nIf any of the starred keywords are included, different prints of the same card will appear on different lines.\n");

		@FXML
		private transient TextField cardFormat;

		@FXML
		private transient Label cardPreview;

		@FXML
		private transient Label helpLabel;

		@Override
		protected String formatCardLine(int quantity, Card.Print pr) {
			String fmt = cardFormat.getText().replace("{qty}", Integer.toString(quantity));

			for (Map.Entry<String, Keyword> keyword : Keyword.KEYWORD_MAP.entrySet()) {
				fmt = fmt.replace("{" + keyword.getKey() + "}", keyword.getValue().formatter.apply(pr));
			}

			return fmt;
		}

		@Override
		protected boolean preservePrints() {
			final String fmt = cardFormat.getText();
			return Arrays.stream(Keyword.values())
					.anyMatch(k -> k.preservesPrints && fmt.contains("{" + k.key + "}"));
		}

		private Card.Print randomPrint() {
			return Context.get().data.prints()
					.parallelStream()
					.filter(pr -> pr.card().faces().size() != pr.card().mainFaces().size())
					.findAny().orElseThrow(() -> new NoSuchElementException("Unable to find any DFC print! Bwuh!?"));
		}

		private Alert formatPrompt() {
			Alert alert = AlertBuilder.query(null)
					.title("User Defined Text Format")
					.headerText("Enter Line Format")
					.modal(Modality.APPLICATION_MODAL)
					.buttons(ButtonType.OK, ButtonType.CANCEL)
					.get();

			FxUtils.FXML(this, alert.getDialogPane());

			cardPreview.textProperty().bind(Bindings.createStringBinding(() -> formatCardLine(1 + (int) (Math.random() * 4), randomPrint()), cardFormat.textProperty()));
			helpLabel.setText(HELP_TEXT);

			return alert;
		}

		// TODO: Custom zone name mapping?

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

		@Override
		public void exportDeck(DeckList deck, Writer writer) throws IOException {
			if (formatPrompt().showAndWait().orElse(ButtonType.CANCEL) != ButtonType.OK) return;
			super.exportDeck(deck, writer);
		}

		@Override
		public DataFormat importFormat() {
			return null; // TODO
		}

		@Override
		public DataFormat exportFormat() {
			return TextDataFormat.UserDefined;
		}

		@Override
		public EnumSet<Feature> supportedFeatures() {
			return EnumSet.of(Feature.OtherZones, Feature.CardArt);
		}

		@Override
		public String toString() {
			return "Custom Text Format";
		}
	}
}
