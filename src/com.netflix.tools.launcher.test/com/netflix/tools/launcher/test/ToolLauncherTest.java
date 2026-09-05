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

package com.netflix.tools.launcher.test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.function.Supplier;
import java.util.spi.ToolProvider;
import javax.lang.model.SourceVersion;
import javax.tools.Tool;

import com.netflix.tools.launcher.ToolLauncher;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ToolLauncherTest {

    @Test
    void runsSelectedProviderWithUserArguments() {
        var calls = new ArrayList<List<String>>();
        var provider = provider("probe", calls, 0);
        var out = new StringWriter();
        var err = new StringWriter();

        int result = ToolLauncher.run(
                "probe",
                new String[] {"one", "two"},
                new PrintWriter(out),
                new PrintWriter(err),
                false,
                _ -> Optional.empty(),
                () -> provider);

        assertEquals(0, result);
        assertEquals(List.of(List.of("one", "two")), calls);
    }

    @Test
    void trainingRunsOnlyTheDeclaredWarmupArgument() {
        var calls = new ArrayList<List<String>>();
        Supplier<ToolProvider> provider = () -> provider("probe", calls, 0);

        int result = ToolLauncher.run(
                "probe",
                new String[] {"real"},
                new PrintWriter(new StringWriter()),
                new PrintWriter(new StringWriter()),
                true,
                _ -> Optional.of("--warmup"),
                provider);

        assertEquals(0, result);
        assertEquals(List.of(List.of("--warmup")), calls);
    }

    @Test
    void trainingRequiresADeclaredWarmupArgument() {
        var calls = new ArrayList<List<String>>();
        var diagnostics = new StringWriter();

        int result = ToolLauncher.run(
                "probe",
                new String[] {"real"},
                new PrintWriter(new StringWriter()),
                new PrintWriter(diagnostics),
                true,
                _ -> Optional.empty(),
                () -> provider("probe", calls, 0));

        assertEquals(2, result);
        assertEquals(List.of(), calls);
        Assertions.assertTrue(diagnostics.toString()
                .contains("probe does not declare a warmup argument"));
    }

    @Test
    void compilerToolReceivesStandardInput() {
        var input = new ByteArrayInputStream("input".getBytes());
        var output = new ByteArrayOutputStream();
        Tool tool = new Tool() {
            @Override
            public String name() {
                return "probe";
            }

            @Override
            public int run(InputStream in, OutputStream out, OutputStream err,
                           String... arguments) {
                try {
                    out.write(in.readAllBytes());
                    return 7;
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
            }

            @Override
            public Set<SourceVersion> getSourceVersions() {
                return Set.of();
            }
        };

        int result = ToolLauncher.runTool(
                "probe",
                new String[] {"argument"},
                input,
                output,
                new ByteArrayOutputStream(),
                false,
                _ -> Optional.empty(),
                () -> tool);

        assertEquals(7, result);
        assertEquals("input", output.toString());
    }

    private static ToolProvider provider(String name, List<List<String>> calls, int result) {
        return new ToolProvider() {
            @Override
            public String name() {
                return name;
            }

            @Override
            public int run(PrintWriter out, PrintWriter err, String... args) {
                calls.add(List.of(args));
                return result;
            }
        };
    }
}
