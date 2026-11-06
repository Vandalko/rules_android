load("@rules_java//java:java_library.bzl", "java_library")
load("//rules:rules.bzl", "aar_import", "android_application", "android_binary", "android_library")

[
    android_binary(
        name = name,
        srcs = ["BasicActivity.java"],
        manifest = "AndroidManifest.xml",
        proguard_generate_mapping = generate_pg_map,
        proguard_specs = specs,
        resource_files = glob(["res/**"]),
        shrink_resources = shrink,
        visibility = ["//test/rules/android_binary/r8_integration:__pkg__"],
        deps = [
            ":basic_lib",
            ":lib_with_specs",
        ],
    )
    for name, specs, shrink, generate_pg_map in [
        (
            "basic_app_R8_shrink",
            ["proguard.cfg"],
            True,
            False,
        ),
        (
            "basic_app_R8_no_shrink",
            ["proguard.cfg"],
            False,
            False,
        ),
        (
            "basic_app_no_R8",
            [],
            False,
            False,
        ),
        (
            "basic_app_select_R8_generate_pg_map",
            select({
                ":is_c_opt": ["proguard.cfg"],
                "//conditions:default": [],
            }),
            True,
            True,
        ),
    ]
]

android_application(
    name = "basic_app_R8_baseline_profile",
    srcs = ["BasicActivity.java"],
    bundle_config_file = "bundle_config.pb.json",
    manifest = "AndroidManifest.xml",
    manifest_values = {
        "applicationId": "com.basicapp",
        "versionCode": "1",
    },
    proguard_specs = ["proguard_obfuscate.cfg"],
    resource_files = glob(["res/**"]),
    startup_profiles = ["startup-prof.txt"],
    visibility = ["//test/rules/android_binary/r8_integration:__pkg__"],
    deps = [
        ":aar_with_baseline_profile",
        ":lib_with_baseline_profile",
    ],
)

android_library(
    name = "lib_with_baseline_profile",
    baseline_profiles = ["baseline-prof.txt"],
)

aar_import(
    name = "aar_with_baseline_profile",
    aar = "aar_with_baseline_profile.aar",
)

genrule(
    name = "gen_aar_with_baseline_profile",
    srcs = [
        "AndroidManifest_lib.xml",
        "aar-baseline-prof.txt",
        "aar_proguard.cfg",
        ":aar_library",
    ],
    outs = ["aar_with_baseline_profile.aar"],
    cmd = """$(execpath @bazel_tools//tools/zip:zipper) c $@ \
        AndroidManifest.xml=$(execpath AndroidManifest_lib.xml) \
        baseline-prof.txt=$(execpath aar-baseline-prof.txt) \
        classes.jar=$(execpath :aar_library) \
        proguard.txt=$(execpath aar_proguard.cfg)""",
    tools = ["@bazel_tools//tools/zip:zipper"],
)

java_library(
    name = "aar_library",
    srcs = ["AarLibrary.java"],
)

android_library(
    name = "basic_lib",
    srcs = ["UnusedActivity.java"],
    manifest = "AndroidManifest_lib.xml",
    resource_files = glob(["res_lib/**"]),
)

android_library(
    name = "lib_with_specs",
    srcs = ["LibWithSpecsActivity.java"],
    manifest = "AndroidManifest_lib.xml",
    proguard_specs = ["lib_proguard.cfg"],
    deps = [":lib2_with_specs"],
)

android_library(
    name = "lib2_with_specs",
    srcs = ["Lib2WithSpecsActivity.java"],
    manifest = "AndroidManifest_lib.xml",
    proguard_specs = ["lib2_proguard.cfg"],
)

config_setting(
    name = "is_c_opt",
    values = {
        "compilation_mode": "opt",
    },
)
