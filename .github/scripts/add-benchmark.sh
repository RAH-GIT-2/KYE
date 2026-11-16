#!/usr/bin/env bash
# Turns a KYE checkout ($1) into one that can build a "benchmark" APK: same as release
# (shrunk, not debuggable) but signed with the debug key and with SeedActivity added.
# Used only by speed-check.yml; nothing here goes into the app that is sent out.
set -eu
dir=$1
here=$(cd "$(dirname "$0")/.." && pwd)
cp -r "$here/benchmark/src/benchmark" "$dir/app/src/"
cat >> "$dir/app/build.gradle.kts" <<'GRADLE'

android {
    buildTypes {
        create("benchmark") {
            initWith(getByName("release"))
            signingConfig = signingConfigs.getByName("debug")
            matchingFallbacks += listOf("release")
            isDebuggable = false
        }
    }
}
GRADLE
