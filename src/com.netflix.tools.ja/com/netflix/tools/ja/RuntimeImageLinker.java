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
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;

/**
 * Runs {@code jlink} with the selected JDK modules and retains packaged modules
 * needed after linking.
 */
public final class RuntimeImageLinker {
    private final ToolServices tools;
    private final Path javaHome;

    public RuntimeImageLinker(ToolServices tools, Path javaHome) {
        this.tools = tools;
        this.javaHome = javaHome.toAbsolutePath().normalize();
    }

    public int link(List<String> arguments, boolean includeSources, InputStream in,
                    PrintStream out, PrintStream err)
            throws IOException {
        if (!tools.contains("jlink")) {
            throw new IllegalArgumentException("Tool jlink is not installed");
        }
        Path jmods = javaHome.resolve("jmods");
        if (!Files.isDirectory(jmods) || !Files.isRegularFile(jmods.resolve("java.base.jmod"))) {
            throw new IllegalArgumentException("JDK has no linkable modules: " + javaHome);
        }

        Path sources = javaHome.resolve("lib/src.zip");
        Path output = null;
        if (includeSources) {
            if (!Files.isRegularFile(sources)) {
                throw new IllegalArgumentException("JDK source archive is not present: " + sources);
            }
            output = output(arguments);
        }

        AutomaticModules.requireLinkable(arguments);
        var jlinkArguments = withJdkModulePath(arguments, jmods);
        int result = tools.run("jlink", in, out, err, jlinkArguments.toArray(String[]::new));
        if (result != 0 || !includeSources) {
            return result;
        }

        Path destination = output.resolve("lib/src.zip");
        Files.createDirectories(destination.getParent());
        Files.copy(sources, destination, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.COPY_ATTRIBUTES);
        retainPackagedModules(jlinkArguments, output);
        return 0;
    }

    private static List<String> withJdkModulePath(List<String> arguments, Path jmods) {
        var upgradeModulePath = new ArrayList<String>();
        var modulePath = new ArrayList<String>();
        var launcherOptions = LauncherRuntimeOptions.canonicalAccessArguments(arguments);
        var jlinkArguments = new ArrayList<String>();
        for (int i = 0; i < arguments.size(); i++) {
            String argument = arguments.get(i);
            if (LauncherRuntimeOptions.isAccessOption(argument)) {
                if (!argument.equals("--enable-preview") && argument.indexOf('=') < 0) {
                    i++;
                }
            } else if (argument.equals("--module-path") || argument.equals("-p") || argument.equals("--upgrade-module-path")) {
                if (++i >= arguments.size()) {
                    throw new IllegalArgumentException(argument + " requires an argument");
                }
                var destination = argument.equals("--upgrade-module-path") ? upgradeModulePath : modulePath;
                destination.add(arguments.get(i));
            } else if (argument.startsWith("--module-path=")) {
                modulePath.add(argument.substring("--module-path=".length()));
            } else if (argument.startsWith("--upgrade-module-path=")) {
                upgradeModulePath.add(argument.substring("--upgrade-module-path=".length()));
            } else {
                jlinkArguments.add(argument);
            }
        }
        // Replacements must precede same-named modules in the linking JDK.
        var combinedModulePath = new ArrayList<>(upgradeModulePath);
        combinedModulePath.add(jmods.toString());
        combinedModulePath.addAll(modulePath);
        if (Files.isRegularFile(jmods.resolve("openj9.sharedclasses.jmod"))) {
            jlinkArguments.add("--add-modules");
            jlinkArguments.add("openj9.sharedclasses");
        }
        if (!launcherOptions.isEmpty()) {
            jlinkArguments.add("--add-options");
            jlinkArguments.add(String.join(" ", launcherOptions));
        }
        jlinkArguments.addFirst(String.join(System.getProperty("path.separator"), combinedModulePath));
        jlinkArguments.addFirst("--module-path");
        return List.copyOf(jlinkArguments);
    }

    private static void retainPackagedModules(List<String> arguments, Path output) throws IOException {
        for (Path path : ToolArguments.modulePath(arguments)) {
            if (Files.isDirectory(path)) {
                try (var entries = Files.list(path)) {
                    for (Path entry : entries.filter(Files::isRegularFile).toList()) {
                        retainPackagedModule(entry, output);
                    }
                }
            } else {
                retainPackagedModule(path, output);
            }
        }
    }

    private static void retainPackagedModule(Path module, Path output) throws IOException {
        String name = module.getFileName().toString();
        Path directory;
        if (name.endsWith(".jmod")) {
            directory = output.resolve("jmods");
        } else if (name.endsWith(".jar")) {
            directory = output.resolve("lib/ja/modules");
        } else {
            return;
        }
        Files.createDirectories(directory);
        Files.copy(module, directory.resolve(name), StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.COPY_ATTRIBUTES);
    }

    private static Path output(List<String> arguments) {
        Path output = null;
        for (int i = 0; i < arguments.size(); i++) {
            String argument = arguments.get(i);
            String value = null;
            if (argument.equals("--output")) {
                if (++i >= arguments.size()) {
                    throw new IllegalArgumentException("--output requires an argument");
                }
                value = arguments.get(i);
            } else if (argument.startsWith("--output=")) {
                value = argument.substring("--output=".length());
            }
            if (value == null) {
                continue;
            }
            if (output != null) {
                throw new IllegalArgumentException("--output may only be specified once");
            }
            if (value.isEmpty()) {
                throw new IllegalArgumentException("--output requires an argument");
            }
            output = Path.of(value)
                    .toAbsolutePath()
                    .normalize();
        }
        if (output == null) {
            throw new IllegalArgumentException("--include-sources requires --output");
        }
        return output;
    }
}
