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

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.regex.Pattern;

import com.netflix.tools.ja.RequireRequest.UpdatePolicy;

/** Parses and orders semantic versions for stable dependency selection. */
record SemanticVersion(String value, BigInteger major, BigInteger minor,
                       BigInteger patch, List<String> prerelease)
        implements Comparable<SemanticVersion> {
    private static final Pattern VERSION = Pattern.compile("^(0|[1-9][0-9]*)\\.(0|[1-9][0-9]*)\\.(0|[1-9][0-9]*)(?:-([0-9A-Za-z-]+(?:\\.[0-9A-Za-z-]+)*))?(?:" + "\\+[0-9A-Za-z-]+(?:\\.[0-9A-Za-z-]+)*)?$");
    private static final Pattern NUMERIC = Pattern.compile("[0-9]+");

    SemanticVersion {
        prerelease = List.copyOf(prerelease);
    }

    static Optional<SemanticVersion> parse(String value) {
        var matcher = VERSION.matcher(value);
        if (!matcher.matches()) {
            return Optional.empty();
        }
        var prerelease = new ArrayList<String>();
        if (matcher.group(4) != null) {
            for (var identifier : matcher.group(4).split("\\.")) {
                if (NUMERIC.matcher(identifier).matches() && identifier.length() > 1 && identifier.startsWith("0")) {
                    return Optional.empty();
                }
                prerelease.add(identifier);
            }
        }
        return Optional.of(new SemanticVersion(value, new BigInteger(matcher.group(1)), new BigInteger(matcher.group(2)),
                new BigInteger(matcher.group(3)), prerelease));
    }

    static String latest(String current, List<String> candidates, UpdatePolicy policy) {
        var parsedBaseline = parse(current);
        if (parsedBaseline.isEmpty()) {
            return latestNonSemantic(candidates).orElse(current);
        }
        var baseline = parsedBaseline.get();
        return candidates.stream()
                .map(SemanticVersion::parse)
                .flatMap(Optional::stream)
                .filter(candidate -> candidate.compareTo(baseline) > 0)
                .filter(candidate -> baseline.prerelease().isEmpty() ? candidate.prerelease().isEmpty() : true)
                .filter(candidate -> permitted(baseline, candidate, policy))
                .max(Comparator.naturalOrder())
                .map(SemanticVersion::value)
                .orElse(current);
    }

    static Optional<String> latestAvailable(List<String> candidates) {
        var latest = candidates.stream()
                .map(SemanticVersion::parse)
                .flatMap(Optional::stream)
                .filter(candidate -> candidate.prerelease().isEmpty())
                .max(Comparator.naturalOrder())
                .map(SemanticVersion::value);
        return latest.isPresent() ? latest : latestNonSemantic(candidates);
    }

    private static Optional<String> latestNonSemantic(List<String> candidates) {
        if (candidates.isEmpty() || candidates.stream().anyMatch(candidate -> parse(candidate).isPresent())) {
            return Optional.empty();
        }
        // Jig reports versions in ascending repository version order.
        return Optional.of(candidates.getLast());
    }

    private static boolean permitted(SemanticVersion current, SemanticVersion candidate, UpdatePolicy policy) {
        return switch (policy) {
            case NONE -> false;
            case PATCH -> candidate.major().equals(current.major()) && candidate.minor().equals(current.minor());
            case MINOR -> current.major().compareTo(BigInteger.ONE) <= 0 ? candidate.major().compareTo(BigInteger.ONE) <= 0 : candidate.major().equals(current.major());
            case MAJOR -> true;
        };
    }

    @Override
    public int compareTo(SemanticVersion other) {
        var comparison = major.compareTo(other.major);
        if (comparison != 0) {
            return comparison;
        }
        comparison = minor.compareTo(other.minor);
        if (comparison != 0) {
            return comparison;
        }
        comparison = patch.compareTo(other.patch);
        if (comparison != 0) {
            return comparison;
        }
        if (prerelease.isEmpty()) {
            return other.prerelease.isEmpty() ? 0 : 1;
        }
        if (other.prerelease.isEmpty()) {
            return -1;
        }
        var common = Math.min(prerelease.size(), other.prerelease.size());
        for (int i = 0; i < common; i++) {
            comparison = compareIdentifier(prerelease.get(i), other.prerelease.get(i));
            if (comparison != 0) {
                return comparison;
            }
        }
        return Integer.compare(prerelease.size(), other.prerelease.size());
    }

    private static int compareIdentifier(String left, String right) {
        var leftNumeric = NUMERIC.matcher(left).matches();
        var rightNumeric = NUMERIC.matcher(right).matches();
        if (leftNumeric && rightNumeric) {
            return new BigInteger(left).compareTo(new BigInteger(right));
        }
        if (leftNumeric) {
            return -1;
        }
        if (rightNumeric) {
            return 1;
        }
        return left.compareTo(right);
    }
}
