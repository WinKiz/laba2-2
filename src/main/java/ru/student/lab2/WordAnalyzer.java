package ru.student.lab2;
import java.io.IOException;
import java.nio.file.Path;
import java.util.Map;
public interface WordAnalyzer {
    Map<String, Long> analyze(Path directory, int threadCount) throws IOException, InterruptedException;
}
