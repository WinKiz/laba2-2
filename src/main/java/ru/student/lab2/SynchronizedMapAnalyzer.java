package ru.student.lab2;
import java.io.IOException;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
public final class SynchronizedMapAnalyzer implements WordAnalyzer {
    public Map<String, Long> analyze(Path directory, int threads) throws IOException, InterruptedException {
        Map<String, Integer> counts = Collections.synchronizedMap(new HashMap<>());
        Workers.run(TextFiles.list(directory), threads, files -> {
            for (Path file : files) TextFiles.read(file, word -> counts.merge(word, 1, Math::addExact));
            return Boolean.TRUE;
        });
        return TextFiles.toLong(counts);
    }
}
