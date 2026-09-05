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

import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import com.netflix.tools.launcher.CommandLine;
import com.netflix.tools.launcher.CommandLine.Cardinality;
import com.netflix.tools.launcher.ToolInvocation;
import com.netflix.tools.launcher.ToolOption;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CommandLineTest {
    private static final ToolOption CHECK = ToolOption.builder("--check")
            .description("Check without making changes")
            .build();
    private static final ToolOption OUTPUT = ToolOption.builder("--output")
            .alias("-o")
            .argument("PATH")
            .description("Write output to PATH")
            .build();

    @Test
    void parsesDeclaredOptionsAndOperands() {
        var commandLine = CommandLine.builder()
                .options(CHECK, OUTPUT)
                .operand("FILE", "Input file", Cardinality.ZERO_OR_MORE)
                .build();

        var parsed = commandLine.parse("--check", "-o", "output", "input");

        assertTrue(parsed.contains(CHECK));
        assertEquals(List.of("output"), parsed.values(OUTPUT));
        assertEquals(List.of("input"), parsed.operands());
    }

    @Test
    void parsesAttachedOptionArguments() {
        var commandLine = CommandLine.builder()
                .option(OUTPUT)
                .build();

        var parsed = commandLine.parse("--output=output");

        assertEquals(List.of("output"), parsed.values(OUTPUT));
    }

    @Test
    void rejectsUnknownAndIncompleteOptions() {
        var commandLine = CommandLine.builder()
                .option(OUTPUT)
                .build();

        assertEquals("Unknown option: --other", assertThrows(IllegalArgumentException.class, () -> commandLine.parse("--other")).getMessage());
        assertEquals("--output requires PATH", assertThrows(IllegalArgumentException.class, () -> commandLine.parse("--output")).getMessage());
    }

    @Test
    void derivesOptionCheckerFromAliases() {
        var commandLine = CommandLine.builder()
                .options(CHECK, OUTPUT)
                .build();

        assertEquals(0, commandLine.isSupportedOption("--check"));
        assertEquals(1, commandLine.isSupportedOption("--output"));
        assertEquals(1, commandLine.isSupportedOption("-o"));
        assertEquals(-1, commandLine.isSupportedOption("--other"));
    }

    @Test
    void rendersHelpUsingTheInvocationName() {
        var commandLine = CommandLine.builder()
                .description("Process files")
                .options(CHECK, OUTPUT)
                .operand("FILE", "Input file", Cardinality.ZERO_OR_MORE)
                .build();

        String help = commandLine.help("probe");

        assertTrue(help.contains("Usage: probe [OPTIONS] [FILE...]"), help);
        assertTrue(help.contains("Process files"), help);
        assertTrue(help.contains("--check"), help);
        assertTrue(help.contains("-o, --output <PATH>"), help);
        assertTrue(help.contains("Input file"), help);
    }

    @Test
    void parsesOptionalAttachedArgumentsWithoutConsumingAnOperand() {
        var completion = ToolOption.builder("--completion")
                .optionalArgument("SHELL")
                .choices("bash", "zsh", "fish", "powershell")
                .build();
        var commandLine = CommandLine.builder()
                .option(completion)
                .operand("VALUE", "A value", Cardinality.ZERO_OR_ONE)
                .build();

        var detected = commandLine.parse("--completion");
        var selected = commandLine.parse("--completion=powershell");
        var operand = commandLine.parse("--completion", "symbol");

        assertTrue(detected.contains(completion));
        assertEquals(List.of(), detected.values(completion));
        assertEquals(List.of("powershell"), selected.values(completion));
        assertEquals(List.of("symbol"), operand.operands());
        assertEquals(0, commandLine.isSupportedOption("--completion"));
        assertTrue(commandLine.help("probe")
                              .contains("--completion[=<SHELL>]"));
        assertEquals(List.of("--completion=bash", "--completion=fish", "--completion=powershell", "--completion=zsh"),
                commandLine.complete(List.of("--completion=")).stream()
                        .map(candidate -> candidate.value())
                        .toList());
    }

    @Test
    void workingDirectoryConventionIsOptIn() {
        var plain = CommandLine.builder().build();
        var enabled = CommandLine.builder()
                .workingDirectory()
                .build();
        var invocation = new ToolInvocation(Path.of("project"), List.of("-C", "app", "argument"));

        assertEquals(invocation, plain.prepare(invocation));
        assertEquals(new ToolInvocation(Path.of("project/app"), List.of("argument")),
                enabled.prepare(invocation));
        assertEquals(1, enabled.isSupportedOption("-C"));
        assertEquals(-1, plain.isSupportedOption("-C"));
    }

    @Test
    void workingDirectoryConventionHonorsTheOptionSeparator() {
        var commandLine = CommandLine.builder()
                .workingDirectory()
                .remainder("ARGUMENTS", "Tool arguments")
                .build();
        var invocation = ToolInvocation.of("--", "-C", "tool-directory");

        assertEquals(new ToolInvocation(Path.of(""), List.of("-C", "tool-directory")),
                commandLine.prepare(invocation));
    }

    @Test
    void argumentFilesAreOptIn(@TempDir Path directory) throws Exception {
        Path arguments = directory.resolve("arguments.txt");
        Files.writeString(arguments, "--check 'source file'");
        var invocation = ToolInvocation.of("@" + arguments);

        assertEquals(invocation,
                CommandLine.builder()
                        .build()
                        .prepare(invocation));
        assertEquals(ToolInvocation.of("--check", "source file"),
                CommandLine.builder()
                        .argumentFiles()
                        .build()
                        .prepare(invocation));
    }

    @Test
    void javaToolOptionsAreOptInAndUseTheInvocationDirectory(@TempDir Path directory) throws Exception {
        Path options = Files.createDirectories(directory.resolve(".java-tool-options"));
        Files.writeString(options.resolve("probe.args"), "--check\n");
        var diagnostics = new StringWriter();
        var invocation = new ToolInvocation(directory, List.of("explicit"));

        assertEquals(invocation,
                CommandLine.builder()
                        .build()
                        .prepare("probe", invocation, new PrintWriter(diagnostics, true)));
        assertEquals(new ToolInvocation(directory, List.of("--check", "explicit")),
                CommandLine.builder()
                        .javaToolOptions()
                        .build()
                        .prepare("probe", invocation, new PrintWriter(diagnostics, true)));
        assertTrue(diagnostics.toString().contains("picked up options"),
                diagnostics.toString());
    }

    @Test
    void acceptsOptionsAtTheStartOfAnUnparsedRemainder() {
        var commandLine = CommandLine.builder()
                .option(CHECK)
                .remainder("TOOL-ARGUMENTS", "Arguments passed to another tool")
                .build();

        var parsed = commandLine.parse("--other", "value", "--check");

        assertEquals(List.of("--other", "value", "--check"), parsed.operands());
    }

    @Test
    void acceptsAnUnparsedCommandRemainder() {
        var commandLine = CommandLine.builder()
                .option(CHECK)
                .remainder("COMMAND-ARGUMENTS", "A command followed by its arguments")
                .build();

        var parsed = commandLine.parse("--check", "compile", "--recompile");

        assertTrue(parsed.contains(CHECK));
        assertEquals(List.of("compile", "--recompile"), parsed.operands());
        assertEquals(List.of(), commandLine.complete(List.of("compile", "--rec")));
    }

    @Test
    void describesParsesAndCompletesSubcommands() {
        var compile = CommandLine.builder()
                .option(CHECK)
                .build();
        var commandLine = CommandLine.builder()
                .option(OUTPUT)
                .command("compile", "Compile source", compile)
                .command("run", "Run an application",
                        CommandLine.builder().build())
                .build();

        var parsed = commandLine.parse("--output", "classes", "compile", "--check");
        var selected = parsed.command().orElseThrow();

        assertEquals("compile", selected.name());
        assertTrue(selected.arguments()
                           .contains(CHECK));
        assertTrue(commandLine.help("probe")
                              .contains("compile  Compile source"));
        assertEquals(List.of("compile"),
                commandLine.complete(List.of("co")).stream()
                        .map(completion -> completion.value())
                        .toList());
        assertEquals(List.of("--check"),
                commandLine.complete(List.of("compile", "--c")).stream()
                        .map(completion -> completion.value())
                        .toList());
    }

    @Test
    void rejectsMissingAndUnknownSubcommands() {
        var commandLine = CommandLine.builder()
                .command("compile", "Compile source",
                        CommandLine.builder().build())
                .build();

        assertEquals("Missing command", assertThrows(IllegalArgumentException.class, commandLine::parse).getMessage());
        assertEquals("Unknown command: other", assertThrows(IllegalArgumentException.class, () -> commandLine.parse("other")).getMessage());
    }

    @Test
    void completesOptionNames() {
        var commandLine = CommandLine.builder()
                .options(CHECK, OUTPUT)
                .build();

        assertEquals(List.of("--check", "--output"),
                commandLine.complete(List.of("--")).stream()
                        .map(completion -> completion.value())
                        .toList());
        assertEquals(List.of("--output"),
                commandLine.complete(List.of("--o")).stream()
                        .map(completion -> completion.value())
                        .toList());
        assertEquals(List.of(), commandLine.complete(List.of("--output", "--")));
    }
}
