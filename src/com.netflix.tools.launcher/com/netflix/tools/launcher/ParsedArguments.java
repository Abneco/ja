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

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/** Options and operands parsed from a {@link CommandLine}. */
public final class ParsedArguments {
    private final Map<ToolOption, List<String>> values;
    private final List<String> operands;
    private final SelectedCommand command;

    ParsedArguments(Map<ToolOption, List<String>> values, List<String> operands) {
        this(values, operands, null);
    }

    ParsedArguments(Map<ToolOption, List<String>> values, List<String> operands, SelectedCommand command) {
        var copied = new LinkedHashMap<ToolOption, List<String>>();
        values.forEach((option, occurrences) -> copied.put(option, List.copyOf(occurrences)));
        this.values = Map.copyOf(copied);
        this.operands = List.copyOf(operands);
        this.command = command;
    }

    public boolean contains(ToolOption option) {
        return values.containsKey(Objects.requireNonNull(option));
    }

    public List<String> values(ToolOption option) {
        return values.getOrDefault(Objects.requireNonNull(option), List.of());
    }

    public List<String> operands() {
        return operands;
    }

    public Optional<SelectedCommand> command() {
        return Optional.ofNullable(command);
    }

    /** A selected subcommand and the arguments parsed by its command line. */
    public record SelectedCommand(String name, ParsedArguments arguments) {
        public SelectedCommand {
            Objects.requireNonNull(name);
            Objects.requireNonNull(arguments);
        }
    }
}
