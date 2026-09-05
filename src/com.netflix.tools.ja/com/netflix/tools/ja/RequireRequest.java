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

import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;

/** Describes a dependency requirement operation and its version-update policy. */
public record RequireRequest(
        List<Dependency> dependencies,
        List<String> unresolvedTargets,
        List<String> updateModules,
        UpdatePolicy updatePolicy,
        boolean staticPhase,
        boolean transitive,
        List<RuntimeAccess> runtimeAccess) {

    public enum UpdatePolicy {
        NONE,
        PATCH,
        MINOR,
        MAJOR
    }

    public RequireRequest(List<Dependency> dependencies, boolean staticPhase, boolean transitive) {
        this(dependencies, List.of(), List.of(), UpdatePolicy.NONE,
                staticPhase, transitive, List.of());
    }

    public RequireRequest(List<Dependency> dependencies, List<String> updateModules, UpdatePolicy updatePolicy,
                          boolean staticPhase, boolean transitive) {
        this(dependencies, List.of(), updateModules, updatePolicy, staticPhase,
                transitive, List.of());
    }

    public RequireRequest(List<Dependency> dependencies, List<String> unresolvedTargets, List<String> updateModules,
                          UpdatePolicy updatePolicy, boolean staticPhase, boolean transitive) {
        this(dependencies, unresolvedTargets, updateModules, updatePolicy, staticPhase, transitive,
                List.of());
    }

    public RequireRequest {
        dependencies = List.copyOf(dependencies);
        unresolvedTargets = List.copyOf(unresolvedTargets);
        updateModules = List.copyOf(updateModules);
        runtimeAccess = List.copyOf(new LinkedHashSet<>(runtimeAccess));
        if (updatePolicy == null) {
            throw new NullPointerException("updatePolicy");
        }
        if (updatePolicy == UpdatePolicy.NONE && dependencies.isEmpty() && unresolvedTargets.isEmpty()) {
            throw new IllegalArgumentException("require needs at least one module");
        }
        if (updatePolicy != UpdatePolicy.NONE && (!dependencies.isEmpty() || !unresolvedTargets.isEmpty())) {
            throw new IllegalArgumentException("require cannot combine --update with explicit versions");
        }
        if (updatePolicy != UpdatePolicy.NONE && (staticPhase || transitive)) {
            throw new IllegalArgumentException("require cannot combine --update with requirement modifiers");
        }
        if (updatePolicy != UpdatePolicy.NONE && !runtimeAccess.isEmpty()) {
            throw new IllegalArgumentException("require cannot combine --update with runtime access");
        }
        var names = new HashSet<String>();
        for (var dependency : dependencies) {
            if (!names.add(dependency.moduleName())) {
                throw new IllegalArgumentException("require contains duplicate module " + dependency.moduleName());
            }
        }
        for (var target : unresolvedTargets) {
            if (target.isBlank()) {
                throw new IllegalArgumentException("require expects a module name or package URL");
            }
            if (!names.add(target)) {
                throw new IllegalArgumentException("require contains duplicate target " + target);
            }
        }
        for (var module : updateModules) {
            if (module.isBlank()) {
                throw new IllegalArgumentException("require --update expects a module name");
            }
            if (!names.add(module)) {
                throw new IllegalArgumentException("require contains duplicate module " + module);
            }
        }
    }

    public boolean updatesVersions() {
        return updatePolicy != UpdatePolicy.NONE;
    }

    public record Dependency(String moduleName, String version) {
        public Dependency {
            if (moduleName.isBlank() || version.isBlank()) {
                throw new IllegalArgumentException("require expects <module>@<version>");
            }
        }
    }

    public record RuntimeAccess(String tag, String value) {
        public RuntimeAccess {
            if (!List.of("enableNativeAccess", "enableFinalFieldMutation", "addExports", "addOpens").contains(tag)) {
                throw new IllegalArgumentException("Unknown runtime access: " + tag);
            }
            if (value.isBlank()) {
                throw new IllegalArgumentException("Runtime access needs a value: " + tag);
            }
        }
    }
}
