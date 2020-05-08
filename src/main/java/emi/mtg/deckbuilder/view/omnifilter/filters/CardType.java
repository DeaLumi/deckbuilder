package emi.mtg.deckbuilder.view.omnifilter.filters;

import emi.lib.mtg.characteristic.CardTypeLine;
import emi.lib.mtg.characteristic.Supertype;
import emi.mtg.deckbuilder.model.CardInstance;
import emi.mtg.deckbuilder.view.omnifilter.Omnifilter;
import emi.mtg.deckbuilder.view.omnifilter.Util;

import java.util.Arrays;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.Set;
import java.util.function.Predicate;

public class CardType implements Omnifilter.Subfilter {
	@Override
	public String key() {
		return "type";
	}

	@Override
	public String shorthand() {
		return "t";
	}

	@Override
	public Predicate<CardInstance> create(Omnifilter.Operator operator, String value) {
		String[] parts = value.toLowerCase().split("[ ]+");

		Set<Supertype> supertypes = EnumSet.noneOf(Supertype.class);
		Set<emi.lib.mtg.characteristic.CardType> cardTypes = EnumSet.noneOf(emi.lib.mtg.characteristic.CardType.class);
		Set<String> subtypes = new HashSet<>();

		for (String part : parts) {
			Supertype st = Arrays.stream(Supertype.values())
					.filter(t -> t.toString().toLowerCase().contains(part))
					.findAny()
					.orElse(null);

			if (st != null) {
				supertypes.add(st);
				continue;
			}

			emi.lib.mtg.characteristic.CardType ct = Arrays.stream(emi.lib.mtg.characteristic.CardType.values())
					.filter(t -> t.toString().toLowerCase().contains(part))
					.findAny()
					.orElse(null);

			// The card type "Elemental" is problematic...
			if (ct != null && ct != emi.lib.mtg.characteristic.CardType.Elemental) {
				cardTypes.add(ct);
				continue;
			}

			subtypes.add(part);
		}

		return (Omnifilter.FaceFilter) face -> {
			CardTypeLine tl = face.type();
			Util.SetComparison sts = supertypes.isEmpty() ? Util.SetComparison.GREATER_THAN : Util.compareSets(tl.supertypes(), supertypes);
			Util.SetComparison cts = cardTypes.isEmpty() ? Util.SetComparison.GREATER_THAN : Util.compareSets(tl.cardTypes(), cardTypes);
			Util.SetComparison uts = subtypes.isEmpty() ? Util.SetComparison.GREATER_THAN : Util.compareStringSetsInsensitive(tl.subtypes(), subtypes);

			switch (operator) {
				case EQUALS:
					return sts == Util.SetComparison.EQUALS && cts == Util.SetComparison.EQUALS && uts == Util.SetComparison.EQUALS;
				case NOT_EQUALS:
					return sts != Util.SetComparison.EQUALS || cts != Util.SetComparison.EQUALS || uts != Util.SetComparison.EQUALS;
				case DIRECT:
				case GREATER_OR_EQUALS:
					return (sts == Util.SetComparison.EQUALS || sts == Util.SetComparison.GREATER_THAN) &&
							(cts == Util.SetComparison.EQUALS || cts == Util.SetComparison.GREATER_THAN) &&
							(uts == Util.SetComparison.EQUALS || uts == Util.SetComparison.GREATER_THAN);
				case GREATER_THAN:
					return sts == Util.SetComparison.GREATER_THAN && cts == Util.SetComparison.GREATER_THAN && uts == Util.SetComparison.GREATER_THAN;
				case LESS_OR_EQUALS:
					return (sts == Util.SetComparison.EQUALS || sts == Util.SetComparison.LESS_THAN) &&
							(cts == Util.SetComparison.EQUALS || cts == Util.SetComparison.LESS_THAN) &&
							(uts == Util.SetComparison.EQUALS || uts == Util.SetComparison.LESS_THAN);
				case LESS_THAN:
					return sts == Util.SetComparison.LESS_THAN && cts == Util.SetComparison.LESS_THAN && uts == Util.SetComparison.LESS_THAN;
				default:
					assert false;
					return false;
			}
		};
	}
}
