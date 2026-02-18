package app;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

final class FileUtils {
    private FileUtils() {
    }

    static List<Path> listPngFrames(Path directory, String prefix) throws IOException {
        try (Stream<Path> stream = Files.list(directory)) {
            return stream
                    .filter(path -> {
                        String name = path.getFileName().toString();
                        return name.startsWith(prefix) && name.endsWith(".png");
                    })
                    .sorted(Comparator.comparing(path -> path.getFileName().toString()))
                    .collect(Collectors.toList());
        }
    }

    static void deleteRecursively(Path root) {
        if (root == null || !Files.exists(root)) {
            return;
        }
        try (Stream<Path> walk = Files.walk(root)) {
            walk.sorted(Comparator.reverseOrder()).forEach(path -> {
                try {
                    Files.deleteIfExists(path);
                } catch (IOException ignored) {
                }
            });
        } catch (IOException ignored) {
        }
    }

    static void clearDirectory(Path directory) throws IOException {
        Files.createDirectories(directory);
        try (Stream<Path> stream = Files.list(directory)) {
            for (Path child : stream.collect(Collectors.toList())) {
                deleteRecursivelyStrict(child);
            }
        }
    }

    private static void deleteRecursivelyStrict(Path root) throws IOException {
        if (root == null || !Files.exists(root)) {
            return;
        }
        try (Stream<Path> walk = Files.walk(root)) {
            for (Path path : walk.sorted(Comparator.reverseOrder()).collect(Collectors.toList())) {
                Files.deleteIfExists(path);
            }
        }
    }
}
