#!/usr/bin/env bash
#
# Reproducible secret inspection for release APKs (task 14.2).
# Unpacks the APK, optionally decompiles with JADX, and fails on unclassified
# high-signal markers (secrets, tokens, .env, staging/loopback endpoints).
# Generic HTTP terms (Authorization/Bearer) are reported, never failed:
# they legitimately occur in OkHttp/Retrofit.
#
# Usage:
#   scripts/audit-release-apk.sh <apk>
#   scripts/audit-release-apk.sh --self-test   # verifies the matcher itself
set -euo pipefail

HIGH_SIGNAL='twitch[_-]?client[_-]?secret|client[_-]?secret|api[_-]?key|apikey|\.env|10\.0\.2\.2|localhost:|127\.0\.0\.1:[0-9]|action_test_notification|(^|[^[:alnum:]])staging([.:/_-]|$)'
GENERIC='Authorization|Bearer'

match_high_signal() {
    # $1: directory to scan. Prints MATCHING FILE PATHS only, never matching
    # lines: a detected secret must not be copied into CI logs. Exit 0 with
    # output = finding; silent return 0 = clean. Scanner errors (>1) propagate
    # as failures, never as clean results.
    local root="$1"
    local output
    local status

    set +e
    output="$(grep -r -l -i -E "$HIGH_SIGNAL" "$root" 2>&1)"
    status=$?
    set -e

    case "$status" in
        0) printf '%s\n' "$output" ;;
        1) return 0 ;;
        *)
            printf '%s\n' "$output" >&2
            return "$status"
            ;;
    esac
}

report_generic() {
    # Informational only (filenames): these legitimately occur in OkHttp/Retrofit.
    grep -r -l -E "$GENERIC" "$1" | head -20 || true
}


if [[ "${1:-}" == "--self-test" ]]; then
    TMP="$(mktemp -d)"
    trap 'rm -rf "$TMP"' EXIT
    mkdir -p "$TMP/app"
    echo 'twitch_client_secret = "must-not-appear-in-output"' > "$TMP/app/Canary.smali"
    echo 'https://staging.example.com/v1/games' > "$TMP/app/Endpoint.smali"
    echo 'Authorization: Bearer xyz' > "$TMP/app/OkHttp.smali"
    if [[ -z "$(match_high_signal "$TMP/app")" ]]; then
        echo "SELF-TEST FAILED: canary marker not detected"
        exit 1
    fi
    echo "self-test: canary detected"
    if ! match_high_signal "$TMP/app" | grep -q 'Endpoint.smali'; then
        echo "SELF-TEST FAILED: staging marker not detected"
        exit 1
    fi
    echo "self-test: staging marker detected"
    if match_high_signal "$TMP/app" 2>&1 | grep -q 'must-not-appear-in-output'; then
        echo "SELF-TEST FAILED: secret value leaked into output"
        exit 1
    fi
    if ! match_high_signal "$TMP/app" 2>&1 | grep -q 'Canary.smali'; then
        echo "SELF-TEST FAILED: affected filename not reported"
        exit 1
    fi
    echo "self-test: filenames reported, values redacted"
    report_generic "$TMP/app" > /dev/null
    echo "self-test: generic terms reported without failing"
    mkdir -p "$TMP/locked/inner"
    chmod 000 "$TMP/locked/inner"
    if [[ "$(id -u)" != "0" ]] && match_high_signal "$TMP/locked" > /dev/null 2>&1; then
        echo "SELF-TEST FAILED: scanner error reported as clean"
        exit 1
    fi
    echo "self-test: scanner errors are not clean results"
    echo "SELF-TEST PASSED"
    exit 0
fi

APK="${1:?Usage: audit-release-apk.sh <apk> | --self-test}"
OUT="${RUNNER_TEMP:-build}/release-apk-audit"

rm -rf "$OUT"
mkdir -p "$OUT/unpacked" "$OUT/jadx"

sha256sum "$APK" | tee "$OUT/apk.sha256"
unzip -q "$APK" -d "$OUT/unpacked"

SCAN_ROOTS=("$OUT/unpacked")
if command -v jadx > /dev/null; then
    jadx --deobf --output-dir "$OUT/jadx" "$APK" > /dev/null
    SCAN_ROOTS+=("$OUT/jadx/io/github/typenil/gametracker")
fi

if command -v apkanalyzer > /dev/null; then
    apkanalyzer manifest print "$APK" > "$OUT/manifest.xml"
fi

FINDINGS="$OUT/unclassified-findings.txt"
: > "$FINDINGS"
for root in "${SCAN_ROOTS[@]}"; do
    [[ -d "$root" ]] && match_high_signal "$root" >> "$FINDINGS"
done

echo "--- generic HTTP terms (informational) ---" | tee "$OUT/generic-terms.txt"
for root in "${SCAN_ROOTS[@]}"; do
    [[ -d "$root" ]] && report_generic "$root" >> "$OUT/generic-terms.txt"
done

if [[ -s "$FINDINGS" ]]; then
    cat "$FINDINGS"
    echo "ERROR: unclassified release APK security markers found (see $FINDINGS)"
    exit 1
fi
echo "audit clean: no high-signal markers in $APK"
