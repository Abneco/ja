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

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.EOFException;
import java.io.IOException;
import java.io.UTFDataFormatException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.Optional;

/** A persistently memoized content identity for a modular runtime image. */
public final class RuntimeImageHash {
    private static final int CACHE_FORMAT = 1;
    private static final String IDENTITY_FORMAT = "runtime-image-v1";

    /**
     * Facts used to validate a cached content hash without rereading the image
     * file.
     */
    private record Facts(
            String modulesPath,
            String fileKey,
            long size,
            long modifiedSeconds,
            int modifiedNanos,
            long createdSeconds,
            int createdNanos) {}

    private record Entry(Facts facts, String modulesHash) {}

    private final Path javaHome;
    private final Path cache;

    public RuntimeImageHash(Path javaHome, Path cache) {
        this.javaHome = javaHome.toAbsolutePath().normalize();
        this.cache = cache.toAbsolutePath().normalize();
    }

    public String hash() throws IOException {
        var image = javaHome.toRealPath();
        var modules = image.resolve("lib/modules").toRealPath();
        var release = image.resolve("release");
        if (!Files.isRegularFile(release)) {
            throw new IOException("Runtime image has no release file: " + release);
        }
        for (int attempt = 0; attempt < 3; attempt++) {
            var releaseHash = fileHash(release);
            var before = facts(modules);
            var path = cache.resolve(pathHash(before.modulesPath()));
            var existing = read(path);
            if (existing.filter(entry -> entry.facts().equals(before)).isPresent()) {
                var after = facts(modules);
                if (before.equals(after) && releaseHash.equals(fileHash(release))) {
                    return identity(releaseHash, existing.orElseThrow()
                            .modulesHash());
                }
                continue;
            }
            var modulesHash = fileHash(modules);
            var after = facts(modules);
            if (!before.equals(after) || !releaseHash.equals(fileHash(release))) {
                continue;
            }
            write(path, new Entry(before, modulesHash));
            return identity(releaseHash, modulesHash);
        }
        throw new IOException("Runtime image changed while it was being inspected: " + javaHome);
    }

    private static Facts facts(Path modules) throws IOException {
        var attributes = Files.readAttributes(modules, BasicFileAttributes.class);
        if (!attributes.isRegularFile()) {
            throw new IOException("Runtime system modules are not a regular file: " + modules);
        }
        var modified = attributes.lastModifiedTime().toInstant();
        var created = attributes.creationTime().toInstant();
        return new Facts(
                modules.toString(),
                attributes.fileKey() == null ? "" : attributes.fileKey().toString(),
                attributes.size(),
                modified.getEpochSecond(),
                modified.getNano(),
                created.getEpochSecond(),
                created.getNano());
    }

    private static Optional<Entry> read(Path path) throws IOException {
        if (!Files.isRegularFile(path)) {
            return Optional.empty();
        }
        try (var input = new DataInputStream(Files.newInputStream(path))) {
            if (input.readInt() != CACHE_FORMAT) {
                return Optional.empty();
            }
            var facts = new Facts(
                    input.readUTF(),
                    input.readUTF(),
                    input.readLong(),
                    input.readLong(),
                    input.readInt(),
                    input.readLong(),
                    input.readInt());
            var entry = new Entry(facts, input.readUTF());
            return input.read() == -1 && entry.modulesHash().matches("[0-9a-f]{64}")
                    ? Optional.of(entry)
                    : Optional.empty();
        } catch (EOFException | UTFDataFormatException _) {
            return Optional.empty();
        }
    }

    private static void write(Path target, Entry entry) throws IOException {
        Files.createDirectories(target.getParent());
        var temporary = Files.createTempFile(target.getParent(), ".runtime-image-", ".tmp");
        try {
            try (var output = new DataOutputStream(Files.newOutputStream(temporary))) {
                var facts = entry.facts();
                output.writeInt(CACHE_FORMAT);
                output.writeUTF(facts.modulesPath());
                output.writeUTF(facts.fileKey());
                output.writeLong(facts.size());
                output.writeLong(facts.modifiedSeconds());
                output.writeInt(facts.modifiedNanos());
                output.writeLong(facts.createdSeconds());
                output.writeInt(facts.createdNanos());
                output.writeUTF(entry.modulesHash());
            }
            try {
                Files.move(temporary, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            } catch (AtomicMoveNotSupportedException _) {
                Files.move(temporary, target, StandardCopyOption.REPLACE_EXISTING);
            }
        } finally {
            Files.deleteIfExists(temporary);
        }
    }

    private static String identity(String releaseHash, String modulesHash) {
        return new Sha256()
                .add(IDENTITY_FORMAT)
                .add(releaseHash)
                .add(modulesHash)
                .hex();
    }

    private static String pathHash(String path) {
        return Sha256.hash(path);
    }

    private static String fileHash(Path path) throws IOException {
        return Sha256.hash(path);
    }
}
