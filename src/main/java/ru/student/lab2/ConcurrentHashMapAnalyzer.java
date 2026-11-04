package ru.student.lab2;
import java.io.IOException;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
public final class ConcurrentHashMapAnalyzer implements WordAnalyzer {
    public Map<String, Long> analyze(Path directory, int threads) throws IOException, InterruptedException {
        Map<String, Integer> counts = new ConcurrentHashMap<>();
        AnalysisSupport.run(AnalysisSupport.list(directory), threads, files -> {
            for (Path file : files) AnalysisSupport.read(file, word -> counts.merge(word, 1, Math::addExact));
            return Boolean.TRUE;
        });
        return AnalysisSupport.toLong(counts);
    }
}
