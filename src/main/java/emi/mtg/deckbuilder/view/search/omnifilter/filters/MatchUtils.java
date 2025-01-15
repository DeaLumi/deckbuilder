package emi.mtg.deckbuilder.view.search.omnifilter.filters;

import emi.lib.mtg.Card;
import emi.lib.mtg.DataSource;
import emi.mtg.deckbuilder.model.Preferences;
import emi.mtg.deckbuilder.util.PluginUtils;

import java.io.IOException;
import java.util.*;
import java.util.function.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

public class MatchUtils {
    public interface SequenceMatcher {
        int match(CharSequence haystack, int start);

        int match(CharSequence haystack, int start, boolean canSkip);

        boolean skipping();
    }

    public interface NonSkippingSequenceMatcher extends SequenceMatcher {
        default boolean skipping() {
            return false;
        }

        default int match(CharSequence haystack, int start, boolean canSkip) {
            return match(haystack, start);
        }

        int match(CharSequence haystack, int start);
    }

    public interface SkippingSequenceMatcher extends SequenceMatcher {
        default boolean skipping() {
            return true;
        }

        default int match(CharSequence haystack, int start) {
            return match(haystack, start, false);
        }

        int match(CharSequence haystack, int start, boolean canSkip);
    }

    public static int matchesCardNameOrSubName(CharSequence haystack, int start, CharSequence needleName) {
        int partialMatch = 0, i = 0, comma = -1, firstSpace = -1, secondSpace = -1;
        for (; i < needleName.length(); ++i) {
            if (comma < 0 && needleName.charAt(i) == ',') comma = i;
            if (firstSpace < 0 && needleName.charAt(i) == ' ') firstSpace = i;
            if (firstSpace >= 0 && secondSpace < 0 && needleName.charAt(i) == ' ') secondSpace = i;
            if (start + i >= haystack.length() || haystack.charAt(start + i) != needleName.charAt(i)) break;
            if (needleName.charAt(i) != ',' && needleName.charAt(i) != ' ') partialMatch = i;
        }

        return (partialMatch == comma - 1 || partialMatch == firstSpace - 1 || partialMatch == secondSpace - 1 || partialMatch == needleName.length() - 1) ? start + partialMatch + 1 : -1;
    }

    public static NonSkippingSequenceMatcher cardNameOrSubNameMatcher(String cardName) {
        return (s, i) -> MatchUtils.matchesCardNameOrSubName(s, i, cardName);
    }

    public static int matchesCharSequence(CharSequence haystack, int start, CharSequence needle, boolean ignoreCase) {
        if (start + needle.length() > haystack.length()) return -1;

        int i = 0;
        for (; i < needle.length(); ++i) {
            if (start + i >= haystack.length()) return -1;
            char h = haystack.charAt(start + i), n = needle.charAt(i);
            if (h != n && !ignoreCase) return -1;
            if (ignoreCase && Character.toLowerCase(h) != Character.toLowerCase(n) && Character.toUpperCase(h) != Character.toUpperCase(n)) return -1;
        }

        return start + i;
    }

    public static NonSkippingSequenceMatcher charSequenceMatcher(String needle, boolean ignoreCase) {
        return (s, i) -> MatchUtils.matchesCharSequence(s, i, needle, ignoreCase);
    }

    public static int matchesRegex(CharSequence haystack, int start, Pattern pattern) {
        Matcher matcher = pattern.matcher(haystack).region(start, haystack.length());
        return matcher.lookingAt() ? matcher.end() : -1;
    }

    public static SkippingSequenceMatcher regexMatcher(Pattern pattern) {
        return new SkippingSequenceMatcher() {
            private CharSequence haystack = null;
            private final Matcher matcher = pattern.matcher("");

            @Override
            public int match(CharSequence haystack, int start, boolean skip) {
                if (this.haystack != haystack) {
                    this.haystack = haystack;
                    matcher.reset(haystack);
                    if (start != 0) matcher.region(start, haystack.length());
                } else {
                    matcher.region(start, haystack.length());
                }

                if (skip) return matcher.find() ? matcher.end() : -1;
                return matcher.lookingAt() ? matcher.end() : -1;
            }
        };
    }

    public static int matchesRope(CharSequence haystack, int start, SequenceMatcher[] rope) {
        startSearch: for (int i = start; i < haystack.length(); ++i) {
            int tmp = i;

            for (int segment = 0; segment < rope.length; ++segment) {
                SequenceMatcher sm = rope[segment];
                tmp = sm.match(haystack, tmp, segment == 0);
                if (tmp < 0) {
                    if (sm.skipping() && segment == 0) break startSearch;
                    continue startSearch;
                } else {
                    if (sm.skipping() && segment == 0) i = tmp;
                }
            }

            return tmp;
        }

        return -1;
    }

    public static int matchesRope(CharSequence haystack, int start, List<? extends SequenceMatcher> rope) {
        return matchesRope(haystack, start, rope.toArray(new SequenceMatcher[0]));
    }

    public static class DeferredRope<T> {
        private final List<Function<T, ? extends SequenceMatcher>> source;

        public DeferredRope(List<Function<T, ? extends SequenceMatcher>> source) {
            this.source = source;
        }

        public List<? extends SequenceMatcher> resolve(T context) {
            return source.stream()
                    .map(s -> s.apply(context))
                    .collect(Collectors.toList());
        }
    }

    public static Function<String, ? extends SequenceMatcher> cardTextTokenMatcher(String token, boolean ignoreCase, boolean regex) {
        if ("~".equals(token) || "CARDNAME".equals(token)) {
            return MatchUtils::cardNameOrSubNameMatcher;
        } else {
            final SequenceMatcher matcher;
            if (regex && ignoreCase) {
                matcher = regexMatcher(Pattern.compile(token, Pattern.CASE_INSENSITIVE));
            } else if (regex) {
                matcher = regexMatcher(Pattern.compile(token));
            } else {
                matcher = charSequenceMatcher(token.toLowerCase(), ignoreCase);
            }
            return t -> matcher;
        }
    }

    public static void main(String[] args) throws IOException {
        DataSource data = PluginUtils.providers(DataSource.class).get(0);
        data.loadData(Preferences.instantiate().dataPath, f -> System.out.printf("\rLoading data: %.2f", f * 100.0));
        System.out.println();

        String testPattern = "When(?:ever)? ~ enters(?: the battlefield)?(?: or attacks)?";
        BiPredicate<String, String> oldStyle = Regex.create(testPattern, true);
        BiPredicate<String, String> newStyle = Regex.create(testPattern, false);

        double oldRuntime, newRuntime, oldTotalRuntime = 0.0, newTotalRuntime = 0.0;
        long firstStart, firstEnd, secondStart, secondEnd;
        int firstCount, secondCount, newCount, oldCount;

        final int ITERATIONS = 100;

        for (int iteration = 0; iteration < ITERATIONS; ++iteration) {
            System.gc(); System.gc();

            BiPredicate<String, String> pred = Math.random() < 0.5 ? oldStyle : newStyle;
            firstCount = 0;
            firstStart = System.nanoTime();

            for (Card.Print pr : data.prints()) {
                for (Card.Face face : pr.card().faces()) {
                    if (pred.test(face.rules(), face.name())) ++firstCount;
                }
            }

            firstEnd = System.nanoTime();

            pred = (pred == oldStyle) ? newStyle : oldStyle;
            secondCount = 0;
            secondStart = System.nanoTime();

            for (Card.Print pr : data.prints()) {
                for (Card.Face face : pr.card().faces()) {
                    if (pred.test(face.rules(), face.name())) ++secondCount;
                }
            }

            secondEnd = System.nanoTime();

            if (pred == oldStyle) {
                newRuntime = ((double) (firstEnd - firstStart)) / 1.0e9;
                newCount = firstCount;
                oldRuntime = ((double) (secondEnd - secondStart)) / 1.0e9;
                oldCount = secondCount;
            } else {
                newRuntime = ((double) (secondEnd - secondStart)) / 1.0e9;
                newCount = secondCount;
                oldRuntime = ((double) (firstEnd - firstStart)) / 1.0e9;
                oldCount = firstCount;
            }

            System.out.printf("Iteration %d: New method took %.3f seconds to count %d cards; old method took %.3f seconds to count %d cards.%n",
                    iteration,
                    newRuntime, newCount,
                    oldRuntime, oldCount);

            newTotalRuntime += newRuntime;
            oldTotalRuntime += oldRuntime;
        }

        System.out.printf("%d iterations of %d faces: new method: %.2f seconds, old method: %.2f seconds%n", ITERATIONS, data.cards().stream().mapToLong(c -> c.faces().size()).sum(), newTotalRuntime, oldTotalRuntime);
    }
}
