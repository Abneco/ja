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

import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import com.netflix.tools.launcher.LauncherConfigurationException;
import com.netflix.tools.launcher.ToolOptions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ToolOptionsTest {
    @Test
    void activatesOptionsNamedForTheTool(@TempDir Path root) throws Exception {
        Path projectOptions = Files.createDirectories(root.resolve(".java-tool-options"));
        Files.writeString(projectOptions.resolve("probe.args"), "--class-path\n\"dependency path\"\n");
        Files.writeString(projectOptions.resolve("other.args"), "other\n");
        Path source = Files.createDirectories(root.resolve("src/main/java"));
        var diagnostics = new StringWriter();

        String[] arguments = ToolOptions.activate("probe", source, new String[] {"explicit"}, new PrintWriter(diagnostics, true));

        assertEquals(List.of("--class-path", "dependency path", "explicit"), List.of(arguments));
        assertTrue(diagnostics.toString().contains("probe: picked up options from .java-tool-options/probe.args"),
                diagnostics.toString());
    }

    @Test
    void ignoresOptionsNamedForAnotherTool(@TempDir Path root) throws Exception {
        Path options = Files.createDirectories(root.resolve(".java-tool-options"));
        Files.writeString(options.resolve("other.args"), "other\n");

        String[] arguments = ToolOptions.activate("probe", root, new String[] {"explicit"}, new PrintWriter(new StringWriter()));

        assertEquals(List.of("explicit"), List.of(arguments));
    }

    @Test
    void rejectsToolNamesThatCouldEscapeTheOptionsDirectory(@TempDir Path root) {
        var failure = assertThrows(LauncherConfigurationException.class,
                () -> ToolOptions.activate("../probe", root, new String[0], new PrintWriter(new StringWriter())));

        assertEquals("Invalid tool name: ../probe", failure.getMessage());
    }

    @Test
    void mirroredScopesSelectEnclosingOptions(@TempDir Path root) throws Exception {
        Path rootOptions = Files.createDirectories(root.resolve(".java-tool-options"));
        Path app = Files.createDirectories(rootOptions.resolve("app"));
        Path main = Files.createDirectories(rootOptions.resolve("app/src/main"));
        Path test = Files.createDirectories(rootOptions.resolve("app/src/test"));
        Files.writeString(app.resolve("probe.args"), "app\n");
        Files.writeString(main.resolve("probe.args"), "main\n");
        Files.writeString(test.resolve("probe.args"), "test\n");
        Path source = Files.createDirectories(root.resolve("app/src/test/java"));

        String[] arguments = ToolOptions.activate("probe", source, new String[0], new PrintWriter(new StringWriter()));

        assertEquals(List.of("test"), List.of(arguments));
    }

    @Test
    void mirroredScopesTakePrecedenceBelowAnExplicitRootDefault(@TempDir Path root) throws Exception {
        Path rootOptions = Files.createDirectories(root.resolve(".java-tool-options"));
        Files.writeString(rootOptions.resolve("probe.args"), "root\n");
        Path main = Files.createDirectories(rootOptions.resolve("app/src/main"));
        Files.writeString(main.resolve("probe.args"), "main\n");
        Path source = Files.createDirectories(root.resolve("app/src/main/java"));

        String[] nested = ToolOptions.activate("probe", source, new String[0], new PrintWriter(new StringWriter()));
        String[] project = ToolOptions.activate("probe", root, new String[0], new PrintWriter(new StringWriter()));

        assertEquals(List.of("main"), List.of(nested));
        assertEquals(List.of("root"), List.of(project));
    }

    @Test
    void mirroredScopesRejectAnAmbiguousAncestor(@TempDir Path root) throws Exception {
        Path rootOptions = Files.createDirectories(root.resolve(".java-tool-options"));
        Files.createDirectories(root.resolve("app/src/main"));
        Files.createDirectories(root.resolve("app/src/test"));
        Files.createDirectories(rootOptions.resolve("app/src/main"));
        Files.createDirectories(rootOptions.resolve("app/src/test"));
        Files.writeString(rootOptions.resolve("app/src/main/probe.args"), "main\n");
        Files.writeString(rootOptions.resolve("app/src/test/probe.args"), "test\n");

        var failure = assertThrows(LauncherConfigurationException.class,
                () -> ToolOptions.activate("probe", root, new String[0], new PrintWriter(new StringWriter())));

        assertTrue(failure.getMessage()
                          .contains("Multiple probe option scopes are contained by"));
        assertTrue(failure.getMessage()
                          .contains("-C app/src/main"));
        assertTrue(failure.getMessage()
                          .contains("-C app/src/test"));
    }

    @Test
    void mirroredScopesSelectTheOnlyContainedScope(@TempDir Path root) throws Exception {
        Path options = Files.createDirectories(root.resolve(".java-tool-options/app/src/main"));
        Files.writeString(options.resolve("probe.args"), "main\n");
        Files.createDirectories(root.resolve("app/src/main"));

        String[] arguments = ToolOptions.activate("probe", root, new String[0], new PrintWriter(new StringWriter()));

        assertEquals(List.of("main"), List.of(arguments));
    }

    @Test
    void passesConfiguredArgumentsWithoutInterpretingThem(@TempDir Path root) throws Exception {
        Path options = Files.createDirectories(root.resolve(".java-tool-options"));
        Files.writeString(options.resolve("probe.args"),
                """
                --class-path
                classes
                --module-path
                modules
                --enable-preview
                """);

        String[] arguments = ToolOptions.activate("probe", root, new String[] {"explicit"}, new PrintWriter(new StringWriter()));

        assertEquals(
                List.of("--class-path", "classes", "--module-path", "modules", "--enable-preview", "explicit"),
                List.of(arguments));
    }

    @Test
    void preservesArgumentsAlreadyExpandedByTheNativeLauncher(@TempDir Path root) {
        String property = "com.netflix.tools.launcher.arguments.expanded";
        String previous = System.setProperty(property, "true");
        try {
            String[] arguments = ToolOptions.activate("probe", root, new String[] {"@nested", "@@literal"},
                    new PrintWriter(new StringWriter()));

            assertEquals(List.of("@nested", "@@literal"), List.of(arguments));
        } finally {
            if (previous == null) {
                System.clearProperty(property);
            } else {
                System.setProperty(property, previous);
            }
        }
    }

    @Test
    void expandsExplicitArgumentFilesWithoutProjectOptions(@TempDir Path root) throws Exception {
        Path arguments = root.resolve("tool.args");
        Files.writeString(arguments, "# comment\n'one value'\n@@literal\n");

        String[] expanded = ToolOptions.activate("probe", root, new String[] {"@" + arguments, "explicit"},
                new PrintWriter(new StringWriter()));

        assertEquals(List.of("one value", "@@literal", "explicit"), List.of(expanded));
    }

    @Test
    void retainsArgumentFileReadFailure(@TempDir Path root) throws Exception {
        Path arguments = Files.createDirectory(root.resolve("invalid.args"));

        var failure = assertThrows(
                LauncherConfigurationException.class,
                () -> ToolOptions.activate("probe", root, new String[] {"@" + arguments},
                        new PrintWriter(new StringWriter())));

        assertInstanceOf(IOException.class, failure.getCause());
    }
}
