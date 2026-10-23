package eu.amadeos.apps.books.domain;

import java.awt.Color;
import java.util.Optional;

/** The colours of the page: warm paper, sepia, or light text on dark for the night. */
public enum ReadTheme {
    PAPER("paper", new Color(0xfbf8f1), new Color(0x262421), new Color(0x8a8378), new Color(0x2a5d8f)),
    SEPIA("sepia", new Color(0xf3e9d2), new Color(0x4d3b2a), new Color(0x9a8566), new Color(0x7a4a1c)),
    NIGHT("night", new Color(0x1d1f22), new Color(0xc9c4b8), new Color(0x6d6a63), new Color(0x8fb4d9));

    private final String key;
    private final Color background;
    private final Color ink;
    private final Color dim;
    private final Color link;

    ReadTheme(String key, Color background, Color ink, Color dim, Color link) {
        this.key = key;
        this.background = background;
        this.ink = ink;
        this.dim = dim;
        this.link = link;
    }

    public String key() {
        return key;
    }

    public Color background() {
        return background;
    }

    public Color ink() {
        return ink;
    }

    /** Running heads and page numbers. */
    public Color dim() {
        return dim;
    }

    public Color link() {
        return link;
    }

    public boolean dark() {
        return this == NIGHT;
    }

    public static Optional<ReadTheme> forKey(String key) {
        for (ReadTheme t : values()) {
            if (t.key.equals(key)) return Optional.of(t);
        }
        return Optional.empty();
    }
}
