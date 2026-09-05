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

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import com.netflix.tools.ja.JUnitTestResults;
import com.netflix.tools.ja.TestResult.Status;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.junit.jupiter.api.Assertions.assertEquals;

class JUnitTestResultsTest {
    @Test
    void readsOnlyExactSuccessfulAndFailedMethods(@TempDir Path directory) throws Exception {
        Files.writeString(directory.resolve("TEST-junit-jupiter.xml"),
                """
                <testsuite>
                  <testcase classname="example.ExampleTest" name="passes(String[])">
                    <system-out>unique-id: [engine:junit-jupiter]/[class:example.ExampleTest]/[method:passes(java.lang.String[])]</system-out>
                  </testcase>
                  <testcase classname="example.ExampleTest" name="fails()">
                    <failure message="failed"/>
                    <system-out>unique-id: [engine:junit-jupiter]/[class:example.ExampleTest]/[method:fails()]</system-out>
                  </testcase>
                  <testcase classname="example.ExampleTest" name="skips()">
                    <skipped/>
                    <system-out>unique-id: [engine:junit-jupiter]/[class:example.ExampleTest]/[method:skips()]</system-out>
                  </testcase>
                  <testcase classname="example.ExampleTest" name="other()">
                    <system-out>unique-id: [engine:junit-jupiter]/[class:example.ExampleTest]/[method:other()]</system-out>
                  </testcase>
                </testsuite>
                """);

        assertEquals(
                Map.of(
                "example.ExampleTest#passes(java.lang.String[])", Status.SUCCESS,
                "example.ExampleTest#fails()", Status.FAILURE),
                JUnitTestResults.read(directory, List.of("example.ExampleTest#passes(java.lang.String[])", "example.ExampleTest#fails()", "example.ExampleTest#skips()")));
    }

    @Test
    void readsFrameworkInvocationsAsMethodResults(@TempDir Path directory) throws Exception {
        Files.writeString(directory.resolve("TEST-junit-jupiter.xml"),
                """
                <testsuite>
                  <testcase classname="example.ExampleTest">
                    <system-out>unique-id: [engine:junit-jupiter]/[class:example.ExampleTest]/[test-template:repeated(java.lang.String[], int)]/[test-template-invocation:#1]</system-out>
                  </testcase>
                  <testcase classname="example.ExampleTest">
                    <error message="failed"/>
                    <system-out>unique-id: [engine:junit-jupiter]/[class:example.ExampleTest]/[test-template:repeated(java.lang.String[], int)]/[test-template-invocation:#2]</system-out>
                  </testcase>
                  <testcase classname="example.ExampleTest">
                    <system-out>unique-id: [engine:junit-jupiter]/[class:example.ExampleTest]/[test-factory:factory()]/[dynamic-test:#1]</system-out>
                  </testcase>
                </testsuite>
                """);

        assertEquals(
                Map.of("example.ExampleTest#repeated(java.lang.String[],int)", Status.FAILURE, "example.ExampleTest#factory()", Status.SUCCESS),
                JUnitTestResults.read(directory, List.of("example.ExampleTest#repeated(java.lang.String[],int)", "example.ExampleTest#factory()")));
    }
}
