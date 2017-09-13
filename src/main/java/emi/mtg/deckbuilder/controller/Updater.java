package emi.mtg.deckbuilder.controller;

import emi.mtg.deckbuilder.view.MainWindow;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.Writer;
import java.net.URI;
import java.nio.file.*;
import java.nio.file.attribute.FileAttribute;
import java.nio.file.attribute.PosixFilePermissions;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

public class Updater {
	protected final String scriptExtension;
	protected final String header;
	protected final String sleep, cpR, rmR, rm;
	protected final FileAttribute[] scriptAttributes;

	protected final Context context;

	public Updater(Context context) throws IOException {
		this.context = context;

		if (System.getProperty("os.name").startsWith("Windows")) {
			scriptExtension = "bat";
			header = "@echo off";
			sleep = "ping -n 5 127.0.0.1 > nul";
			cpR = "xcopy.exe /S /K /Y %s .";
			rm = "del %s";
			rmR = "rmdir /S /Q %s";
			scriptAttributes = new FileAttribute[0];
		} else {
			scriptExtension = "sh";
			header = "#!/bin/sh";
			sleep = "sleep 5";
			cpR = "cp -r %s/ .";
			rm = "rm %s";
			rmR = "rm -r %s";
			scriptAttributes = new FileAttribute[] { PosixFilePermissions.asFileAttribute(PosixFilePermissions.fromString("rwxrwxrwx")) };
		}
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

		final String nl = System.lineSeparator();

		Path script = Files.createFile(Paths.get(".update." + scriptExtension), scriptAttributes);
		Writer scriptWriter = Files.newBufferedWriter(script, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);

		scriptWriter.append(header).append(nl);
		scriptWriter.append(sleep).append(nl);

		scriptWriter.append(String.format(cpR, updateDir.toString())).append(nl);
		scriptWriter.append(String.format(rmR, updateDir.toString())).append(nl);

		String java = Paths.get(System.getProperty("java.home"), "bin", "java").toString();
		String jar = MainWindow.class.getProtectionDomain().getCodeSource().getLocation().toString().substring(6);

		Path input = Files.createTempFile("input", "");
		Path output = Files.createTempFile("output", "");
		Path error = Files.createTempFile("error", "");

		scriptWriter.append(String.format("\"%s\" -jar \"%s\"", java, jar)).append(nl);
		scriptWriter.append(String.format(rm, input.toString())).append(nl);
		scriptWriter.append(String.format(rm, output.toString())).append(nl);
		scriptWriter.append(String.format(rm, error.toString())).append(nl);
		scriptWriter.append(String.format(rm, script.toString())).append(nl);

		scriptWriter.close();

		// TODO: Launch script
		// TODO: Restart program (end of script?)

		new ProcessBuilder()
				.command(script.toAbsolutePath().toString())
				.redirectInput(input.toFile())
				.redirectOutput(output.toFile())
				.redirectError(error.toFile())
				.start();

		System.exit(0);
	}

}
