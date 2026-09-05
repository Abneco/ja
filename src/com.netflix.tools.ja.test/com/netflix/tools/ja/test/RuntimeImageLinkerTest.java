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

package com.netflix.tools.ja.test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.io.PrintWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.spi.ToolProvider;

import com.netflix.tools.ja.RuntimeImageLinker;
import com.netflix.tools.ja.ToolServices;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

class RuntimeImageLinkerTest {
    @TempDir
    Path temporaryDirectory;

    @Test
    void includesTheLinkingJdkModulesAndSourcesWhenRequested() throws Exception {
        Path javaHome = Files.createDirectories(temporaryDirectory.resolve("jdk"));
        Path jmods = Files.createDirectories(javaHome.resolve("jmods"));
        Files.writeString(jmods.resolve("java.base.jmod"), "base");
        Path replacement = Files.createDirectories(temporaryDirectory.resolve("replacement"));
        Path toolJmod = Files.writeString(temporaryDirectory.resolve("com.example.tool.jmod"), "tool");
        Path sources = Files.createDirectories(javaHome.resolve("lib")).resolve("src.zip");
        Files.writeString(sources, "sources");
        Path output = temporaryDirectory.resolve("image");
        var jlinkArguments = new ArrayList<String>();
        ToolServices tools = ToolServices.of(tool("jlink",
                (out, arguments) -> {
                    jlinkArguments.addAll(arguments);
                    Files.createDirectories(output.resolve("lib"));
                    return 0;
                }));

        int result = new RuntimeImageLinker(tools, javaHome).link(
                List.of("--upgrade-module-path", replacement.toString(), "--module-path", toolJmod.toString(),
                        "--add-modules", "ALL-MODULE-PATH", "--output", output.toString()),
                true,
                new ByteArrayInputStream(new byte[0]),
                new PrintStream(new ByteArrayOutputStream()),
                new PrintStream(new ByteArrayOutputStream()));

        assertEquals(0, result);
        assertEquals(1,
                jlinkArguments.stream()
                        .filter(argument -> argument.equals("--module-path"))
                        .count());
        assertEquals(
                replacement
                        + System.getProperty("path.separator")
                        + jmods
                        + System.getProperty("path.separator")
                        + toolJmod,
                jlinkArguments.get(jlinkArguments.indexOf("--module-path") + 1));
        assertFalse(jlinkArguments.contains("--upgrade-module-path"));
        assertFalse(jlinkArguments.contains("--release-info"));
        assertEquals("sources", Files.readString(output.resolve("lib/src.zip")));
        assertEquals("base", Files.readString(output.resolve("jmods/java.base.jmod")));
        assertEquals("tool", Files.readString(output.resolve("jmods/com.example.tool.jmod")));
    }

    @Test
    void includesOpenJ9SharedClassesWhenAvailable() throws Exception {
        Path javaHome = Files.createDirectories(temporaryDirectory.resolve("jdk"));
        Path jmods = Files.createDirectories(javaHome.resolve("jmods"));
        Files.writeString(jmods.resolve("java.base.jmod"), "base");
        Files.writeString(jmods.resolve("openj9.sharedclasses.jmod"), "shared classes");
        var jlinkArguments = new ArrayList<String>();
        ToolServices tools = ToolServices.of(tool("jlink",
                (out, arguments) -> {
                    jlinkArguments.addAll(arguments);
                    return 0;
                }));

        int result = new RuntimeImageLinker(tools, javaHome).link(List.of("--add-modules", "java.base", "--output", "image"), false, new ByteArrayInputStream(new byte[0]),
                new PrintStream(new ByteArrayOutputStream()), new PrintStream(new ByteArrayOutputStream()));

        assertEquals(0, result);
        int moduleOption = jlinkArguments.lastIndexOf("--add-modules");
        assertEquals("openj9.sharedclasses", jlinkArguments.get(moduleOption + 1));
    }

    @Test
    void omitsSourcesUnlessRequested() throws Exception {
        Path javaHome = Files.createDirectories(temporaryDirectory.resolve("jdk"));
        Path jmods = Files.createDirectories(javaHome.resolve("jmods"));
        Files.writeString(jmods.resolve("java.base.jmod"), "base");
        Files.createDirectories(javaHome.resolve("lib"));
        Path output = temporaryDirectory.resolve("image");
        ToolServices tools = ToolServices.of(tool("jlink",
                (out, arguments) -> {
                    Files.createDirectories(output.resolve("lib"));
                    return 0;
                }));

        int result = new RuntimeImageLinker(tools, javaHome).link(List.of("--output", output.toString()), false, new ByteArrayInputStream(new byte[0]),
                new PrintStream(new ByteArrayOutputStream()), new PrintStream(new ByteArrayOutputStream()));

        assertEquals(0, result);
        assertFalse(Files.exists(output.resolve("lib/src.zip")));
    }

    @Test
    void rejectsAnAutomaticModuleBeforeLinking() throws Exception {
        Path javaHome = Files.createDirectories(temporaryDirectory.resolve("jdk"));
        Path jmods = Files.createDirectories(javaHome.resolve("jmods"));
        Files.writeString(jmods.resolve("java.base.jmod"), "base");
        Path automatic = TestModules.writeAutomaticJar(temporaryDirectory.resolve("com.example.library-1.2.3.jar"));
        var ran = new AtomicBoolean();
        ToolServices tools = ToolServices.of(tool("jlink",
                (out, arguments) -> {
                    ran.set(true);
                    return 0;
                }));

        IllegalArgumentException failure = assertThrows(
                IllegalArgumentException.class,
                () -> new RuntimeImageLinker(tools, javaHome).link(List.of("--module-path", automatic.toString()), false, new ByteArrayInputStream(new byte[0]),
                        new PrintStream(new ByteArrayOutputStream()), new PrintStream(new ByteArrayOutputStream())));

        assertEquals(
                """
                Automatic modules cannot be linked into runtime images:
                  com.example.library
                """
                        .stripTrailing(),
                failure.getMessage());
        assertFalse(ran.get());
    }

    @Test
    void rejectsMissingRequestedSourcesBeforeLinking() throws Exception {
        Path javaHome = Files.createDirectories(temporaryDirectory.resolve("jdk"));
        Path jmods = Files.createDirectories(javaHome.resolve("jmods"));
        Files.writeString(jmods.resolve("java.base.jmod"), "base");
        Path output = temporaryDirectory.resolve("image");
        var ran = new AtomicBoolean();
        ToolServices tools = ToolServices.of(tool("jlink",
                (out, arguments) -> {
                    ran.set(true);
                    return 0;
                }));

        IllegalArgumentException failure = assertThrows(
                IllegalArgumentException.class,
                () -> new RuntimeImageLinker(tools, javaHome).link(List.of("--output", output.toString()), true, new ByteArrayInputStream(new byte[0]),
                        new PrintStream(new ByteArrayOutputStream()), new PrintStream(new ByteArrayOutputStream())));

        assertEquals("JDK source archive is not present: " + javaHome.resolve("lib/src.zip"), failure.getMessage());
        assertFalse(ran.get());
    }

    private static ToolProvider tool(String name, Operation operation) {
        return new ToolProvider() {
            @Override
            public String name() {
                return name;
            }

            @Override
            public int run(PrintWriter out, PrintWriter err, String... arguments) {
                try {
                    return operation.run(out, List.of(arguments));
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }
            }
        };
    }

    @FunctionalInterface
    private interface Operation {
        int run(PrintWriter output, List<String> arguments) throws Exception;
    }
}
