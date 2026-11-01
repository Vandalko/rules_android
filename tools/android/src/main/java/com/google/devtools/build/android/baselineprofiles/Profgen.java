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
import com.android.tools.profgen.HumanReadableProfile;
import com.android.tools.profgen.HumanReadableProfileKt;
import com.android.tools.profgen.ObfuscationMap;
import com.android.tools.profgen.ObfuscationMapKt;
import com.android.tools.profgen.ProfgenUtilsKt;
import com.android.tools.profgen.ProfileDumperKt;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

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

  private static final Pattern DEX_ENTRY = Pattern.compile("classes(\\d*)\\.dex");
  private static final Diagnostics FAIL_ON_ERROR =
      error -> {
        throw new IllegalArgumentException(error);
      };

  private Profgen() {}

  public static void main(String[] args) throws IOException {
    if (args.length == 0) {
      throw new IllegalArgumentException("Usage: profgen <bin|expandWildcards|dumpProfile> ...");
    }
    List<String> positional = new ArrayList<>();
    Map<String, String> flags = new HashMap<>();
    for (int i = 1; i < args.length; i++) {
      if (args[i].startsWith("--")) {
        if (i + 1 >= args.length) {
          throw new IllegalArgumentException("Missing value for " + args[i]);
        }
        flags.put(args[i], args[++i]);
      } else {
        positional.add(args[i]);
      }
    }

    switch (args[0]) {
      case "bin":
        compile(
            single(positional, "profile"),
            required(flags, "--apk"),
            required(flags, "--output"),
            required(flags, "--output-meta"),
            flags.get("--map"));
        break;
      case "expandWildcards":
        ProfgenUtilsKt.expandWildcards(
            required(flags, "--profile"), required(flags, "--output"), positional, FAIL_ON_ERROR);
        break;
      case "dumpProfile":
        dump(required(flags, "--profile"), required(flags, "--apk"), required(flags, "--output"));
        break;
      default:
        throw new IllegalArgumentException("Unknown command: " + args[0]);
    }
  }

  private static void compile(
      String profilePath, String apkPath, String outputPath, String outputMetaPath, String mapPath)
      throws IOException {
    HumanReadableProfile profile =
        HumanReadableProfileKt.HumanReadableProfile(new File(profilePath), FAIL_ON_ERROR);
    if (profile == null) {
      throw new IllegalArgumentException("Unable to parse " + profilePath);
    }
    ObfuscationMap obfuscationMap =
        mapPath == null
            ? ObfuscationMap.Companion.getEmpty()
            : ObfuscationMapKt.ObfuscationMap(new File(mapPath));
    ArtProfile artProfile = ArtProfileKt.ArtProfile(profile, obfuscationMap, readDexFiles(apkPath), "");
    // Same formats AGP packages: P+ profile with a separate metadata file for N-O devices.
    save(artProfile, outputPath, ArtProfileSerializer.V0_1_0_P);
    save(artProfile, outputMetaPath, ArtProfileSerializer.METADATA_0_0_2);
  }

  private static void dump(String profilePath, String apkPath, String outputPath)
      throws IOException {
    Apk apk = new Apk(readDexFiles(apkPath), new File(apkPath).getName());
    ProfileDumperKt.dumpProfile(
        new File(outputPath),
        ArtProfileKt.ArtProfile(new File(profilePath)),
        apk,
        ObfuscationMap.Companion.getEmpty(),
        true);
  }

  private static void save(ArtProfile profile, String path, ArtProfileSerializer serializer)
      throws IOException {
    try (OutputStream out = new FileOutputStream(path)) {
      profile.save(out, serializer);
    }
  }

  /** Reads top level classes*.dex entries in the order ART loads them. */
  private static List<DexFile> readDexFiles(String zipPath) throws IOException {
    List<DexFile> dexFiles = new ArrayList<>();
    try (ZipFile zip = new ZipFile(zipPath)) {
      List<ZipEntry> entries = new ArrayList<>();
      for (ZipEntry entry : Collections.list(zip.entries())) {
        if (DEX_ENTRY.matcher(entry.getName()).matches()) {
          entries.add(entry);
        }
      }
      entries.sort(Comparator.comparingInt(Profgen::dexIndex));
      for (ZipEntry entry : entries) {
        try (InputStream in = zip.getInputStream(entry)) {
          dexFiles.add(DexDataKt.DexFile(in, entry.getName()));
        }
      }
    }
    if (dexFiles.isEmpty()) {
      throw new IllegalArgumentException("No classes*.dex entries found in " + zipPath);
    }
    return dexFiles;
  }

  private static int dexIndex(ZipEntry entry) {
    Matcher matcher = DEX_ENTRY.matcher(entry.getName());
    matcher.matches();
    return matcher.group(1).isEmpty() ? 1 : Integer.parseInt(matcher.group(1));
  }

  private static String single(List<String> positional, String name) {
    if (positional.size() != 1) {
      throw new IllegalArgumentException("Expected a single <" + name + "> argument");
    }
    return positional.get(0);
  }

  private static String required(Map<String, String> flags, String flag) {
    String value = flags.get(flag);
    if (value == null) {
      throw new IllegalArgumentException("Missing required flag " + flag);
    }
    return value;
  }
}
