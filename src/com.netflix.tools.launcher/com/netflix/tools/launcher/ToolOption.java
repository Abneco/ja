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

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/** Describes one command-line option and its aliases. */
public final class ToolOption {
    /** A semantic group of related command-line options. */
    public interface Group {
        List<ToolOption> options();

        default Set<String> optionKeys() {
            return options().stream()
                    .map(ToolOption::key)
                    .collect(Collectors.toUnmodifiableSet());
        }
    }

    private final String key;
    private final List<String> names;
    private final String argument;
    private final boolean optionalArgument;
    private final List<String> choices;
    private final String description;

    private ToolOption(String key, List<String> names, String argument,
                       boolean optionalArgument, List<String> choices, String description) {
        this.key = requireKey(key);
        this.names = List.copyOf(names);
        this.argument = argument;
        this.optionalArgument = optionalArgument;
        this.choices = List.copyOf(choices);
        this.description = Objects.requireNonNull(description);
        if (this.names.isEmpty()) {
            throw new IllegalArgumentException("An option requires a name");
        }
        var distinct = new LinkedHashSet<String>();
        for (String name : this.names) {
            requireName(name);
            if (!distinct.add(name)) {
                throw new IllegalArgumentException("Duplicate option name: " + name);
            }
        }
        if (argument != null && argument.isBlank()) {
            throw new IllegalArgumentException("Option argument name is blank");
        }
        if (argument == null && (optionalArgument || !choices.isEmpty())) {
            throw new IllegalArgumentException("Option choices require an argument");
        }
        var distinctChoices = new LinkedHashSet<String>();
        for (String choice : this.choices) {
            if (choice.isBlank()) {
                throw new IllegalArgumentException("Option choice is blank");
            }
            if (!distinctChoices.add(choice)) {
                throw new IllegalArgumentException("Duplicate option choice: " + choice);
            }
        }
    }

    public static Builder builder(String name) {
        return new Builder(name);
    }

    public String key() {
        return key;
    }

    public List<String> names() {
        return names;
    }

    public Optional<String> argument() {
        return Optional.ofNullable(argument);
    }

    public boolean optionalArgument() {
        return optionalArgument;
    }

    public List<String> choices() {
        return choices;
    }

    public String description() {
        return description;
    }

    public int argumentCount() {
        return argument == null || optionalArgument ? 0 : 1;
    }

    private static String defaultKey(String name) {
        int first = 0;
        while (first < name.length() && name.charAt(first) == '-') {
            first++;
        }
        return name.substring(first);
    }

    private static String requireKey(String key) {
        Objects.requireNonNull(key);
        if (key.isBlank()) {
            throw new IllegalArgumentException("Option key is blank");
        }
        return key;
    }

    private static void requireName(String name) {
        Objects.requireNonNull(name);
        if (name.length() < 2 || name.charAt(0) != '-') {
            throw new IllegalArgumentException("Invalid option name: " + name);
        }
    }

    /** Builds a command-line option. */
    public static final class Builder {
        private String key;
        private final List<String> names = new ArrayList<>();
        private String argument;
        private boolean optionalArgument;
        private final List<String> choices = new ArrayList<>();
        private String description = "";

        private Builder(String name) {
            requireName(name);
            key = defaultKey(name);
            names.add(name);
        }

        public Builder key(String value) {
            key = requireKey(value);
            return this;
        }

        public Builder alias(String name) {
            requireName(name);
            names.add(name);
            return this;
        }

        public Builder argument(String name) {
            return argument(name, false);
        }

        public Builder optionalArgument(String name) {
            return argument(name, true);
        }

        private Builder argument(String name, boolean optional) {
            if (argument != null) {
                throw new IllegalStateException("Option argument is already declared");
            }
            argument = Objects.requireNonNull(name);
            optionalArgument = optional;
            return this;
        }

        public Builder choices(String... values) {
            for (String value : values) {
                choices.add(Objects.requireNonNull(value));
            }
            return this;
        }

        public Builder description(String value) {
            description = Objects.requireNonNull(value);
            return this;
        }

        public ToolOption build() {
            return new ToolOption(key, names, argument, optionalArgument, choices, description);
        }
    }
}
