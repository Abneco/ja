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

import java.io.InputStream;
import java.io.OutputStream;
import java.io.PrintWriter;
import java.util.List;
import java.util.Set;
import java.util.spi.ToolProvider;
import javax.lang.model.SourceVersion;
import javax.tools.Tool;

import com.netflix.tools.launcher.CommandLine;
import com.netflix.tools.launcher.CompletionRequest;
import com.netflix.tools.launcher.DescribedTool;
import com.netflix.tools.launcher.DescribedToolProvider;
import com.netflix.tools.launcher.JdkModuleOptions;
import com.netflix.tools.launcher.ToolInvocation;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;

class DescribedToolProviderTest {
    @Test
    void remainsAStandardToolProviderAndDerivesItsOptionChecker() {
        DescribedToolProvider provider = new Probe();

        assertInstanceOf(ToolProvider.class, provider);
        assertEquals("probe", provider.name());
        assertEquals(1, provider.isSupportedOption("--module-path"));
        assertEquals(-1, provider.isSupportedOption("--module-source-path"));
        assertEquals(List.of("--module-path"),
                provider.complete(new CompletionRequest(ToolInvocation.of(), "--mod")).stream()
                        .map(completion -> completion.value())
                        .toList());
    }

    @Test
    void describedToolRemainsAStandardStreamOrientedTool() {
        DescribedTool tool = new StreamProbe();

        assertInstanceOf(Tool.class, tool);
        assertEquals("stream-probe", tool.name());
        assertEquals(1, tool.isSupportedOption("--module-path"));
    }

    private static final class Probe implements DescribedToolProvider {
        private static final CommandLine COMMAND_LINE = CommandLine.builder()
                .option(JdkModuleOptions.modulePath())
                .build();

        @Override
        public String name() {
            return "probe";
        }

        @Override
        public CommandLine commandLine() {
            return COMMAND_LINE;
        }

        @Override
        public int run(PrintWriter out, PrintWriter err, String... args) {
            return 0;
        }
    }

    private static final class StreamProbe implements DescribedTool {
        private static final CommandLine COMMAND_LINE = CommandLine.builder()
                .option(JdkModuleOptions.modulePath())
                .build();

        @Override
        public String name() {
            return "stream-probe";
        }

        @Override
        public CommandLine commandLine() {
            return COMMAND_LINE;
        }

        @Override
        public int run(InputStream in, OutputStream out, OutputStream err,
                       String... arguments) {
            return 0;
        }

        @Override
        public Set<SourceVersion> getSourceVersions() {
            return Set.of();
        }
    }
}
