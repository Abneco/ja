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
import java.util.Arrays;
import java.util.List;
import java.util.Objects;

/** Working-directory context and arguments for one tool invocation. */
public record ToolInvocation(Path workingDirectory, List<String> arguments) {
    public ToolInvocation {
        workingDirectory = Objects.requireNonNull(workingDirectory);
        arguments = List.copyOf(arguments);
    }

    public ToolInvocation(List<String> arguments) {
        this(Path.of(""), arguments);
    }

    public static ToolInvocation of(String... arguments) {
        Objects.requireNonNull(arguments);
        return new ToolInvocation(Arrays.asList(arguments));
    }
}
