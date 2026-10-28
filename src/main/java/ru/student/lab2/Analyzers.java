package ru.student.lab2;
import java.util.List;
public final class Analyzers {
    public static final List<String> NAMES = List.of("sequential", "hashmap", "synchronized-map",
            "concurrent-get-put", "concurrent-merge", "long-adder", "local-maps");
    private Analyzers() {}
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
