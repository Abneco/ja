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

import java.lang.classfile.ClassFile;
import java.lang.classfile.ClassModel;
import java.lang.classfile.ClassTransform;
import java.lang.classfile.CodeModel;
import java.lang.classfile.MethodModel;
import java.lang.classfile.MethodTransform;
import java.util.Collection;
import java.util.Comparator;
import java.util.IdentityHashMap;
import java.util.Map;

/** Hashes the methods and class structure observed during a test execution. */
final class ObservedCodeHash {
    private static final ClassFile CLASS_FILE = ClassFile.of();

    private final Map<MethodModel, byte[]> methodContent = new IdentityHashMap<>();
    private final Map<ClassModel, byte[]> classContent = new IdentityHashMap<>();

    String hash(Collection<MethodModel> methods, Collection<ClassModel> classes) {
        var digest = new Sha256();
        methods.stream()
                .distinct()
                .sorted(Comparator.comparing(ObservedCodeHash::methodName))
                .forEach(
                        method ->
                        add(
                                digest,
                                "method",
                                methodName(method),
                                methodContent.computeIfAbsent(
                                        method,
                                        candidate -> CLASS_FILE.build(
                                                candidate.parent()
                                                         .orElseThrow()
                                                         .thisClass()
                                                         .asSymbol(),
                                                builder -> builder.transformMethod(candidate, MethodTransform.ACCEPT_ALL)))));
        classes.stream()
                .distinct()
                .sorted(Comparator.comparing(ObservedCodeHash::className))
                .forEach(model -> {
                    var content = classContent.computeIfAbsent(
                            model,
                            candidate ->
                                    CLASS_FILE.build(
                                            candidate.thisClass().asSymbol(),
                                            builder -> builder.transform(candidate, ClassTransform.transformingMethods(MethodTransform.dropping(CodeModel.class::isInstance)))));
                    add(digest, "class", className(model), content);
                });
        return digest.hex();
    }

    private static String methodName(MethodModel method) {
        return className(method.parent()
                               .orElseThrow())
                + "."
                + method.methodName().stringValue()
                + method.methodType().stringValue();
    }

    private static String className(ClassModel model) {
        return model.thisClass().asInternalName();
    }

    private static void add(Sha256 digest, String kind, String name,
                            byte[] content) {
        digest.add(kind)
              .add(name)
              .add(content);
    }
}
