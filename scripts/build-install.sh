#!/usr/bin/env bash

set -euo pipefail

project_root="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")/.." && pwd)"
sdk_root="${ANDROID_SDK_ROOT:-${ANDROID_HOME:-$HOME/Android/Sdk}}"
gradle_version="${GRADLE_VERSION:-8.9}"
gradle_cache_dir="${GRADLE_CACHE_DIR:-$project_root/.gradle/gradle-dist}"
gradle_home="$gradle_cache_dir/gradle-$gradle_version"
gradle_bin="$gradle_home/bin/gradle"

log() {
    printf '[dict2anki] %s\n' "$*"
}

die() {
    printf '[dict2anki] error: %s\n' "$*" >&2
    exit 1
}

find_gradle() {
    if [[ -x "$project_root/gradlew" ]]; then
        gradle_bin="$project_root/gradlew"
        return
    fi

    if [[ -x "$gradle_bin" ]]; then
        return
    fi

    if command -v gradle >/dev/null 2>&1; then
        gradle_bin="$(command -v gradle)"
        return
    fi

    command -v curl >/dev/null 2>&1 || die "curl is required to download Gradle"
    command -v unzip >/dev/null 2>&1 || die "unzip is required to install Gradle"

    mkdir -p "$gradle_cache_dir"
    local archive
    archive="$(mktemp "${TMPDIR:-/tmp}/dict2anki-gradle.XXXXXX.zip")"

    log "Gradle was not found; downloading Gradle $gradle_version"
    curl --fail --location --retry 3 --output "$archive" \
        "https://services.gradle.org/distributions/gradle-$gradle_version-bin.zip"
    unzip -q "$archive" -d "$gradle_cache_dir"
    rm -f "$archive"

    [[ -x "$gradle_bin" ]] || die "Gradle download did not contain $gradle_bin"
}

find_adb() {
    if [[ -x "$sdk_root/platform-tools/adb" ]]; then
        adb_bin="$sdk_root/platform-tools/adb"
    elif command -v adb >/dev/null 2>&1; then
        adb_bin="$(command -v adb)"
    else
        die "adb was not found. Install Android SDK platform-tools or set ANDROID_SDK_ROOT."
    fi
}

select_device() {
    local device_count
    device_count="$($adb_bin devices | awk '$2 == "device" { count++ } END { print count + 0 }')"

    if [[ -n "${ANDROID_SERIAL:-}" ]]; then
        device_serial="$ANDROID_SERIAL"
        return
    fi

    case "$device_count" in
        0)
            die "no authorized adb device found; connect and authorize a phone, then retry"
            ;;
        1)
            device_serial="$($adb_bin devices | awk '$2 == "device" { print $1; exit }')"
            ;;
        *)
            die "multiple adb devices found; set ANDROID_SERIAL to choose one"
            ;;
    esac
}

[[ -d "$sdk_root" ]] || die "Android SDK not found at $sdk_root"

find_gradle
find_adb

log "Using Android SDK: $sdk_root"
log "Using Gradle: $gradle_bin"

export ANDROID_SDK_ROOT="$sdk_root"
export ANDROID_HOME="$sdk_root"

cd "$project_root"
"$gradle_bin" --no-daemon :app:assembleDebug

apk="$project_root/app/build/outputs/apk/debug/app-debug.apk"
[[ -f "$apk" ]] || die "Gradle completed, but the debug APK was not found at $apk"

log "Starting adb"
"$adb_bin" start-server >/dev/null
select_device

log "Installing on $device_serial"
"$adb_bin" -s "$device_serial" install -r "$apk"
log "Installed $apk"
