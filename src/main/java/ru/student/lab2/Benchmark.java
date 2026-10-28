package ru.student.lab2;
import java.nio.file.*;
import java.util.*;
public final class Benchmark {
    private Benchmark() {}
    public static void main(String[] args) throws Exception {
        if (args.length != 5) throw new IllegalArgumentException("Usage: Benchmark directory output warmups repeats threadsCSV");
        Path directory = Path.of(args[0]);
        Path output = Path.of(args[1]);
        int warmups = Integer.parseInt(args[2]);
        int repeats = Integer.parseInt(args[3]);
        if (warmups < 1 || repeats < 3) throw new IllegalArgumentException("Use warmups>=1 repeats>=3");
        int[] threads = Arrays.stream(args[4].split(",")).mapToInt(Integer::parseInt).toArray();
        for (int p : threads) if (p < 1) throw new IllegalArgumentException("Positive threads required");
        Files.createDirectories(output);
        var expected = Statistics.load(directory);
        Files.writeString(output.resolve("environment.txt"), "java=" + System.getProperty("java.runtime.version")
                + "\nvm=" + System.getProperty("java.vm.name") + "\nos=" + System.getProperty("os.name")
                + "\nprocessors=" + Runtime.getRuntime().availableProcessors() + "\nwarmups=" + warmups
                + "\nrepeats=" + repeats + "\ncorpus=" + directory.toAbsolutePath() + "\n");
        try (var writer = Files.newBufferedWriter(output.resolve("measurements.csv"))) {
            writer.write("variant,threads,phase,run,time_seconds,correct,details\n");
            for (String name : Analyzers.NAMES) {
                int[] counts = name.equals("sequential") ? new int[]{1} : threads;
                for (int p : counts) {
                    var analyzer = Analyzers.create(name);
                    for (int run = -warmups; run < repeats; run++) {
                        Map<String, Long> result = null;
                        String failure = null;
                        long start = System.nanoTime();
                        try { result = analyzer.analyze(directory, p); }
                        catch (java.io.IOException | RuntimeException e) { failure = e.toString(); }
                        double seconds = (System.nanoTime() - start) / 1_000_000_000.0;
                        String detail = failure == null ? Statistics.difference(expected, result) : "ERROR " + failure;
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
