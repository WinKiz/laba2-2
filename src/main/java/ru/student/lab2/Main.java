package ru.student.lab2;
import java.nio.file.*;
import java.util.*;
public final class Main {
    private Main() {}
    public static void main(String[] args) throws Exception {
        if (args.length > 0 && args[0].equals("benchmark")) {
            benchmark(Arrays.copyOfRange(args, 1, args.length));
            return;
        }
        if (args.length != 3) throw new IllegalArgumentException("Usage: Main directory threads variant");
        var directory = Path.of(args[0]);
        int threads = Integer.parseInt(args[1]);
        var expected = AnalysisSupport.load(directory);
        var analyzer = WordAnalyzer.create(args[2]);
        long start = System.nanoTime();
        var result = analyzer.analyze(directory, threads);
        double seconds = (System.nanoTime() - start) / 1_000_000_000.0;
        String check = AnalysisSupport.difference(expected, result);
        System.out.printf(java.util.Locale.ROOT, "%s threads=%d seconds=%.6f %s%n", args[2], threads, seconds, check);
        if (!check.equals("OK")) System.exit(2);
    }

    public static void benchmark(String[] args) throws Exception {
        if (args.length != 5) throw new IllegalArgumentException("Usage: Main benchmark directory output warmups repeats threadsCSV");
        Path directory = Path.of(args[0]);
        Path output = Path.of(args[1]);
        int warmups = Integer.parseInt(args[2]);
        int repeats = Integer.parseInt(args[3]);
        if (warmups < 1 || repeats < 3) throw new IllegalArgumentException("Use warmups>=1 repeats>=3");
        int[] threads = Arrays.stream(args[4].split(",")).mapToInt(Integer::parseInt).toArray();
        for (int p : threads) if (p < 1) throw new IllegalArgumentException("Positive threads required");
        Files.createDirectories(output);
        var expected = AnalysisSupport.load(directory);
        Files.writeString(output.resolve("environment.txt"), "java=" + System.getProperty("java.runtime.version")
                + "\nvm=" + System.getProperty("java.vm.name") + "\nos=" + System.getProperty("os.name")
                + "\nprocessors=" + Runtime.getRuntime().availableProcessors() + "\nwarmups=" + warmups
                + "\nrepeats=" + repeats + "\ncorpus=" + directory.toAbsolutePath() + "\n");
        try (var writer = Files.newBufferedWriter(output.resolve("measurements.csv"))) {
            writer.write("variant,threads,phase,run,time_seconds,correct,details\n");
            for (String name : WordAnalyzer.NAMES) {
                int[] counts = name.equals("sequential") ? new int[]{1} : threads;
                for (int p : counts) {
                    var analyzer = WordAnalyzer.create(name);
                    for (int run = -warmups; run < repeats; run++) {
                        Map<String, Long> result = null;
                        String failure = null;
                        long start = System.nanoTime();
                        try { result = analyzer.analyze(directory, p); }
                        catch (java.io.IOException | RuntimeException e) { failure = e.toString(); }
                        double seconds = (System.nanoTime() - start) / 1_000_000_000.0;
                        String detail = failure == null ? AnalysisSupport.difference(expected, result) : "ERROR " + failure;
                        boolean correct = detail.equals("OK");
                        String phase = run < 0 ? "warmup" : "measurement";
                        writer.write(String.format(Locale.ROOT, "%s,%d,%s,%d,%.9f,%s,%s%n",
                                name, p, phase, run < 0 ? run + warmups + 1 : run + 1, seconds, correct,
                                detail.replace(',', ';').replace('\n', ' ')));
                        writer.flush();
                    }
                    System.out.println(directory.getFileName() + " " + name + " p=" + p + " done");
                }
            }
        }
    }
}
