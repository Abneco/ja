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

import java.io.IOException;
import java.io.InputStream;
import java.io.PrintStream;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/** Prepares module content and runs the runtime-image linker. */
final class LinkCommand {
    private final RuntimeImageLinker linker;
    private final List<ToolDefinition> definitions;

    LinkCommand(ToolServices tools, Path javaHome, List<ToolDefinition> definitions) {
        this.linker = new RuntimeImageLinker(tools, javaHome);
        this.definitions = List.copyOf(definitions);
    }

    int run(
            LinkRequest request,
            Optional<ModuleSourcePath> moduleSourcePath,
            List<String> compileArguments,
            List<String> resolvedArguments,
            List<String> toolArguments,
            InputStream in,
            PrintStream out,
            PrintStream err)
            throws IOException {
        try (var filtered = FilteredModulePath.prepare(moduleSourcePath.map(ModuleSourcePath::moduleNames).orElseGet(List::of), compileArguments,
                resolvedArguments, definitions)) {
            var arguments = new ArrayList<>(filtered.arguments());
            arguments.addAll(toolArguments);
            return linker.link(arguments, request.includeSources(), in, out, err);
        }
    }
}
