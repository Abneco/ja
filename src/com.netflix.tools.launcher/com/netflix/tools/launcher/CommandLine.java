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

import java.io.PrintWriter;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import javax.tools.OptionChecker;

import com.netflix.tools.launcher.ParsedArguments.SelectedCommand;
import com.netflix.tools.launcher.ToolOption.Group;

/** An enumerable description of a tool's command-line arguments. */
public final class CommandLine implements OptionChecker {
    private static final ToolOption COMPLETION = ToolOption.builder("--completion")
            .optionalArgument("SHELL")
            .choices("bash", "zsh", "fish", "powershell")
            .description("Print a completion shim for the current or selected shell")
            .build();
    private static final ToolOption WORKING_DIRECTORY = ToolOption.builder("-C")
            .argument("DIRECTORY")
            .description("Run in the specified directory")
            .build();

    public enum Cardinality {
        ZERO_OR_ONE,
        EXACTLY_ONE,
        ZERO_OR_MORE,
        ONE_OR_MORE
    }

    private final String description;
    private final List<ToolOption> options;
    private final List<Group> optionGroups;
    private final Map<String, ToolOption> optionsByName;
    private final List<Subcommand> commands;
    private final Map<String, Subcommand> commandsByName;
    private final Operand operand;
    private final boolean workingDirectory;
    private final boolean argumentFiles;
    private final boolean javaToolOptions;
    private final boolean completion;

    private CommandLine(
            String description,
            List<ToolOption> options,
            List<Group> optionGroups,
            List<Subcommand> commands,
            Operand operand,
            boolean workingDirectory,
            boolean argumentFiles,
            boolean javaToolOptions,
            boolean completion) {
        this.description = description;
        this.options = List.copyOf(options);
        this.optionGroups = List.copyOf(optionGroups);
        this.commands = List.copyOf(commands);
        this.operand = operand;
        this.workingDirectory = workingDirectory;
        this.argumentFiles = argumentFiles;
        this.javaToolOptions = javaToolOptions;
        this.completion = completion;
        var byName = new LinkedHashMap<String, ToolOption>();
        for (ToolOption option : options) {
            for (String name : option.names()) {
                var previous = byName.putIfAbsent(name, option);
                if (previous != null) {
                    throw new IllegalArgumentException("Duplicate option name: " + name);
                }
            }
        }
        optionsByName = Map.copyOf(byName);
        var commandsByName = new LinkedHashMap<String, Subcommand>();
        for (Subcommand command : commands) {
            var previous = commandsByName.putIfAbsent(command.name(), command);
            if (previous != null) {
                throw new IllegalArgumentException("Duplicate command: " + command.name());
            }
        }
        this.commandsByName = Map.copyOf(commandsByName);
        if (!commands.isEmpty() && operand != null) {
            throw new IllegalArgumentException("A command line cannot declare both commands and operands");
        }
    }

    public static Builder builder() {
        return new Builder();
    }

    public String description() {
        return description;
    }

    public List<ToolOption> options() {
        return options;
    }

    public List<Group> optionGroups() {
        return optionGroups;
    }

    public List<Subcommand> commands() {
        return commands;
    }

    @Override
    public int isSupportedOption(String option) {
        var declared = optionsByName.get(option);
        return declared == null ? -1 : declared.argumentCount();
    }

    public ToolInvocation prepare(ToolInvocation invocation) {
        var prepared = prepareDirectory(invocation);
        if (!argumentFiles) {
            return prepared;
        }
        return new ToolInvocation(prepared.workingDirectory(), ToolOptions.expand(prepared.arguments()));
    }

    public ToolInvocation prepare(String invocationName, ToolInvocation invocation, PrintWriter diagnostics) {
        Objects.requireNonNull(invocationName);
        Objects.requireNonNull(diagnostics);
        var prepared = prepareDirectory(invocation);
        var arguments = new ArrayList<String>();
        if (javaToolOptions) {
            arguments.addAll(ToolOptions.configured(invocationName, prepared.workingDirectory(), diagnostics));
        }
        arguments.addAll(argumentFiles ? ToolOptions.expand(prepared.arguments()) : prepared.arguments());
        if (arguments.equals(prepared.arguments())) {
            return prepared;
        }
        return new ToolInvocation(prepared.workingDirectory(), arguments);
    }

    private ToolInvocation prepareDirectory(ToolInvocation invocation) {
        Objects.requireNonNull(invocation);
        if (!workingDirectory) {
            return invocation;
        }
        Path selected = invocation.workingDirectory();
        List<String> arguments = invocation.arguments();
        int first = 0;
        if (!arguments.isEmpty() && arguments.getFirst().equals("--")) {
            first = 1;
        } else {
            while (first < arguments.size() && arguments.get(first).equals("-C")) {
                if (++first >= arguments.size()) {
                    throw new IllegalArgumentException("-C requires DIRECTORY");
                }
                selected = selected.resolve(arguments.get(first));
                first++;
            }
        }
        return first == 0 ? invocation : new ToolInvocation(selected, arguments.subList(first, arguments.size()));
    }

    public Optional<String> completionShim(String invocationName, ToolInvocation invocation) {
        Objects.requireNonNull(invocationName);
        Objects.requireNonNull(invocation);
        if (!completion) {
            return Optional.empty();
        }
        var prepared = prepare(invocation);
        if (prepared.arguments().isEmpty()) {
            return Optional.empty();
        }
        String argument = prepared.arguments().getFirst();
        if (!argument.equals("--completion") && !argument.startsWith("--completion=")) {
            return Optional.empty();
        }
        if (prepared.arguments().size() != 1) {
            throw new IllegalArgumentException("--completion does not accept other arguments");
        }
        CompletionShell shell = argument.equals("--completion") ? CompletionShell.detect().orElseThrow(() -> new IllegalArgumentException("Cannot determine completion shell; use --completion=bash|zsh|fish|powershell")) : CompletionShell.parse(argument.substring("--completion=".length()));
        return Optional.of(CompletionShims.render(shell, invocationName, this));
    }

    public ParsedArguments parse(ToolInvocation invocation) {
        var prepared = prepare(invocation);
        return parseArguments(prepared.arguments());
    }

    public ParsedArguments parse(String invocationName, ToolInvocation invocation, PrintWriter diagnostics) {
        var prepared = prepare(invocationName, invocation, diagnostics);
        return parseArguments(prepared.arguments());
    }

    public ParsedArguments parse(String... arguments) {
        return parse(ToolInvocation.of(arguments));
    }

    private ParsedArguments parseArguments(List<String> arguments) {
        var values = new LinkedHashMap<ToolOption, List<String>>();
        var operands = new ArrayList<String>();
        SelectedCommand selectedCommand = null;
        boolean optionsEnabled = true;
        for (int i = 0; i < arguments.size(); i++) {
            String argument = Objects.requireNonNull(arguments.get(i));
            if (optionsEnabled && argument.equals("--")) {
                optionsEnabled = false;
                continue;
            }
            if (!optionsEnabled || !argument.startsWith("-") || argument.equals("-")) {
                if (!commands.isEmpty()) {
                    var command = commandsByName.get(argument);
                    if (command == null) {
                        throw new IllegalArgumentException("Unknown command: " + argument);
                    }
                    var commandArguments = command.commandLine().parseArguments(arguments.subList(i + 1, arguments.size()));
                    selectedCommand = new SelectedCommand(command.name(), commandArguments);
                    break;
                }
                operands.add(argument);
                if (operand != null && operand.remainder()) {
                    optionsEnabled = false;
                }
                continue;
            }

            int equals = argument.indexOf('=');
            String name = equals < 0 ? argument : argument.substring(0, equals);
            var option = optionsByName.get(name);
            if (option == null) {
                if (operand != null && operand.remainder()) {
                    operands.addAll(arguments.subList(i, arguments.size()));
                    break;
                }
                throw new IllegalArgumentException("Unknown option: " + name);
            }
            var occurrences = values.computeIfAbsent(option, _ -> new ArrayList<>());
            if (option.argument().isEmpty()) {
                if (equals >= 0) {
                    throw new IllegalArgumentException(name + " does not accept an argument");
                }
                continue;
            }
            if (option.optionalArgument()) {
                if (equals >= 0) {
                    addOptionValue(option, name, argument.substring(equals + 1), occurrences);
                }
                continue;
            }
            if (equals >= 0) {
                addOptionValue(option, name, argument.substring(equals + 1), occurrences);
                continue;
            }
            if (++i >= arguments.size()) {
                throw new IllegalArgumentException(name + " requires " + option.argument().orElseThrow());
            }
            addOptionValue(option, name, Objects.requireNonNull(arguments.get(i)), occurrences);
        }
        validateOperands(operands);
        if (!commands.isEmpty() && selectedCommand == null) {
            throw new IllegalArgumentException("Missing command");
        }
        return new ParsedArguments(values, operands, selectedCommand);
    }

    public String help(String invocationName) {
        Objects.requireNonNull(invocationName);
        if (invocationName.isBlank()) {
            throw new IllegalArgumentException("Invocation name is blank");
        }
        var help = new StringBuilder("Usage: ").append(invocationName);
        if (!options.isEmpty()) {
            help.append(" [OPTIONS]");
        }
        if (operand != null) {
            help.append(' ').append(operand.usage());
        }
        help.append('\n');
        if (!description.isEmpty()) {
            help.append('\n')
                .append(description)
                .append('\n');
        }
        if (!commands.isEmpty()) {
            help.append("\nCommands:\n");
            for (Subcommand command : commands) {
                help.append("  ")
                    .append(command.name())
                    .append("  ")
                    .append(command.description())
                    .append('\n');
            }
        }
        if (operand != null) {
            help.append("\nArguments:\n  ")
                .append(operand.name())
                .append("  ")
                .append(operand.description())
                .append('\n');
        }
        if (!options.isEmpty()) {
            help.append('\n').append(optionsHelp());
        }
        return help.toString();
    }

    public String optionsHelp() {
        if (options.isEmpty()) {
            return "";
        }
        var help = new StringBuilder("Options:\n");
        for (ToolOption option : options) {
            help.append("  ")
                .append(display(option))
                .append("  ")
                .append(option.description())
                .append('\n');
        }
        return help.toString();
    }

    public List<Completion> complete(CompletionRequest request) {
        Objects.requireNonNull(request);
        var arguments = new ArrayList<>(request.invocation()
                .arguments());
        arguments.add(request.current());
        return complete(arguments);
    }

    public List<Completion> complete(List<String> arguments) {
        Objects.requireNonNull(arguments);
        String prefix = arguments.isEmpty() ? "" : Objects.requireNonNull(arguments.getLast());
        var delegated = commandCompletion(arguments);
        if (delegated != null) {
            return delegated;
        }
        if (!commands.isEmpty() && !prefix.startsWith("-")) {
            return commands.stream()
                    .filter(command -> command.name().startsWith(prefix))
                    .map(command -> new Completion(command.name(), command.description()))
                    .sorted(Comparator.comparing(Completion::value))
                    .toList();
        }
        if (!prefix.startsWith("-") || optionsEnded(arguments)) {
            return List.of();
        }
        int equals = prefix.indexOf('=');
        if (equals >= 0) {
            String name = prefix.substring(0, equals);
            var option = optionsByName.get(name);
            if (option == null || option.choices().isEmpty()) {
                return List.of();
            }
            return option.choices().stream()
                    .map(choice -> new Completion(name + "=" + choice, option.description()))
                    .filter(candidate -> candidate.value().startsWith(prefix))
                    .sorted(Comparator.comparing(Completion::value))
                    .toList();
        }
        var completions = new ArrayList<Completion>();
        for (ToolOption option : options) {
            for (String name : option.names()) {
                if (name.startsWith(prefix)) {
                    completions.add(new Completion(name, option.description()));
                }
            }
        }
        completions.sort(Comparator.comparing(Completion::value));
        return List.copyOf(completions);
    }

    private List<Completion> commandCompletion(List<String> arguments) {
        if (commands.isEmpty() || arguments.isEmpty()) {
            return null;
        }
        int current = arguments.size() - 1;
        boolean optionsEnabled = true;
        for (int i = 0; i < current; i++) {
            String argument = Objects.requireNonNull(arguments.get(i));
            if (optionsEnabled && argument.equals("--")) {
                optionsEnabled = false;
                continue;
            }
            if (optionsEnabled && argument.startsWith("-") && !argument.equals("-")) {
                int equals = argument.indexOf('=');
                String name = equals < 0 ? argument : argument.substring(0, equals);
                var option = optionsByName.get(name);
                if (option != null && option.argumentCount() > 0 && equals < 0) {
                    i++;
                }
                continue;
            }
            var command = commandsByName.get(argument);
            if (command == null) {
                return List.of();
            }
            return command.commandLine().complete(arguments.subList(i + 1, arguments.size()));
        }
        return null;
    }

    private boolean optionsEnded(List<String> arguments) {
        int current = arguments.size() - 1;
        for (int i = 0; i < current; i++) {
            String argument = Objects.requireNonNull(arguments.get(i));
            if (argument.equals("--")) {
                return true;
            }
            if (!argument.startsWith("-") || argument.equals("-")) {
                if (operand != null && operand.remainder()) {
                    return true;
                }
                continue;
            }
            int equals = argument.indexOf('=');
            String name = equals < 0 ? argument : argument.substring(0, equals);
            var option = optionsByName.get(name);
            if (option != null && option.argumentCount() > 0 && equals < 0) {
                if (++i == current) {
                    return true;
                }
            }
        }
        return false;
    }

    private static void addOptionValue(ToolOption option, String name, String value,
            List<String> occurrences) {
        if (value.isEmpty()) {
            throw new IllegalArgumentException(name + " requires " + option.argument().orElseThrow());
        }
        if (!option.choices().isEmpty() && !option.choices().contains(value)) {
            throw new IllegalArgumentException(name + " expects one of: " + String.join(", ", option.choices()));
        }
        occurrences.add(value);
    }

    private void validateOperands(List<String> operands) {
        int count = operands.size();
        if (operand == null) {
            if (count != 0) {
                throw new IllegalArgumentException("Unexpected argument: " + operands.getFirst());
            }
            return;
        }
        boolean valid = switch (operand.cardinality()) {
            case ZERO_OR_ONE -> count <= 1;
            case EXACTLY_ONE -> count == 1;
            case ZERO_OR_MORE -> true;
            case ONE_OR_MORE -> count >= 1;
        };
        if (!valid) {
            throw new IllegalArgumentException("Invalid number of " + operand.name() + " arguments: " + count);
        }
    }

    private static String display(ToolOption option) {
        var names = new ArrayList<>(option.names());
        names.sort(Comparator.comparingInt(String::length)
                .thenComparing(Comparator.naturalOrder()));
        var display = new StringBuilder(String.join(", ", names));
        option.argument().ifPresent(argument -> {
            if (option.optionalArgument()) {
                display.append("[=<")
                       .append(argument)
                       .append(">]");
            } else {
                display.append(" <")
                       .append(argument)
                       .append('>');
            }
        });
        return display.toString();
    }

    /** A named command and the command line it selects. */
    public record Subcommand(String name, String description, CommandLine commandLine) {
        public Subcommand {
            Objects.requireNonNull(name);
            Objects.requireNonNull(description);
            Objects.requireNonNull(commandLine);
            if (name.isBlank() || name.startsWith("-") || name.chars().anyMatch(Character::isWhitespace)) {
                throw new IllegalArgumentException("Invalid command name: " + name);
            }
        }
    }

    private record Operand(String name, String description, Cardinality cardinality,
                           boolean remainder) {
        private Operand {
            Objects.requireNonNull(name);
            Objects.requireNonNull(description);
            Objects.requireNonNull(cardinality);
            if (name.isBlank()) {
                throw new IllegalArgumentException("Operand name is blank");
            }
        }

        String usage() {
            return switch (cardinality) {
                case ZERO_OR_ONE -> "[" + name + "]";
                case EXACTLY_ONE -> name;
                case ZERO_OR_MORE -> "[" + name + "...]";
                case ONE_OR_MORE -> name + "...";
            };
        }
    }

    /** Builds a command-line description. */
    public static final class Builder {
        private String description = "";
        private final List<ToolOption> options = new ArrayList<>();
        private final List<Group> optionGroups = new ArrayList<>();
        private final List<Subcommand> commands = new ArrayList<>();
        private Operand operand;
        private boolean workingDirectory;
        private boolean argumentFiles;
        private boolean javaToolOptions;
        private boolean completion;

        private Builder() {}

        public Builder description(String value) {
            description = Objects.requireNonNull(value);
            return this;
        }

        public Builder option(ToolOption option) {
            options.add(Objects.requireNonNull(option));
            return this;
        }

        public Builder options(ToolOption... declared) {
            for (ToolOption option : declared) {
                option(option);
            }
            return this;
        }

        public Builder options(Group... groups) {
            for (Group group : groups) {
                var declared = Objects.requireNonNull(group);
                optionGroups.add(declared);
                options.addAll(declared.options());
            }
            return this;
        }

        public Builder command(String name, String description, CommandLine commandLine) {
            commands.add(new Subcommand(name, description, commandLine));
            return this;
        }

        public Builder completion() {
            if (completion) {
                throw new IllegalStateException("Completion is already enabled");
            }
            completion = true;
            option(COMPLETION);
            return this;
        }

        public Builder argumentFiles() {
            if (argumentFiles) {
                throw new IllegalStateException("Argument file expansion is already enabled");
            }
            argumentFiles = true;
            return this;
        }

        public Builder javaToolOptions() {
            if (javaToolOptions) {
                throw new IllegalStateException(".java-tool-options are already enabled");
            }
            javaToolOptions = true;
            return this;
        }

        public Builder workingDirectory() {
            if (workingDirectory) {
                throw new IllegalStateException("The working directory convention is already enabled");
            }
            workingDirectory = true;
            option(WORKING_DIRECTORY);
            return this;
        }

        public Builder operand(String name, String description, Cardinality cardinality) {
            if (operand != null) {
                throw new IllegalStateException("An operand is already declared");
            }
            operand = new Operand(name, description, cardinality, false);
            return this;
        }

        public Builder remainder(String name, String description) {
            if (operand != null) {
                throw new IllegalStateException("An operand is already declared");
            }
            operand = new Operand(name, description, Cardinality.ZERO_OR_MORE, true);
            return this;
        }

        public CommandLine build() {
            return new CommandLine(description, options, optionGroups, commands, operand, workingDirectory,
                    argumentFiles, javaToolOptions, completion);
        }
    }
}
