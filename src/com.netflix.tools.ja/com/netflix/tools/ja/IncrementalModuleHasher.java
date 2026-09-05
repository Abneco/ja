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
import java.lang.module.ModuleReference;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.HashSet;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.zip.ZipFile;

import com.netflix.module.ModuleHash;
import com.netflix.module.ModuleHash.Type;

/** Hashes the module content that is not represented by observed class code. */
final class IncrementalModuleHasher {
    private IncrementalModuleHasher() {}

    static ModuleHash moduleSha256(ModuleReference reference) throws IOException {
        var digest = new Sha256();
        try (var reader = reference.open();
             var names = reader.list()) {
            for (var name : names.filter(resource -> !resource.endsWith("/"))
                                 .filter(IncrementalModuleHasher::included)
                                 .sorted()
                                 .distinct()
                                 .toList()) {
                var input = reader.open(name);
                if (input.isPresent()) {
                    try (var stream = input.orElseThrow()) {
                        digest.add(name).add(stream.readAllBytes());
                    }
                }
            }
        }
        return moduleHash(Type.MODULE, digest);
    }

    static ModuleHash patchSha256(Path path) throws IOException {
        var digest = new Sha256();
        if (Files.isDirectory(path)) {
            try (var files = Files.walk(path)) {
                for (var file : files.filter(Files::isRegularFile)
                                     .sorted()
                                     .toList()) {
                    var resource = path.relativize(file)
                                       .toString()
                                       .replace(file.getFileSystem()
                                                    .getSeparator(),
                                               "/");
                    if (included(resource)) {
                        digest.add(resource).add(Files.readAllBytes(file));
                    }
                }
            }
        } else {
            hashJar(path, digest);
        }
        return moduleHash(Type.PATCH, digest);
    }

    private static void hashJar(Path path, Sha256 digest) throws IOException {
        try (var jar = new JarFile(path.toFile(), true, ZipFile.OPEN_READ, Runtime.version())) {
            var seen = new HashSet<String>();
            var entries = jar.versionedStream()
                             .filter(entry -> !entry.isDirectory())
                             .filter(entry -> included(entry.getName()))
                             .sorted(Comparator.comparing(JarEntry::getName))
                             .toList();
            for (var entry : entries) {
                if (seen.add(entry.getName())) {
                    try (var stream = jar.getInputStream(entry)) {
                        digest.add(entry.getName()).add(stream.readAllBytes());
                    }
                }
            }
        }
    }

    private static boolean included(String resource) {
        return !resource.endsWith(".class") || resource.equals("module-info.class") || !ExecutionTraceInstrumentation.isInstrumentable(resource);
    }

    private static ModuleHash moduleHash(Type type, Sha256 digest) {
        return new ModuleHash(type, "sha256", digest.hex());
    }
}
