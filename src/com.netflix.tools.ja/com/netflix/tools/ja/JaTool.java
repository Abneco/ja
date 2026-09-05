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
import java.io.OutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.Set;
import javax.lang.model.SourceVersion;
import javax.tools.OptionChecker;
import javax.tools.Tool;

import com.netflix.tools.cli.CommandLine;

/** Exposes {@code ja} through the standard tool service interface. */
public final class JaTool implements Tool, OptionChecker {
    @Override
    public String name() {
        return "ja";
    }

    public CommandLine commandLine() {
        return Ja.commandLine();
    }

    @Override
    public int isSupportedOption(String option) {
        return commandLine().isSupportedOption(option);
    }

    @Override
    public int run(InputStream in, OutputStream out, OutputStream err,
                   String... args) {
        var input = in == null ? System.in : in;
        var output = printStream(out, System.out);
        var errors = printStream(err, System.err);
        try {
            return Ja.run(input, output, errors, Path.of(""), args);
        } catch (IOException e) {
            errors.println("ja: " + e.getMessage());
            return 2;
        } finally {
            output.flush();
            errors.flush();
        }
    }

    @Override
    public Set<SourceVersion> getSourceVersions() {
        return Set.of();
    }

    private static PrintStream printStream(OutputStream requested, PrintStream fallback) {
        if (requested == null) {
            return fallback;
        }
        if (requested instanceof PrintStream stream) {
            return stream;
        }
        return new PrintStream(requested, true, StandardCharsets.UTF_8);
    }
}
