package eu.amadeos.apps.books.repository;

import eu.amadeos.commons.json.Json;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Reading and writing the JSON files of the library; a file that is missing or broken reads as empty. */
final class JsonFiles {
    private static final Logger LOG = LoggerFactory.getLogger(JsonFiles.class);

    private JsonFiles() {
    }

    static Map<String, Object> read(Path file) {
        if (!Files.isRegularFile(file)) return new LinkedHashMap<>();
        try {
            return Json.parseObject(Files.readString(file, StandardCharsets.UTF_8));
        } catch (IOException | RuntimeException e) {
            LOG.warn("Cannot read {}, starting empty: {}", file, e.toString());
            return new LinkedHashMap<>();
        }
    }

    /** Writes to a temporary file first, so a crash never leaves half a file. */
    static void write(Path file, Map<String, Object> content) {
        try {
            Files.createDirectories(file.getParent());
            Path temporary = file.resolveSibling(file.getFileName() + ".tmp");
            Files.writeString(temporary, Json.write(content), StandardCharsets.UTF_8);
            Files.move(temporary, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (IOException | RuntimeException e) {
            LOG.warn("Cannot write {}: {}", file, e.toString());
        }
    }

    static double number(Object object, String key, double fallback) {
        return Json.member(object, key) instanceof Number n ? n.doubleValue() : fallback;
    }

    static long longNumber(Object object, String key) {
        return Json.member(object, key) instanceof Number n ? n.longValue() : 0;
    }

    static boolean flag(Object object, String key) {
        return Json.member(object, key) instanceof Boolean b && b;
    }

    static List<String> strings(Object object, String key) {
        return Json.list(object, key).stream().filter(String.class::isInstance).map(String.class::cast).toList();
    }
}
