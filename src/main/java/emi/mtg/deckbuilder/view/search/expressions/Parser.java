package emi.mtg.deckbuilder.view.search.expressions;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.*;
import java.util.stream.Collectors;

public class Parser {
	private static final List<Class<? extends Grammar.Rule>> RULES = rules();
	private static final Map<Class<? extends Grammar.Rule>, Class<? extends Grammar.Rule>[]> FIRST_MAP = firstMap();
	private static final Map<Class<? extends Grammar.Rule>, Fn<?>> PARSER_MAP = parserMap();
	private static final List<Class<? extends Grammar.Rule>> TERMINALS = terminals();
	private static final Map<Class<? extends Grammar.Rule>, List<Class<? extends Grammar.Rule>>> PARENTS = parents();

	@SuppressWarnings("unchecked")
	private static List<Class<? extends Grammar.Rule>> rules() {
		return Arrays.stream(Grammar.class.getClasses())
				.filter(c -> Grammar.Rule.class.equals(c.getSuperclass()))
				.map(c -> (Class<? extends Grammar.Rule>) c) // Cast is safe by above check.
				.collect(Collectors.toList());
	}

	@SuppressWarnings("unchecked")
	private static Map<Class<? extends Grammar.Rule>, Class<? extends Grammar.Rule>[]> firstMap() {
		return Collections.unmodifiableMap(RULES.stream()
				.collect(Collectors.toMap(c -> c, c -> {
					Grammar.First first = c.getAnnotation(Grammar.First.class);
					if (first == null) return (Class<? extends Grammar.Rule>[]) new Class[] {}; // Cast is safe since array is empty.
					return c.getAnnotation(Grammar.First.class).value();
				})));
	}

	private static Map<Class<? extends Grammar.Rule>, Fn<?>> parserMap() {
		return Collections.unmodifiableMap(RULES.stream()
				.collect(Collectors.toMap(c -> c, c -> {
					try {
						final Method parse = c.getDeclaredMethod("parse", String.class, Entry[].class, int.class);
						if (!Grammar.Rule.class.isAssignableFrom(parse.getReturnType())) throw new Error("Rule type " + c.getSimpleName() + "'s parse() does not return a Rule!");
						return (source, table, start) -> {
							try {
								return (Grammar.Rule) parse.invoke(null, source, table, start);
							} catch (IllegalAccessException | InvocationTargetException e) {
								throw new Error(e);
							}
						};
					} catch (NoSuchMethodException | SecurityException e) {
						throw new Error("Rule type " + c.getSimpleName() + " has no parse(String, Parser.Entry[], int) method!");
					}
				})));
	}

	private static List<Class<? extends Grammar.Rule>> terminals() {
		return Collections.unmodifiableList(FIRST_MAP.entrySet().stream()
				.filter(e -> e.getValue().length == 0)
				.map(Map.Entry::getKey)
				.collect(Collectors.toList()));
	}

	private static Map<Class<? extends Grammar.Rule>, List<Class<? extends Grammar.Rule>>> parents() {
		Map<Class<? extends Grammar.Rule>, List<Class<? extends Grammar.Rule>>> tmp = new HashMap<>();

		for (Map.Entry<Class<? extends Grammar.Rule>, Class<? extends Grammar.Rule>[]> firstEntry : FIRST_MAP.entrySet()) {
			for (Class<? extends Grammar.Rule> child : firstEntry.getValue()) {
				tmp.computeIfAbsent(child, k -> new ArrayList<>()).add(firstEntry.getKey());
			}
		}

		for (Class<? extends Grammar.Rule> rule : RULES) {
			if (!tmp.containsKey(rule)) {
				tmp.put(rule, Collections.emptyList());
				continue;
			}

			List<Class<? extends Grammar.Rule>> old = tmp.get(rule);
			assert !old.isEmpty(); // Lists aren't created unless they're gonna have a value...
			if (old.size() == 1) {
				old = Collections.singletonList(old.get(0));
			} else {
				old = Collections.unmodifiableList(old);
			}
			tmp.put(rule, old);
		}

		return Collections.unmodifiableMap(tmp);
	}

	interface Fn<T extends Grammar.Rule> {
		T parse(String source, Entry[] table, int start);
	}

	public static class Entry {
		public final int ch;
		private final Map<Class<? extends Grammar.Rule>, Grammar.Rule> memo;

		public Entry(int ch) {
			this.ch = ch;
			this.memo = new HashMap<>();
		}

		public <T extends Grammar.Rule> T node(Class<T> rule, String source, Entry[] table, int at) {
			T node;

			if (!memo.containsKey(rule)) {
				Fn<T> fn = (Fn<T>) PARSER_MAP.get(rule);
				node = fn.parse(source, table, at);
				memo.put(rule, node);
			} else {
				node = (T) memo.get(rule);
			}

			return node;
		}
	}

	private final String source;
	private final Entry[] table;

	public Parser(String source) {
		this.source = source;
		this.table = new Entry[source.length() + 1];

		for (int i = 0; i < source.length(); ++i) {
			this.table[i] = new Entry(source.charAt(i));
		}

		this.table[source.length()] = new Entry(-1);
	}

	public <T extends Grammar.Rule> T parseRoot(Class<T> type) {
		for (int i = table.length - 1; i >= 0; --i) {
			LinkedList<Class<? extends Grammar.Rule>> queue = new LinkedList<>(TERMINALS);
			while (!queue.isEmpty()) {
				Class<? extends Grammar.Rule> rule = queue.removeFirst();
				Grammar.Rule node = PARSER_MAP.get(rule).parse(source, table, i);
				if (node != null) {
					Grammar.Rule old = table[i].memo.get(rule);
					if (old == null || old.lexeme.length() < node.lexeme.length()) {
						if (old != null) System.err.printf("At %d for %s: %s is better than older %s\n", i, rule.getSimpleName(), node, old);
						table[i].memo.put(rule, node);
						queue.addAll(PARENTS.get(rule));
					}
				}
			}
		}

		return table[0].node(type, source, table, 0);
	}

	public static void main(String[] args) {
		Scanner in = new Scanner(System.in);
		while (true) {
			Parser parser = new Parser(in.nextLine());
			Grammar.Query query = parser.parseRoot(Grammar.Query.class);
			System.out.println(query);
		}
	}
}
