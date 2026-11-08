#!/usr/bin/env bash

set -euo pipefail

project_root="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")/.." && pwd)"
sdk_root="${ANDROID_SDK_ROOT:-${ANDROID_HOME:-$HOME/Android/Sdk}}"
gradle_version="${GRADLE_VERSION:-8.9}"
gradle_cache_dir="${GRADLE_CACHE_DIR:-$project_root/.gradle/gradle-dist}"
gradle_home="$gradle_cache_dir/gradle-$gradle_version"
gradle_bin="$gradle_home/bin/gradle"
adb_bin="$sdk_root/platform-tools/adb"
emulator_bin="$sdk_root/emulator/emulator"
avdmanager_bin="$sdk_root/cmdline-tools/latest/bin/avdmanager"
emulator_started=false
emulator_pid=""
device_serial=""

log() {
    printf '[dict2anki-e2e] %s\n' "$*"
}

die() {
    printf '[dict2anki-e2e] error: %s\n' "$*" >&2
    exit 1
}

find_gradle() {
    if [[ -x "$project_root/gradlew" ]]; then
        gradle_bin="$project_root/gradlew"
    elif [[ -x "$gradle_bin" ]]; then
        :
    elif command -v gradle >/dev/null 2>&1; then
        gradle_bin="$(command -v gradle)"
    else
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
    fi
}

stop_emulator() {
    if [[ "$emulator_started" == true ]]; then
        log "Stopping emulator $device_serial"
        "$adb_bin" -s "$device_serial" emu kill >/dev/null 2>&1 || true
        if [[ -n "$emulator_pid" ]]; then
            wait "$emulator_pid" 2>/dev/null || true
        fi
    fi
}

start_or_select_emulator() {
    "$adb_bin" start-server >/dev/null
    local -a devices
    mapfile -t devices < <("$adb_bin" devices | awk '$1 ~ /^emulator-/ && $2 == "device" { print $1 }')

    if [[ -n "${ANDROID_SERIAL:-}" ]]; then
        device_serial="$ANDROID_SERIAL"
        [[ "$device_serial" == emulator-* ]] || die "ANDROID_SERIAL must name an emulator for this script"
        printf '%s\n' "${devices[@]}" | grep -Fxq "$device_serial" || die "emulator $device_serial is not connected to adb"
    elif ((${#devices[@]} == 1)); then
        device_serial="${devices[0]}"
    elif ((${#devices[@]} > 1)); then
        die "multiple emulators are connected; set ANDROID_SERIAL to choose one"
    else
        [[ -x "$emulator_bin" ]] || die "Android emulator was not found under $sdk_root"
        local avd_name="${ANDROID_AVD_NAME:-}"
        if [[ -z "$avd_name" ]]; then
            avd_name="$("$emulator_bin" -list-avds | head -n 1)"
        fi
        if [[ -z "$avd_name" ]]; then
            [[ -x "$avdmanager_bin" ]] || die "avdmanager was not found under $sdk_root"
            local image="system-images;android-35;google_apis;x86_64"
            [[ -d "$sdk_root/system-images/android-35/google_apis/x86_64" ]] || die "Android 35 Google APIs x86_64 system image is missing"
            avd_name="${ANDROID_AVD_NAME:-dict2anki-e2e}"
            log "Creating emulator profile $avd_name"
            printf 'no\n' | "$avdmanager_bin" create avd --force --name "$avd_name" --package "$image" --device pixel_2 >/dev/null
        fi

        local port="${E2E_EMULATOR_PORT:-5554}"
        device_serial="emulator-$port"
        local emulator_log="${TMPDIR:-/tmp}/dict2anki-e2e-emulator.log"
        log "Starting emulator profile $avd_name"
        "$emulator_bin" -avd "$avd_name" -port "$port" -no-snapshot -no-audio -no-window -no-boot-anim -gpu swiftshader_indirect >"$emulator_log" 2>&1 &
        emulator_pid=$!
        emulator_started=true
        local attempt device_state=""
        for attempt in $(seq 1 120); do
            device_state="$("$adb_bin" -s "$device_serial" get-state 2>/dev/null || true)"
            [[ "$device_state" == "device" ]] && break
            if ! kill -0 "$emulator_pid" 2>/dev/null; then
                die "emulator failed to start; check $emulator_log"
            fi
            sleep 2
        done
        [[ "$device_state" == "device" ]] || die "emulator did not connect to adb; check $emulator_log"
    fi

    log "Waiting for Android to finish booting on $device_serial"
    local attempt
    for attempt in $(seq 1 180); do
        if [[ "$("$adb_bin" -s "$device_serial" shell getprop sys.boot_completed 2>/dev/null | tr -d '\r')" == "1" ]]; then
            "$adb_bin" -s "$device_serial" shell input keyevent 82 >/dev/null 2>&1 || true
            return
        fi
        sleep 2
    done
    die "emulator did not finish booting in 6 minutes"
}

[[ -d "$sdk_root" ]] || die "Android SDK not found at $sdk_root"
[[ -x "$adb_bin" ]] || die "adb was not found at $adb_bin"

find_gradle
export ANDROID_SDK_ROOT="$sdk_root"
export ANDROID_HOME="$sdk_root"
trap stop_emulator EXIT INT TERM
start_or_select_emulator

log "Running connected Compose UI tests on $device_serial"
cd "$project_root"
ANDROID_SERIAL="$device_serial" "$gradle_bin" --no-daemon --console=plain :app:connectedE2eAndroidTest "$@"
