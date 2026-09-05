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

package com.netflix.tools.launcher.test;

import java.util.List;
import java.util.Optional;
import java.util.Set;

import com.netflix.tools.launcher.CommandLine;
import com.netflix.tools.launcher.JdkCompilationOptions;
import com.netflix.tools.launcher.JdkModuleOptions;
import com.netflix.tools.launcher.ModuleRuntimeAccessArguments;
import com.netflix.tools.launcher.ModuleSelection;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class JdkOptionGroupsTest {
    @Test
    void singleModuleSelectionAcceptsOneModule() {
        var modules = ModuleSelection.single();
        var commandLine = CommandLine.builder()
                .options(modules)
                .build();

        var selected = modules.selected(commandLine.parse("--module", "com.example.app"));

        assertEquals(List.of("com.example.app"), selected.modules());
        assertEquals(Optional.empty(), selected.mainClass());
        assertEquals(Set.of("module=single"), modules.optionKeys());
    }

    @Test
    void listModuleSelectionSplitsModules() {
        var modules = ModuleSelection.list();
        var commandLine = CommandLine.builder()
                .options(modules)
                .build();

        var selected = modules.selected(commandLine.parse("-m", "com.example.app,com.example.lib"));

        assertEquals(List.of("com.example.app", "com.example.lib"), selected.modules());
        assertEquals(Set.of("module=list"), modules.optionKeys());
    }

    @Test
    void mainModuleSelectionSeparatesTheMainClass() {
        var modules = ModuleSelection.main();
        var commandLine = CommandLine.builder()
                .options(modules)
                .build();

        var selected = modules.selected(commandLine.parse("--module=com.example.app/com.example.Main"));

        assertEquals(List.of("com.example.app"), selected.modules());
        assertEquals(Optional.of("com.example.Main"), selected.mainClass());
        assertEquals(Set.of("module=main"), modules.optionKeys());
    }

    @Test
    void rootSelectionCoordinatesModuleAndAddModules() {
        var modules = ModuleSelection.roots();
        var commandLine = CommandLine.builder()
                .options(modules)
                .build();

        var selected = modules.selected(commandLine.parse("--module", "com.example.app", "--add-modules=com.example.lib,com.example.extra"));

        assertEquals(List.of("com.example.app", "com.example.lib", "com.example.extra"), selected.modules());
        assertEquals(Set.of("module=roots", "add-modules"), modules.optionKeys());
        assertEquals(1, commandLine.isSupportedOption("--module"));
        assertEquals(1, commandLine.isSupportedOption("--add-modules"));
    }

    @Test
    void singleModuleSelectionRejectsAList() {
        var modules = ModuleSelection.single();
        var commandLine = CommandLine.builder()
                .options(modules)
                .build();

        var failure = assertThrows(IllegalArgumentException.class, () -> modules.selected(commandLine.parse("--module", "one,two")));

        assertEquals("--module expects one module: one,two", failure.getMessage());
    }

    @Test
    void exposesModuleCompilationAndRuntimeAccessGroups() {
        var runtime = CommandLine.builder()
                .options(JdkModuleOptions.runtimePaths(), ModuleRuntimeAccessArguments.all())
                .option(JdkCompilationOptions.enablePreview())
                .build();
        var compilation = CommandLine.builder()
                .options(JdkCompilationOptions.paths())
                .build();

        assertEquals(1, runtime.isSupportedOption("--module-path"));
        assertEquals(1, runtime.isSupportedOption("-p"));
        assertEquals(1, runtime.isSupportedOption("--patch-module"));
        assertEquals(0, runtime.isSupportedOption("--enable-preview"));
        assertEquals(1, runtime.isSupportedOption("--add-opens"));
        assertEquals(1, compilation.isSupportedOption("--module-source-path"));
    }
}
