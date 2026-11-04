package ru.student.lab2;
import java.io.IOException;
import java.nio.file.Path;
import java.util.Map;
import java.util.List;
public interface WordAnalyzer {
    Map<String, Long> analyze(Path directory, int threadCount) throws IOException, InterruptedException;
    public static final List<String> NAMES = List.of("sequential", "hashmap", "synchronized-map",
            "concurrent-get-put", "concurrent-merge", "long-adder", "local-maps");
    public static WordAnalyzer create(String name) {
        return switch (name) {
            case "sequential" -> new SequentialAnalyzer();
            case "hashmap" -> new HashMapAnalyzer();
            case "synchronized-map" -> new SynchronizedMapAnalyzer();
            case "concurrent-get-put" -> new ConcurrentGetPutAnalyzer();
            case "concurrent-merge" -> new ConcurrentHashMapAnalyzer();
            case "long-adder" -> new LongAdderAnalyzer();
            case "local-maps" -> new LocalMapsAnalyzer();
            default -> throw new IllegalArgumentException("Unknown variant: " + name);
        };
    }
}
