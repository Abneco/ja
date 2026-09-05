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

import com.netflix.tools.launcher.ToolOption.Group;

import static com.netflix.tools.launcher.JdkOptionDescriptors.flag;
import static com.netflix.tools.launcher.JdkOptionDescriptors.group;
import static com.netflix.tools.launcher.JdkOptionDescriptors.option;

/** Standard JDK options used when compiling modules and source files. */
public final class JdkCompilationOptions {
    private static final ToolOption PROCESSOR_MODULE_PATH = option("processor-module-path", "--processor-module-path", "PATH", "Where to find annotation processors");
    private static final ToolOption MODULE_SOURCE_PATH = option("module-source-path", "--module-source-path", "PATH", "Where to find module sources");
    private static final ToolOption SOURCE_PATH = option("source-path", "--source-path", "PATH", "Where to find source files");
    private static final ToolOption RELEASE = option("release", "--release", "RELEASE", "Compile for the specified Java release");
    private static final ToolOption ENABLE_PREVIEW = flag("enable-preview", "--enable-preview", "Enable preview language features");
    private static final ToolOption DESTINATION = option("d", "-d", "DIRECTORY", "Write output to DIRECTORY");
    private static final Group PATHS = group(JdkModuleOptions.modulePath(), PROCESSOR_MODULE_PATH, JdkModuleOptions.upgradeModulePath(),
            JdkModuleOptions.patchModule(), MODULE_SOURCE_PATH, SOURCE_PATH);

    private JdkCompilationOptions() {}

    public static ToolOption processorModulePath() {
        return PROCESSOR_MODULE_PATH;
    }

    public static ToolOption moduleSourcePath() {
        return MODULE_SOURCE_PATH;
    }

    public static ToolOption sourcePath() {
        return SOURCE_PATH;
    }

    public static ToolOption release() {
        return RELEASE;
    }

    public static ToolOption enablePreview() {
        return ENABLE_PREVIEW;
    }

    public static ToolOption destination() {
        return DESTINATION;
    }

    public static Group paths() {
        return PATHS;
    }
}
