/*
 * Copyright 2026 The Bazel Authors. All rights reserved.

 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at

 *   http://www.apache.org/licenses/LICENSE-2.0

 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
*/

package com.google.devtools.build.android.baselineprofiles;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.LinkedHashSet;
import java.util.Set;

/**
 * Merges human readable baseline profiles: {@code --input <profile>... --output <profile>}, read
 * one argument per line from an {@code @<params file>}.
 *
 * <p>Only exact duplicates are dropped: profgen skips comments and blank lines, and ORs the flags
 * of rules that only differ in their HSP prefix.
 */
public final class MergeBaselineProfiles {

  private MergeBaselineProfiles() {}

  public static void main(String[] args) throws IOException {
    args = Files.readAllLines(Paths.get(args[0].substring(1))).toArray(new String[0]);
    Set<String> rules = new LinkedHashSet<>();
    Path output = null;
    for (int i = 0; i < args.length; i += 2) {
      Path path = Paths.get(args[i + 1]);
      if (args[i].equals("--output")) {
        output = path;
      } else {
        rules.addAll(Files.readAllLines(path));
      }
    }
    Files.write(output, rules);
  }
}
