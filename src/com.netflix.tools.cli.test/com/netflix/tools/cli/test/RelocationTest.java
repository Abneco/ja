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

package com.netflix.tools.cli.test;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.regex.Pattern;
import java.util.spi.ToolProvider;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RelocationTest {
    @Test
    void compilesAfterChangingOnlyThePackageDeclaration(@TempDir Path directory) throws Exception {
        Path source = sourceFile();
        String canonical = Files.readString(source);
        String declaration = "package com.netflix.tools.cli;";
        assertEquals(1, canonical.split(Pattern.quote(declaration), -1).length - 1);
        String relocated = canonical.replace(declaration, "package example.internal.cli;");
        assertTrue(!canonical.equals(relocated));

        Path module = directory.resolve("src/example.cli");
        Path packageDirectory = module.resolve("example/internal/cli");
        Files.createDirectories(packageDirectory);
        Files.writeString(packageDirectory.resolve("CommandLine.java"), relocated);
        Files.writeString(module.resolve("module-info.java"),
                """
                module example.cli {
                    requires java.compiler;
                    exports example.internal.cli;
                }
                """);

        Path output = directory.resolve("modules");
        var diagnostics = new StringWriter();
        int result = ToolProvider.findFirst("javac")
                .orElseThrow()
                .run(
                        new PrintWriter(diagnostics, true),
                        new PrintWriter(diagnostics, true),
                        "--module-source-path",
                        directory.resolve("src").toString(),
                        "-d",
                        output.toString(),
                        "-m",
                        "example.cli");

        assertEquals(0, result, diagnostics.toString());
        assertTrue(Files.isRegularFile(output.resolve("example.cli/example/internal/cli/CommandLine.class")));
    }

    private static Path sourceFile() {
        Path directory = Path.of("")
                .toAbsolutePath()
                .normalize();
        while (directory != null) {
            Path source = directory.resolve("src/com.netflix.tools.cli/com/netflix/tools/cli/CommandLine.java");
            if (Files.isRegularFile(source)) {
                return source;
            }
            directory = directory.getParent();
        }
        throw new IllegalStateException("Cannot locate CommandLine.java");
    }
}
