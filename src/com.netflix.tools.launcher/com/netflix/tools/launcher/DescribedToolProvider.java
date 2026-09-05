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
import java.util.List;
import java.util.Optional;
import java.util.spi.ToolProvider;
import javax.tools.OptionChecker;

/** A standard tool provider with an enumerable command-line description. */
public interface DescribedToolProvider extends ToolProvider, OptionChecker {
    CommandLine commandLine();

    @Override
    default int isSupportedOption(String option) {
        return commandLine().isSupportedOption(option);
    }

    default ToolInvocation prepare(ToolInvocation invocation, PrintWriter diagnostics) {
        return commandLine().prepare(name(), invocation, diagnostics);
    }

    default List<Completion> complete(CompletionRequest request) {
        return commandLine().complete(request);
    }

    default Optional<String> completionShim(ToolInvocation invocation) {
        return commandLine().completionShim(name(), invocation);
    }
}
