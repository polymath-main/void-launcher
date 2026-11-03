#!/usr/bin/env bash
# Area 07 step 6 — version-consistency check for CI.
# Compares app/build.gradle versionName against the stamps in
# docs/ARCHITECTURE.md and docs/ARCHITECTURE_OVERVIEW.md.
# Deliberately NOT compared against git tags: no 3.0.0 tag exists
# yet (tag creation is Shuvo's decision) and the old tags
# v1.0.0/v1.0.1/v1.0.2 would fail the check spuriously.
set -euo pipefail
cd "$(dirname "$0")/.."

gradle_version=$(grep -E 'versionName "' app/build.gradle | head -1 | sed -E 's/.*"([^"]+)".*/\1/')
arch_version=$(grep -F '**Version**:' docs/ARCHITECTURE.md | head -1 | sed -E 's/.*Version\*\*: ([0-9.]+).*/\1/')
overview_version=$(grep -E 'v[0-9]+\.[0-9]+\.[0-9]+' docs/ARCHITECTURE_OVERVIEW.md | head -1 | sed -E 's/.*v([0-9]+\.[0-9]+\.[0-9]+).*/\1/')

echo "gradle:   $gradle_version"
echo "arch:     $arch_version"
echo "overview: $overview_version"

fail=0
[ "$gradle_version" = "$arch_version" ] || { echo "MISMATCH: gradle vs ARCHITECTURE.md"; fail=1; }
[ "$gradle_version" = "$overview_version" ] || { echo "MISMATCH: gradle vs ARCHITECTURE_OVERVIEW.md"; fail=1; }
exit $fail
