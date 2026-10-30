# amade-books

**amade-books** is the e-book and comic reader of the [amadeOS](https://github.com/amadeos-eu/amadeos) desktop: a bookshelf with covers, EPUB books set into pages by its own small typesetter, and CBZ comics with double pages and right-to-left reading for manga, in one Motif window.

![License](https://img.shields.io/badge/license-EUPL--1.2-blue)
![Java](https://img.shields.io/badge/Java-25-orange)

![amade-books](docs/screenshots/amade-books.png)

## Features

- **Library**: every EPUB and CBZ in `~/Books` and its subfolders (more folders in *File → Library Folders*) on a shelf of covers or as a list. The sidebar sorts them into shelves: *Reading*, *Not started*, *Finished*, the **format** (books, comics), **genre**, **author**, **series** and your own **collections**. Sort by last read, title, author (by last name), series or date added; the search box filters by title, author and series. *File → Add to Library* copies books into the first folder.
- **Opening**: a double click on the shelf, *File → Open* (Ctrl+O), a file dropped on the window, or `amade-books file.epub` (also `file://` URLs). The last book opens again at start (*Options*).
- **Pages**: one page or two side by side, justified and hyphenated in the language of the book (Liang patterns from the `hyphen-*` packages), with running heads and page numbers. Headings are kept with the next line, no single line of a paragraph is left alone at the top or bottom of a page.
- **Pictures** are scaled to the page and centred with their caption; pictures the book floats to the left or right have the text flowing around them. A picture that does not fit any more moves to the top of the next page and the text goes on before it.
- **Appearance** (the *Aa* button): font (or the publisher's fonts the book brings), size, line spacing, margins, justification, hyphenation, the colours **Paper**, **Sepia** and **Night**, one or two pages. Each book keeps its own appearance.
- **Moving around**: the arrow keys, Page Up/Down, Space, the mouse wheel and clicks in the outer thirds of the page turn pages; Home and End go to the start and end of the book. The progress bar below shows how far you are, with a tick for each chapter; a click jumps there.
- **Contents and bookmarks** in the sidebar (F9). Ctrl+D sets or removes a bookmark on the page. Links inside the book jump to their target and **Back** and **Forward** (Alt+Left, Alt+Right) return; footnotes open in a small window; links to the web open in the browser.
- **Search** (Ctrl+F) through the whole book, ignoring case and accents; F3 and Shift+F3 go to the next and previous hit.
- **Text selection** with the mouse, Ctrl+C copies.
- **Comics** (CBZ): fit to page, width or height, zoom, double pages (the cover and wide pages alone) and **right to left** for manga, taken from `ComicInfo.xml` when the archive has one.
- **Full screen** (F11) without toolbar and status bar; Esc leaves it.
- **Reading position** of every book, its bookmarks and collections in `~/.local/share/amadeos/books/library.json`; settings in `~/.config/amadeos/books/books.properties`; cover thumbnails in `~/.cache/amadeos/books/`.
- Five languages (English, Deutsch, Français, Español, Italiano), following the amadeOS desktop language; *Options → Language* overrides it.
- Theme colours and Motif look from [amade-commons](https://github.com/amadeos-eu/amade-commons).

## Why an own typesetter

EPUB books are XHTML and CSS. A browser engine (JavaFX WebView, a Chromium) would bring hundreds of megabytes and still not set pages; Swing's `HTMLEditorKit` only knows HTML 3.2 and CSS 1. amade-books therefore reads the XHTML with [jsoup](https://jsoup.org/), applies the part of CSS that books really use (type, class and id selectors, descendants, `font-*`, `text-align`, `text-indent`, margins, `float`, `width`, `page-break-*`, `@font-face`) and sets the paragraphs with Java2D's `LineBreakMeasurer` and `TextLayout`. That keeps the program small, the pages exact, and gives full control over hyphenation, widows and orphans and the reading position (chapter and character, independent of the window size).

## Architecture

Java 25, Swing and Guice; start-up, theme and language come from `AmadeApplication` in amade-commons.

| Package | Contents |
|---|---|
| `bootstrap` | `AmadeBooksApplication`, `BooksModule` |
| `domain` | `BookInfo`, `BookFormat`, `Position`, `ReadTheme`, `ComicFit`, `Settings` |
| `domain.epub` | `EpubBook` (container, package document, navigation, NCX), `EpubSession` (chapter texts and layouts with caches, progress, links, footnotes, pictures, fonts, search), `ZipPaths` |
| `domain.layout` | `Css`, `Style`, `ChapterParser` (XHTML to blocks), `Typesetter` (blocks to pages), `Hyphenator`, `PageGeometry`, `Appearance`, `Page`, `ChapterLayout` |
| `domain.comic` | `ComicBook`, `ComicSession`, `Spreads`, `NaturalOrder` |
| `domain.library` | `LibraryScanner`, `Shelf` (facets, filters, sorting), `BookState`, `Bookmark`, `LibraryEntry` |
| `repository` | `BooksPreferences`, `ReadingStates`, `LibraryCache` |
| `ui.main` | window, presenter, menus, `LibraryPanel`, `CoverGrid`, `PageView`, `ComicView`, `ReaderSidebar`, `AppearancePanel` |

Opening, scanning the library, setting a chapter and decoding comic pages run on one background thread; the results come back on the Swing thread, and results for a book that was closed in the meantime are dropped. The chapter after the current one is set in advance, so turning into it does not wait.

## Build and run

```sh
./gradlew run                              # start
./gradlew test                             # Swing tests need a display: xvfb-run ./gradlew test
./gradlew deb                              # build/distributions/amade-books_<version>_all.deb
./gradlew installToAmadeOs                 # the .deb into ../amadeos/config/packages.chroot/ for the ISO build
```

If `../amade-commons` is checked out next to this repository, Gradle builds against that checkout instead of the JitPack version.

## Planned

- A scrolling view as an alternative to pages
- CBR comics (RAR archives) and PDF comics
- Notes and highlights
- Tables with real columns and vertical writing

## License

[EUPL-1.2](LICENSE)
