#!/data/data/com.termux/files/usr/bin/bash
set -e

echo "=== [VoidLauncher] Build & Test Preflight ==="
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(dirname "$SCRIPT_DIR")"

cd "$PROJECT_ROOT"

echo "[1/2] Checking Termux environment..."
if [ -z "$PREFIX" ]; then
    echo "Warning: Not running in native Termux!"
fi

echo "[2/2] Running Universal QA Analyzer on Kotlin/XML codebase..."
if [ -f "$HOME/.gemini/config/skills/qa-analyzer/scripts/analyzer.py" ]; then
    python3 "$HOME/.gemini/config/skills/qa-analyzer/scripts/analyzer.py" "$PROJECT_ROOT/app"
fi

echo "=== VoidLauncher Preflight Complete ==="
