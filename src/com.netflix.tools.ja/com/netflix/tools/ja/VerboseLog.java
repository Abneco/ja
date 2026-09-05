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

import java.io.PrintStream;
import java.util.List;

/** Writes deterministic verbose diagnostics for resolution and execution. */
final class VerboseLog {
    private VerboseLog() {}

    static void resolving(PrintStream err, List<String> arguments) {
        write(err, "resolve", "jig", arguments);
    }

    static void executing(PrintStream err, String command, List<String> arguments) {
        write(err, "execute", command, arguments);
    }

    private static void write(PrintStream err, String operation, String command,
            List<String> arguments) {
        err.print('[');
        err.print(operation);
        if (command != null) {
            err.print(' ');
            err.print(command);
        }
        for (String argument : arguments) {
            err.print(' ');
            err.print(quote(argument));
        }
        err.println(']');
    }

    private static String quote(String argument) {
        if (!argument.isEmpty() && argument.chars().noneMatch(character -> Character.isWhitespace(character) || character == '"' || character == '\\')) {
            return argument;
        }
        return '"' + argument.replace("\\", "\\\\")
                             .replace("\"", "\\\"")
                             .replace("\n", "\\n")
                             .replace("\r", "\\r")
                             .replace("\t", "\\t")
                + '"';
    }
}
