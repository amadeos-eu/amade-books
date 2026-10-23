package eu.amadeos.apps.books.domain;

import java.util.Optional;

/** How comic pages are scaled into the window. */
public enum ComicFit {
    PAGE("page"), WIDTH("width"), HEIGHT("height");

    private final String key;

    ComicFit(String key) {
        this.key = key;
    }

    public String key() {
        return key;
    }

    /** The scale for pictures of this total size in a window of this size. */
    public double scale(double pictureWidth, double pictureHeight, double windowWidth, double windowHeight) {
        double w = windowWidth / Math.max(1, pictureWidth);
        double h = windowHeight / Math.max(1, pictureHeight);
        return switch (this) {
            case PAGE -> Math.min(w, h);
            case WIDTH -> w;
            case HEIGHT -> h;
        };
    }

    public static Optional<ComicFit> forKey(String key) {
        for (ComicFit f : values()) {
            if (f.key.equals(key)) return Optional.of(f);
        }
        return Optional.empty();
    }
}
