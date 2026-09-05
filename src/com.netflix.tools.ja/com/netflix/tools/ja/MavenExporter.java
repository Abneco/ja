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

/** Exports source modules as a Maven build project. */
final class MavenExporter {
    private final ToolServices tools;

    MavenExporter(ToolServices tools) {
        this.tools = tools;
    }

    int run(JaInvocation commandLine, ModuleSourcePath moduleSourcePath, InputStream in,
            PrintStream out, PrintStream err)
            throws IOException {
        Path project = request(commandLine.workingDirectory(), commandLine.toolArguments());
        if (commandLine.rootModules().isEmpty()) {
            throw new IllegalArgumentException("maven export requires at least one source module");
        }
        for (String module : commandLine.rootModules()) {
            if (!moduleSourcePath.modules().containsKey(module)) {
                throw new IllegalArgumentException("Maven export requires a source module: " + module);
            }
        }

        var arguments = new ArrayList<>(commandLine.resolutionArguments());
        arguments.add("--verify-module-hashes");
        arguments.add("--no-compile-diagnostics");
        arguments.add("--generate-module-poms");
        arguments.add(project.toString());
        return tools.run("jig", in, out, err, arguments.toArray(String[]::new));
    }

    private static Path request(Path workingDirectory, List<String> arguments) {
        if (arguments.isEmpty() || !arguments.getFirst().equals("export")) {
            throw new IllegalArgumentException("maven requires the export operation");
        }
        Path project = null;
        for (int i = 1; i < arguments.size(); i++) {
            String argument = arguments.get(i);
            if (argument.startsWith("-")) {
                throw new IllegalArgumentException("Unknown maven export option: " + argument);
            }
            if (project != null) {
                throw new IllegalArgumentException("maven export accepts one project directory");
            }
            project = workingDirectory.resolve(argument).normalize();
        }
        if (project == null) {
            throw new IllegalArgumentException("maven export requires a project directory");
        }
        return project;
    }
}
