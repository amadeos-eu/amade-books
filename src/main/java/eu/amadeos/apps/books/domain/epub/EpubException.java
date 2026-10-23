package eu.amadeos.apps.books.domain.epub;

import java.io.IOException;

/** The file is not a readable EPUB: no ZIP, no package document or no reading order. */
public class EpubException extends IOException {
    public EpubException(String message) {
        super(message);
    }

    public EpubException(String message, Throwable cause) {
        super(message, cause);
    }
}
