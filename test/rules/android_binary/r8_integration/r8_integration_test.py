# Copyright 2023 The Bazel Authors. All rights reserved.
#
# Licensed under the Apache License, Version 2.0 (the "License");
# you may not use this file except in compliance with the License.
# You may obtain a copy of the License at
#
#    http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing, software
# distributed under the License is distributed on an "AS IS" BASIS,
# WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
# See the License for the specific language governing permissions and
# limitations under the License.

import json
import os
import subprocess
import sys
import unittest
import zipfile


class R8IntegrationTest(unittest.TestCase):
  """Tests Bazel's R8 integration."""

  def _r8_integration_check(
      self, apk, expect_unused_activity_resource, expect_unused_activity_class
  ):
    tmp = os.environ["TEST_TMPDIR"]
    apk_directory = "test/rules/android_binary/r8_integration/java/com/basicapp"
    apk_tmp = os.path.join(tmp, apk)
    classes_dex = os.path.join(apk_tmp, "classes.dex")
    with zipfile.ZipFile(os.path.join(apk_directory, apk)) as zf:
      apk_files = zf.namelist()
      zf.extract("classes.dex", apk_tmp)

    self.assertEqual(
        expect_unused_activity_resource,
        "res/layout/unused_activity.xml" in apk_files,
    )

    dexdump_proc = subprocess.run(
        [dexdump, classes_dex],
        stdout=subprocess.PIPE,
        stderr=subprocess.PIPE,
        check=True,
    )
    dexdump_stdout = str(dexdump_proc.stdout)
    self.assertEqual(
        expect_unused_activity_class,
        "Class descriptor  : 'Lcom/basicapp/UnusedActivity;'" in dexdump_stdout,
    )

    # In all cases, the Lib2WithSpecsActivity class should be in the app,
    # since lib2_proguard.cfg (an indirect dependency) specifies to keep it.
    self.assertIn(
        "Class descriptor  : 'Lcom/basicapp/Lib2WithSpecsActivity;'",
        dexdump_stdout,
    )

  def test_r8_integration(self):
    # No R8, so unused resources and unused classes should be in the app
    self._r8_integration_check(
        "basic_app_no_R8.apk",
        expect_unused_activity_resource=True,
        expect_unused_activity_class=True,
    )

    # Run R8, don't shrink, so unused class should not be in the app but unused
    # resource should remain.
    self._r8_integration_check(
        "basic_app_R8_no_shrink.apk",
        expect_unused_activity_resource=True,
        expect_unused_activity_class=False,
    )

    # Run R8 and shrinkings, so unused classes and resources should not be in
    # the app.
    self._r8_integration_check(
        "basic_app_R8_shrink.apk",
        expect_unused_activity_resource=False,
        expect_unused_activity_class=False,
    )

    # No R8 when run in default test mode (non-opt),
    # so unused resources and unused classes should be in the app
    self._r8_integration_check(
        "basic_app_select_R8_generate_pg_map.apk",
        expect_unused_activity_resource=True,
        expect_unused_activity_class=True,
    )

  def _assert_art_profile(self, profile, profile_metadata):
    self.assertEqual(b"pro\x00010\x00", profile[:8])
    self.assertEqual(b"prm\x00002\x00", profile_metadata[:8])

  def test_r8_baseline_profile(self):
    app_directory = "test/rules/android_binary/r8_integration/java/com/basicapp"
    with zipfile.ZipFile(
        os.path.join(app_directory, "basic_app_R8_baseline_profile.apk")
    ) as zf:
      self._assert_art_profile(
          zf.read("assets/dexopt/baseline.prof"),
          zf.read("assets/dexopt/baseline.profm"),
      )

    with zipfile.ZipFile(
        os.path.join(
            app_directory, "basic_app_R8_baseline_profile_aab_unsigned.aab"
        )
    ) as zf:
      self._assert_art_profile(
          zf.read("BUNDLE-METADATA/com.android.tools.build.profiles/baseline.prof"),
          zf.read("BUNDLE-METADATA/com.android.tools.build.profiles/baseline.profm"),
      )
      r8_metadata = json.loads(zf.read("BUNDLE-METADATA/com.android.tools/r8.json"))
    self.assertTrue(r8_metadata["dexFiles"][0]["startup"])

    with open(
        os.path.join(app_directory, "basic_app_R8_baseline_profile_primary.prof")
    ) as f:
      dumped_profile = f.read()
    self.assertIn(
        "Lcom/basicapp/BasicActivity;->onCreate(Landroid/os/Bundle;)V",
        dumped_profile,
    )
    # Only matched by the wildcard rule, and only present if R8 rewrote it to
    # the renamed listener class.
    self.assertIn("onClick(Landroid/view/View;)V", dumped_profile)
    self.assertNotIn("BasicActivity$1", dumped_profile)
    # Only in the startup profile, which R8 doesn't merge into the ART profile.
    self.assertIn("Lcom/basicapp/BasicActivity;-><init>()V", dumped_profile)
    # Only in the baseline profile packaged in the AAR.
    self.assertIn("Lcom/basicapp/AarLibrary;->run()V", dumped_profile)


if __name__ == "__main__":
  dexdump = sys.argv.pop()
  unittest.main(argv=None)
