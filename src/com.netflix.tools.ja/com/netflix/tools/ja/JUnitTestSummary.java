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

import java.util.EnumMap;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/** Compact rendering of the JUnit Platform execution summary. */
public final class JUnitTestSummary {
    private static final String LINE_SEPARATOR = System.lineSeparator();
    private static final Pattern HEADING = Pattern.compile("(?m)^Test run finished after (\\d+) ms\\R");
    private static final Pattern COUNT = Pattern.compile("^\\[\\s*(\\d+) (containers|tests) (found|skipped|started|aborted|successful|failed)\\s*]$");

    private enum Outcome {
        FOUND,
        SKIPPED,
        STARTED,
        ABORTED,
        SUCCESSFUL,
        FAILED
    }

    private JUnitTestSummary() {}

    /** Replaces a JUnit summary table with the compact test result layout. */
    public static String compact(String output, int cachedContainers, int cachedTests) {
        requireNonnegative(cachedContainers, "cachedContainers");
        requireNonnegative(cachedTests, "cachedTests");
        var heading = HEADING.matcher(output);
        if (!heading.find()) {
            return output;
        }
        var containers = new EnumMap<Outcome, Integer>(Outcome.class);
        var tests = new EnumMap<Outcome, Integer>(Outcome.class);
        for (var line : output.substring(heading.end())
                              .lines()
                              .toList()) {
            var count = COUNT.matcher(line);
            if (!count.matches()) {
                continue;
            }
            var target = count.group(2).equals("containers") ? containers : tests;
            target.put(Outcome.valueOf(count.group(3).toUpperCase(Locale.ROOT)),
                    Integer.parseInt(count.group(1)));
        }
        if (!containers.containsKey(Outcome.FOUND) || !tests.containsKey(Outcome.FOUND)) {
            return output;
        }

        var result = new StringBuilder();
        var prefix = output.substring(0, heading.start()).strip();
        if (!prefix.isEmpty()) {
            result.append(prefix.lines().collect(Collectors.joining(LINE_SEPARATOR)))
                  .append(LINE_SEPARATOR)
                  .append(LINE_SEPARATOR);
        }
        result.append("Test run finished after ")
              .append(heading.group(1))
              .append(" ms")
              .append(LINE_SEPARATOR);
        append(result, "Containers", containers, cachedContainers);
        append(result, "Tests", tests, cachedTests);
        return result.toString();
    }

    /** Renders a result when every discovered test method was cached. */
    public static String cached(int containers, int tests) {
        requireNonnegative(containers, "containers");
        requireNonnegative(tests, "tests");
        return "Test results"
                + LINE_SEPARATOR
                + "  Containers: "
                + containers
                + " found, "
                + containers
                + " cached"
                + LINE_SEPARATOR
                + "  Tests:      "
                + tests
                + " found, "
                + tests
                + " cached"
                + LINE_SEPARATOR;
    }

    private static void append(StringBuilder result, String label, Map<Outcome, Integer> counts,
            int cached) {
        result.append("  ")
              .append(label)
              .append(':')
              .append(" ".repeat("Containers".length() - label.length() + 1))
              .append(counts.get(Outcome.FOUND) + cached)
              .append(" found");
        if (cached > 0) {
            result.append(", ")
                  .append(cached)
                  .append(" cached");
        }
        appendNonzero(result, counts, Outcome.SKIPPED, "skipped");
        appendNonzero(result, counts, Outcome.ABORTED, "aborted");
        appendNonzero(result, counts, Outcome.SUCCESSFUL, "successful");
        appendNonzero(result, counts, Outcome.FAILED, "failed");
        result.append(LINE_SEPARATOR);
    }

    private static void requireNonnegative(int value, String name) {
        if (value < 0) {
            throw new IllegalArgumentException(name + " must not be negative");
        }
    }

    private static void appendNonzero(StringBuilder result, Map<Outcome, Integer> counts, Outcome outcome,
            String label) {
        int count = counts.getOrDefault(outcome, 0);
        if (count > 0) {
            result.append(", ")
                  .append(count)
                  .append(' ')
                  .append(label);
        }
    }
}
