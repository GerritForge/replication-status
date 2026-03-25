load(
    "//tools/bzl:plugin.bzl",
    "PLUGIN_DEPS",
    "PLUGIN_TEST_DEPS",
    "gerrit_plugin",
)
load("//tools/bzl:junit.bzl", "junit_tests")

gerrit_plugin(
    name = "replication-status",
    srcs = glob(["src/main/java/**/*.java"]),
    manifest_entries = [
        "Gerrit-PluginName: replication-status",
        "Gerrit-Module: com.gerritforge.gerrit.plugins.replicationstatus.Module",
        "Implementation-Title: Replication Status",
        "Implementation-URL: https://github.com/GerritForge/replication-status",
    ],
    resources = glob(["src/main/resources/**/*"]),
    deps = [
        ":replication-neverlink",
        "//java/com/google/gerrit/proto",
        "//plugins/replication-status/proto:replication_status_cache_java_proto",
    ],
)

junit_tests(
    name = "replicationstatus_tests",
    srcs = glob(["src/test/java/**/*.java"]),
    resources = glob(["src/test/resources/**/*"]),
    deps = [
        ":replicationstatus__plugin_test_deps",
    ],
)

java_library(
    name = "replication-neverlink",
    neverlink = 1,
    exports = ["//plugins/replication"],
)

java_library(
    name = "replicationstatus__plugin_test_deps",
    testonly = 1,
    visibility = ["//visibility:public"],
    exports = PLUGIN_DEPS + PLUGIN_TEST_DEPS + [
        ":replication-status__plugin",
        "//plugins/replication",
    ],
)
