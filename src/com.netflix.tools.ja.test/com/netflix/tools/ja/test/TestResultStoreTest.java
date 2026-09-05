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

import java.nio.file.Path;
import java.util.List;
import java.util.Set;

import com.netflix.module.ModuleHash;
import com.netflix.module.ModuleHash.Type;
import com.netflix.tools.ja.ExecutionTrace.Event;
import com.netflix.tools.ja.ResolvedClassModels.ModuleState;
import com.netflix.tools.ja.TestExecution;
import com.netflix.tools.ja.TestResult;
import com.netflix.tools.ja.TestResult.Status;
import com.netflix.tools.ja.TestResultStore;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TestResultStoreTest {
    private static final String A = "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa";
    private static final String B = "bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb";

    @Test
    void reusesOnlyTheExactObservedExecution(@TempDir Path directory) throws Exception {
        var store = new TestResultStore(directory);
        var execution = execution("code", A, A);

        assertFalse(store.hasSuccessfulResult(execution));
        store.record(new TestResult(execution, Status.SUCCESS));

        assertTrue(store.hasSuccessfulResult(execution));
        assertFalse(store.hasSuccessfulResult(execution("changed", A, A)));
        assertFalse(store.hasSuccessfulResult(execution("code", A, B)));
        assertFalse(store.hasSuccessfulResult(execution("code", B, A)));
    }

    @Test
    void aFailureInvalidatesEarlierSuccessfulExecutions(@TempDir Path directory) throws Exception {
        var store = new TestResultStore(directory);
        var first = execution("code-a", A, A);
        var second = execution("code-b", A, A);
        var third = execution("code-c", A, A);
        store.record(new TestResult(first, Status.SUCCESS));
        store.record(new TestResult(second, Status.FAILURE));
        store.record(new TestResult(third, Status.SUCCESS));

        assertFalse(store.hasSuccessfulResult(first));
        assertTrue(store.hasSuccessfulResult(third));
    }

    @Test
    void persistsTheObservedTraceBySelector(@TempDir Path directory) throws Exception {
        var store = new TestResultStore(directory);
        var execution = execution("code", A, A);

        assertTrue(store.trace(execution.selector())
                        .isEmpty());
        store.record(new TestResult(execution, Status.SUCCESS));

        assertEquals(execution.trace(),
                store.trace(execution.selector()).orElseThrow());
    }

    @Test
    void aLaterFailureMakesTheTestRunAgainUntilItSucceeds(@TempDir Path directory) throws Exception {
        var store = new TestResultStore(directory);
        var execution = execution("code", A, A);
        store.record(new TestResult(execution, Status.SUCCESS));
        store.record(new TestResult(execution, Status.FAILURE));

        assertFalse(store.hasSuccessfulResult(execution));
        assertTrue(store.hasFailure(execution));

        store.record(new TestResult(execution, Status.SUCCESS));

        assertTrue(store.hasSuccessfulResult(execution));
        assertFalse(store.hasFailure(execution));
    }

    private static TestExecution execution(String code, String module, String patch) {
        return new TestExecution(
                "example.Test#test()",
                code,
                List.of(new ModuleState("example.module", new ModuleHash(Type.MODULE, "sha256", module), List.of(new ModuleHash(Type.PATCH, "sha256", patch)))),
                "runtime",
                Set.of(new Event("example/Test.test()V", "example.module", "example.Test"), new Event("example/Test.staticHelper()V", null, null)));
    }
}
