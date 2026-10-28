package ru.student.lab2;
import java.io.*;
import java.nio.file.*;
import java.util.*;
import java.util.function.Consumer;
import java.util.regex.Pattern;
public final class TextFiles {
    private static final Pattern WORD = Pattern.compile("[\\p{L}\\p{N}]+");
    private TextFiles() {}
    public static List<Path> list(Path directory) throws IOException {
        try (var paths = Files.list(directory)) {
            var files = paths.filter(Files::isRegularFile)
                    .filter(p -> p.getFileName().toString().matches("text-[0-9]+\\.txt"))
                    .sorted().toList();
            if (files.isEmpty()) throw new IOException("No text-NNN.txt files: " + directory);
            return files;
        }
    }
    public static void read(Path file, Consumer<String> accept) throws IOException {
        try (var reader = Files.newBufferedReader(file)) {
            String line;
            while ((line = reader.readLine()) != null) {
                var matcher = WORD.matcher(line.toLowerCase(Locale.ROOT));
                while (matcher.find()) accept.accept(matcher.group());
            }
        }
    }
    public static Map<String, Long> toLong(Map<String, Integer> source) {
        Map<String, Long> result = new HashMap<>();
        source.forEach((word, count) -> result.put(word, count.longValue()));
        return result;
    }
}
