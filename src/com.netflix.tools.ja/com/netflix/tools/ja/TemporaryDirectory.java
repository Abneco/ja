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

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;

/**
 * Owns the temporary directory layout used while materializing source modules.
 */
public final class TemporaryDirectory implements AutoCloseable {
    private final Path root;
    private final boolean deleteOnClose;

    private TemporaryDirectory(Path root, boolean deleteOnClose) {
        this.root = root.toAbsolutePath().normalize();
        this.deleteOnClose = deleteOnClose;
    }

    public static TemporaryDirectory create() throws IOException {
        return new TemporaryDirectory(Files.createTempDirectory("ja-"), true);
    }

    public static TemporaryDirectory unmanaged(Path root) {
        return new TemporaryDirectory(root, false);
    }

    public Path root() {
        return root;
    }

    public Path modules() {
        return root.resolve("modules");
    }

    @Override
    public void close() throws IOException {
        if (!deleteOnClose || !Files.exists(root)) {
            return;
        }
        try (var paths = Files.walk(root)) {
            for (Path path : paths.sorted(Comparator.reverseOrder()).toList()) {
                Files.deleteIfExists(path);
            }
        }
    }
}
