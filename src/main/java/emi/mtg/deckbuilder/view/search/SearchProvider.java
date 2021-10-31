package emi.mtg.deckbuilder.view.search;

import emi.mtg.deckbuilder.model.CardInstance;

import java.util.Map;
import java.util.NavigableMap;
import java.util.ServiceLoader;
import java.util.SortedMap;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;

/**
 * Specifies a search provider to respond to searches in the filter bar above card views.
 * The primary function of a search provider is to compile a search query (string) into a predicate on CardInstances.
 * CardInstance is used specifically so filters can check things like deck tags.
 *
 * Search providers are instantiated as singletons within a deckbuilder interface, so no factory is required.
 */
public interface SearchProvider {
	/**
	 * Returns a user-friendly name of this search provider, e.g. "Omnifilter".
	 * @return a user-friendly name of this search provider.
	 */
	String name();

	/**
	 * Returns a string describing how to use this search provider. This might describe the query format or explain
	 * limitations inherent in the search tool. The returned string may include basic HTML.
	 *
	 * @return a string describing how to use this search provider.
	 */
	String usage();

	/**
	 * Given a search query, returns a function which can be used to test whether a particular card instance meets the
	 * query.
	 *
	 * @param query The string the user entered to search for. No sanitization has been performed. Be wise.
	 * @return A predicate which can test whether a given card instance meets the user's query.
	 * @throws IllegalArgumentException If the query has a syntax error or what have you. This will be presented to the user.
	 */
	Predicate<CardInstance> parse(String query) throws IllegalArgumentException;

	Map<String, SearchProvider> SEARCH_PROVIDERS = StreamSupport.stream(ServiceLoader.load(SearchProvider.class).spliterator(), false)
			.collect(Collectors.toMap(SearchProvider::name, v -> v));
}
