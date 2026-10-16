#!/usr/bin/env bash
set -euo pipefail
repo_dir="$(cd "$(dirname "$0")/.." && pwd)"
classes_dir="$(mktemp -d)"
trap 'rm -rf "$classes_dir"' EXIT
javac --release 17 -encoding UTF-8 -d "$classes_dir" \
    "$repo_dir/app/src/main/java/tw/junba/reminder/NativeAlarmPlan.java" \
    "$repo_dir/tests/NativeAlarmPlanTest.java"
java -cp "$classes_dir" tw.junba.reminder.NativeAlarmPlanTest
