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
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;

/** Runs the JDK JAR tool with filtered module content. */
final class JarPackager {
    record Result(int exitCode, boolean automatic) {}

    private final ToolServices tools;
    private final List<ToolDefinition> definitions;

    JarPackager(ToolServices tools, List<ToolDefinition> definitions) {
        this.tools = tools;
        this.definitions = List.copyOf(definitions);
    }

    int run(
            List<String> rootModules,
            List<String> compileArguments,
            List<String> resolvedArguments,
            List<String> dependencyArguments,
            List<String> jarArguments,
            Path workingDirectory,
            InputStream in,
            PrintStream out,
            PrintStream err)
            throws IOException {
        if (mutates(jarArguments) && rootModules.size() == 1) {
            var moduleName = rootModules.getFirst();
            var source = ToolArguments.moduleLocation(moduleName, resolvedArguments);
            if (source.isPresent()) {
                try (var content = FilteredModuleContent.prepare(moduleName, compileArguments, resolvedArguments, definitions)) {
                    return packageModule(
                            resolvedArguments,
                            dependencyArguments,
                            replaceDirectory(jarArguments, source.orElseThrow(), content.path(), workingDirectory),
                            in,
                            out,
                            err);
                }
            }
        }
        return packageModule(resolvedArguments, dependencyArguments, jarArguments,
                in, out, err);
    }

    Result createExact(
            String moduleName,
            Path moduleContent,
            List<String> moduleArguments,
            List<String> runtimeArguments,
            Path archive,
            boolean omitJmod,
            InputStream in,
            PrintStream out,
            PrintStream err)
            throws IOException {
        Path parent = archive.toAbsolutePath()
                             .normalize()
                             .getParent();
        if (parent == null) {
            throw new IllegalArgumentException("jar output has no parent: " + archive);
        }
        Files.createDirectories(parent);
        Path staged = Files.createTempFile(parent, ".ja-jar-", ".jar");
        try {
            Files.delete(staged);
            var arguments = new ArrayList<String>();
            arguments.add("--create");
            arguments.add("--no-manifest");
            arguments.add("--file");
            arguments.add(staged.toString());
            arguments.addAll(moduleArguments);
            arguments.add("-C");
            arguments.add(moduleContent.toString());
            arguments.add(".");
            int result = tools.run("jar", in, out, err, arguments.toArray(String[]::new));
            if (result != 0) {
                return new Result(result, false);
            }
            var derivedNames = AutomaticModules.findDerivedNames(runtimeArguments);
            if (!derivedNames.isEmpty()) {
                AutomaticModuleArchives.rewrite(staged, moduleName);
                AutomaticModules.warnExport(moduleName, derivedNames, omitJmod, err);
            }
            publish(staged, archive);
            return new Result(0, !derivedNames.isEmpty());
        } finally {
            Files.deleteIfExists(staged);
        }
    }

    private int packageModule(List<String> resolvedArguments, List<String> dependencyArguments, List<String> jarArguments,
            InputStream in, PrintStream out, PrintStream err) {
        if (mutates(jarArguments)) {
            AutomaticModules.warn(dependencyArguments, err);
        }
        return runJar(resolvedArguments, jarArguments, in, out, err);
    }

    private static List<String> replaceDirectory(List<String> arguments, Path source, Path replacement,
            Path workingDirectory) {
        var replaced = new ArrayList<>(arguments);
        Path expected = source.toAbsolutePath().normalize();
        for (int i = 0; i + 1 < replaced.size(); i++) {
            if (!replaced.get(i).equals("-C")) {
                continue;
            }
            Path selected = workingDirectory.resolve(replaced.get(i + 1))
                    .toAbsolutePath()
                    .normalize();
            if (selected.equals(expected)) {
                replaced.set(i + 1, replacement.toString());
            }
            i++;
        }
        return List.copyOf(replaced);
    }

    static boolean mutates(List<String> arguments) {
        for (String argument : arguments) {
            if (argument.equals("--create")
                    || argument.equals("-c")
                    || argument.equals("--update")
                    || argument.equals("-u")) {
                return true;
            }
        }
        if (arguments.isEmpty()) {
            return false;
        }
        String legacy = legacyOptions(arguments.getFirst());
        return legacy != null && (legacy.startsWith("c") || legacy.startsWith("u"));
    }

    private int runJar(List<String> resolvedArguments, List<String> jarArguments, InputStream in,
                       PrintStream out, PrintStream err) {
        var arguments = new ArrayList<>(resolvedArguments);
        arguments.addAll(jarArguments);
        return tools.run("jar", in, out, err, arguments.toArray(String[]::new));
    }

    private static String legacyOptions(String argument) {
        String options = argument;
        if (options.startsWith("-") && !options.startsWith("--")) {
            options = options.substring(1);
        }
        return options.matches("[ctxuivfemM0pP]+") ? options : null;
    }

    private static void publish(Path source, Path destination) throws IOException {
        try {
            Files.move(source, destination, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
        } catch (AtomicMoveNotSupportedException _) {
            Files.move(source, destination, StandardCopyOption.REPLACE_EXISTING);
        }
    }
}
