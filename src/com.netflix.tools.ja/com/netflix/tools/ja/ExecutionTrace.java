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

import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BiConsumer;
import java.util.stream.Collectors;

import com.netflix.tools.ja.ExecutionTrace.Event;

/** Methods and receiver classes observed in JUnit execution scopes. */
public final class ExecutionTrace implements BiConsumer<String, Object> {
    public record Event(String method, String receiverModule, String receiverClass) {}

    private final Map<String, Set<Event>> events = new ConcurrentHashMap<>();

    @Override
    public void accept(String method, Object receiver) {
        var execution = ExecutionTraceInstrumentation.currentExecution().orElseThrow(() -> new IllegalStateException("instrumented code executed outside an execution trace scope"));
        String receiverModule = null;
        String receiverClass = null;
        var type = receiverType(receiver);
        if (type != null) {
            receiverModule = type.getModule().getName();
            receiverClass = type.getName();
        }
        events.computeIfAbsent(execution, _ -> ConcurrentHashMap.newKeySet()).add(new Event(method, receiverModule, receiverClass));
    }

    static Class<?> receiverType(Object receiver) {
        return switch (receiver) {
            case null -> null;
            case Class<?> type -> type;
            default -> receiver.getClass();
        };
    }

    Set<String> containers() {
        return events.keySet().stream()
                .map(ExecutionTrace::container)
                .flatMap(Optional::stream)
                .collect(Collectors.toUnmodifiableSet());
    }

    private static Optional<String> container(String execution) {
        var prefix = "/[class:";
        int start = execution.lastIndexOf(prefix);
        if (start < 0) {
            return Optional.empty();
        }
        int content = start + prefix.length();
        int end = execution.indexOf(']', content);
        return end < 0 ? Optional.empty() : Optional.of(execution.substring(content, end));
    }

    public Set<Event> events(String execution) {
        var result = new LinkedHashSet<Event>();
        for (var entry : events.entrySet()) {
            if (isAncestor(entry.getKey(), execution)) {
                result.addAll(entry.getValue());
            }
        }
        return Set.copyOf(result);
    }

    public Set<Event> eventsFor(String selector) {
        int separator = selector.indexOf('#');
        if (separator < 1 || separator == selector.length() - 1) {
            throw new IllegalArgumentException("Invalid test selector: " + selector);
        }
        var classSegment = "/[class:" + selector.substring(0, separator) + "]";
        var method = selector.substring(separator + 1);
        var methodSegments = Set.of("/[method:" + method + "]", "/[test-template:" + method + "]", "/[test-factory:" + method + "]");
        var result = new LinkedHashSet<Event>();
        var methodExecutions = events.keySet().stream()
                .filter(execution -> execution.contains(classSegment))
                .filter(execution -> methodSegments.stream().anyMatch(execution::contains))
                .toList();
        if (methodExecutions.isEmpty()) {
            events.keySet().stream()
                    .filter(execution -> execution.endsWith(classSegment))
                    .forEach(execution -> result.addAll(events(execution)));
        } else {
            methodExecutions.forEach(execution -> result.addAll(events(execution)));
        }
        return Set.copyOf(result);
    }

    public Set<String> executions() {
        return Set.copyOf(events.keySet());
    }

    private static boolean isAncestor(String candidate, String execution) {
        return candidate.equals("[execution-trace]")
                || candidate.equals("[test-plan]")
                || execution.equals(candidate)
                || execution.startsWith(candidate + "/");
    }
}
