#!/usr/bin/env bash
# Сборка APK «Утка Разраба» (полностью offline, см. roadmap.md).
#
# JDK: используется org.gradle.java.home из gradle.properties (Homebrew OpenJDK 17).
# Результаты:
#   app/build/outputs/apk/debug/app-debug.apk            — сразу устанавливаемый
#   app/build/outputs/apk/release/app-release-unsigned.apk — release без подписи
#     (подпишите его своим keystore, либо добавьте signingConfig в app/build.gradle.kts)
set -euo pipefail
cd "$(dirname "$0")/.."

echo "==> Сборка debug + release"
./gradlew :app:assembleDebug :app:assembleRelease

echo
echo "Артефакты:"
ls -lh app/build/outputs/apk/debug/app-debug.apk
ls -lh app/build/outputs/apk/release/app-release-unsigned.apk 2>/dev/null || true