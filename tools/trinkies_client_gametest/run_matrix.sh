#!/usr/bin/env bash
set -euo pipefail

PROJECT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
ARTIFACT_DIR="$PROJECT_DIR/build/matrix-artifacts"
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

copy_game_test_screenshots() {
    local version="$1"
    local destination="$ARTIFACT_DIR/$version"
    local screenshot_dir="$PROJECT_DIR/build/run/clientGameTest/screenshots"
    local -a screenshots=()

    if [[ -d "$screenshot_dir" ]]; then
        mapfile -d '' screenshots < <(find "$screenshot_dir" -type f -name '*.png' -print0)
    fi

    if (( ${#screenshots[@]} == 0 )); then
        echo "No client GameTest screenshot found for $version in $screenshot_dir" >&2
        return 1
    fi

    mkdir -p "$destination"
    cp "${screenshots[@]}" "$destination/"
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

    mkdir -p "$target_dir"
    echo "=== Trinkies baseline: Minecraft $version / Java $java_version / Fabric API $fabric_api ==="

    echo "Gradle client task has a 14-minute limit for $version; filtered progress is streamed below."
    set +e
    timeout --signal=TERM --kill-after=30s 14m xvfb-run -a gradle \
        -p "$PROJECT_DIR" \
        "-Pminecraft_version=$version" \
        -Ploader_version=0.20.1 \
        -Ploom_version=1.19-SNAPSHOT \
        "-Pfabric_api_version=$fabric_api" \
        "-Ptest_runtime_java=$java_version" \
        "$task" --console=plain 2>&1 \
        | tee "$log_file" \
        | grep --line-buffered -E '(^> Task|^BUILD|^FAILURE:|TRINKIES_CLIENT_GAMETEST_STARTED|ERROR|FATAL|Exception|Caused by:|Timeout|Fabric Loom:)'
    pipeline_status=("${PIPESTATUS[@]}")
    code="${pipeline_status[0]}"
    if (( code == 0 && ${pipeline_status[1]:-0} != 0 )); then
        code="${pipeline_status[1]}"
    fi
    set -e

    tail -50 "$log_file"
    if (( code != 0 )); then
        local diagnostic_file="$target_dir/failure-diagnostics.log"
        echo "Client test failed for Minecraft $version / Java $java_version (exit $code)."
        {
            echo '=== Runtime and failure markers ==='
            grep -inE 'TRINKIES_CLIENT_GAMETEST_STARTED|ERROR|FATAL|Exception|Caused by:|OutOfMemoryError|failed to|Failure:|Timeout' "$log_file" | tail -150 || true
            echo '=== Final Gradle/game log lines ==='
            tail -120 "$log_file"
        } > "$diagnostic_file"
        gzip -9c "$diagnostic_file" | base64 -w0 | fold -w 3500 |
            while IFS= read -r chunk || [[ -n "$chunk" ]]; do
                echo "::error title=client-matrix-${label}-log-gzip-base64::$chunk"
            done
        return "$code"
    fi

    if [[ "$task" == "runClientGameTest" ]] && ! copy_game_test_screenshots "$version"; then
        return 1
    fi
    return 0
}

status=0
run_target 26.3 25 '0.165.2+26.3' runClientGameTest || status=1
run_target 1.21.11 21 '0.141.6+1.21.11' runClientGameTest || status=1
run_target 1.20.1 17 '0.92.12+1.20.1' verifyClientBootSmoke || status=1

if (( status != 0 )); then
    exit "$status"
fi

echo "All approved Fabric client smoke targets passed."
