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

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.util.List;

import com.netflix.tools.ja.RuntimeImageHash;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RuntimeImageHashTest {
    @Test
    void persistsAStableRuntimeImageHash(@TempDir Path directory) throws Exception {
        var image = image(directory, "runtime", "modules");
        var cache = directory.resolve("cache");
        var first = new RuntimeImageHash(image, cache).hash();

        List<Path> entries;
        try (var files = Files.list(cache)) {
            entries = files.toList();
        }
        assertEquals(1, entries.size());
        var cacheTime = Files.getLastModifiedTime(entries.getFirst());
        var second = new RuntimeImageHash(image, cache).hash();

        assertEquals(first, second);
        assertEquals(cacheTime, Files.getLastModifiedTime(entries.getFirst()));
    }

    @Test
    void reusesTheRecordedHashWhileRuntimeImageFactsAreUnchanged(@TempDir Path directory) throws Exception {
        var image = image(directory, "runtime", "modules");
        var cache = directory.resolve("cache");
        var modules = image.resolve("lib/modules");
        var modified = Files.getLastModifiedTime(modules);
        var first = new RuntimeImageHash(image, cache).hash();

        Files.writeString(modules, "changed");
        Files.setLastModifiedTime(modules, modified);
        var second = new RuntimeImageHash(image, cache).hash();

        assertEquals(first, second);
    }

    @Test
    void replacesATruncatedRuntimeImageHashEntry(@TempDir Path directory) throws Exception {
        var image = image(directory, "runtime", "modules");
        var cache = directory.resolve("cache");
        var first = new RuntimeImageHash(image, cache).hash();
        Path entry;
        try (var files = Files.list(cache)) {
            entry = files.findFirst().orElseThrow();
        }
        Files.writeString(entry, "bad");

        var second = new RuntimeImageHash(image, cache).hash();

        assertEquals(first, second);
        assertTrue(Files.size(entry) > 3);
    }

    @Test
    void invalidatesChangedRuntimeImageContent(@TempDir Path directory) throws Exception {
        var image = image(directory, "runtime", "modules");
        var cache = directory.resolve("cache");
        var first = new RuntimeImageHash(image, cache).hash();

        var modules = image.resolve("lib/modules");
        var originalTime = Files.getLastModifiedTime(modules);
        Files.writeString(modules, "changed");
        Files.setLastModifiedTime(modules, FileTime.fromMillis(originalTime.toMillis() + 2_000));
        var modulesChanged = new RuntimeImageHash(image, cache).hash();

        Files.writeString(image.resolve("release"), "runtime=changed\n");
        var releaseChanged = new RuntimeImageHash(image, cache).hash();

        assertNotEquals(first, modulesChanged);
        assertNotEquals(modulesChanged, releaseChanged);
        try (var files = Files.list(cache)) {
            assertTrue(files.findAny()
                            .isPresent());
        }
    }

    private static Path image(Path directory, String release, String modules) throws Exception {
        var image = Files.createDirectories(directory.resolve("image"));
        Files.writeString(image.resolve("release"), release);
        var library = Files.createDirectories(image.resolve("lib"));
        Files.writeString(library.resolve("modules"), modules);
        return image;
    }
}
