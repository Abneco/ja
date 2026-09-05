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

import com.netflix.tools.ja.TemporaryDirectory;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TemporaryDirectoryTest {
    @Test
    void createsDistinctDirectoriesAndDeletesThemOnClose() throws Exception {
        TemporaryDirectory first = TemporaryDirectory.create();
        TemporaryDirectory second = TemporaryDirectory.create();
        try {
            assertNotEquals(first, second);
            assertTrue(Files.isDirectory(first.root()));
            assertTrue(Files.isDirectory(second.root()));
        } finally {
            first.close();
            second.close();
        }

        assertFalse(Files.exists(first.root()));
        assertFalse(Files.exists(second.root()));
    }
}
