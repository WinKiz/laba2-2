package ru.student.lab2;
import java.io.IOException;
import java.nio.file.*;
import java.util.*;
public final class Statistics {
    private Statistics() {}
    public static Map<String, Long> load(Path directory) throws IOException {
        Map<String, Long> expected = new TreeMap<>();
        try (var reader = Files.newBufferedReader(directory.resolve("expected-statistics.txt"))) {
            if (!"word count".equals(reader.readLine())) throw new IOException("Invalid statistics header");
            String line;
            while ((line = reader.readLine()) != null) {
                String[] fields = line.split("\\s+");
                if (fields.length != 2) throw new IOException("Invalid statistics line: " + line);
                long count = Long.parseLong(fields[1]);
                if (count < 0 || expected.put(fields[0], count) != null) throw new IOException("Invalid count/duplicate");
            }
        }
        return expected;
    }
    public static String difference(Map<String, Long> expected, Map<String, Long> actual) {
        Set<String> keys = new TreeSet<>(expected.keySet());
        keys.addAll(actual.keySet());
        int errors = 0;
        String first = "";
        for (String key : keys) {
            if (!Objects.equals(expected.get(key), actual.get(key))) {
                if (errors++ == 0) first = key + ": expected=" + expected.get(key) + " actual=" + actual.get(key);
            }
        }
        return errors == 0 ? "OK" : "MISMATCH keys=" + errors + " first=" + first;
    }
}
