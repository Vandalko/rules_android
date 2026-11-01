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

import com.android.tools.profgen.Apk;
import com.android.tools.profgen.ArtProfile;
import com.android.tools.profgen.ArtProfileKt;
import com.android.tools.profgen.ArtProfileSerializer;
import com.android.tools.profgen.DexDataKt;
import com.android.tools.profgen.DexFile;
import com.android.tools.profgen.Diagnostics;
import com.android.tools.profgen.HumanReadableProfileKt;
import com.android.tools.profgen.ObfuscationMap;
import com.android.tools.profgen.ObfuscationMapKt;
import com.android.tools.profgen.ProfgenUtilsKt;
import com.android.tools.profgen.ProfileDumperKt;
import java.io.File;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Command line interface for the profgen library, which is only distributed inside the Android
 * Gradle Plugin jar.
 *
 * <pre>
 * bin &lt;profile&gt; --apk &lt;dex zip&gt; --output &lt;baseline.prof&gt; --output-meta &lt;baseline.profm&gt; [--map &lt;mapping&gt;]
 * expandWildcards &lt;program jar&gt; --profile &lt;profile&gt; --output &lt;expanded profile&gt;
 * dumpProfile --profile &lt;baseline.prof&gt; --apk &lt;dex zip&gt; --output &lt;profile&gt;
 * </pre>
 */
public final class Profgen {

  private static final Diagnostics FAIL_ON_ERROR =
      error -> {
        throw new IllegalArgumentException(error);
      };

  private Profgen() {}

  public static void main(String[] args) {
    List<String> positional = new ArrayList<>();
    Map<String, String> flags = new HashMap<>();
    for (int i = 1; i < args.length; i++) {
      if (args[i].startsWith("--")) {
        flags.put(args[i], args[++i]);
      } else {
        positional.add(args[i]);
      }
    }

    switch (args[0]) {
      case "bin":
        compile(
            positional.get(0),
            flags.get("--apk"),
            flags.get("--output"),
            flags.get("--output-meta"),
            flags.get("--map"));
        break;
      case "expandWildcards":
        ProfgenUtilsKt.expandWildcards(
            flags.get("--profile"), flags.get("--output"), positional, FAIL_ON_ERROR);
        break;
      case "dumpProfile":
        ProfileDumperKt.dumpProfile(
            new File(flags.get("--output")),
            ArtProfileKt.ArtProfile(new File(flags.get("--profile"))),
            new Apk(readDexFiles(flags.get("--apk")), ""),
            ObfuscationMap.Companion.getEmpty(),
            true);
        break;
      default:
        throw new IllegalArgumentException("Unknown command: " + args[0]);
    }
  }

  private static void compile(
      String profilePath, String apkPath, String outputPath, String outputMetaPath, String mapPath) {
    ArtProfile profile =
        ArtProfileKt.ArtProfile(
            HumanReadableProfileKt.HumanReadableProfile(new File(profilePath), FAIL_ON_ERROR),
            mapPath == null
                ? ObfuscationMap.Companion.getEmpty()
                : ObfuscationMapKt.ObfuscationMap(new File(mapPath)),
            readDexFiles(apkPath),
            "");
    // Same formats AGP packages: P+ profile with a separate metadata file for N-O devices.
    ArtProfileKt.save(profile, new File(outputPath), ArtProfileSerializer.V0_1_0_P);
    ArtProfileKt.save(profile, new File(outputMetaPath), ArtProfileSerializer.METADATA_0_0_2);
  }

  /** Reads classes*.dex in the order ART loads them, which zip entry order doesn't guarantee. */
  private static List<DexFile> readDexFiles(String zipPath) {
    List<DexFile> dexFiles = new ArrayList<>(DexDataKt.Apk(new File(zipPath), "").getDexes());
    dexFiles.sort(Comparator.comparingInt(Profgen::dexIndex));
    return dexFiles;
  }

  private static int dexIndex(DexFile dex) {
    String index = dex.getName().replaceAll("\\D", "");
    return index.isEmpty() ? 1 : Integer.parseInt(index);
  }
}
