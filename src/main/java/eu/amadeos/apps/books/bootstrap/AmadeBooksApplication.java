package eu.amadeos.apps.books.bootstrap;

import eu.amadeos.apps.books.ui.main.MainPresenter;
import eu.amadeos.apps.books.ui.main.MainWindow;
import eu.amadeos.commons.app.AmadeApplication;
import eu.amadeos.commons.app.AppInfo;

import java.util.List;

public final class AmadeBooksApplication {
    public static final AppInfo APP = new AppInfo("amade-books", "Books", "books");

    private AmadeBooksApplication() {
    }

    /** The version from the jar's manifest; "dev" when run from the IDE. */
    public static String version() {
        String version = AmadeBooksApplication.class.getPackage().getImplementationVersion();
        return version == null ? "dev" : version;
    }

    // --8<-- [start:main]
    public static void main(String[] args) {
        if (args.length > 0 && (args[0].equals("--version") || args[0].equals("-v"))) {
            System.out.println("amade-books " + version());
            return;
        }
        AmadeApplication.launch(APP, injector -> {
            MainWindow window = injector.getInstance(MainWindow.class);
            MainPresenter presenter = injector.getInstance(MainPresenter.class);
            window.bind(presenter);
            window.setVisible(true);
            presenter.start(List.of(args));
        }, new BooksModule());
    }
    // --8<-- [end:main]
}
