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
import java.lang.classfile.Attributes;
import java.lang.classfile.MethodModel;
import java.lang.constant.ClassDesc;
import java.lang.constant.MethodTypeDesc;
import java.lang.reflect.AccessFlag;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/** Discovers supported Jupiter method roots from resolved root modules. */
final class TestDiscovery {
    private static final String JUPITER_MODULE_PREFIX = "org.junit.jupiter";
    private static final String TEST_ANNOTATION = "Lorg/junit/jupiter/api/Test;";
    private static final ClassDesc VOID = ClassDesc.ofDescriptor("V");
    private static final Comparator<TestMethod> ORDER = Comparator.comparing(TestMethod::moduleName)
            .thenComparing(TestMethod::className)
            .thenComparing(TestMethod::methodName)
            .thenComparing(TestMethod::descriptor);

    record TestMethod(String moduleName, String className, String methodName,
                      String descriptor, MethodModel model) {
        String container() {
            return className;
        }

        String selector() {
            var type = MethodTypeDesc.ofDescriptor(descriptor);
            var result = new StringBuilder(className)
                    .append('#')
                    .append(methodName)
                    .append('(');
            for (int i = 0; i < type.parameterCount(); i++) {
                if (i > 0) {
                    result.append(',');
                }
                result.append(qualifiedJavaType(type.parameterType(i)));
            }
            return result.append(')').toString();
        }

        private static String qualifiedJavaType(ClassDesc type) {
            var descriptor = type.descriptorString();
            int dimensions = 0;
            while (descriptor.charAt(dimensions) == '[') {
                dimensions++;
            }
            var component = descriptor.substring(dimensions);
            String name = component.charAt(0) == 'L' ? component.substring(1, component.length() - 1)
                    .replace('/', '.')
                    .replace('$', '.')
                    : ClassDesc.ofDescriptor(component)
                    .displayName()
                    .replace('$', '.');
            return name + "[]".repeat(dimensions);
        }
    }

    List<TestMethod> discover(List<String> rootModules, ResolvedClassModels classes) throws IOException {
        var tests = new ArrayList<TestMethod>();
        for (var module : rootModules) {
            if (!classes.isDirectoryModule(module) || classes.requirements(module).stream()
                    .noneMatch(TestDiscovery::isJupiterModule)) {
                continue;
            }
            for (var entry : classes.classes(module)) {
                var model = entry.model();
                var className = model.thisClass()
                                     .asInternalName()
                                     .replace('/', '.');
                for (var method : model.methods()) {
                    if (!isCacheableTestMethod(method, model.methods())) {
                        continue;
                    }
                    tests.add(
                            new TestMethod(
                                    module,
                                    className,
                                    method.methodName().stringValue(),
                                    method.methodType().stringValue(),
                                    method));
                }
            }
        }
        tests.sort(ORDER);
        return List.copyOf(tests);
    }

    private static boolean isJupiterModule(String moduleName) {
        return moduleName.equals(JUPITER_MODULE_PREFIX) || moduleName.startsWith(JUPITER_MODULE_PREFIX + ".");
    }

    private static boolean isCacheableTestMethod(MethodModel method, List<MethodModel> methods) {
        if (method.flags().has(AccessFlag.SYNTHETIC) || method.flags().has(AccessFlag.BRIDGE) || !method.methodTypeSymbol()
                .returnType()
                .equals(VOID)) {
            return false;
        }
        var name = method.methodName().stringValue();
        if (methods.stream()
                .filter(candidate -> candidate.methodName()
                        .stringValue()
                        .equals(name))
                .count()
                != 1) {
            return false;
        }
        return method.findAttribute(Attributes.runtimeVisibleAnnotations()).stream()
                .flatMap(attribute -> attribute.annotations().stream())
                .map(annotation -> annotation.className().stringValue())
                .anyMatch(TEST_ANNOTATION::equals);
    }
}
