package emi.mtg.deckbuilder.view.search.omnifilter;

import java.util.Collection;
import java.util.Set;
import java.util.stream.Collectors;

public class Util {
	public enum SetComparison {
		EQUALS,
		LESS_THAN,
		GREATER_THAN,
		UNRELATED;
	}

	public static <T> SetComparison compareSets(Collection<? extends T> a, Collection<? extends T> b) {
		boolean aContainsB = a.containsAll(b);
		boolean bContainsA = b.containsAll(a);

		if (aContainsB) {
			if (bContainsA) {
				return SetComparison.EQUALS;
			} else {
				return SetComparison.GREATER_THAN;
			}
		} else {
			if (bContainsA) {
				return SetComparison.LESS_THAN;
			} else {
				return SetComparison.UNRELATED;
			}
		}
	}

	public static SetComparison compareStringSetsInsensitive(Collection<String> a, Collection<String> b) {
		Set<String> aLower = a.stream().map(String::toLowerCase).collect(Collectors.toSet());
		Set<String> bLower = b.stream().map(String::toLowerCase).collect(Collectors.toSet());

		return compareSets(aLower, bLower);
	}
}
