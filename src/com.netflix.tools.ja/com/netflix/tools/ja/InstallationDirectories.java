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

import java.nio.file.Path;
import java.util.Locale;
import java.util.Map;

/**
 * Identifies the platform-specific directories used for installed images and
 * commands.
 */
public record InstallationDirectories(Path applications, Path commands) {
    private static final String APPLICATION_ID = "com.netflix.tools.ja";

    public InstallationDirectories {
        applications = applications.toAbsolutePath().normalize();
        commands = commands.toAbsolutePath().normalize();
    }

    static InstallationDirectories defaults() {
        return forPlatform(System.getProperty("os.name"), Path.of(System.getProperty("user.home")), System.getenv());
    }

    public static InstallationDirectories forPlatform(String operatingSystem, Path userHome, Map<String, String> environment) {
        String applicationOverride = environment.get("JA_INSTALL_HOME");
        Path dataHome = path(environment.get("XDG_DATA_HOME"));
        Path commands = path(environment.get("JA_BIN_HOME"));
        if (commands == null) {
            commands = path(environment.get("XDG_BIN_HOME"));
        }
        if (commands == null) {
            commands = dataHome == null ? userHome.resolve(".local/bin") : dataHome.resolveSibling("bin");
        }

        if (operatingSystem.toLowerCase(Locale.ROOT).contains("win")) {
            Path root = path(applicationOverride);
            if (root == null) {
                Path localApplicationData = path(environment.get("LOCALAPPDATA"));
                root = (localApplicationData == null ? userHome : localApplicationData).resolve("Programs").resolve(APPLICATION_ID);
            }
            return new InstallationDirectories(root, commands);
        }

        Path applications = path(applicationOverride);
        if (applications == null) {
            applications = (dataHome == null ? userHome.resolve(".local/share") : dataHome).resolve(APPLICATION_ID);
        }
        return new InstallationDirectories(applications, commands);
    }

    private static Path path(String value) {
        return value == null || value.isBlank()
                ? null
                : Path.of(value);
    }
}
