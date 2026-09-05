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

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.PrintStream;
import java.io.PrintWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.spi.ToolProvider;

import com.netflix.tools.ja.CommandRunner;
import com.netflix.tools.ja.JaInvocation;
import com.netflix.tools.ja.ToolCatalog;
import com.netflix.tools.ja.ToolServices;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MavenDeploymentTest {
    @Test
    void deploysAssembledArtifactsToMavenCentral(@TempDir Path directory) throws Exception {
        sourceModule(directory, "com.example.library");
        Path metadata = Files.writeString(directory.resolve("metadata.pom"), "<project/>");
        var deployment = new ArrayList<String>();
        ToolServices tools = tools(directory, deployment);
        var commandLine = JaInvocation.parse(
                directory,
                new String[] {"maven", "deploy-central", "--module-version", "1.0", "--merge-consumer-pom", "metadata.pom",
                        "--name", "Example 1.0", "--manual"});

        int result = run(commandLine, tools);

        assertEquals(0, result);
        assertEquals("maven", deployment.get(0));
        assertEquals("deploy-central", deployment.get(1));
        assertEquals(metadata.toString(), value(deployment, "--merge-consumer-pom"));
        assertEquals("Example 1.0", value(deployment, "--name"));
        assertTrue(deployment.contains("--manual"));
        Path artifacts = Path.of(deployment.getLast());
        assertFalse(Files.exists(artifacts));
    }

    @Test
    void deploysUsingTheConventionalConsumerPom(@TempDir Path directory) throws Exception {
        sourceModule(directory, "com.example.library");
        Path metadata = Files.writeString(directory.resolve("consumer.pom"), "<project/>");
        Path repository = directory.resolve("repository");
        var deployment = new ArrayList<String>();
        ToolServices tools = tools(directory, deployment);
        var commandLine = JaInvocation.parse(
                directory,
                new String[] {"maven", "deploy", "--module-version", "1.0", "--repository",
                        repository.toString(), "--sign"});

        int result = run(commandLine, tools);

        assertEquals(0, result);
        assertEquals(List.of("maven", "deploy"), deployment.subList(0, 2));
        assertEquals(metadata.toString(), value(deployment, "--merge-consumer-pom"));
        assertEquals(repository.toString(), value(deployment, "--repository"));
        assertTrue(deployment.contains("--sign"));
    }

    @Test
    void installsWithoutPublicationMetadata(@TempDir Path directory) throws Exception {
        sourceModule(directory, "com.example.library");
        var deployment = new ArrayList<String>();
        ToolServices tools = tools(directory, deployment);
        var commandLine = JaInvocation.parse(directory, new String[] {"maven", "install", "--module-version", "1.0"});

        int result = run(commandLine, tools);

        assertEquals(0, result);
        assertEquals(List.of("maven", "install"), deployment.subList(0, 2));
        assertFalse(deployment.contains("--merge-consumer-pom"));
    }

    private static ToolServices tools(Path directory, List<String> deployment) throws Exception {
        String moduleName = "com.example.library";
        Path runtimeModule = Files.createDirectories(directory.resolve("runtime")
                .resolve(moduleName));
        TestModules.writeModuleInfo(runtimeModule, moduleName);
        ToolProvider jig = tool("jig",
                arguments -> {
                    if (!arguments.isEmpty() && arguments.getFirst().equals("maven")) {
                        Path artifacts = Path.of(arguments.getLast());
                        assertTrue(Files.isRegularFile(artifacts.resolve(moduleName + ".jar")));
                        assertTrue(Files.isRegularFile(artifacts.resolve(moduleName + "-sources.jar")));
                        assertTrue(Files.isRegularFile(artifacts.resolve(moduleName + "-javadoc.jar")));
                        deployment.addAll(arguments);
                        return 0;
                    }
                    int write = arguments.indexOf("--write-argfile");
                    if (write >= 0) {
                        String options = arguments.get(arguments.indexOf("--resolve-options") + 1);
                        String content;
                        if (options.equals("main-class,module-version")) {
                            content = "--module-version\n1.0\n";
                        } else if (options.contains("module-source-path")) {
                            content = "";
                        } else {
                            content = "--module-path\n" + runtimeModule + "\n";
                        }
                        Files.writeString(Path.of(arguments.get(write + 1)), content);
                    }
                    return 0;
                });
        return ToolServices.of(jig, ToolProvider.findFirst("jar").orElseThrow(),
                tool("javadoc", arguments -> 0));
    }

    private static int run(JaInvocation commandLine, ToolServices tools) throws Exception {
        return new CommandRunner(ModuleLayer.boot(), tools, ToolCatalog.load(ModuleLayer.boot()), () -> null)
                .run(commandLine, InputStream.nullInputStream(), new PrintStream(new ByteArrayOutputStream()),
                        new PrintStream(new ByteArrayOutputStream()));
    }

    private static String value(List<String> arguments, String option) {
        return arguments.get(arguments.indexOf(option) + 1);
    }

    private static void sourceModule(Path directory, String module) throws Exception {
        Path source = Files.createDirectories(directory.resolve("src")
                .resolve(module));
        Files.writeString(source.resolve("module-info.java"), "module " + module + " {}\n");
        Files.writeString(source.resolve("module-info.hash"), "");
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
                    return operation.run(List.of(arguments));
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }
            }
        };
    }

    @FunctionalInterface
    private interface Operation {
        int run(List<String> arguments) throws Exception;
    }
}
