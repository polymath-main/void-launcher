/*
 * voidquickjs.h — the real QuickJS macro engine for VoidLauncher.
 *
 * This is the native module QuickJsEngine.kt loads as `voidquickjs`.
 * One call runs one script under one manifest; the engine enforces
 * the manifest permissions natively (defence in depth — the Kotlin
 * runner also wraps adapters with PermissionCheckingAdapters).
 *
 * Result protocol (matches QuickJsEngine.parseNativeResult):
 *   OK:<return value stringified, empty for undefined>
 *   ERR:<message>  DENIED:<permission>  TIMEOUT:<timeout ms>
 *
 * JS surface (only these globals are added; no DOM, no network):
 *   print(...), console.log(...) -> host log
 *   Void.log(msg)                 (no permission)
 *   Void.notify(title, body)      NOTIFY
 *   Void.mode(name)               MODE
 *   Void.telemetry()              TELEMETRY -> {batteryPct, memFreeMB, memTotalMB, uptimeSec}
 *   Void.apps.list/search/launch  APPS
 *   Void.files.list/read/write    FILES
 *   Void.shell.run(cmd)           SHELL -> output string
 *   Void.storage.get/set          STORAGE
 *   Void.sleep(ms)                (no permission, clamped 0..5000 ms)
 * No trigger/workspace properties: the Kotlin manifest carries
 * neither, so none are invented here.
 *
 * Limits, enforced here: script cap 256 KiB (matches the runner's
 * file cap); timeout clamped 1000..60000 ms (matches manifest
 * parsing); one runtime per run; state lives on the JS context,
 * so concurrent macros on different threads do not share state.
 * Input strings are NUL-terminated UTF-8; embedded NULs truncate.
 */
#ifndef VOIDQUICKJS_H
#define VOIDQUICKJS_H

#include <stdint.h>
#include <stddef.h>

#ifdef __cplusplus
extern "C" {
#endif

/* Host callbacks implemented by the embedder (JNI on Android,
 * stubs in the host test). Returned strings are malloc'd and freed
 * by the engine; list callbacks return a NULL-terminated array of
 * malloc'd strings (free each entry, then the array). Apps entries
 * are "package<TAB>label" strings. NULL returns mean host error,
 * except storage_get, where NULL means "key missing". */
typedef struct {
    void (*log)(void *ctx, const char *message);
    void (*notify)(void *ctx, const char *title, const char *body);
    void (*set_mode)(void *ctx, const char *name);
    void (*telemetry)(void *ctx, int *battery_pct, int64_t *mem_free_mb,
                      int64_t *mem_total_mb, int64_t *uptime_sec);
    char **(*apps_list)(void *ctx, int *count);
    char **(*apps_search)(void *ctx, const char *query, int *count);
    int (*apps_launch)(void *ctx, const char *package_name);
    char **(*files_list)(void *ctx, const char *path, int *count);
    char *(*files_read)(void *ctx, const char *path);
    int (*files_write)(void *ctx, const char *path, const char *content);
    char *(*shell_run)(void *ctx, const char *command);
    char *(*storage_get)(void *ctx, const char *key);
    void (*storage_set)(void *ctx, const char *key, const char *value);
    void (*sleep_ms)(void *ctx, int64_t ms);
    void *ctx;
} VqHost;

/* Runs source under manifest_json {"name":..,"permissions":[..],
 * "timeout_ms":N} and returns a malloc'd result string the caller
 * frees. Never returns NULL (out-of-memory aside). */
char *vq_run(const char *source, const char *manifest_json, const VqHost *host);

#ifdef __cplusplus
}
#endif

#endif
