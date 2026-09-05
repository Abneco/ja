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

import java.nio.file.Path;
import java.util.Optional;

/**
 * Describes application installation, naming, replacement, and
 * static-requirement choices.
 */
public record InstallRequest(Optional<String> target, Optional<String> name, boolean force,
        boolean includeStatic, Optional<Path> output) {
    public InstallRequest {
        target = target.map(String::strip);
        if (target.filter(String::isEmpty).isPresent()) {
            throw new IllegalArgumentException("install target must not be empty");
        }
        name = name.map(String::strip);
        if (name.filter(String::isEmpty).isPresent()) {
            throw new IllegalArgumentException("--name requires a value");
        }
        output = output.map(path -> path.toAbsolutePath().normalize());
    }

    public InstallRequest(String target, Optional<String> name, boolean force) {
        this(Optional.of(target), name, force, false,
                Optional.empty());
    }

    public InstallRequest(Optional<String> target, Optional<String> name, boolean force) {
        this(target, name, force, false, Optional.empty());
    }
}
