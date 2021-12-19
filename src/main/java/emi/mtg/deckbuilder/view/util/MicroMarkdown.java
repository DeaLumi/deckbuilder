package emi.mtg.deckbuilder.view.util;


import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.concurrent.atomic.AtomicBoolean;

public class MicroMarkdown {
	private static final char[] SPACES = new char[1024];
	static {
		Arrays.fill(SPACES, ' ');
	}

	private static final char[] SCRATCH = new char[1024];

	protected final Reader source;
	protected final StringBuilder buffer;
	protected int position;

	public MicroMarkdown(Reader source) throws IOException {
		this(source, 1024);
	}

	public MicroMarkdown(String source) throws IOException {
		this(new StringReader(source), 1024);
	}

	public MicroMarkdown(Reader source, int sizeEstimate) throws IOException {
		this.source = source;
		this.buffer = new StringBuilder(sizeEstimate);

		render(null);
	}

	protected char ch(int delta) throws IOException {
		if (position + delta >= buffer.length()) {
			int read = source.read(SCRATCH);
			if (read < 0) throw new EOFException();
			buffer.append(SCRATCH, 0, read);
			if (position + delta >= buffer.length()) throw new EOFException();
		}

		return buffer.charAt(position + delta);
	}

	protected char ch() throws IOException {
		return ch(0);
	}

	protected boolean str(int delta, String match) throws IOException {
		for (int i = 0; i < match.length(); ++i) {
			if (ch(delta + i) == match.charAt(i)) continue;
			return false;
		}

		return true;
	}

	protected boolean str(String match) throws IOException {
		return str(0, match);
	}

	protected int countIndent(int delta) throws IOException {
		int n = 0;
		while (ch(delta + n) == ' ') ++n;
		return n;
	}

	protected int countIndent() throws IOException {
		return countIndent(0);
	}

	protected int insert(int delta, String str) {
		buffer.insert(position + delta, str);
		return position + str.length();
	}

	protected int insert(String str) {
		return insert(0, str);
	}

	protected int indent(int delta, int count) {
		buffer.insert(position + delta, SPACES, 0, count);
		return position + delta + count;
	}

	protected int indent(int count) {
		return indent(0, count);
	}

	protected int replace(int len, String str) {
		buffer.replace(position, position + len, str);
		return position + str.length();
	}

	protected int delete(int delta, int len) {
		buffer.delete(position + delta, position + delta + len);
		return position + delta;
	}

	protected int delete(int len) {
		return delete(0, len);
	}

	private interface ListItemMatcher {
		int match(int delta) throws IOException;
	}

	protected void list(int indent, String tag, ListItemMatcher matcher) throws IOException {
		// 1. Insert a <ul> or <ol> line before the first list item.
		// 2. Replace the start of each list item with <li>.
		// 3. Consume until the beginning of the next nonempty line at the same or less indent, then insert </li>.
		// 4. Rerender the segment of buffer for the list item in between.
		// 5. If that next nonempty line is at the *same* indent, check for a new list-item start. If so, goto 2.
		// 6. Insert </ul> and run for the hills.

		position = indent(indent);
		position = insert(String.format("<%s>\n", tag));

		int liLen;

		while (countIndent() == indent && (liLen = matcher.match(indent)) > 0) {
			position += indent;
			position = indent(2); // Budge over, dangit.
			position = replace(liLen, "<li>");

			AtomicBoolean multiline = new AtomicBoolean();

			render(() -> {
				if (ch() != '\n') return true;
				if (countIndent(1) <= indent) return false;
				multiline.set(true);
				indent(1, 2);
				return true;
			});

			if (multiline.get()) {
				position = insert("\n");
				position = indent(indent + 2);
			}
			position = insert("</li>");
			++position;
		}

		position = indent(indent);
		position = insert(String.format("</%s>\n", tag)) - 1;
	}

	protected int ulMatcher(int delta) throws IOException {
		return str(delta, "- ") ? 2 : -1;
	}

	protected void ul(int indent) throws IOException {
		list(indent, "ul", this::ulMatcher);
	}

	protected int olMatcher(int delta) throws IOException {
		int d = 0;
		while (Character.isDigit(ch(delta + d))) ++d;
		return d != 0 && ch(delta + d) == '.' && ch(delta + d + 1) == ' ' ? d + 2 : -1;
	}

	protected void ol(int indent) throws IOException {
		list(indent, "ol", this::olMatcher);
	}

	protected void header(int indent) throws IOException {
		position += indent;

		int n = 0;
		while (ch(n) == '#') ++n;
		if (ch(n) != ' ') return;

		position = replace(n + 1, String.format("<h%d>", n));

		while (ch() != '\n') ++position;
		position = insert(String.format("</h%d>", n));
	}

	protected void hr() {
		replace(3, "<hr/>");
	}

	protected void blockquote(int indent) throws IOException {
		position = indent(indent);
		position = insert("<blockquote>\n") - 1;

		render(() -> {
			if (ch() != '\n') return true;
			if (countIndent(1) < indent) return false;
			if (!str(1 + indent, "> ")) return false;
			position += 1 + indent;
			position = replace(2, "  ") - 1;
			return true;
		});

		++position;
		position = indent(indent);
		position = insert("</blockquote>\n") - 1;
	}

	protected void codeBlock(int indent) throws IOException {
		position += indent;
		position = replace(3, "<pre>") + 1;

		render(() -> {
			if (ch() != '\n') return true;
			if (countIndent(1) < indent) return false;
			return !str(1 + indent, "```\n");
		});

		position += 1 + indent;
		position = replace(3, "</pre>");
	}

	protected void inlineCode() throws IOException {
		position = replace(1, "<code>");
		while (ch() != '`') ++position;
		position = replace(1, "</code>");
	}

	private final static String[] STRONGEM_START = { "<em>", "<strong>", "<strong><em>" };
	private final static String[] STRONGEM_END = { "</em>", "</strong>", "</strong></em>" };

	protected void strongEm() throws IOException {
		int n = 0;
		while (ch(n) == '*' || ch(n) == '_') ++n;
		if (n > STRONGEM_START.length) {
			position += n;
			return;
		}

		final int nx = n;
		position = replace(nx, STRONGEM_START[nx - 1]);

		render(() -> {
			int n2 = 0;
			while (ch(n2) == '*' || ch(n2) == '_') ++n2;
			return n2 < nx;
		});

		position = replace(nx, STRONGEM_END[nx - 1]);
	}

	protected void link() throws IOException {
		++position;
		int start = position;

		render(() -> ch() != ']');

		if (ch(1) != '(') return;
		int textEnd = position;
		while (ch() != ')') ++position;
		int linkEnd = position;

		position = start - 1;
		position = replace(linkEnd - start + 3, String.format("<a href=\"%s\">%s</a>", buffer.substring(textEnd + 2, linkEnd), buffer.substring(start, textEnd)));
	}

	protected void ampersand() throws IOException {
		int at = position;
		++position;

		render(() -> ch() != ';' && ch() != '\n');

		if (ch() == '\n') {
			int tmp = position;
			position = at;
			replace(1, "&amp;");
			position = tmp;
		}
	}

	private interface RenderHook {
		boolean check() throws IOException;
	}

	protected void render(RenderHook hook) throws IOException {
		try {
			while (true) {
				char ch = ch();

				if (hook != null && !hook.check()) return;

				if (ch == '\n' || position == 0) {
					if (position != 0) ++position;

					int x = countIndent();

					if (ulMatcher(x) > 0) {
						ul(x);
					} else if (olMatcher(x) > 0) {
						ol(x);
					} else if (x == 0 && str(x, "---\n")) {
						hr();
					} else if (ch(x) == '#') {
						header(x);
					} else if (ch(x) == '>') {
						blockquote(x);
					} else if (str(x, "```\n")) {
						codeBlock(x);
					}
				} else if (ch == '`') {
					inlineCode();
				} else if (ch == '*' || ch == '_') {
					strongEm();
				} else if (ch == '[') {
					link();
				} else if (ch == '&') {
					ampersand();
				} else if (ch == '<' && !Character.isLetter(ch(1)) && ch(1) != '/') {
					position = replace(1, "&lt;");
				} else if (ch == '\\') {
					delete(1);
					++position;
				} else {
					++position;
				}
			}
		} catch (EOFException eofe) {
			// Pass
		}
	}

	@Override
	public String toString() {
		return buffer.toString();
	}

	public static void main(String[] args) throws IOException {
		System.out.println(new MicroMarkdown(new BufferedReader(new InputStreamReader(MicroMarkdown.class.getClassLoader().getResourceAsStream("META-INF/changelog.md"), StandardCharsets.UTF_8))));
	}
}
