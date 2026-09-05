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

/** Standard launcher arguments that authorize module runtime access. */
public final class ModuleRuntimeAccessArguments {
    private static final ToolOption ENABLE_NATIVE_ACCESS = option("enable-native-access", "--enable-native-access", "MODULE[,MODULE...]", "Allow restricted native access");
    private static final ToolOption ENABLE_FINAL_FIELD_MUTATION = option("enable-final-field-mutation", "--enable-final-field-mutation", "MODULE[,MODULE...]", "Allow final field mutation");
    private static final ToolOption ADD_OPENS = option("add-opens", "--add-opens", "MODULE/PACKAGE=TARGET", "Open a package to another module");
    private static final ToolOption ADD_EXPORTS = option("add-exports", "--add-exports", "MODULE/PACKAGE=TARGET", "Export a package to another module");
    private static final Group ALL = group(ENABLE_NATIVE_ACCESS, ENABLE_FINAL_FIELD_MUTATION, ADD_OPENS, ADD_EXPORTS);

    private ModuleRuntimeAccessArguments() {}

    public static ToolOption enableNativeAccess() {
        return ENABLE_NATIVE_ACCESS;
    }

    public static ToolOption enableFinalFieldMutation() {
        return ENABLE_FINAL_FIELD_MUTATION;
    }

    public static ToolOption addOpens() {
        return ADD_OPENS;
    }

    public static ToolOption addExports() {
        return ADD_EXPORTS;
    }

    public static Group all() {
        return ALL;
    }
}
