package ru.student.lab2;
import java.io.IOException;
import java.nio.file.Path;
import java.util.*;
public final class SequentialAnalyzer implements WordAnalyzer {
    public Map<String, Long> analyze(Path directory, int threads) throws IOException {
        if (threads != 1) throw new IllegalArgumentException("sequential requires 1 thread");
        Map<String, Integer> counts = new HashMap<>();
        for (Path file : TextFiles.list(directory)) {
            TextFiles.read(file, word -> counts.merge(word, 1, Math::addExact));
        }
        return TextFiles.toLong(counts);
    }
}
