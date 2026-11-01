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
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Merges human readable baseline profiles: {@code --input <profile>... --output <profile>}.
 *
 * <p>Rules are de-duplicated in first-seen order; profgen ORs the flags of rules that only differ
 * in their HSP prefix, so no further normalization is needed.
 */
public final class MergeBaselineProfiles {

  private MergeBaselineProfiles() {}

  public static void main(String[] args) throws IOException {
    List<Path> inputs = new ArrayList<>();
    Path output = null;
    for (int i = 0; i < args.length; i++) {
      if (i + 1 >= args.length) {
        throw new IllegalArgumentException("Missing value for " + args[i]);
      }
      switch (args[i]) {
        case "--input":
          inputs.add(Paths.get(args[++i]));
          break;
        case "--output":
          output = Paths.get(args[++i]);
          break;
        default:
          throw new IllegalArgumentException("Unknown argument: " + args[i]);
      }
    }
    if (output == null) {
      throw new IllegalArgumentException("Missing required flag --output");
    }

    Set<String> rules = new LinkedHashSet<>();
    for (Path input : inputs) {
      for (String line : Files.readAllLines(input, StandardCharsets.UTF_8)) {
        String rule = line.trim();
        if (!rule.isEmpty() && !rule.startsWith("#")) {
          rules.add(rule);
        }
      }
    }
    Files.write(output, rules, StandardCharsets.UTF_8);
  }
}
