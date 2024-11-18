package emi.mtg.deckbuilder.util;

import java.io.PrintStream;
import java.time.OffsetDateTime;

public class Slog {
	private final Slog parent;

	private String name;
	private long stopwatch;

	public Slog(String name) {
		this(null, name);
	}

	public Slog rename(String name) {
		this.name = name;
		return this;
	}

	protected Slog(Slog parent, String name) {
		this.parent = parent;
		this.name = name;
		this.stopwatch = -1;
	}

	protected void nameChain(PrintStream dest) {
		if (parent != null) {
			parent.nameChain(dest);
			dest.print(" / ");
		}
		dest.print(name);
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

	public Slog log(java.io.PrintStream dest, boolean newline, String format, Object... args) {
		dest.printf("[%1$tF %1$tT.%1$tL %1$tZ] [", OffsetDateTime.now());
		nameChain(dest);
		dest.print("] ");
		dest.printf(format, args);
		if (newline) dest.println();
		return this;
	}
}
