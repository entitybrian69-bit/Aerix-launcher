#!/usr/bin/env bash
set -euo pipefail

PROJECT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
ARTIFACT_DIR="$PROJECT_DIR/build/matrix-artifacts"
XVFB_SERVER_ARGS="${TRINKIES_XVFB_SERVER_ARGS:--screen 0 1280x720x24 +extension GLX +render -noreset}"
mkdir -p "$ARTIFACT_DIR"

for variable in TRINKIES_JDK_17 TRINKIES_JDK_21 TRINKIES_JDK_25; do
    if [[ -z "${!variable:-}" || ! -x "${!variable}/bin/java" ]]; then
        echo "Missing installed Java toolchain: $variable=${!variable:-<unset>}" >&2
        exit 2
    fi
done

# Loom 1.19-SNAPSHOT must run on JDK 25. Loom's RunGameTask uses the Java toolchain
# configured in build.gradle for the actual Minecraft client process.
export JAVA_HOME="$TRINKIES_JDK_25"
export PATH="$JAVA_HOME/bin:$PATH"
export LIBGL_ALWAYS_SOFTWARE="${LIBGL_ALWAYS_SOFTWARE:-1}"
export MESA_LOADER_DRIVER_OVERRIDE="${MESA_LOADER_DRIVER_OVERRIDE:-llvmpipe}"
export GALLIUM_DRIVER="${GALLIUM_DRIVER:-llvmpipe}"

check_headless_graphics() {
    local diagnostic_file="$ARTIFACT_DIR/headless-graphics.log"
    local xvfb_status=0

    {
        echo "Xvfb server args: $XVFB_SERVER_ARGS"
        echo "LIBGL_ALWAYS_SOFTWARE=$LIBGL_ALWAYS_SOFTWARE"
        echo "MESA_LOADER_DRIVER_OVERRIDE=$MESA_LOADER_DRIVER_OVERRIDE"
        echo "GALLIUM_DRIVER=$GALLIUM_DRIVER"
        if [[ -r /etc/os-release ]]; then
            cat /etc/os-release
        fi
    } | tee "$diagnostic_file"

    for binary in xvfb-run glxinfo; do
        if ! command -v "$binary" >/dev/null 2>&1; then
            echo "Required headless graphics tool is missing: $binary" | tee -a "$diagnostic_file" >&2
            return 2
        fi
    done

    set +e
    xvfb-run -a -s "$XVFB_SERVER_ARGS" glxinfo -B 2>&1 | tee -a "$diagnostic_file"
    local pipeline_status=("${PIPESTATUS[@]}")
    xvfb_status="${pipeline_status[0]}"
    set -e

    if (( xvfb_status != 0 )); then
        echo "Xvfb GLX probe failed with exit code $xvfb_status." | tee -a "$diagnostic_file" >&2
        return "$xvfb_status"
    fi
    if ! grep -Eiq '^OpenGL renderer string:.*llvmpipe' "$diagnostic_file"; then
        echo "Xvfb started, but Mesa did not report the expected llvmpipe OpenGL renderer." | tee -a "$diagnostic_file" >&2
        return 1
    fi
    echo "Verified Xvfb GLX with Mesa llvmpipe before launching Minecraft." | tee -a "$diagnostic_file"
}

copy_game_test_screenshots() {
    local version="$1"
    local destination="$ARTIFACT_DIR/$version"
    local screenshot_dir="$PROJECT_DIR/build/run/clientGameTest/screenshots"
    local -a screenshots=()

    if [[ -d "$screenshot_dir" ]]; then
        mapfile -d '' screenshots < <(find "$screenshot_dir" -type f -name "*trinkies-client-startup-${version}*.png" -print0)
    fi

    if (( ${#screenshots[@]} == 0 )); then
        echo "No client GameTest screenshot found for $version in $screenshot_dir" >&2
        return 1
    fi

    mkdir -p "$destination"
    cp "${screenshots[@]}" "$destination/"
}

write_target_result() {
    local target_dir="$1"
    local version="$2"
    local java_version="$3"
    local fabric_api="$4"
    local task="$5"
    local status="$6"
    local runtime_java="${7:-unknown}"
    local detail="${8:-none}"
    # The contract names what is actually asserted. The mod loading is not the interesting part;
    # reading the launcher's wardrobe - worn slots and keybound wheel alike - and answering it is.
    # Neither covers in-world attachment drawing or a real key press.
    local contract="client-gametest-wardrobe-and-wheel-roundtrip-no-world"

    if [[ "$task" == "verifyClientBootSmoke" ]]; then
        contract="client-boot-wardrobe-and-wheel-roundtrip"
    fi

    mkdir -p "$target_dir"
    cat > "$target_dir/client-matrix-result.txt" <<EOF
status=$status
minecraft_version=$version
expected_java_major=$java_version
runtime_java_version=$runtime_java
fabric_loader=0.20.1
fabric_api=$fabric_api
gradle_task=$task
smoke_contract=$contract
detail=$detail
EOF
}

is_expected_java_major() {
    local actual="$1"
    local expected="$2"
    [[ "$actual" == "$expected" || "$actual" == "$expected".* ]]
}

run_target() {
    local version="$1"
    local java_version="$2"
    local fabric_api="$3"
    local task="$4"
    local label="${version//./_}"
    local target_dir="$ARTIFACT_DIR/$version"
    local log_file="$target_dir/gradle.log"
    local code=0
    local runtime_java="unknown"
    local evidence=""
    local failure_reason=""

    mkdir -p "$target_dir"
    write_target_result "$target_dir" "$version" "$java_version" "$fabric_api" "$task" RUNNING
    echo "=== Trinkies baseline: Minecraft $version / Java $java_version / Fabric API $fabric_api ==="

    echo "Gradle client task has a 14-minute limit for $version; filtered progress is streamed below."
    set +e
    timeout --signal=TERM --kill-after=30s 14m xvfb-run -a -s "$XVFB_SERVER_ARGS" gradle \
        -p "$PROJECT_DIR" \
        "-Pminecraft_version=$version" \
        -Ploader_version=0.20.1 \
        -Ploom_version=1.19-SNAPSHOT \
        "-Pfabric_api_version=$fabric_api" \
        "-Ptest_runtime_java=$java_version" \
        "$task" --console=plain 2>&1 \
        | tee "$log_file" \
        | grep --line-buffered -E '(^> Task|^BUILD|^FAILURE:|TRINKIES_CLIENT_(GAMETEST|BOOT_SMOKE)|ERROR|FATAL|Exception|Caused by:|Timeout|Fabric Loom:)'
    pipeline_status=("${PIPESTATUS[@]}")
    code="${pipeline_status[0]}"
    if (( code == 0 && ${pipeline_status[1]:-0} != 0 )); then
        code="${pipeline_status[1]}"
    fi
    set -e

    tail -50 "$log_file"
    if [[ "$task" == "runClientGameTest" ]]; then
        local started_line
        started_line="$(grep -F 'TRINKIES_CLIENT_GAMETEST_STARTED:' "$log_file" | tail -1 || true)"
        if [[ -n "$started_line" ]]; then
            runtime_java="$(printf '%s\n' "$started_line" | sed -E 's/.*java=([^, ]+).*/\1/')"
        fi
    else
        local boot_marker="$target_dir/client-boot-smoke.ok"
        if [[ -f "$boot_marker" ]]; then
            runtime_java="$(sed -n 's/^java=//p' "$boot_marker" | head -n 1)"
        fi
    fi

    if (( code == 0 )); then
        if [[ "$task" == "runClientGameTest" ]]; then
            local pass_line
            pass_line="$(grep -F 'TRINKIES_CLIENT_GAMETEST_PASS:' "$log_file" | tail -1 || true)"
            if [[ -z "$pass_line" ]]; then
                failure_reason="Gradle returned success without the Client GameTest pass marker"
                code=1
            else
                runtime_java="$(printf '%s\n' "$pass_line" | sed -E 's/.*java=([^, ]+).*/\1/')"
                if ! is_expected_java_major "$runtime_java" "$java_version"; then
                    failure_reason="Expected Java $java_version, but Client GameTest reported $runtime_java"
                    code=1
                elif ! copy_game_test_screenshots "$version"; then
                    failure_reason="Client GameTest passed but no screenshot was copied into the matrix artifact"
                    code=1
                else
                    evidence="startup screenshot copied to $target_dir"
                fi
            fi
        else
            local marker_file="$target_dir/client-boot-smoke.ok"
            if ! grep -Fq 'TRINKIES_CLIENT_BOOT_SMOKE_PASS:' "$log_file"; then
                failure_reason="Gradle returned success without the legacy client boot pass marker"
                code=1
            elif [[ ! -f "$marker_file" ]]; then
                failure_reason="Legacy client boot task passed without its marker file: $marker_file"
                code=1
            elif ! grep -Fxq "minecraft=$version" "$marker_file" || ! grep -Fxq 'trinkies_loaded=true' "$marker_file"; then
                failure_reason="Legacy client boot marker does not confirm the expected Minecraft version and loaded Trinkies"
                code=1
            elif ! grep -Fxq 'trinkies_loadout_applied=true' "$marker_file"; then
                failure_reason="Legacy client booted, but the Trinkies mod never answered trinkies/equipped.json"
                code=1
            elif ! grep -Fxq 'trinkies_emote_wheel_slots=1' "$marker_file"; then
                failure_reason="Legacy client read its wardrobe slots but never accepted the keybound emote wheel"
                code=1
            else
                runtime_java="$(sed -n 's/^java=//p' "$marker_file" | head -n 1)"
                if ! is_expected_java_major "$runtime_java" "$java_version"; then
                    failure_reason="Expected Java $java_version, but legacy client boot reported $runtime_java"
                    code=1
                else
                    evidence="client boot marker at $marker_file"
                fi
            fi
        fi
    fi

    if (( code != 0 )); then
        if [[ -z "$failure_reason" ]]; then
            failure_reason="Gradle client task exited with code $code"
        fi
        echo "Client test failed for Minecraft $version / Java $java_version (exit $code): $failure_reason"
        write_target_result "$target_dir" "$version" "$java_version" "$fabric_api" "$task" FAILED "$runtime_java" "$failure_reason"
        local diagnostic_file="$target_dir/failure-diagnostics.log"
        {
            echo "failure_reason=$failure_reason"
            echo '=== Runtime and failure markers ==='
            grep -inE 'TRINKIES_CLIENT_(GAMETEST|BOOT_SMOKE)|ERROR|FATAL|Exception|Caused by:|OutOfMemoryError|failed to|Failure:|Timeout|GLX|Vulkan' "$log_file" | tail -150 || true
            echo '=== Final Gradle/game log lines ==='
            tail -120 "$log_file"
        } > "$diagnostic_file"
        gzip -9c "$diagnostic_file" | base64 -w0 | fold -w 3500 |
            while IFS= read -r chunk || [[ -n "$chunk" ]]; do
                echo "::error title=client-matrix-${label}-log-gzip-base64::$chunk"
            done
        return "$code"
    fi

    write_target_result "$target_dir" "$version" "$java_version" "$fabric_api" "$task" PASS "$runtime_java" "$evidence"
    echo "TRINKIES_CLIENT_MATRIX_PASS: minecraft=$version, expected_java=$java_version, runtime_java=$runtime_java, fabric_api=$fabric_api, task=$task, evidence=$evidence"
    return 0
}

check_headless_graphics || exit $?

status=0
run_target 26.3 25 '0.165.2+26.3' runClientGameTest || status=1
run_target 1.21.11 21 '0.141.6+1.21.11' runClientGameTest || status=1
run_target 1.20.1 17 '0.92.12+1.20.1' verifyClientBootSmoke || status=1

if (( status != 0 )); then
    exit "$status"
fi

echo "All approved Fabric client smoke targets passed."
