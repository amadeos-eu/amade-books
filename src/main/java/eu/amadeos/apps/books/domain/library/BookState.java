package eu.amadeos.apps.books.domain.library;

import eu.amadeos.apps.books.domain.Position;
import eu.amadeos.apps.books.domain.layout.Appearance;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

/**
 * What the reader did with one book: where they are, how far that is, bookmarks, collections, and the choices
 * made for this book (appearance of an EPUB, reading direction and two pages of a comic).
 *
 * @param progress   0 to 1; 0 with {@code lastRead} 0 means not started
 * @param lastRead   milliseconds since 1970, 0 when never opened
 * @param appearance empty when the book uses the default
 * @param rightToLeft for comics: empty when the archive decides
 * @param twoPages   empty when the default applies
 */
public record BookState(Position position, double progress, long lastRead, boolean finished, List<Bookmark> bookmarks,
                        List<String> collections, Optional<Appearance> appearance, Optional<Boolean> rightToLeft,
                        Optional<Boolean> twoPages) {
    public static final BookState NEW = new BookState(Position.START, 0, 0, false, List.of(), List.of(), Optional.empty(),
            Optional.empty(), Optional.empty());

    public BookState {
        bookmarks = bookmarks.stream().sorted(Comparator.comparing(Bookmark::position)).toList();
        collections = List.copyOf(collections);
        progress = Math.clamp(progress, 0, 1);
    }

    public boolean started() {
        return lastRead > 0 || progress > 0;
    }

    /** Started and not finished. */
    public boolean reading() {
        return started() && !finished;
    }

    public BookState read(Position at, double newProgress, long now) {
        return new BookState(at, newProgress, now, finished || newProgress >= 0.999, bookmarks, collections, appearance,
                rightToLeft, twoPages);
    }

    public BookState withFinished(boolean done) {
        return new BookState(done ? position : Position.START, done ? 1 : 0, done ? lastRead : 0, done,
                done ? bookmarks : bookmarks, collections, appearance, rightToLeft, twoPages);
    }

    /** Adds the bookmark, or removes the one at the same position. */
    public BookState toggleBookmark(Bookmark bookmark) {
        List<Bookmark> list = new ArrayList<>(bookmarks);
        if (!list.removeIf(b -> b.position().equals(bookmark.position()))) list.add(bookmark);
        return new BookState(position, progress, lastRead, finished, list, collections, appearance, rightToLeft, twoPages);
    }

    public BookState withoutBookmark(Bookmark bookmark) {
        List<Bookmark> list = new ArrayList<>(bookmarks);
        list.remove(bookmark);
        return new BookState(position, progress, lastRead, finished, list, collections, appearance, rightToLeft, twoPages);
    }

    public BookState withCollection(String name, boolean member) {
        List<String> list = new ArrayList<>(collections);
        list.remove(name);
        if (member) list.add(name);
        return new BookState(position, progress, lastRead, finished, bookmarks, list, appearance, rightToLeft, twoPages);
    }

    public BookState withAppearance(Appearance a) {
        return new BookState(position, progress, lastRead, finished, bookmarks, collections, Optional.of(a), rightToLeft,
                twoPages);
    }

    public BookState withRightToLeft(boolean rtl) {
        return new BookState(position, progress, lastRead, finished, bookmarks, collections, appearance, Optional.of(rtl),
                twoPages);
    }

    public BookState withTwoPages(boolean two) {
        return new BookState(position, progress, lastRead, finished, bookmarks, collections, appearance, rightToLeft,
                Optional.of(two));
    }
}
