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

import java.nio.file.Path;
import java.util.Locale;
import java.util.Optional;

/** Shells for which completion shims can be generated. */
public enum CompletionShell {
    BASH("bash"),
    ZSH("zsh"),
    FISH("fish"),
    POWERSHELL("powershell");

    private final String option;

    CompletionShell(String option) {
        this.option = option;
    }

    public String option() {
        return option;
    }

    public static CompletionShell parse(String value) {
        for (CompletionShell shell : values()) {
            if (shell.option.equalsIgnoreCase(value)) {
                return shell;
            }
        }
        throw new IllegalArgumentException("Unknown completion shell: " + value);
    }

    public static Optional<CompletionShell> detect() {
        for (var process = ProcessHandle.current().parent();
             process.isPresent();
             process = process.orElseThrow().parent()) {
            var shell = fromCommand(process.orElseThrow()
                    .info()
                    .command()
                    .orElse(""));
            if (shell.isPresent()) {
                return shell;
            }
        }
        return fromCommand(System.getenv("SHELL"));
    }

    private static Optional<CompletionShell> fromCommand(String command) {
        if (command == null || command.isBlank()) {
            return Optional.empty();
        }
        String name = Path.of(command)
                .getFileName()
                .toString()
                .toLowerCase(Locale.ROOT);
        if (name.endsWith(".exe")) {
            name = name.substring(0, name.length() - 4);
        }
        return switch (name) {
            case "bash" -> Optional.of(BASH);
            case "zsh" -> Optional.of(ZSH);
            case "fish" -> Optional.of(FISH);
            case "powershell", "pwsh" -> Optional.of(POWERSHELL);
            default -> Optional.empty();
        };
    }
}
