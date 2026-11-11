"""Full-size experiments; isolate each configuration to contain unsafe HashMap hangs.

Run after build.ps1 with Python 3. The generated Java worker stays under target/.
Uses the unchanged analyzers, parser and reference checker from src/main/java.
"""
from pathlib import Path
import argparse
import csv
import json
import os
import shutil
import subprocess
import time

ROOT = Path(__file__).resolve().parents[1]
OUT = ROOT / 'results/full-isolated-2026-11-11'
TEMP = ROOT / 'target/full-runner'
WARMUPS, REPEATS, TIMEOUT = 3, 5, 30
NAMES = ['sequential', 'hashmap', 'synchronized-map', 'concurrent-get-put',
         'concurrent-merge', 'long-adder', 'local-maps']
CORPORA = {'small': 'large', 'medium': 'full-medium', 'large': 'full-large'}
FIELDS = ['variant', 'threads', 'phase', 'run', 'time_seconds', 'correct', 'details']
JAVA = r'C:\Program Files\Java\jdk-23\bin\java.exe'
JAVAC = r'C:\Program Files\Java\jdk-23\bin\javac.exe'
JCMD = r'C:\Program Files\Java\jdk-23\bin\jcmd.exe'
if not Path(JAVA).exists():
    JAVA, JAVAC, JCMD = shutil.which('java'), shutil.which('javac'), shutil.which('jcmd')

WORKER = r'''
import ru.student.lab2.*;
import java.nio.file.*;
import java.util.*;
import java.nio.file.StandardOpenOption;
public final class BenchmarkWorker {
    public static void main(String[] args) throws Exception {
        Path directory = Path.of(args[0]);
        int threads = Integer.parseInt(args[1]);
        String name = args[2];
        Path output = Path.of(args[3]);
        int first = Integer.parseInt(args[4]);
        int warmups = Integer.parseInt(args[5]);
        int repeats = Integer.parseInt(args[6]);
        var expected = AnalysisSupport.load(directory);
        var analyzer = WordAnalyzer.create(name);
        try (var writer = Files.newBufferedWriter(output, StandardOpenOption.CREATE, StandardOpenOption.APPEND)) {
            for (int index = first; index < warmups + repeats; index++) {
                Map<String, Long> result = null;
                String failure = null;
                long start = System.nanoTime();
                try { result = analyzer.analyze(directory, threads); }
                catch (java.io.IOException | RuntimeException e) { failure = e.toString(); }
                double seconds = (System.nanoTime() - start) / 1_000_000_000.0;
                String detail = failure == null ? AnalysisSupport.difference(expected, result) : "ERROR " + failure;
                String phase = index < warmups ? "warmup" : "measurement";
                int run = index < warmups ? index + 1 : index - warmups + 1;
                writer.write(String.format(Locale.ROOT, "%s,%d,%s,%d,%.9f,%s,%s%n", name, threads,
                        phase, run, seconds, detail.equals("OK"), detail.replace(',', ';').replace('\n', ' ')));
                writer.flush();
            }
        }
    }
}
'''


def read_rows(file):
    with file.open(encoding='utf-8', newline='') as stream:
        return list(csv.DictReader(stream))


def main():
    global OUT, TIMEOUT
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--output', default='results/full-isolated-2026-11-11', help='New result directory; must not already exist.')
    parser.add_argument('--timeout', type=int, default=30, help='External limit in seconds without a completed run.')
    options = parser.parse_args()
    OUT = (ROOT / options.output).resolve()
    TIMEOUT = options.timeout
    if TIMEOUT < 1:
        raise SystemExit('Timeout must be positive')
    if OUT.exists():
        raise SystemExit(f'Refusing to overwrite an existing series: {OUT}')
    TEMP.mkdir(parents=True, exist_ok=True)
    (TEMP / 'BenchmarkWorker.java').write_text(WORKER, encoding='utf-8')
    sources = [str(p.relative_to(ROOT)) for p in (ROOT / 'src/main/java').rglob('*.java')]
    subprocess.run([JAVAC, '--release', '21', '-encoding', 'UTF-8',
                    '-d', 'target/full-runner', 'target/full-runner/BenchmarkWorker.java', *sources], cwd=ROOT, check=True)
    OUT.mkdir(parents=True)
    cp = 'target/full-runner'
    protocol = {'warmups': WARMUPS, 'repeats': REPEATS, 'timeout_seconds': TIMEOUT,
                'isolation': 'One fresh JVM per algorithm/thread configuration.',
                'timeout_policy': 'Save thread dump, kill hung JVM, mark missing result TIMEOUT with blank time, continue remaining runs in a fresh JVM. Only unsafe variants required such recovery.',
                'time_scope': 'Same analyze call as Main.benchmark. JVM startup, reference loading, validation and CSV output are excluded.',
                'corpora': CORPORA, 'java': JAVA,
                'source_commit': subprocess.check_output(['git', 'rev-parse', 'HEAD'], cwd=ROOT).decode().strip()}
    (OUT / 'protocol.json').write_text(json.dumps(protocol, indent=2, ensure_ascii=False), encoding='utf-8')
    version = subprocess.run([JAVA, '-version'], capture_output=True, text=True)
    for corpus, source in CORPORA.items():
        folder = OUT / corpus
        folder.mkdir()
        (folder / 'environment.txt').write_text(version.stderr + '\nwarmups=3\nrepeats=5\n'
                  'isolation=fresh JVM per configuration\n-Xms256m -Xmx1g\ncorpus=' + str(ROOT / 'generated-data' / source) + '\n', encoding='utf-8')
        combined = folder / 'measurements.csv'
        with combined.open('w', encoding='utf-8', newline='') as stream:
            writer = csv.DictWriter(stream, fieldnames=FIELDS)
            writer.writeheader()
            stream.flush()
            for name in NAMES:
                for threads in ([1] if name == 'sequential' else [1, 2, 4, 8, 16, 32, 64]):
                    part = folder / f'{name}-{threads}.csv'
                    with part.open('w', encoding='utf-8', newline='') as pf:
                        csv.writer(pf).writerow(FIELDS)
                    done = 0
                    while done < WARMUPS + REPEATS:
                        command = [JAVA, '-Xms256m', '-Xmx1g', '-cp', cp, 'BenchmarkWorker',
                                   str(Path('generated-data') / source), str(threads), name,
                                   str(part.relative_to(ROOT)), str(done), str(WARMUPS), str(REPEATS)]
                        with (folder / f'{name}-{threads}.log').open('a', encoding='utf-8') as log:
                            proc = subprocess.Popen(command, cwd=ROOT, stdout=log, stderr=log)
                            last_progress = time.monotonic()
                            while proc.poll() is None:
                                count = len(read_rows(part))
                                if count > done:
                                    done = count
                                    last_progress = time.monotonic()
                                if time.monotonic() - last_progress > TIMEOUT:
                                    if JCMD:
                                        dump = subprocess.run([JCMD, str(proc.pid), 'Thread.print'], capture_output=True, text=True, timeout=10)
                                        (folder / f'{name}-{threads}-hang-{done}.txt').write_text(dump.stdout, encoding='utf-8')
                                    proc.kill()
                                    proc.wait()
                                    break
                                time.sleep(0.25)
                            done = len(read_rows(part))
                            if done < WARMUPS + REPEATS:
                                record = dict(variant=name, threads=threads,
                                    phase='warmup' if done < WARMUPS else 'measurement',
                                    run=done + 1 if done < WARMUPS else done - WARMUPS + 1,
                                    time_seconds='', correct='false',
                                    details=f'TIMEOUT >{TIMEOUT}s; no completed result' if proc.returncode != 0 and time.monotonic()-last_progress >= TIMEOUT else f'ERROR worker exit={proc.returncode}; no completed result')
                                with part.open('a', encoding='utf-8', newline='') as pf:
                                    csv.DictWriter(pf, fieldnames=FIELDS).writerow(record)
                                print(f'{corpus} {name} p={threads} {record["phase"]} {record["run"]}: {record["details"]}', flush=True)
                                done += 1
                    rows = read_rows(part)
                    assert len(rows) == WARMUPS + REPEATS
                    writer.writerows(rows)
                    stream.flush()
                    bad = sum(r['correct'] != 'true' for r in rows)
                    print(f'{corpus} {name} p={threads} done; incorrect={bad}/8', flush=True)
                    # Per-configuration CSV/log are recovery evidence; keep them separate from combined raw rows.
    print('FULL ISOLATED SERIES COMPLETE', flush=True)


if __name__ == '__main__':
    main()
