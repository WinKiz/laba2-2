package ru.student.lab2;
import java.io.IOException;
import java.nio.file.Path;
import java.util.*;
public final class LocalMapsAnalyzer implements WordAnalyzer {
    public Map<String, Long> analyze(Path directory, int threads) throws IOException, InterruptedException {
        List<Map<String, Integer>> local = Workers.run(TextFiles.list(directory), threads, files -> {
            Map<String, Integer> counts = new HashMap<>();
            for (Path file : files) TextFiles.read(file, word -> counts.merge(word, 1, Math::addExact));
            return counts;
        });
        Map<String, Long> result = new HashMap<>();
        for (Map<String, Integer> counts : local) {
            counts.forEach((word, count) -> result.merge(word, count.longValue(), Math::addExact));
        }
        return result;
    }
}
