# Blueprint 04 — Omni-Bar, Command Center, LiveScript & Macro Engine

## impl-04 status (read this before the older text below)

- **VoidShim decision (plan step 2) — SUPERSEDED by impl-01 step 6:**
  VoidShim was deleted, not wired in. There is no `pmc` / `nodeos` /
  `void` command surface and no `VoidShim.tryHandle` interception.
  `:nodeos` / `:ping` report Void daemon task status only. Do not
  reintroduce the shim; the plan's step-2 wiring option is closed.
- **Unknown `:command` (impl-04 step 1, plan gate S2):** an unknown
  colon command no longer falls through to VoidShell. It prints an
  unknown-command message with suggestions. Explicit shell is
  `:run`, `:sh`, or `:! <cmd>`.
- **`:help` (impl-04 step 0):** generated from the `CommandGuide`
  table in code, not from the hard-coded text this blueprint used
  to quote. The table is authoritative; this document's command
  lists are historical and may omit newer commands (`:macros`,
  `:void-js`, `:audit`, `:!`).
- **Command-center pipeline (until impl-04 step 3):** command center
  still has no `## alias` extraction and never records macro steps,
  unlike omni. Its search cap is 10, omni's is 5.

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
