# Blueprint 04 — Omni-Bar, Command Center, LiveScript & Macro Engine

## 1. Domain Scope
Provides keyboard-driven command execution, math/expression evaluation, persistent SQLite command history, inline snippet creation (`## <alias>`), and multi-step macro recording/playback.

## 2. Micro-Service & Module Inventory

| Module | File | Responsibility |
|---|---|---|
| `CommandHelpMicroService` | [`CommandHelpMicroService.kt`](file:///data/data/com.termux/files/home/Projects/void-launcher/app/src/main/java/com/polymath/voidlauncher/core/services/CommandHelpMicroService.kt) | Parses inline `## <alias>` snippet definitions, resolves saved snippet aliases, formats `:help` reference output, and dispatches `:wallpaper` commands. |
| `CommandPalette` | [`CommandPalette.kt`](file:///data/data/com.termux/files/home/Projects/void-launcher/app/src/main/java/com/polymath/voidlauncher/core/CommandPalette.kt) | SQLite (`void_command_history.db`) store tracking execution frequency (`use_count`), timestamps, and user-defined snippet aliases. |
| `LiveScriptEngine` | [`LiveScriptEngine.kt`](file:///data/data/com.termux/files/home/Projects/void-launcher/app/src/main/java/com/polymath/voidlauncher/core/LiveScriptEngine.kt) | Zero-dependency shunting-yard + RPN evaluator supporting `+ - * / % ^`, parentheses, `0x` hex, `0b` binary, `time`, and `nanotime`. |
| `MacroRecorder` | [`MacroRecorder.kt`](file:///data/data/com.termux/files/home/Projects/void-launcher/app/src/main/java/com/polymath/voidlauncher/core/MacroRecorder.kt) | Captures sequences of Omni-Bar submissions into JSON files (`:record <name>`, `:stop`, `:play <name>`). |

## 3. Omni-Bar Dispatch Pipeline (`handleOmniSubmit`)
1. **Inline Snippet Extraction**: `CommandHelpMicroService.extractInlineSnippet(rawInput)` splits `<cmd> ## <alias>` and persists `<alias> := <cmd>`.
2. **History & Macro Recording**: Records command in SQLite `CommandPalette` and appends to `MacroRecorder` if recording is active.
3. **Bare Integer Fast Intercept**: Single or multi-digit integer (`1..7`) routes immediately through `BareIntegerRouter.routeInteger(n)`.
4. **Colon Command Mode (`:<cmd>`)**:
   - Built-in commands (`:help`, `:wallpaper`, `:eval`, `:task`, `:tasks`, `:kill`, `:graph`, `:persona`, `:id`, `:record`, `:stop`, `:play`, `:history`, `:snippet`, `:snippets`, `:term`, `:power`, `:focus`, `:themes`, `:theme`, `:mode`, `:run`, `:sh`, `:work`, `:files`, `:apps`, `:settings`, `:still`, `:cockpit`, `:minimal`, `:deck`, `:cmd`, `:reload`).
   - Saved snippet alias lookup via `CommandHelpMicroService.findSnippetTarget(cmd)`.
   - Fallback one-shot execution via `VoidShell.run(cmd)`.
5. **Fuzzy App Launch**: Launches `AppIndexer.findBestMatch(query)`.
