package ru.student.lab2;
import java.io.IOException;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.LongAdder;
public final class LongAdderAnalyzer implements WordAnalyzer {
    public Map<String, Long> analyze(Path directory, int threads) throws IOException, InterruptedException {
        ConcurrentHashMap<String, LongAdder> counts = new ConcurrentHashMap<>();
        Workers.run(TextFiles.list(directory), threads, files -> {
            for (Path file : files) TextFiles.read(file,
                    word -> counts.computeIfAbsent(word, key -> new LongAdder()).increment());
            return Boolean.TRUE;
        });
        Map<String, Long> result = new HashMap<>();
        counts.forEach((word, adder) -> result.put(word, adder.sum()));
        return result;
    }
}
