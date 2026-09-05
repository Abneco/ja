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
import java.io.InputStream;
import java.io.PrintStream;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.FutureTask;

/** Launches the selected Java executable and connects its standard streams. */
final class JavaProcess {
    private JavaProcess() {}

    static int launch(List<String> arguments, InputStream in, PrintStream out,
                      PrintStream err)
            throws IOException {
        String executable = System.getProperty("os.name").startsWith("Windows") ? "java.exe" : "java";
        var command = new ArrayList<String>();
        command.add(Path.of(System.getProperty("java.home"), "bin", executable)
                .toString());
        command.addAll(arguments);
        if (in == System.in && out == System.out && err == System.err) {
            return waitFor(new ProcessBuilder(command)
                    .inheritIO()
                    .start());
        }
        Process process = new ProcessBuilder(command).start();

        FutureTask<Void> output = transfer(process.getInputStream(), out);
        FutureTask<Void> errors = transfer(process.getErrorStream(), err);
        Thread.ofVirtual().start(output);
        Thread.ofVirtual().start(errors);
        Thread input = Thread.ofVirtual().start(() -> {
            try (var childInput = process.getOutputStream()) {
                in.transferTo(childInput);
            } catch (IOException _) {
                // The child may close standard input before it exits.
            }
        });

        try {
            int exitCode = process.waitFor();
            await(output);
            await(errors);
            return exitCode;
        } catch (InterruptedException e) {
            process.destroyForcibly();
            Thread.currentThread().interrupt();
            throw new IOException("Interrupted while waiting for java", e);
        } finally {
            input.interrupt();
        }
    }

    private static int waitFor(Process process) throws IOException {
        try {
            return process.waitFor();
        } catch (InterruptedException e) {
            process.destroyForcibly();
            Thread.currentThread().interrupt();
            throw new IOException("Interrupted while waiting for java", e);
        }
    }

    private static FutureTask<Void> transfer(InputStream input, PrintStream output) {
        return new FutureTask<>(() -> {
            input.transferTo(output);
            output.flush();
            return null;
        });
    }

    private static void await(FutureTask<Void> transfer) throws IOException, InterruptedException {
        try {
            transfer.get();
        } catch (ExecutionException e) {
            if (e.getCause() instanceof IOException io) {
                throw io;
            }
            if (e.getCause() instanceof RuntimeException runtime) {
                throw runtime;
            }
            throw new IOException("Failed to transfer java process output", e.getCause());
        }
    }
}
