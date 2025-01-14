package emi.mtg.deckbuilder.controller;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Collections;
import java.util.function.Consumer;
import java.util.function.DoubleConsumer;

public interface Updateable {
	interface Progress extends DoubleConsumer, Consumer<String> {
		void accept(double progress, String message);

		default void accept(double progress) {
			accept(progress, "");
		}

		default void accept(String message) {
			accept(-1.0, message);
		}

		static void cmdLine(String label, int width, double progress, String message) {
			if (!message.isEmpty()) message = " " + message + " ";
			int mwidth = message.length();
			int leftWidth = (width - mwidth) / 2;
			int rightWidth = leftWidth + ((width - mwidth) % 2 == 0 ? 0 : 1);
			int nLeft = Math.max(0, Math.min((int) Math.floor(width * progress), leftWidth));
			int sLeft = Math.max(0, Math.min(leftWidth - nLeft, leftWidth));
			int sRight = Math.max(0, Math.min((int) Math.ceil(width * (1.0 - progress)), rightWidth));
			int nRight = Math.max(0, Math.min(rightWidth - sRight, rightWidth));
			System.out.printf(
					"\r%s: [%s%s%s%s%s] %3.1f%%",
					label,
					String.join("", Collections.nCopies(nLeft, "=")),
					String.join("", Collections.nCopies(sLeft, " ")),
					message,
					String.join("", Collections.nCopies(nRight, "=")),
					String.join("", Collections.nCopies(sRight, " ")),
					progress * 100.0
			);

			if (progress == 1.0) {
				System.out.println();
			}
		}

		static Progress cmdLine(String label, int width) {
			return (p, m) -> cmdLine(label, width, p, m);
		}
	}

	String description();

	boolean updateAvailable(Path localData);

	void update(Path localData, Progress progress) throws IOException;
}
