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

import java.lang.module.Configuration;
import java.lang.module.ModuleFinder;
import java.lang.module.ModuleReference;
import java.lang.module.ResolvedModule;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

final class Configurations {
    private Configurations() {}

    static Configuration resolve(Configuration parent, List<String> arguments) {
        return resolve(parent, arguments, Set.of());
    }

    static Configuration resolve(Configuration parent, List<String> arguments, Set<String> beforeModules) {
        var roots = ToolArguments.addedModules(arguments);
        if (roots.isEmpty()) {
            return parent;
        }

        var paths = ToolArguments.applicationModulePath(arguments);
        var pathFinder = ModuleFinder.of(paths.toArray(Path[]::new));
        return Configuration.resolve(selectedModules(pathFinder, beforeModules),
                List.of(parent), pathFinder, roots);
    }

    static Set<ResolvedModule> reachableModules(Configuration configuration, Set<String> roots) {
        var reachable = new LinkedHashSet<ResolvedModule>();
        var pending = new ArrayDeque<ResolvedModule>();
        roots.stream()
                .map(configuration::findModule)
                .flatMap(Optional::stream)
                .forEach(pending::add);
        while (!pending.isEmpty()) {
            var module = pending.removeFirst();
            if (reachable.add(module)) {
                pending.addAll(module.reads());
            }
        }
        return Set.copyOf(reachable);
    }

    private static ModuleFinder selectedModules(ModuleFinder finder, Set<String> selected) {
        return new ModuleFinder() {
            @Override
            public Optional<ModuleReference> find(String name) {
                return selected.contains(name) ? finder.find(name) : Optional.empty();
            }

            @Override
            public Set<ModuleReference> findAll() {
                return finder.findAll().stream()
                        .filter(reference -> selected.contains(reference.descriptor()
                                .name()))
                        .collect(Collectors.toUnmodifiableSet());
            }
        };
    }
}
