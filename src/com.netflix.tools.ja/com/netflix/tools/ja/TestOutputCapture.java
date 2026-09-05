/*
 * Copyright 2026 Netflix, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except
 * in compliance with the License. You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License
 * is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express
 * or implied. See the License for the specific language governing permissions and limitations under
 * the License.
 */

package com.netflix.tools.ja;

import java.io.IOException;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;

import com.netflix.tools.ja.TestResult.Status;

final class TestOutputCapture implements AutoCloseable {
    private static final int MAX_TEST_OUTPUT_BYTES = 8 * 1024;
    private static final String JUNIT_CONSOLE_MODULE = "org.junit.platform.console";
    private static final String CAPTURE_STDOUT = "junit.platform.output.capture.stdout";
    private static final String CAPTURE_STDERR = "junit.platform.output.capture.stderr";
    private static final String CAPTURE_LIMIT = "junit.platform.output.capture.maxBuffer";

    private final TemporaryDirectory directory;
    private final Path outputPath;
    private final Path errorPath;
    private final PrintStream out;
    private final PrintStream err;
    private boolean managedReports;
    private boolean retainDirectory;
    private boolean finished;

    private TestOutputCapture(TemporaryDirectory directory, Path outputPath, Path errorPath,
            PrintStream out, PrintStream err) {
        this.directory = directory;
        this.outputPath = outputPath;
        this.errorPath = errorPath;
        this.out = out;
        this.err = err;
    }

    static TestOutputCapture create() throws IOException {
        var directory = TemporaryDirectory.create();
        try {
            var outputPath = directory.root().resolve("stdout");
            var errorPath = directory.root().resolve("stderr");
            var out = new PrintStream(Files.newOutputStream(outputPath), true, StandardCharsets.UTF_8);
            try {
                var err = new PrintStream(Files.newOutputStream(errorPath), true, StandardCharsets.UTF_8);
                return new TestOutputCapture(directory, outputPath, errorPath, out, err);
            } catch (IOException | RuntimeException | Error failure) {
                out.close();
                throw failure;
            }
        } catch (IOException | RuntimeException | Error failure) {
            try {
                directory.close();
            } catch (IOException closeFailure) {
                failure.addSuppressed(closeFailure);
            }
            throw failure;
        }
    }

    PrintStream out() {
        return out;
    }

    PrintStream err() {
        return err;
    }

    static boolean supportsStructuredOutput(ToolDefinition definition) {
        return definition.provider().equals("junit") && definition.module()
                .filter(JUNIT_CONSOLE_MODULE::equals)
                .isPresent();
    }

    List<String> junitArgumentFile(List<String> arguments) throws IOException {
        var path = directory.root().resolve("tests.args");
        Files.write(path, arguments, StandardCharsets.UTF_8);
        return List.of("@" + path);
    }

    List<String> junitArguments(List<String> definitionDefaults, List<String> arguments) {
        var result = new ArrayList<String>();
        addConfiguration(result, definitionDefaults, arguments, CAPTURE_STDOUT, "true");
        addConfiguration(result, definitionDefaults, arguments, CAPTURE_STDERR, "true");
        addConfiguration(result, definitionDefaults, arguments, CAPTURE_LIMIT, Integer.toString(MAX_TEST_OUTPUT_BYTES));
        if (!hasOption(definitionDefaults, arguments, "--redirect-stdout")) {
            result.add("--redirect-stdout=" + directory.root().resolve("test-stdout"));
        }
        if (!hasOption(definitionDefaults, arguments, "--redirect-stderr")) {
            result.add("--redirect-stderr=" + directory.root().resolve("test-stderr"));
        }
        managedReports = !hasOption(definitionDefaults, arguments, "--reports-dir");
        if (managedReports) {
            result.add("--reports-dir=" + directory.root().resolve("reports"));
        }
        result.addAll(arguments);
        return List.copyOf(result);
    }

    Map<String, Status> testResults(Collection<String> selected) throws IOException {
        finish();
        if (!managedReports) {
            return Map.of();
        }
        return JUnitTestResults.read(directory.root()
                .resolve("reports"),
                selected);
    }

    void replay(PrintStream output, PrintStream error) throws IOException {
        finish();
        replayRunner(output, error);
    }

    void replaySummary(PrintStream output, PrintStream error, int cachedContainers,
                       int cachedTests)
            throws IOException {
        finish();
        output.print(JUnitTestSummary.compact(Files.readString(outputPath, StandardCharsets.UTF_8), cachedContainers, cachedTests));
        output.flush();
        Files.copy(errorPath, error);
        error.flush();
    }

    void replayFailure(PrintStream output, PrintStream error, int cachedContainers,
                       int cachedTests)
            throws IOException {
        finish();
        replaySummary(output, error, cachedContainers, cachedTests);
        if (managedReports) {
            retainReports(error);
        }
    }

    private void replayRunner(PrintStream output, PrintStream error) throws IOException {
        Files.copy(outputPath, output);
        output.flush();
        Files.copy(errorPath, error);
        error.flush();
    }

    private void retainReports(PrintStream error) throws IOException {
        var reports = directory.root().resolve("reports");
        if (!Files.isDirectory(reports)) {
            return;
        }
        List<Path> reportFiles;
        try (var files = Files.list(reports)) {
            reportFiles = files.filter(Files::isRegularFile)
                               .sorted()
                               .toList();
        }
        if (reportFiles.isEmpty()) {
            return;
        }
        retainDirectory = true;
        if (Files.size(outputPath) > 0 || Files.size(errorPath) > 0) {
            error.println();
        }
        error.println(reportFiles.size() == 1 ? "Test report:" : "Test reports:");
        for (Path report : reportFiles) {
            error.println("  " + report.toUri());
        }
        error.flush();
    }

    private static void addConfiguration(List<String> result, List<String> definitionDefaults, List<String> arguments,
            String key, String value) {
        if (!hasConfiguration(definitionDefaults, key) && !hasConfiguration(arguments, key)) {
            result.add("--config=" + key + "=" + value);
        }
    }

    private static boolean hasConfiguration(List<String> arguments, String key) {
        for (int i = 0; i < arguments.size(); i++) {
            var argument = arguments.get(i);
            if (argument.startsWith("--config=" + key + "=")) {
                return true;
            }
            if (argument.equals("--config") && i + 1 < arguments.size() && arguments.get(i + 1).startsWith(key + "=")) {
                return true;
            }
        }
        return false;
    }

    private static boolean hasOption(List<String> definitionDefaults, List<String> arguments, String option) {
        return hasOption(definitionDefaults, option) || hasOption(arguments, option);
    }

    private static boolean hasOption(List<String> arguments, String option) {
        return arguments.stream().anyMatch(argument -> argument.equals(option) || argument.startsWith(option + "="));
    }

    private void finish() throws IOException {
        if (finished) {
            return;
        }
        boolean failed = out.checkError() | err.checkError();
        out.close();
        err.close();
        finished = true;
        if (failed) {
            throw new IOException("Failed to capture test output");
        }
    }

    @Override
    public void close() throws IOException {
        try {
            finish();
        } finally {
            if (!retainDirectory) {
                directory.close();
            }
        }
    }
}
