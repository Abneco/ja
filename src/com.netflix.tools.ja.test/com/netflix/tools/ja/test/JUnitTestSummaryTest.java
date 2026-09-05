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

import com.netflix.tools.ja.JUnitTestSummary;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class JUnitTestSummaryTest {
    @Test
    void compactsTheFrameworkSummaryAndRetainsFailureDetails() {
        var output =
                """

                Failures (1):
                  JUnit Jupiter:ExampleTest:fails()
                    => expected: <true> but was: <false>

                Test run finished after 16740 ms
                [        45 containers found      ]
                [         0 containers skipped    ]
                [        45 containers started    ]
                [         0 containers aborted    ]
                [        45 containers successful ]
                [         0 containers failed     ]
                [       363 tests found           ]
                [         0 tests skipped         ]
                [       363 tests started         ]
                [         0 tests aborted         ]
                [       348 tests successful      ]
                [        15 tests failed          ]
                """;

        assertEquals(
                platformLines("""
                        Failures (1):
                          JUnit Jupiter:ExampleTest:fails()
                            => expected: <true> but was: <false>

                        Test run finished after 16740 ms
                          Containers: 47 found, 2 cached, 45 successful
                          Tests:      370 found, 7 cached, 348 successful, 15 failed
                        """),
                JUnitTestSummary.compact(output, 2, 7));
    }

    @Test
    void includesNonzeroSkippedAndAbortedOutcomes() {
        var output =
                """
                Test run finished after 42 ms
                [         3 containers found      ]
                [         1 containers skipped    ]
                [         2 containers started    ]
                [         1 containers aborted    ]
                [         1 containers successful ]
                [         0 containers failed     ]
                [         8 tests found           ]
                [         2 tests skipped         ]
                [         6 tests started         ]
                [         1 tests aborted         ]
                [         5 tests successful      ]
                [         0 tests failed          ]
                """;

        assertEquals(
                platformLines("""
                        Test run finished after 42 ms
                          Containers: 3 found, 1 skipped, 1 aborted, 1 successful
                          Tests:      8 found, 2 skipped, 1 aborted, 5 successful
                        """),
                JUnitTestSummary.compact(output, 0, 0));
    }

    @Test
    void reportsACompletelyCachedPlanWithoutAFrameworkRun() {
        assertEquals(
                platformLines("""
                        Test results
                          Containers: 1 found, 1 cached
                          Tests:      1 found, 1 cached
                        """),
                JUnitTestSummary.cached(1, 1));
    }

    private static String platformLines(String value) {
        return value.replace("\n", System.lineSeparator());
    }
}
