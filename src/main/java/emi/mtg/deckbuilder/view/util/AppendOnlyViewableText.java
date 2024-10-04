package emi.mtg.deckbuilder.view.util;

import javafx.scene.control.TextArea;

import java.io.IOException;
import java.io.OutputStream;
import java.io.PrintStream;
import java.util.HashSet;
import java.util.Set;

public class AppendOnlyViewableText implements Appendable {
    public class Tee extends PrintStream {
        public Tee(OutputStream out) {
            super(out);
        }

        @Override
        public void write(int b) {
            try {
                AppendOnlyViewableText.this.append((char) b);
            } catch (IOException ioe) {
                this.setError();
            }
            super.write(b);
        }

        @Override
        public void write(byte[] buf, int off, int len) {
            try {
                AppendOnlyViewableText.this.append(new String(buf, off, len));
            } catch (IOException ioe) {
                this.setError();
            }
            super.write(buf, off, len);
        }

        @Override
        public PrintStream append(CharSequence csq) {
            try {
                AppendOnlyViewableText.this.append(csq);
            } catch (IOException ioe) {
                this.setError();
            }
            return super.append(csq);
        }

        @Override
        public PrintStream append(CharSequence csq, int start, int end) {
            try {
                AppendOnlyViewableText.this.append(csq, start, end);
            } catch (IOException ioe) {
                this.setError();
            }
            return super.append(csq, start, end);
        }

        @Override
        public PrintStream append(char c) {
            try {
                AppendOnlyViewableText.this.append(c);
            } catch (IOException ioe) {
                this.setError();
            }
            return super.append(c);
        }
    }

    private final StringBuffer buffer;
    private final Set<TextArea> views;

    public AppendOnlyViewableText(int initialCapacity) {
        this.buffer = new StringBuffer(initialCapacity);
        this.views = new HashSet<>();
    }

    public void addView(TextArea view) {
        this.views.add(view);
    }

    public void removeView(TextArea view) {
        this.views.remove(view);
    }

    public CharSequence get() {
        return buffer;
    }

    @Override
    public Appendable append(CharSequence csq) throws IOException {
        return append(csq, 0, csq.length());
    }

    @Override
    public Appendable append(CharSequence csq, int start, int end) throws IOException {
        buffer.append(csq);

        String s;
        if (csq instanceof String) {
            s = ((String) csq).substring(start, end);
        } else {
            s = new String(csq.codePoints().toArray(), start, end);
        }
        for (TextArea view : views) view.appendText(s);

        return this;
    }

    @Override
    public Appendable append(char c) throws IOException {
        buffer.append(c);

        for (TextArea view : views) view.appendText(Character.toString(c));

        return this;
    }
}
