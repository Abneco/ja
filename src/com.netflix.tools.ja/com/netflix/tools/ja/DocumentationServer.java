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

import java.awt.Desktop;
import java.awt.Desktop.Action;
import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

import com.netflix.tools.jdocserver.DocumentationHandler;
import com.sun.net.httpserver.HttpServer;

/**
 * Starts the documentation server, opens the selected URI, and waits for the
 * browsing session.
 */
public final class DocumentationServer {
    @FunctionalInterface
    public interface UriOpener {
        void open(URI uri) throws IOException;
    }

    @FunctionalInterface
    public interface Awaiter {
        void await() throws InterruptedException;
    }

    private DocumentationServer() {}

    static int browse(List<String> arguments, Optional<String> type) throws IOException {
        return browse(arguments, type, DocumentationServer::open, () -> new CountDownLatch(1).await());
    }

    public static int browse(List<String> arguments, Optional<String> type, UriOpener opener,
            Awaiter awaiter)
            throws IOException {
        try (var running = RunningDocumentation.start(arguments)) {
            URI request = type.map(running.handler()::typeUri).orElseGet(running.handler()::indexUri);
            opener.open(running.baseUri()
                               .resolve(request));

            var shutdownHook = new Thread(running::closeQuietly, "ja-documentation-shutdown");
            Runtime.getRuntime().addShutdownHook(shutdownHook);
            try {
                awaiter.await();
                return 0;
            } catch (InterruptedException failure) {
                Thread.currentThread().interrupt();
                return 130;
            } finally {
                try {
                    Runtime.getRuntime().removeShutdownHook(shutdownHook);
                } catch (IllegalStateException _) {
                    // JVM shutdown is already running the hook.
                }
            }
        }
    }

    private static void open(URI uri) throws IOException {
        try {
            if (!Desktop.isDesktopSupported()) {
                throw new IOException("desktop browsing is not supported");
            }
            Desktop desktop = Desktop.getDesktop();
            if (!desktop.isSupported(Action.BROWSE)) {
                throw new IOException("no browser handler is available");
            }
            desktop.browse(uri);
        } catch (IOException failure) {
            throw new IOException("Cannot browse " + uri + ": " + failure.getMessage(), failure);
        } catch (UnsupportedOperationException | SecurityException failure) {
            throw new IOException("Cannot browse " + uri + ": " + failure, failure);
        }
    }

    private static final class RunningDocumentation implements AutoCloseable {
        private final DocumentationHandler handler;
        private final HttpServer server;
        private final ExecutorService executor;
        private final AtomicBoolean closed = new AtomicBoolean();

        private RunningDocumentation(DocumentationHandler handler, HttpServer server, ExecutorService executor) {
            this.handler = handler;
            this.server = server;
            this.executor = executor;
        }

        static RunningDocumentation start(List<String> arguments) throws IOException {
            var handler = DocumentationHandler.create(arguments);
            var executor = Executors.newVirtualThreadPerTaskExecutor();
            try {
                var server = HttpServer.create(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0);
                server.createContext("/", handler);
                server.setExecutor(executor);
                server.start();
                return new RunningDocumentation(handler, server, executor);
            } catch (IOException | RuntimeException failure) {
                executor.close();
                handler.close();
                throw failure;
            }
        }

        DocumentationHandler handler() {
            return handler;
        }

        URI baseUri() {
            InetSocketAddress address = server.getAddress();
            try {
                return new URI(
                        "http",
                        null,
                        address.getAddress().getHostAddress(),
                        address.getPort(),
                        "/",
                        null,
                        null);
            } catch (URISyntaxException failure) {
                throw new IllegalStateException("Invalid documentation server address", failure);
            }
        }

        void closeQuietly() {
            try {
                close();
            } catch (IOException _) {
                // Temporary documentation is best-effort cleanup during shutdown.
            }
        }

        @Override
        public void close() throws IOException {
            if (!closed.compareAndSet(false, true)) {
                return;
            }
            server.stop(0);
            executor.close();
            handler.close();
        }
    }
}
