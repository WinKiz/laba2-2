package ru.student.lab2;
import java.nio.file.Path;
public final class Main {
    private Main() {}
    public static void main(String[] args) throws Exception {
        if (args.length != 3) throw new IllegalArgumentException("Usage: Main directory threads variant");
        var directory = Path.of(args[0]);
        int threads = Integer.parseInt(args[1]);
        var expected = Statistics.load(directory);
        var analyzer = Analyzers.create(args[2]);
        long start = System.nanoTime();
        var result = analyzer.analyze(directory, threads);
        double seconds = (System.nanoTime() - start) / 1_000_000_000.0;
        String check = Statistics.difference(expected, result);
        System.out.printf(java.util.Locale.ROOT, "%s threads=%d seconds=%.6f %s%n", args[2], threads, seconds, check);
        if (!check.equals("OK")) System.exit(2);
    }
}
