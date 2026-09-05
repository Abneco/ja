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

package com.netflix.tools.launcher;

import java.io.IOException;
import java.io.PrintWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public final class ToolOptions {
    private static final String ARGUMENTS_EXPANDED = "com.netflix.tools.launcher.arguments.expanded";
    private static final String OPTIONS_DIRECTORY = ".java-tool-options";

    private ToolOptions() {}

    public static String[] activate(String tool, Path effectiveDirectory, String[] explicitArguments,
            PrintWriter err) {
        var arguments = new ArrayList<>(configured(tool, effectiveDirectory, err));
        arguments.addAll(expand(List.of(explicitArguments)));
        return arguments.toArray(String[]::new);
    }

    static List<String> configured(String tool, Path effectiveDirectory, PrintWriter err) {
        try {
            optionFileName(tool);
            Path effective = normalizeDirectory(effectiveDirectory);
            Path optionFile = findOptions(tool, effective);
            if (optionFile == null) {
                return List.of();
            }
            err.println(tool + ": picked up options from " + displayPath(displayRoot(optionFile), optionFile));
            return JavaArgumentFiles.read(optionFile);
        } catch (IOException e) {
            throw new LauncherConfigurationException(e.getMessage(), e);
        }
    }

    static List<String> expand(List<String> arguments) {
        if (Boolean.getBoolean(ARGUMENTS_EXPANDED)) {
            return List.copyOf(arguments);
        }
        try {
            return JavaArgumentFiles.expand(arguments);
        } catch (IOException e) {
            throw new LauncherConfigurationException(e.getMessage(), e);
        }
    }

    private static Path displayRoot(Path optionFile) {
        for (var directory = optionFile.getParent();
             directory != null;
             directory = directory.getParent()) {
            if (directory.getFileName() != null && directory.getFileName()
                    .toString()
                    .equals(OPTIONS_DIRECTORY)) {
                return directory.getParent();
            }
        }
        return null;
    }

    private static Path findOptions(String tool, Path effective) throws IOException {
        for (Path directory = effective;
             directory != null;
             directory = directory.getParent()) {
            Path optionsDirectory = directory.resolve(OPTIONS_DIRECTORY);
            if (!Files.isDirectory(optionsDirectory)) {
                continue;
            }
            return selectOptions(tool, effective, directory, optionsDirectory);
        }
        return null;
    }

    private static Path selectOptions(String tool, Path effective, Path root,
            Path optionsDirectory)
            throws IOException {
        String fileName = optionFileName(tool);
        Path relative = root.relativize(effective);

        if (relative.toString().isEmpty()) {
            Path direct = optionsDirectory.resolve(fileName);
            if (Files.isRegularFile(direct)) {
                return direct.toRealPath();
            }
        }

        for (Path scope = relative;
             scope != null && !scope.toString().isEmpty();
             scope = scope.getParent()) {
            Path options = optionsDirectory.resolve(scope).resolve(fileName);
            if (Files.isRegularFile(options)) {
                return options.toRealPath();
            }
        }

        var contained = containedOptions(fileName, effective, root, optionsDirectory, relative);
        if (contained.size() > 1) {
            throw ambiguousOptions(tool, effective, contained);
        }
        if (contained.size() == 1) {
            return contained.getFirst().options();
        }

        Path fallback = optionsDirectory.resolve(fileName);
        return Files.isRegularFile(fallback) ? fallback.toRealPath() : null;
    }

    private static String optionFileName(String tool) {
        if (tool.isBlank()
                || tool.equals(".")
                || tool.equals("..")
                || tool.indexOf('/') >= 0
                || tool.indexOf('\\') >= 0) {
            throw new LauncherConfigurationException("Invalid tool name: " + tool);
        }
        return tool + ".args";
    }

    private static List<OptionScope> containedOptions(String fileName, Path effective, Path root,
            Path optionsDirectory, Path relative)
            throws IOException {
        Path subtree = optionsDirectory.resolve(relative);
        if (!Files.isDirectory(subtree)) {
            return List.of();
        }

        var candidates = new ArrayList<OptionScope>();
        try (var files = Files.walk(subtree)) {
            for (Path options : files.filter(Files::isRegularFile)
                    .filter(path -> path.getFileName()
                                        .toString()
                                        .equals(fileName))
                    .toList()) {
                Path scope = root.resolve(optionsDirectory.relativize(options.getParent()));
                if (!Files.isDirectory(scope)) {
                    continue;
                }
                Path directory = normalizeDirectory(scope);
                if (directory.startsWith(effective)) {
                    candidates.add(new OptionScope(directory, options.toRealPath()));
                }
            }
        }
        candidates.sort(Comparator.comparing(candidate -> candidate.directory().toString()));
        return List.copyOf(candidates);
    }

    private static LauncherConfigurationException ambiguousOptions(String tool, Path effective, List<OptionScope> candidates) {
        var message = new StringBuilder("Multiple ")
                .append(tool)
                .append(" option scopes are contained by ")
                .append(effective)
                .append("; select one with -C:");
        Path root = optionsRoot(effective);
        for (var candidate : candidates) {
            message.append("\n  -C ").append(displayPath(root, candidate.directory()));
        }
        return new LauncherConfigurationException(message.toString());
    }

    private static Path optionsRoot(Path effective) {
        for (Path directory = effective;
             directory != null;
             directory = directory.getParent()) {
            if (Files.isDirectory(directory.resolve(OPTIONS_DIRECTORY))) {
                return directory;
            }
        }
        return effective;
    }

    private static Path normalizeDirectory(Path directory) throws IOException {
        Path normalized = directory.toAbsolutePath().normalize();
        if (!Files.isDirectory(normalized)) {
            throw new LauncherConfigurationException("Option directory is not a directory: " + normalized);
        }
        return normalized.toRealPath();
    }

    private static String displayPath(Path root, Path path) {
        if (root != null && path.startsWith(root)) {
            Path relative = root.relativize(path);
            return relative.getNameCount() == 0 ? "." : relative.toString();
        }
        return path.toString();
    }

    private record OptionScope(Path directory, Path options) {}
}
