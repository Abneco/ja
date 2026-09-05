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

import java.io.ByteArrayInputStream;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.util.Optional;
import java.util.spi.ToolProvider;

import com.netflix.tools.launcher.LauncherConfigurationException;
import com.netflix.tools.launcher.WarmupPlans;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class WarmupPlansTest {
    @Test
    void readsConfigurationFromProviderModule() {
        ToolProvider provider = provider("configured-probe");

        assertEquals("--warmup", WarmupPlans.find(provider)
                .orElseThrow());
    }

    @Test
    void metadataWithoutWarmupDoesNotDeclareWarmup() throws Exception {
        assertEquals(Optional.empty(), WarmupPlans.read(new ByteArrayInputStream("capabilities=compile\n".getBytes(StandardCharsets.UTF_8)), "test configuration"));
    }

    @Test
    void rejectsUnknownProperties() {
        String configuration = "warmup.unknown=true\n";

        var exception = assertThrows(LauncherConfigurationException.class, () -> WarmupPlans.read(new ByteArrayInputStream(configuration.getBytes(StandardCharsets.UTF_8)), "test configuration"));

        assertEquals("Unknown warmup property warmup.unknown in test configuration", exception.getMessage());
    }

    private static ToolProvider provider(String name) {
        return new ToolProvider() {
            @Override
            public String name() {
                return name;
            }

            @Override
            public int run(PrintWriter out, PrintWriter err, String... args) {
                return 0;
            }
        };
    }
}
