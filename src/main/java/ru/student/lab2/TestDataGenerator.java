package ru.student.lab2;
import java.io.*;
import java.nio.file.*;
import java.util.*;
public final class TestDataGenerator {
    private TestDataGenerator() {}
    public static void main(String[] args) throws IOException {
        if (args.length < 1) throw new IllegalArgumentException("Usage: generator directory [files words vocabulary seed]");
        Path output = Path.of(args[0]);
        int files = args.length > 1 ? Integer.parseInt(args[1]) : 128;
        int words = args.length > 2 ? Integer.parseInt(args[2]) : 250_000;
        int size = args.length > 3 ? Integer.parseInt(args[3]) : 500;
        long seed = args.length > 4 ? Long.parseLong(args[4]) : new java.security.SecureRandom().nextLong();
        if (files < 1 || words < 1 || size < 1) throw new IllegalArgumentException("Positive sizes required");
        if (Files.exists(output)) {
            try (var entries = Files.list(output)) {
                if (entries.findAny().isPresent()) throw new IOException("Use an empty directory; existing corpus is protected");
            }
        }
        Files.createDirectories(output);
        Random random = new Random(seed);
        String[] vocabulary = new String[size];
        double[] weights = new double[size];
        double[] cumulative = new double[size];
        long[] expected = new long[size];
        double total = 0;
        for (int i = 0; i < size; i++) {
            vocabulary[i] = String.format(Locale.ROOT, "word%04d", i);
            weights[i] = 0.05 + random.nextDouble();
            total += weights[i];
        }
        double sum = 0;
        try (var writer = Files.newBufferedWriter(output.resolve("distribution.txt"))) {
            writer.write("word probability\n");
            for (int i = 0; i < size; i++) {
                double probability = weights[i] / total;
                cumulative[i] = (sum += probability);
                writer.write(String.format(Locale.ROOT, "%s %.17g%n", vocabulary[i], probability));
            }
        }
        cumulative[size - 1] = 1.0;
        String[] punctuation = {"", "", "", ".", ",", ";", ":", "!", "?"};
        for (int f = 1; f <= files; f++) {
            try (var writer = Files.newBufferedWriter(output.resolve(String.format(Locale.ROOT, "text-%03d.txt", f)))) {
                for (int n = 0; n < words; n++) {
                    double value = random.nextDouble();
                    int left = 0;
                    int right = size - 1;
                    while (left < right) {
                        int middle = (left + right) >>> 1;
                        if (value < cumulative[middle]) right = middle;
                        else left = middle + 1;
                    }
                    expected[left]++;
                    String word = vocabulary[left];
                    int letterCase = random.nextInt(4);
                    if (letterCase == 0) word = word.toUpperCase(Locale.ROOT);
                    else if (letterCase == 1) word = "W" + word.substring(1);
                    writer.write(word + punctuation[random.nextInt(punctuation.length)]);
                    writer.write((n + 1) % 20 == 0 ? '\n' : ' ');
                }
            }
        }
        try (var writer = Files.newBufferedWriter(output.resolve("expected-statistics.txt"))) {
            writer.write("word count\n");
            for (int i = 0; i < size; i++) if (expected[i] > 0) writer.write(vocabulary[i] + " " + expected[i] + "\n");
        }
        Files.writeString(output.resolve("metadata.txt"), "seed=" + seed + "\nfiles=" + files
                + "\nwords_per_file=" + words + "\nvocabulary=" + size + "\n");
        System.out.println("Generated " + (long) files * words + " words; seed=" + seed);
    }
}
