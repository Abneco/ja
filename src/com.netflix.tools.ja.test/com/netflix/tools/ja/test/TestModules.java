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

import java.io.IOException;
import java.lang.classfile.ClassFile;
import java.lang.classfile.attribute.ModuleAttribute;
import java.lang.constant.ClassDesc;
import java.lang.constant.ModuleDesc;
import java.lang.reflect.AccessFlag;
import java.lang.reflect.ClassFileFormatVersion;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;

final class TestModules {
    private TestModules() {}

    static Path writeJar(Path path, String moduleName, String... requires) throws IOException {
        return writeJar(path, moduleName, Set.of(), requires);
    }

    static Path writeJarWithStatic(Path path, String moduleName, String... staticRequires) throws IOException {
        return writeJar(path, moduleName, Set.of(staticRequires));
    }

    static Path writeJarWithTransitive(Path path, String moduleName, String transitiveRequires) throws IOException {
        try (var output = new JarOutputStream(Files.newOutputStream(path))) {
            output.putNextEntry(new JarEntry("module-info.class"));
            var attribute = ModuleAttribute.of(ModuleDesc.of(moduleName),
                    builder -> {
                        builder.requires(ModuleDesc.of("java.base"), Set.of(AccessFlag.MANDATED), null);
                        builder.requires(ModuleDesc.of(transitiveRequires), Set.of(AccessFlag.TRANSITIVE), null);
                    });
            output.write(ClassFile.of()
                    .buildModule(attribute, builder -> builder.withVersion(ClassFileFormatVersion.RELEASE_9.major(), 0)));
            output.closeEntry();
        }
        return path;
    }

    private static Path writeJar(Path path, String moduleName, Set<String> staticRequires,
            String... requires)
            throws IOException {
        try (var output = new JarOutputStream(Files.newOutputStream(path))) {
            output.putNextEntry(new JarEntry("module-info.class"));
            output.write(moduleInfo(moduleName, staticRequires, requires));
            output.closeEntry();
        }
        return path;
    }

    static Path writeAutomaticJar(Path path) throws IOException {
        try (var _ = new JarOutputStream(Files.newOutputStream(path))) {
            return path;
        }
    }

    static void writeModuleInfo(Path directory, String moduleName, String... requires) throws IOException {
        Files.write(directory.resolve("module-info.class"), moduleInfo(moduleName, requires));
    }

    static void writeModuleInfo(Path directory, String moduleName, Set<String> staticRequires,
            String... requires)
            throws IOException {
        Files.write(directory.resolve("module-info.class"), moduleInfo(moduleName, staticRequires, requires));
    }

    static void writeModuleInfoWithProvider(Path directory, String moduleName, String service,
            String provider, String... requires)
            throws IOException {
        Files.write(directory.resolve("module-info.class"), moduleInfo(moduleName, Set.of(), service, provider, requires));
    }

    private static byte[] moduleInfo(String moduleName, String... requires) {
        return moduleInfo(moduleName, Set.of(), requires);
    }

    private static byte[] moduleInfo(String moduleName, Set<String> staticRequires, String... requires) {
        return moduleInfo(moduleName, staticRequires, null, null, requires);
    }

    private static byte[] moduleInfo(String moduleName, Set<String> staticRequires, String service,
            String provider, String... requires) {
        var dependencies = new LinkedHashSet<String>();
        dependencies.add("java.base");
        for (String dependency : requires) {
            dependencies.add(dependency);
        }
        var attribute = ModuleAttribute.of(ModuleDesc.of(moduleName),
                builder -> {
                    for (String dependency : dependencies) {
                        builder.requires(
                                ModuleDesc.of(dependency),
                                dependency.equals("java.base") ? Set.of(AccessFlag.MANDATED) : Set.of(),
                                null);
                    }
                    for (String dependency : staticRequires) {
                        builder.requires(ModuleDesc.of(dependency), Set.of(AccessFlag.STATIC_PHASE), null);
                    }
                    if (service != null) {
                        builder.provides(ClassDesc.of(service), ClassDesc.of(provider));
                    }
                });
        return ClassFile.of().buildModule(attribute, builder -> builder.withVersion(ClassFileFormatVersion.RELEASE_9.major(), 0));
    }
}
