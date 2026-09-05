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

import static com.netflix.tools.launcher.JdkOptionDescriptors.flag;

/** Options following conventions shared by JDK tools. */
public final class JdkToolOptions {
    private static final ToolOption HELP = flag("help", "--help", "Print help", "-h");
    private static final ToolOption VERBOSE = flag("verbose", "--verbose", "Enable verbose output");

    private JdkToolOptions() {}

    public static ToolOption help() {
        return HELP;
    }

    public static ToolOption verbose() {
        return VERBOSE;
    }
}
