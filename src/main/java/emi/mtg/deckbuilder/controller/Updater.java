package emi.mtg.deckbuilder.controller;

import emi.mtg.deckbuilder.view.MainWindow;

import java.io.IOException;
import java.io.InputStream;
import java.io.Writer;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.file.*;
import java.nio.file.attribute.FileAttribute;
import java.nio.file.attribute.PosixFilePermissions;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

public class Updater {
	// Script parameters:
	// 1 - path of JRE's java executable
	// 2 - path of this jar's parent directory
	// 3 - complete path of this jar
	// 4 - path of extracted update

	private static final String WINDOWS_SCRIPT =
			"@echo off\r\n" +
			"ping -n 3 127.0.0.1 > nul\r\n" +
			"xcopy /S /K /Y \"%4$s\" \"%2$s\"\r\n" +
			"rmdir /S /Q \"%4$s\"\r\n" +
			"start /B \"%1$s\" -jar \"%3$s\"" +
			"(goto) 2>nul & del \"%~f0\"";

	private static final String BASH_SCRIPT =
			"#!/bin/sh\n" +
			"sleep 3\n" +
			"cp -r \"%4$s/\" \"%2$s\"\n" +
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

	protected final Context context;

	public Updater(Context context) throws IOException {
		this.context = context;
	}

	public void update(URI remote) throws IOException {
		Path updateDir = Files.createDirectories(Paths.get(".update"));

		try (InputStream input = remote.toURL().openStream()) {
			ZipInputStream zin = new ZipInputStream(input);

			ZipEntry entry = zin.getNextEntry();
			while (entry != null) {
				Path child = Paths.get(updateDir.toString(), entry.getName());

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

		Path script = Files.createFile(Paths.get(".update." + SCRIPT_EXTENSION), SCRIPT_ATTRIBUTES);
		Writer scriptWriter = Files.newBufferedWriter(script, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);

		String java = Paths.get(System.getProperty("java.home"), "bin", "java").toString();

		URL jarUrl = MainWindow.class.getProtectionDomain().getCodeSource().getLocation();
		String jarPath;
		String jarDir;
		try {
			jarPath = jarUrl.toURI().getPath();
		} catch (URISyntaxException urise) {
			jarPath = jarUrl.getPath();
		}
		jarDir = Paths.get(jarPath).getParent().toString();

		scriptWriter.append(String.format(SCRIPT, java, jarDir, jarPath, updateDir.toString()));
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
	}

}
