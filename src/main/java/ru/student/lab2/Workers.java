package ru.student.lab2;
import java.io.IOException;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.*;
public final class Workers {
    private Workers() {}
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
