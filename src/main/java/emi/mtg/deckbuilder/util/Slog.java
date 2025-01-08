package emi.mtg.deckbuilder.util;

import java.io.PrintStream;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeFormatterBuilder;
import java.time.temporal.ChronoField;
import java.util.Formatter;

public class Slog {
	private final Slog parent;

	private String name;
	private long stopwatch;
	private final ThreadLocal<StringBuilder> buffer;

	public Slog(String name) {
		this(null, name);
	}

	protected Slog(Slog parent, String name) {
		this.parent = parent;
		this.name = name;
		this.stopwatch = -1;
		this.buffer = new ThreadLocal<StringBuilder>() {
			@Override
			protected StringBuilder initialValue() {
				return new StringBuilder(1024);
			}
		};
	}

	public Slog rename(String name) {
		this.name = name;
		return this;
	}

	protected void nameChain(PrintStream dest) {
		if (parent != null) {
			parent.nameChain(dest);
			dest.print(" / ");
		}
		dest.print(name);
	}

	protected void nameChain(StringBuilder buffer) {
		if (parent != null) {
			parent.nameChain(buffer);
			buffer.append(" / ");
		}
		buffer.append(name);
	}

	public Slog child(String name) {
		return new Slog(this, name);
	}

	public Slog start() {
		this.stopwatch = System.nanoTime();
		return this;
	}

	public double elapsed() {
		return (double) (System.nanoTime() - this.stopwatch) / 1e9;
	}

	public double lap() {
		double val = elapsed();
		this.start();
		return val;
	}

	public Slog log(String format, Object... args) {
		return log(System.out, true, format, args);
	}

	public Slog err(String format, Object... args) {
		return log(System.err, true, format, args);
	}

	public Slog err(String format, Throwable t, Object... args) {
		StringWriter str = new StringWriter(1024);
		PrintWriter writer = new PrintWriter(str);
		t.printStackTrace(writer);
		return log(System.err, true, String.format(format, args) + "%n%n%s", str.getBuffer().toString()); // TODO string concatenation
	}

	private final static DateTimeFormatter TIMESTAMP_FORMAT = new DateTimeFormatterBuilder()
			.appendLiteral('[')
			.appendValue(ChronoField.YEAR, 4).appendLiteral('-').appendValue(ChronoField.MONTH_OF_YEAR, 2).appendLiteral('-').appendValue(ChronoField.DAY_OF_MONTH, 2)
			.appendLiteral(' ')
			.appendValue(ChronoField.HOUR_OF_DAY, 2).appendLiteral(':').appendValue(ChronoField.MINUTE_OF_HOUR, 2).appendLiteral(':').appendValue(ChronoField.SECOND_OF_MINUTE, 2).appendLiteral('.').appendValue(ChronoField.MILLI_OF_SECOND, 3)
			.appendLiteral(' ')
			.appendZoneOrOffsetId()
			.appendLiteral(']')
			.toFormatter();

	public Slog log(java.io.PrintStream dest, boolean newline, String format, Object... args) {
		StringBuilder sb = buffer.get();
		sb.setLength(0);
		sb.append(TIMESTAMP_FORMAT.format(OffsetDateTime.now())).append(" [");
		nameChain(sb);
		sb.append("] ");
		sb.append(format);
		final String totalFormat = sb.toString();

		synchronized(dest) {
			dest.printf(totalFormat, args);
			if (newline) dest.println();
		}

		return this;
	}
}
