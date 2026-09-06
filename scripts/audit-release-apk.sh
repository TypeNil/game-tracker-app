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

HIGH_SIGNAL='twitch[_-]?client[_-]?secret|client[_-]?secret|api[_-]?key|apikey|\.env|10\.0\.2\.2|localhost:|127\.0\.0\.1:[0-9]|action_test_notification'
GENERIC='Authorization|Bearer'

match_high_signal() {
    # $1: directory to scan. Prints matches, succeeds silently when clean.
    grep -r -n -i -E "$HIGH_SIGNAL" "$1" || true
}

report_generic() {
    grep -r -n -E "$GENERIC" "$1" | head -20 || true
}

if [[ "${1:-}" == "--self-test" ]]; then
    TMP="$(mktemp -d)"
    trap 'rm -rf "$TMP"' EXIT
    mkdir -p "$TMP/app"
    echo 'twitch_client_secret = "canary"' > "$TMP/app/Canary.smali"
    echo 'Authorization: Bearer xyz' > "$TMP/app/OkHttp.smali"
    if [[ -z "$(match_high_signal "$TMP/app")" ]]; then
        echo "SELF-TEST FAILED: canary marker not detected"
        exit 1
    fi
    echo "self-test: canary detected"
    report_generic "$TMP/app" > /dev/null
    echo "self-test: generic terms reported without failing"
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
