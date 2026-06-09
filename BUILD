load(
    "@com_googlesource_gerrit_bazlets//:gerrit_plugin.bzl",
    "gerrit_plugin",
    "gerrit_plugin_tests",
)
load("@rules_java//java:java_library.bzl", "java_library")

gerrit_plugin(
    srcs = glob(["src/main/java/**/*.java"]),
    manifest_entries = [
        "Gerrit-PluginName: replication-status",
        "Gerrit-Module: com.gerritforge.gerrit.plugins.replicationstatus.Module",
        "Implementation-Title: Replication Status",
        "Implementation-URL: https://github.com/GerritForge/replication-status",
    ],
    plugin = "replication-status",
    resources = glob(["src/main/resources/**/*"]),
    deps = [
        ":replication-neverlink",
        "//plugins/replication-status/proto:replication_status_cache_java_proto",
    ],
)

gerrit_plugin_tests(
    srcs = glob(["src/test/java/**/*.java"]),
    plugin = "replication-status",
    deps = ["//plugins/replication"],
)

java_library(
    name = "replication-neverlink",
    neverlink = 1,
    exports = ["//plugins/replication"],
)
