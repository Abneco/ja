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

import java.util.List;

import com.netflix.tools.launcher.ToolOption.Group;

final class JdkOptionDescriptors {
    private JdkOptionDescriptors() {}

    static ToolOption option(String key, String name, String argument,
            String description, String... aliases) {
        var builder = ToolOption.builder(name)
                .key(key)
                .argument(argument)
                .description(description);
        for (String alias : aliases) {
            builder.alias(alias);
        }
        return builder.build();
    }

    static ToolOption flag(String key, String name, String description,
                           String... aliases) {
        var builder = ToolOption.builder(name)
                .key(key)
                .description(description);
        for (String alias : aliases) {
            builder.alias(alias);
        }
        return builder.build();
    }

    static Group group(ToolOption... options) {
        List<ToolOption> declared = List.of(options);
        return () -> declared;
    }
}
