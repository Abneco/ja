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
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/** Stages canonical runtime arguments consumed by generated native commands. */
final class LauncherRuntimeOptions {
    static final String CONFIGURATION_DIRECTORY = "com.netflix.tools.launcher";

    private LauncherRuntimeOptions() {}

    static Path stage(Path destination, Path existingConfiguration, Set<String> commands,
                      Set<String> warmupCommands, List<String> arguments)
            throws IOException {
        if (!commands.containsAll(warmupCommands)) {
            throw new IllegalArgumentException("Warmup command is not a tool command");
        }
        if (commands.isEmpty() || arguments.isEmpty() && warmupCommands.isEmpty()) {
            return existingConfiguration;
        }

        if (existingConfiguration != null) {
            if (!Files.isDirectory(existingConfiguration)) {
                throw new IllegalArgumentException("JMOD configuration is not a directory: " + existingConfiguration);
            }
            copyTree(existingConfiguration, destination);
        } else {
            Files.createDirectories(destination);
        }
        Path optionsDirectory = Files.createDirectories(destination.resolve(CONFIGURATION_DIRECTORY));
        List<String> runtimeArguments = canonicalArguments(arguments);
        for (String command : commands) {
            var generated = new ArrayList<>(runtimeArguments);
            if (warmupCommands.contains(command)) {
                generated.add("-L-aot=auto");
            }
            if (generated.isEmpty()) {
                continue;
            }
            String content = String.join("\n", generated) + "\n";
            Path options = optionsDirectory.resolve(command + ".args");
            if (Files.isRegularFile(options)) {
                String existing = Files.readString(options);
                Files.writeString(options, existing + (existing.endsWith("\n") ? "" : "\n") + content, StandardOpenOption.TRUNCATE_EXISTING);
            } else {
                try {
                    Files.writeString(options, content, StandardOpenOption.CREATE_NEW);
                } catch (FileAlreadyExistsException e) {
                    throw new IllegalArgumentException("Launcher runtime options already exist: " + options, e);
                }
            }
        }
        return destination;
    }

    static List<String> canonicalAccessArguments(List<String> arguments) {
        return canonicalArguments(arguments).stream()
                .filter(argument -> !argument.startsWith("--add-modules="))
                .toList();
    }

    static boolean isAccessOption(String argument) {
        if (argument.equals("--enable-preview")) {
            return true;
        }
        return Set.of("--enable-native-access", "--enable-final-field-mutation", "--add-exports", "--add-opens").stream()
                .anyMatch(option -> argument.equals(option) || argument.startsWith(option + "="));
    }

    private static List<String> canonicalArguments(List<String> arguments) {
        var result = new ArrayList<String>();
        for (int i = 0; i < arguments.size(); i++) {
            var argument = arguments.get(i);
            if (argument.equals("--enable-preview")) {
                result.add(argument);
                continue;
            }
            var option = switch (argument) {
                case "--add-modules", "--enable-native-access", "--enable-final-field-mutation", "--add-exports", "--add-opens" -> argument;
                default -> null;
            };
            if (option == null) {
                if (argument.startsWith("--add-modules=")
                        || argument.startsWith("--enable-native-access=")
                        || argument.startsWith("--enable-final-field-mutation=")
                        || argument.startsWith("--add-exports=")
                        || argument.startsWith("--add-opens=")) {
                    result.add(argument);
                }
                continue;
            }
            if (++i >= arguments.size()) {
                throw new IllegalArgumentException(option + " requires an argument");
            }
            result.add(option + "=" + arguments.get(i));
        }
        return List.copyOf(result);
    }

    private static void copyTree(Path source, Path destination) throws IOException {
        try (var paths = Files.walk(source)) {
            for (Path input : paths.toList()) {
                Path output = destination.resolve(source.relativize(input));
                if (Files.isDirectory(input)) {
                    Files.createDirectories(output);
                } else {
                    Files.copy(input, output, StandardCopyOption.COPY_ATTRIBUTES);
                }
            }
        }
    }
}
