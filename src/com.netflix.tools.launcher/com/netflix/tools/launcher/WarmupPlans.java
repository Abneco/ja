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

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.Optional;
import java.util.Properties;
import java.util.spi.ToolProvider;
import javax.tools.Tool;

public final class WarmupPlans {
    private static final String RESOURCE_PREFIX = "META-INF/com.netflix.tools/tools/";
    private static final String RESOURCE_SUFFIX = ".properties";
    private static final String WARMUP = "warmup";

    private WarmupPlans() {}

    public static Optional<String> find(ToolProvider provider) {
        return find(provider.name(),
                provider.getClass().getModule());
    }

    public static Optional<String> find(Tool tool) {
        return find(tool.name(),
                tool.getClass().getModule());
    }

    private static Optional<String> find(String name, Module module) {
        String resource = RESOURCE_PREFIX + name + RESOURCE_SUFFIX;
        try (InputStream input = module.getResourceAsStream(resource)) {
            if (input == null) {
                return Optional.empty();
            }
            return read(input, module.getName() + "/" + resource);
        } catch (IOException e) {
            throw new UncheckedIOException("Cannot read warmup configuration " + resource, e);
        }
    }

    public static Optional<String> read(InputStream input, String source) throws IOException {
        var properties = new Properties();
        properties.load(new InputStreamReader(input, StandardCharsets.UTF_8));

        for (String name : properties.stringPropertyNames()) {
            if (name.startsWith(WARMUP + ".")) {
                throw new LauncherConfigurationException("Unknown warmup property " + name + " in " + source);
            }
        }
        return Optional.ofNullable(properties.getProperty(WARMUP));
    }
}
