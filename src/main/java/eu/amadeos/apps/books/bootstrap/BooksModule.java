package eu.amadeos.apps.books.bootstrap;

import com.google.inject.AbstractModule;
import com.google.inject.Provides;
import com.google.inject.Scopes;
import com.google.inject.Singleton;
import eu.amadeos.apps.books.domain.layout.Hyphenators;
import eu.amadeos.apps.books.repository.BooksPreferences;
import eu.amadeos.apps.books.repository.LibraryCache;
import eu.amadeos.apps.books.repository.LibraryFolder;
import eu.amadeos.apps.books.repository.ReadingStates;
import eu.amadeos.apps.books.ui.main.MainPresenter;
import eu.amadeos.apps.books.ui.main.MainWindow;
import eu.amadeos.commons.config.XdgDirectories;
import eu.amadeos.commons.desktop.DesktopLauncher;
import eu.amadeos.commons.i18n.LocaleSettings;
import eu.amadeos.commons.i18n.Messages;
import eu.amadeos.commons.swing.EdtExecutor;

import java.nio.file.Path;
import java.util.Locale;
import java.util.concurrent.Executors;

/**
 * amade-books bindings. Theme, language and the other shared services come from
 * {@link eu.amadeos.commons.app.CommonsModule}.
 */
public final class BooksModule extends AbstractModule {
    /** The texts, {@code messages.properties} and its translations. */
    public static final String MESSAGES = "eu.amadeos.apps.books.messages";

    @Override
    protected void configure() {
        bind(BooksPreferences.class).in(Scopes.SINGLETON);
        bind(ReadingStates.class).in(Scopes.SINGLETON);
        bind(LibraryCache.class).in(Scopes.SINGLETON);
        bind(MainWindow.class).in(Scopes.SINGLETON);
    }

    /** {@code ~/Books}, the library folder until the reader chooses others. */
    @Provides
    @LibraryFolder
    Path libraryFolder(XdgDirectories xdg) {
        return xdg.home().resolve("Books");
    }

    /** The hyphenation patterns of the Debian {@code hyphen-*} packages. */
    @Provides
    @Singleton
    Hyphenators hyphenators() {
        return new Hyphenators(Hyphenators.SYSTEM);
    }

    // --8<-- [start:presenter]
    /**
     * One background thread opens books, scans the library, sets chapters and decodes comic pages, so the work
     * is done in the order it was asked for; the results come back on the Swing thread.
     */
    @Provides
    @Singleton
    MainPresenter presenter(MainWindow window, Messages msg, LocaleSettings locales, BooksPreferences preferences,
                            ReadingStates states, LibraryCache cache, Hyphenators hyphenators, DesktopLauncher launcher,
                            EdtExecutor edt) {
        var background = Executors.newSingleThreadExecutor(r -> {
            Thread t = new Thread(r, "books-background");
            t.setDaemon(true);
            return t;
        });
        return new MainPresenter(window, msg, locales, preferences, states, cache, hyphenators, launcher, background, edt,
                System::currentTimeMillis, () -> System.exit(0));
    }
    // --8<-- [end:presenter]

    /** The texts in the language {@code AmadeApplication} chose. */
    @Provides
    @Singleton
    Messages messages(Locale locale) {
        return Messages.load(BooksModule.class, MESSAGES, locale);
    }
}
