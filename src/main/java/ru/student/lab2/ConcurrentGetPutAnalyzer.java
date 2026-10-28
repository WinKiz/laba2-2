package ru.student.lab2;
import java.io.IOException;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
public final class ConcurrentGetPutAnalyzer implements WordAnalyzer {
    public Map<String, Long> analyze(Path directory, int threads) throws IOException, InterruptedException {
        Map<String, Integer> counts = new ConcurrentHashMap<>();
        Workers.run(TextFiles.list(directory), threads, files -> {
            for (Path file : files) TextFiles.read(file, word -> counts.put(word, counts.getOrDefault(word, 0) + 1));
            return Boolean.TRUE;
        });
        return TextFiles.toLong(counts);
    }
}
