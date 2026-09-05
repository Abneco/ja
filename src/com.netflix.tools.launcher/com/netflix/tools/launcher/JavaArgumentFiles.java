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

package com.netflix.tools.launcher;

import java.io.IOException;
import java.io.Reader;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

final class JavaArgumentFiles {
    private JavaArgumentFiles() {}

    static List<String> expand(List<String> arguments) throws IOException {
        var expanded = new ArrayList<String>();
        for (String argument : arguments) {
            if (argument.length() > 1 && argument.charAt(0) == '@') {
                if (argument.charAt(1) == '@') {
                    expanded.add(argument.substring(1));
                } else {
                    expanded.addAll(read(Path.of(argument.substring(1))));
                }
            } else {
                expanded.add(argument);
            }
        }
        return List.copyOf(expanded);
    }

    static List<String> read(Path file) throws IOException {
        try (Reader reader = Files.newBufferedReader(file, Charset.defaultCharset())) {
            var tokenizer = new Tokenizer(reader);
            var arguments = new ArrayList<String>();
            String argument;
            while ((argument = tokenizer.next()) != null) {
                arguments.add(argument);
            }
            return List.copyOf(arguments);
        } catch (IOException e) {
            throw new IOException("Cannot read argument file " + file + ": " + e.getMessage(), e);
        }
    }

    private static final class Tokenizer {
        private final Reader reader;
        private int character;

        Tokenizer(Reader reader) throws IOException {
            this.reader = reader;
            character = reader.read();
        }

        String next() throws IOException {
            skipWhitespace();
            if (character == -1) {
                return null;
            }

            var value = new StringBuilder();
            char quote = 0;
            while (character != -1) {
                switch (character) {
                    case ' ', '\t', '\f' -> {
                        if (quote == 0) {
                            return value.toString();
                        }
                        value.append((char) character);
                    }
                    case '\n', '\r' -> {
                        return value.toString();
                    }
                    case '\'', '"' -> {
                        if (quote == 0) {
                            quote = (char) character;
                        } else if (quote == character) {
                            quote = 0;
                        } else {
                            value.append((char) character);
                        }
                    }
                    case '\\' -> {
                        if (quote != 0) {
                            character = reader.read();
                            switch (character) {
                                case '\n', '\r' -> {
                                    do {
                                        character = reader.read();
                                    } while (isWhitespace(character));
                                    continue;
                                }
                                case 'n' -> character = '\n';
                                case 'r' -> character = '\r';
                                case 't' -> character = '\t';
                                case 'f' -> character = '\f';
                                default -> {}
                            }
                        }
                        value.append((char) character);
                    }
                    default -> value.append((char) character);
                }
                character = reader.read();
            }
            return value.toString();
        }

        private void skipWhitespace() throws IOException {
            while (character != -1) {
                if (isWhitespace(character)) {
                    character = reader.read();
                } else if (character == '#') {
                    do {
                        character = reader.read();
                    } while (character != '\n' && character != '\r' && character != -1);
                } else {
                    return;
                }
            }
        }

        private static boolean isWhitespace(int character) {
            return character == ' '
                    || character == '\t'
                    || character == '\n'
                    || character == '\r'
                    || character == '\f';
        }
    }
}
