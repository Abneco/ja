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

import java.net.InetAddress;
import java.net.URI;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

import com.netflix.tools.ja.DocumentationServer;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DocumentationServerTest {
    @Test
    void servesAndSelectsTheHandlerTypeUri() throws Exception {
        var opened = new AtomicReference<URI>();

        int result = DocumentationServer.browse(List.of(), Optional.of("java.lang.String"), opened::set,
                () -> {});

        URI uri = opened.get();
        assertEquals(0, result);
        assertEquals("http", uri.getScheme());
        assertTrue(InetAddress.getByName(uri.getHost())
                .isLoopbackAddress());
        assertTrue(uri.getPort() > 0);
        assertEquals("/type", uri.getPath());
        assertEquals("name=java.lang.String", uri.getQuery());
    }
}
