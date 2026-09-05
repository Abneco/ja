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

import java.util.LinkedHashSet;
import java.util.Objects;
import java.util.Set;

/** Shell completion registrations composed from command names. */
public final class CompletionBundle {
    private final Set<String> commands;

    private CompletionBundle(Set<String> commands) {
        this.commands = Set.copyOf(commands);
    }

    public static Builder builder() {
        return new Builder();
    }

    public String render(CompletionShell shell) {
        Objects.requireNonNull(shell);
        var result = new StringBuilder();
        commands.stream()
                .sorted()
                .forEach(command -> {
                    if (!result.isEmpty()) {
                        result.append('\n');
                    }
                    result.append(CompletionShims.render(shell, command));
                });
        return result.toString();
    }

    /** Builds a completion bundle from commands supporting {@code __complete}. */
    public static final class Builder {
        private final Set<String> commands = new LinkedHashSet<>();

        private Builder() {}

        public Builder add(String command) {
            Objects.requireNonNull(command);
            if (command.isBlank()) {
                throw new IllegalArgumentException("Tool name is blank");
            }
            commands.add(command);
            return this;
        }

        public CompletionBundle build() {
            return new CompletionBundle(commands);
        }
    }
}
