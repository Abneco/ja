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
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.stream.Collectors;
import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;

import org.w3c.dom.Element;
import org.xml.sax.SAXException;

/** Reads exact method results from JUnit's legacy XML reports. */
public final class JUnitTestResults {
    private static final List<String> METHOD_SEGMENTS = List.of("method", "test-template", "test-factory");

    private enum Status {
        SUCCESS,
        SKIPPED,
        FAILURE
    }

    private JUnitTestResults() {}

    public static Map<String, TestResult.Status> read(Path directory, Collection<String> selected) throws IOException {
        if (!Files.isDirectory(directory)) {
            return Map.of();
        }
        var selectors = Set.copyOf(selected);
        var results = new HashMap<String, Status>();
        try (var files = Files.list(directory)) {
            for (var report : files.filter(Files::isRegularFile)
                                   .filter(path -> path.getFileName()
                                                       .toString()
                                                       .endsWith(".xml"))
                                   .sorted()
                                   .toList()) {
                read(report, selectors, results);
            }
        }
        return results.entrySet().stream()
                .filter(entry -> entry.getValue() != Status.SKIPPED)
                .collect(Collectors.toUnmodifiableMap(Entry::getKey,
                        entry -> entry.getValue() == Status.SUCCESS ? TestResult.Status.SUCCESS : TestResult.Status.FAILURE));
    }

    private static void read(Path report, Set<String> selected, Map<String, Status> results) throws IOException {
        try (var input = Files.newInputStream(report)) {
            var factory = DocumentBuilderFactory.newDefaultInstance();
            factory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
            factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_DTD, "");
            factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_SCHEMA, "");
            var cases = factory.newDocumentBuilder()
                               .parse(input)
                               .getElementsByTagName("testcase");
            for (int i = 0; i < cases.getLength(); i++) {
                var testCase = (Element) cases.item(i);
                var selector = selector(testCase);
                if (selector != null && selected.contains(selector)) {
                    results.merge(selector, status(testCase), JUnitTestResults::merge);
                }
            }
        } catch (ParserConfigurationException | SAXException failure) {
            throw new IOException("Unable to read JUnit report " + report, failure);
        }
    }

    private static String selector(Element testCase) {
        var outputs = testCase.getElementsByTagName("system-out");
        for (int i = 0; i < outputs.getLength(); i++) {
            for (var line : outputs.item(i)
                                   .getTextContent()
                                   .lines()
                                   .toList()) {
                for (var segment : METHOD_SEGMENTS) {
                    var prefix = "/[" + segment + ":";
                    int method = line.lastIndexOf(prefix);
                    if (method < 0) {
                        continue;
                    }
                    int contentStart = method + prefix.length();
                    int nextSegment = line.indexOf("]/[", contentStart);
                    int end = nextSegment >= 0 ? nextSegment : line.lastIndexOf(']');
                    if (end > contentStart) {
                        var signature = line.substring(contentStart, end).replace(" ", "");
                        return testCase.getAttribute("classname") + "#" + signature;
                    }
                }
            }
        }
        return null;
    }

    private static Status status(Element testCase) {
        if (testCase.getElementsByTagName("failure").getLength() > 0 || testCase.getElementsByTagName("error").getLength() > 0) {
            return Status.FAILURE;
        }
        if (testCase.getElementsByTagName("skipped").getLength() > 0) {
            return Status.SKIPPED;
        }
        return Status.SUCCESS;
    }

    private static Status merge(Status first, Status second) {
        if (first == Status.FAILURE || second == Status.FAILURE) {
            return Status.FAILURE;
        }
        if (first == Status.SKIPPED || second == Status.SKIPPED) {
            return Status.SKIPPED;
        }
        return Status.SUCCESS;
    }
}
