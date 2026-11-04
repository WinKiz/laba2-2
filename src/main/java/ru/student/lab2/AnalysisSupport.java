package ru.student.lab2;
import java.io.*;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.Consumer;
import java.util.regex.Pattern;

/** Общие операции: чтение текста, эталон, проверка и выполнение задач. */
public final class AnalysisSupport {
    private AnalysisSupport() {}

    private static final Pattern WORD = Pattern.compile("[\\p{L}\\p{N}]+");
    public static List<Path> list(Path directory) throws IOException {
        try (var paths = Files.list(directory)) {
            var files = paths.filter(Files::isRegularFile)
                    .filter(p -> p.getFileName().toString().matches("text-[0-9]+\\.txt"))
                    .sorted().toList();
            if (files.isEmpty()) throw new IOException("No text-NNN.txt files: " + directory);
            return files;
        }
    }
    public static void read(Path file, Consumer<String> accept) throws IOException {
        try (var reader = Files.newBufferedReader(file)) {
            String line;
            while ((line = reader.readLine()) != null) {
                var matcher = WORD.matcher(line.toLowerCase(Locale.ROOT));
                while (matcher.find()) accept.accept(matcher.group());
            }
        }
    }
    public static Map<String, Long> toLong(Map<String, Integer> source) {
        Map<String, Long> result = new HashMap<>();
        source.forEach((word, count) -> result.put(word, count.longValue()));
        return result;
    }


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


    @FunctionalInterface
    public interface Job<T> { T run(List<Path> files) throws IOException; }
    public static <T> List<T> run(List<Path> files, int threads, Job<T> job)
            throws IOException, InterruptedException {
        if (threads < 1) throw new IllegalArgumentException("threads must be positive");
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        List<Future<T>> futures = new ArrayList<>();
        try {
            for (int worker = 0; worker < threads; worker++) {
                List<Path> part = new ArrayList<>();
                for (int i = worker; i < files.size(); i += threads) part.add(files.get(i));
                futures.add(pool.submit(() -> job.run(part)));
            }
            List<T> results = new ArrayList<>();
            for (Future<T> future : futures) {
                try { results.add(future.get()); }
                catch (ExecutionException e) {
                    if (e.getCause() instanceof IOException io) throw io;
                    throw new IOException("Worker failed", e.getCause());
                }
            }
            return results;
        } finally {
            pool.shutdownNow();
            // Wait for workers before allowing the next measurement to begin.
            pool.close();
        }
    }
}
