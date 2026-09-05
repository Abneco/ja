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
import java.util.List;
import java.util.Objects;
import java.util.Optional;

import com.netflix.tools.launcher.ModuleSelection.Main;
import com.netflix.tools.launcher.ModuleSelection.ModuleList;
import com.netflix.tools.launcher.ModuleSelection.Roots;
import com.netflix.tools.launcher.ModuleSelection.Single;
import com.netflix.tools.launcher.ToolOption.Group;

import static com.netflix.tools.launcher.JdkOptionDescriptors.option;

/** A standard {@code --module} argument shape accepted by a JDK tool. */
public abstract sealed class ModuleSelection implements Group
        permits Single, ModuleList, Main, Roots {
    private static final Single SINGLE = new Single();
    private static final ModuleList LIST = new ModuleList();
    private static final Main MAIN = new Main();
    private static final Roots ROOTS = new Roots();

    private final ToolOption module;
    private final List<ToolOption> options;

    private ModuleSelection(String key, String argument, boolean roots) {
        module = option(key, "--module", argument, "Select the module", "-m");
        options = roots ? List.of(module, JdkModuleOptions.addModules()) : List.of(module);
    }

    public static Single single() {
        return SINGLE;
    }

    public static ModuleList list() {
        return LIST;
    }

    public static Main main() {
        return MAIN;
    }

    public static Roots roots() {
        return ROOTS;
    }

    @Override
    public final List<ToolOption> options() {
        return options;
    }

    public abstract SelectedModules selected(ParsedArguments arguments);

    final ToolOption moduleOption() {
        return module;
    }

    final List<String> moduleValues(ParsedArguments arguments) {
        return Objects.requireNonNull(arguments).values(module);
    }

    static String singleModule(String value) {
        if (value.isBlank() || value.indexOf(',') >= 0) {
            throw new IllegalArgumentException("--module expects one module: " + value);
        }
        return value;
    }

    static void addList(LinkedHashSet<String> modules, String value) {
        for (String module : value.split(",", -1)) {
            String name = module.strip();
            if (name.isEmpty()) {
                throw new IllegalArgumentException("Module list contains an empty module: " + value);
            }
            modules.add(name);
        }
    }

    static SelectedModules selected(LinkedHashSet<String> modules, String mainClass) {
        return new SelectedModules(List.copyOf(modules), Optional.ofNullable(mainClass));
    }

    /** A {@code --module} option accepting one module name per occurrence. */
    public static final class Single extends ModuleSelection {
        private Single() {
            super("module=single", "MODULE", false);
        }

        @Override
        public SelectedModules selected(ParsedArguments arguments) {
            var modules = new LinkedHashSet<String>();
            moduleValues(arguments).stream()
                    .map(ModuleSelection::singleModule)
                    .forEach(modules::add);
            return selected(modules, null);
        }
    }

    /** A {@code --module} option accepting comma-separated module names. */
    public static final class ModuleList extends ModuleSelection {
        private ModuleList() {
            super("module=list", "MODULE[,MODULE...]", false);
        }

        @Override
        public SelectedModules selected(ParsedArguments arguments) {
            var modules = new LinkedHashSet<String>();
            moduleValues(arguments).forEach(value -> addList(modules, value));
            return selected(modules, null);
        }
    }

    /** A {@code --module} option accepting {@code module[/main-class]}. */
    public static final class Main extends ModuleSelection {
        private Main() {
            super("module=main", "MODULE[/MAIN-CLASS]", false);
        }

        @Override
        public SelectedModules selected(ParsedArguments arguments) {
            var modules = new LinkedHashSet<String>();
            String mainClass = null;
            for (String value : moduleValues(arguments)) {
                int separator = value.indexOf('/');
                modules.add(singleModule(separator < 0 ? value : value.substring(0, separator)));
                if (separator >= 0) {
                    String selectedMain = value.substring(separator + 1);
                    if (selectedMain.isBlank()) {
                        throw new IllegalArgumentException("--module main class is empty: " + value);
                    }
                    if (mainClass != null && !mainClass.equals(selectedMain)) {
                        throw new IllegalArgumentException("Multiple main classes were selected");
                    }
                    mainClass = selectedMain;
                }
            }
            return selected(modules, mainClass);
        }
    }

    /** Coordinated {@code --module} and {@code --add-modules} root selection. */
    public static final class Roots extends ModuleSelection {
        private Roots() {
            super("module=roots", "MODULE", true);
        }

        @Override
        public SelectedModules selected(ParsedArguments arguments) {
            var modules = new LinkedHashSet<String>();
            moduleValues(arguments).stream()
                    .map(ModuleSelection::singleModule)
                    .forEach(modules::add);
            arguments.values(JdkModuleOptions.addModules()).forEach(value -> addList(modules, value));
            return selected(modules, null);
        }
    }

    /** Modules and optional main class selected by a module option group. */
    public record SelectedModules(List<String> modules, Optional<String> mainClass) {
        public SelectedModules {
            modules = List.copyOf(modules);
            mainClass = Objects.requireNonNull(mainClass);
        }
    }
}
