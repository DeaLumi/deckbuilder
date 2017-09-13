package emi.mtg.deckbuilder.controller;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.Writer;
import java.net.URI;
import java.nio.file.*;
import java.nio.file.attribute.PosixFilePermissions;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

public class Updater {
	protected final String scriptExtension;
	protected final String header;
	protected final String sleep, cpR, rmR, rm;

	protected final Context context;

	public Updater(Context context) throws IOException {
		this.context = context;

		if (System.getProperty("os.name").startsWith("Windows")) {
			scriptExtension = "bat";
			header = "@echo off";
			sleep = "ping -n 5 127.0.0.1 > nul";
			cpR = "xcopy.exe /S /K";
			rm = "del";
			rmR = "rmdir /S";
		} else {
			scriptExtension = "sh";
			header = "#!/bin/sh";
			sleep = "sleep 5";
			cpR = "cp -r";
			rm = "rm";
			rmR = "rm -r";
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

		Path script = Files.createFile(Paths.get(".update." + scriptExtension), PosixFilePermissions.asFileAttribute(PosixFilePermissions.fromString("rwxrwxrwx")));
		Writer scriptWriter = Files.newBufferedWriter(script, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);

		scriptWriter.append(header).append(nl);
		scriptWriter.append(sleep).append(nl);

		// TODO: Is
		// TODO:				xcopy.exe /S someDir\ .
		// TODO: the same as
		// TODO:				cp -r someDir/ .
		scriptWriter.append(String.format("%s %s %s", cpR, updateDir.toString() + File.pathSeparator, Paths.get("."))).append(nl);
		scriptWriter.append(String.format("%s %s", rm, script.toString())).append(nl);
		scriptWriter.append(String.format("%s %s", rmR, updateDir.toString())).append(nl);
		scriptWriter.close();

		// TODO: Launch script
		// TODO: Restart program (end of script?)

		System.exit(0);
	}

}
