package emi.mtg.deckbuilder.view.omnifilter.filters;

import emi.lib.Service;
import emi.lib.mtg.Card;
import emi.lib.mtg.characteristic.CardTypeLine;
import emi.lib.mtg.characteristic.Supertype;
import emi.mtg.deckbuilder.view.omnifilter.Omnifilter;
import emi.mtg.deckbuilder.view.omnifilter.Util;

import java.util.*;

@Service.Provider(Omnifilter.Subfilter.class)
@Service.Property.String(name="key", value="type")
@Service.Property.String(name="shorthand", value="t")
public class CardType implements Omnifilter.FaceFilter {
	private final Omnifilter.Operator operator;
	private final Set<Supertype> supertypes;
	private final Set<emi.lib.mtg.characteristic.CardType> cardTypes;
	private final Set<String> subtypes;

	public CardType(Omnifilter.Operator operator, String value) {
		this.operator = operator;

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

			if (ct != null) {
				cardTypes.add(ct);
				continue;
			}

			subtypes.add(part);
		}

		this.supertypes = Collections.unmodifiableSet(supertypes);
		this.cardTypes = Collections.unmodifiableSet(cardTypes);
		this.subtypes = Collections.unmodifiableSet(subtypes);
	}

	@Override
	public boolean testFace(Card.Face face) {
		CardTypeLine tl = face.type();
		Util.SetComparison sts = this.supertypes.isEmpty() ? Util.SetComparison.GREATER_THAN : Util.compareSets(tl.supertypes(), this.supertypes);
		Util.SetComparison cts = this.cardTypes.isEmpty() ? Util.SetComparison.GREATER_THAN : Util.compareSets(tl.cardTypes(), this.cardTypes);
		Util.SetComparison uts = this.subtypes.isEmpty() ? Util.SetComparison.GREATER_THAN : Util.compareStringSetsInsensitive(tl.subtypes(), this.subtypes);

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
	}
}
