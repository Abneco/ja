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

import static com.netflix.tools.launcher.JdkOptionDescriptors.group;
import static com.netflix.tools.launcher.JdkOptionDescriptors.option;

/** Standard JDK options for locating, selecting, and describing modules. */
public final class JdkModuleOptions {
    private static final ToolOption MODULE_PATH = option("module-path", "--module-path", "PATH", "Where to find application modules", "-p");
    private static final ToolOption UPGRADE_MODULE_PATH = option("upgrade-module-path", "--upgrade-module-path", "PATH", "Where to find upgradeable modules");
    private static final ToolOption PATCH_MODULE = option("patch-module", "--patch-module", "MODULE=PATH", "Override or augment a module");
    private static final ToolOption ADD_MODULES = option("add-modules", "--add-modules", "MODULE[,MODULE...]", "Root modules to resolve");
    private static final ToolOption DESCRIBE_MODULE = option("describe-module", "--describe-module", "MODULE", "Describe a module");
    private static final ToolOption MAIN_CLASS = option("main-class", "--main-class", "CLASS", "Set the main class");
    private static final ToolOption MODULE_VERSION = option("module-version", "--module-version", "VERSION", "Set the module version");
    private static final ToolOption MULTI_RELEASE = option("multi-release", "--multi-release", "VERSION", "Select a multi-release JAR version");
    private static final Group RUNTIME_PATHS = group(MODULE_PATH, UPGRADE_MODULE_PATH, PATCH_MODULE);

    private JdkModuleOptions() {}

    public static ToolOption modulePath() {
        return MODULE_PATH;
    }

    public static ToolOption upgradeModulePath() {
        return UPGRADE_MODULE_PATH;
    }

    public static ToolOption patchModule() {
        return PATCH_MODULE;
    }

    public static ToolOption addModules() {
        return ADD_MODULES;
    }

    public static ToolOption describeModule() {
        return DESCRIBE_MODULE;
    }

    public static ToolOption mainClass() {
        return MAIN_CLASS;
    }

    public static ToolOption moduleVersion() {
        return MODULE_VERSION;
    }

    public static ToolOption multiRelease() {
        return MULTI_RELEASE;
    }

    public static Group runtimePaths() {
        return RUNTIME_PATHS;
    }
}
