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

package com.netflix.tools.ja.test;

import java.nio.file.Path;
import java.util.Map;

import com.netflix.tools.ja.InstallationDirectories;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class InstallationDirectoriesTest {
    @Test
    void usesXdgLocationsOnUnix() {
        InstallationDirectories directories = InstallationDirectories.forPlatform("Mac OS X", Path.of("/Users/example"), Map.of("XDG_DATA_HOME", "/data/home", "XDG_BIN_HOME", "/commands"));

        assertEquals(Path.of("/data/home/com.netflix.tools.ja"), directories.applications());
        assertEquals(Path.of("/commands"), directories.commands());
    }

    @Test
    void usesLocalShareAndBinByDefaultOnUnix() {
        InstallationDirectories directories = InstallationDirectories.forPlatform("Linux", Path.of("/home/example"), Map.of());

        assertEquals(Path.of("/home/example/.local/share/com.netflix.tools.ja"), directories.applications());
        assertEquals(Path.of("/home/example/.local/bin"), directories.commands());
    }

    @Test
    void usesSiblingBinWhenOnlyXdgDataHomeIsSet() {
        InstallationDirectories directories = InstallationDirectories.forPlatform("Linux", Path.of("/home/example"), Map.of("XDG_DATA_HOME", "/prefix/data"));

        assertEquals(Path.of("/prefix/data/com.netflix.tools.ja"), directories.applications());
        assertEquals(Path.of("/prefix/bin"), directories.commands());
    }

    @Test
    void usesLocalProgramsAndSharedUserBinDirectoriesOnWindows() {
        Path userHome = Path.of("C:/Users/example");
        InstallationDirectories directories = InstallationDirectories.forPlatform("Windows 11", userHome, Map.of("LOCALAPPDATA", "C:/Users/example/AppData/Local"));

        Path root = Path.of("C:/Users/example/AppData/Local/Programs/com.netflix.tools.ja");
        assertEquals(root.toAbsolutePath().normalize(),
                directories.applications());
        assertEquals(userHome.resolve(".local/bin")
                             .toAbsolutePath()
                             .normalize(),
                directories.commands());
    }

    @Test
    void honorsXdgBinDirectoryOnWindows() {
        InstallationDirectories directories = InstallationDirectories.forPlatform("Windows 11", Path.of("C:/Users/example"), Map.of("LOCALAPPDATA", "C:/Users/example/AppData/Local", "XDG_BIN_HOME", "C:/Users/example/bin"));

        assertEquals(Path.of("C:/Users/example/bin")
                .toAbsolutePath()
                .normalize(),
                directories.commands());
    }
}
