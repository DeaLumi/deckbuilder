package emi.mtg.deckbuilder.controller;

import emi.mtg.deckbuilder.model.Preferences;
import emi.mtg.deckbuilder.util.Slog;
import emi.mtg.deckbuilder.view.MainApplication;

import java.io.IOException;
import java.io.InputStream;
import java.io.Writer;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLConnection;
import java.nio.file.*;
import java.nio.file.attribute.FileAttribute;
import java.nio.file.attribute.PosixFilePermissions;
import java.util.function.DoubleConsumer;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

public class Updater {
	private static final Slog LOG = MainApplication.LOG.child("Updater");
	private static final boolean test = false;

	// Script parameters:
	// 1 - path of JRE's java executable
	// 2 - path of this jar's parent directory
	// 3 - complete path of this jar
	// 4 - path of extracted update

	private static final String WINDOWS_SCRIPT =
			"@echo off\r\n" +
			"ping -n 3 127.0.0.1 > nul\r\n" +
			(test ? "" : "xcopy /S /K /Y \"%4$s\" \"%2$s\"\r\n") +
			"rmdir /S /Q \"%4$s\"\r\n" +
			"start \"Deckbuilder\" /B \"%1$s\" -jar \"%3$s\"\r\n" +
			"(goto) 2>nul & del \"%%~f0\"\r\n";

	private static final String BASH_SCRIPT =
			"#!/bin/sh\n" +
			"sleep 3\n" +
			(test ? "" : "cp -r \"%4$s/.\" \"%2$s\"\n")+
			"rm -rf \"%4$s\"\n" +
			"rm \"$0\"\n" +
			"nohup \"%1$s\" -jar \"%3$s\" &\n";

	private static final String SCRIPT;
	private static final String SCRIPT_EXTENSION;
	private static final FileAttribute[] SCRIPT_ATTRIBUTES;

	static {
		if (System.getProperty("os.name").startsWith("Windows")) {
			SCRIPT = WINDOWS_SCRIPT;
			SCRIPT_EXTENSION = "bat";
			SCRIPT_ATTRIBUTES = new FileAttribute[0];
		} else {
			SCRIPT = BASH_SCRIPT;
			SCRIPT_EXTENSION = "sh";
			SCRIPT_ATTRIBUTES = new FileAttribute[] { PosixFilePermissions.asFileAttribute(PosixFilePermissions.fromString("rwxrwxrwx")) };
		}
	}

	public Updater() {
	}

	private static class Downloader extends InputStream {

		private long read;
		private final long length;
		private final InputStream source;
		private final DoubleConsumer progress;

		private Downloader(URL url, DoubleConsumer progress) throws IOException {
			super();

			URLConnection conn = url.openConnection();

			this.read = 0;
			this.length = conn.getContentLengthLong();
			this.source = conn.getInputStream();
			this.progress = progress;

			progress.accept(0.0);
		}

		@Override
		public int read(byte[] b) throws IOException {
			int out = source.read(b);
			incrementProgress(out);
			return out;
		}

		@Override
		public int read(byte[] b, int off, int len) throws IOException {
			int out = source.read(b, off, len);
			incrementProgress(out);
			return out;
		}

		@Override
		public long skip(long n) throws IOException {
			long out = source.skip(n);
			incrementProgress(out);
			return out;
		}

		@Override
		public int available() throws IOException {
			return source.available();
		}

		@Override
		public int read() throws IOException {
			int out = source.read();
			incrementProgress(1);
			return out;
		}

		@Override
		public void close() throws IOException {
			source.close();
			progress.accept(1.0);
		}

		@Override
		public synchronized void mark(int readlimit) {
			source.mark(readlimit);
		}

		@Override
		public synchronized void reset() throws IOException {
			source.reset();
		}

		@Override
		public boolean markSupported() {
			return source.markSupported();
		}

		private void incrementProgress(long delta) {
			read += delta;
			progress.accept((double) read / (double) length);
		}
	}

	public boolean needsUpdate() throws IOException {
		if (Preferences.get().updateUri == null) {
			return false; // If there is no update URI, live off the grid.
		}

		URLConnection connection = Preferences.get().updateUri.toURL().openConnection();

		if (!(connection instanceof HttpURLConnection)) {
			LOG.err("Can't check for updates -- not an HTTP connection...");
			return false;
		}

		HttpURLConnection httpConn = (HttpURLConnection) connection;

		httpConn.setIfModifiedSince(Files.getLastModifiedTime(MainApplication.JAR_PATH).toMillis());
		httpConn.setRequestMethod("HEAD");

		if (httpConn.getResponseCode() >= 400) {
			throw new IOException(String.format("Error checking update server for update: %s", httpConn.getResponseMessage()));
		}

		return httpConn.getResponseCode() == 200 || test;
	}

	public boolean update(DoubleConsumer progress) throws IOException {
		if (Preferences.get().updateUri == null) {
			return false;
		}

		Path updateDir = Files.createDirectories(MainApplication.JAR_DIR.resolve(".update"));

		try (InputStream input = new Downloader(Preferences.get().updateUri.toURL(), progress)) {
			ZipInputStream zin = new ZipInputStream(input);

			ZipEntry entry = zin.getNextEntry();
			while (entry != null) {
				Path child = updateDir.resolve(entry.getName());

				if (entry.isDirectory()) {
					Files.createDirectories(child);
				} else {
					Files.copy(zin, child, StandardCopyOption.REPLACE_EXISTING);
				}

				zin.closeEntry();
				entry = zin.getNextEntry();
			}

			zin.close();
		}

		Path script = Files.createFile(MainApplication.JAR_DIR.resolve(".update." + SCRIPT_EXTENSION), SCRIPT_ATTRIBUTES);
		Writer scriptWriter = Files.newBufferedWriter(script, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);

		Path java = Paths.get(System.getProperty("java.home"), "bin", "java").toAbsolutePath();

		Path jarPath = MainApplication.JAR_PATH;
		Path jarDir = MainApplication.JAR_DIR;

		scriptWriter.append(String.format(SCRIPT, java.toString(), jarDir.toString(), jarPath.toString(), updateDir.toString()));
		scriptWriter.close();

		// TODO: Clean up these files?

		Path input = Files.createTempFile("input", "");
		Path output = Files.createTempFile("output", "");
		Path error = Files.createTempFile("error", "");

		new ProcessBuilder()
				.command(script.toAbsolutePath().toString())
				.redirectInput(input.toFile())
				.redirectOutput(output.toFile())
				.redirectError(error.toFile())
				.start();

		System.exit(0);
		return true; // This is unreachable.
	}

}
