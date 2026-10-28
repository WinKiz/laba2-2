package ru.student.lab2;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;
public final class SelfTest {
    private SelfTest() {}
    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
    public static void main(String[] args) throws Exception {
        Path fixture = Files.createTempDirectory(Path.of("target"), "fixture-");
        Files.writeString(fixture.resolve("text-001.txt"), "Word0001 WORD0001, word0001!\nЁж ёЖ; A42?a42\n");
        Files.writeString(fixture.resolve("text-002.txt"), "word0002: word0001\n");
        Files.writeString(fixture.resolve("text-003.txt"), "");
        Files.writeString(fixture.resolve("distribution.txt"), "IGNORED words");
        Files.writeString(fixture.resolve("notes.txt"), "IGNORED words");
        Map<String, Long> expected = Map.of("word0001", 4L, "word0002", 1L, "ёж", 2L, "a42", 2L);
        for (String name : Analyzers.NAMES) {
            for (int threads : name.equals("sequential") ? new int[]{1} : new int[]{1, 2, 8}) {
                if (threads > 1 && (name.equals("hashmap") || name.equals("concurrent-get-put"))) continue;
                check(expected.equals(Analyzers.create(name).analyze(fixture, threads)), name + " normalization/count");
            }
        }
        check(!Statistics.difference(expected, Map.of()).equals("OK"), "missing keys");
        var wrong = new HashMap<>(expected);
        wrong.put("extra", 1L);
        check(!Statistics.difference(expected, wrong).equals("OK"), "extra keys");
        wrong = new HashMap<>(expected);
        wrong.put("word0001", 5L);
        check(!Statistics.difference(expected, wrong).equals("OK"), "wrong count");
        for (Map<String, Integer> counts : List.of(new ConcurrentHashMap<String, Integer>(),
                Collections.synchronizedMap(new HashMap<String, Integer>()))) {
            counts.put("word", 10);
            CyclicBarrier barrier = new CyclicBarrier(2);
            try (var pool = Executors.newFixedThreadPool(2)) {
                Callable<Void> job = () -> {
                    int old = counts.getOrDefault("word", 0);
                    barrier.await();
                    counts.put("word", old + 1);
                    return null;
                };
                var a = pool.submit(job);
                var b = pool.submit(job);
                a.get();
                b.get();
            }
            check(counts.get("word") == 11, "forced lost update must be 11 rather than 12");
        }
        try {
            Workers.run(List.of(fixture.resolve("missing.txt")), 2, files -> {
                for (Path file : files) TextFiles.read(file, word -> {});
                return true;
            });
            throw new AssertionError("worker failure was hidden");
        } catch (java.io.IOException expectedError) { /* Expected error propagated to caller. */ }
        System.out.println("SELF TEST PASSED: normalization; all safe analyzers; checker; forced lost update; worker IO failure");
    }
}
