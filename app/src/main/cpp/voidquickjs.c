/*
 * voidquickjs.c — real QuickJS macro engine for VoidLauncher.
 *
 * Port of the desktop prototype (quickjs-macro-engine/src/void_macro.c)
 * with the host implementations replaced by VqHost callbacks, so the
 * same engine runs on Android through JNI and on a host through
 * stubs. See voidquickjs.h for the contract.
 */
#define _GNU_SOURCE
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <stdint.h>
#include <time.h>
#include "quickjs.h"
#include "voidquickjs.h"

#define VQ_MAX_SOURCE (256 * 1024)
#define VQ_MIN_TIMEOUT_MS 1000
#define VQ_MAX_TIMEOUT_MS 60000

typedef struct {
    const VqHost *host;
    int perm_notify, perm_telemetry, perm_storage, perm_mode;
    int perm_files, perm_shell, perm_apps;
    int64_t timeout_ms;
    int64_t start_ms;
    int timed_out;
} VqState;

static int64_t now_ms(void) {
    struct timespec ts;
    clock_gettime(CLOCK_MONOTONIC, &ts);
    return (int64_t)ts.tv_sec * 1000 + ts.tv_nsec / 1000000;
}

static int interrupt_handler(JSRuntime *rt, void *opaque) {
    (void)rt;
    VqState *st = (VqState *)opaque;
    if (now_ms() - st->start_ms > st->timeout_ms) {
        st->timed_out = 1;
        return 1;
    }
    return 0;
}

static VqState *state_of(JSContext *ctx) {
    return (VqState *)JS_GetContextOpaque(ctx);
}

/* ---------------- small helpers ---------------- */

static char *dup_str(const char *s) {
    if (!s) return NULL;
    size_t n = strlen(s) + 1;
    char *d = (char *)malloc(n);
    if (d) memcpy(d, s, n);
    return d;
}

/* malloc'd "PREFIX<msg>"; msg may be NULL. */
static char *fmt_result(const char *prefix, const char *msg) {
    size_t pn = strlen(prefix), mn = msg ? strlen(msg) : 0;
    char *r = (char *)malloc(pn + mn + 1);
    if (!r) return NULL;
    memcpy(r, prefix, pn);
    if (msg) memcpy(r + pn, msg, mn);
    r[pn + mn] = '\0';
    return r;
}

/* Argument i as a malloc'd string (JS_ToCString stringifies numbers);
 * missing args become def (may be NULL -> empty). Caller frees. */
static char *arg_str(JSContext *ctx, int argc, JSValueConst *argv, int i, const char *def) {
    if (i >= argc) return dup_str(def ? def : "");
    const char *s = JS_ToCString(ctx, argv[i]);
    if (!s) return dup_str("");
    char *d = dup_str(s);
    JS_FreeCString(ctx, s);
    return d;
}

static int require_perm(JSContext *ctx, int allowed, const char *perm) {
    if (allowed) return 1;
    JS_ThrowTypeError(ctx, "DENIED:%s", perm);
    return 0;
}

static void add_fn(JSContext *ctx, JSValue obj, const char *name, JSCFunction *fn, int len) {
    /* JS_SetPropertyStr consumes the function value: do NOT free it after. */
    JS_SetPropertyStr(ctx, obj, name, JS_NewCFunction(ctx, fn, name, len));
}

/* Free a NULL-terminated string array from a host list callback. */
static void free_str_array(char **arr, int count) {
    if (!arr) return;
    for (int i = 0; i < count; i++) free(arr[i]);
    free(arr);
}

/* ---------------- Void.* functions ---------------- */

static JSValue js_log(JSContext *ctx, JSValueConst this_val, int argc, JSValueConst *argv) {
    (void)this_val;
    VqState *st = state_of(ctx);
    /* Join args with spaces into one log line. */
    size_t total = 1;
    char **parts = NULL;
    if (argc > 0) {
        parts = (char **)calloc((size_t)argc, sizeof(char *));
        for (int i = 0; i < argc; i++) {
            parts[i] = arg_str(ctx, argc, argv, i, "");
            total += strlen(parts[i]) + 1;
        }
    }
    char *line = (char *)malloc(total);
    line[0] = '\0';
    for (int i = 0; i < argc; i++) {
        if (i) strcat(line, " ");
        strcat(line, parts[i]);
        free(parts[i]);
    }
    free(parts);
    st->host->log(st->host->ctx, line);
    free(line);
    return JS_UNDEFINED;
}

static JSValue js_notify(JSContext *ctx, JSValueConst this_val, int argc, JSValueConst *argv) {
    (void)this_val;
    VqState *st = state_of(ctx);
    if (!require_perm(ctx, st->perm_notify, "notify")) return JS_EXCEPTION;
    char *title = arg_str(ctx, argc, argv, 0, "");
    char *body = arg_str(ctx, argc, argv, 1, "");
    st->host->notify(st->host->ctx, title, body);
    free(title);
    free(body);
    return JS_UNDEFINED;
}

static JSValue js_mode(JSContext *ctx, JSValueConst this_val, int argc, JSValueConst *argv) {
    (void)this_val;
    VqState *st = state_of(ctx);
    if (!require_perm(ctx, st->perm_mode, "mode")) return JS_EXCEPTION;
    char *name = arg_str(ctx, argc, argv, 0, "");
    st->host->set_mode(st->host->ctx, name);
    free(name);
    return JS_UNDEFINED;
}

static JSValue js_telemetry(JSContext *ctx, JSValueConst this_val, int argc, JSValueConst *argv) {
    (void)this_val; (void)argc; (void)argv;
    VqState *st = state_of(ctx);
    if (!require_perm(ctx, st->perm_telemetry, "telemetry")) return JS_EXCEPTION;
    int battery = 0;
    int64_t free_mb = 0, total_mb = 0, uptime = 0;
    st->host->telemetry(st->host->ctx, &battery, &free_mb, &total_mb, &uptime);
    JSValue o = JS_NewObject(ctx);
    JS_SetPropertyStr(ctx, o, "batteryPct", JS_NewInt64(ctx, battery));
    JS_SetPropertyStr(ctx, o, "memFreeMB", JS_NewInt64(ctx, free_mb));
    JS_SetPropertyStr(ctx, o, "memTotalMB", JS_NewInt64(ctx, total_mb));
    JS_SetPropertyStr(ctx, o, "uptimeSec", JS_NewInt64(ctx, uptime));
    return o;
}

/* Build [{package, label}] from "pkg\tlabel" entries. */
static JSValue apps_array(JSContext *ctx, char **entries, int count) {
    JSValue arr = JS_NewArray(ctx);
    for (int i = 0; i < count; i++) {
        char *tab = strchr(entries[i], '\t');
        JSValue o = JS_NewObject(ctx);
        if (tab) {
            *tab = '\0';
            JS_SetPropertyStr(ctx, o, "package", JS_NewString(ctx, entries[i]));
            JS_SetPropertyStr(ctx, o, "label", JS_NewString(ctx, tab + 1));
        } else {
            JS_SetPropertyStr(ctx, o, "package", JS_NewString(ctx, entries[i]));
            JS_SetPropertyStr(ctx, o, "label", JS_NewString(ctx, ""));
        }
        JS_SetPropertyUint32(ctx, arr, (uint32_t)i, o);
    }
    return arr;
}

static JSValue js_apps_list(JSContext *ctx, JSValueConst this_val, int argc, JSValueConst *argv) {
    (void)this_val; (void)argc; (void)argv;
    VqState *st = state_of(ctx);
    if (!require_perm(ctx, st->perm_apps, "apps")) return JS_EXCEPTION;
    int count = 0;
    char **entries = st->host->apps_list(st->host->ctx, &count);
    if (!entries) return JS_ThrowTypeError(ctx, "apps.list: host error");
    JSValue arr = apps_array(ctx, entries, count);
    free_str_array(entries, count);
    return arr;
}

static JSValue js_apps_search(JSContext *ctx, JSValueConst this_val, int argc, JSValueConst *argv) {
    (void)this_val;
    VqState *st = state_of(ctx);
    if (!require_perm(ctx, st->perm_apps, "apps")) return JS_EXCEPTION;
    char *q = arg_str(ctx, argc, argv, 0, "");
    int count = 0;
    char **entries = st->host->apps_search(st->host->ctx, q, &count);
    free(q);
    if (!entries) return JS_ThrowTypeError(ctx, "apps.search: host error");
    JSValue arr = apps_array(ctx, entries, count);
    free_str_array(entries, count);
    return arr;
}

static JSValue js_apps_launch(JSContext *ctx, JSValueConst this_val, int argc, JSValueConst *argv) {
    (void)this_val;
    VqState *st = state_of(ctx);
    if (!require_perm(ctx, st->perm_apps, "apps")) return JS_EXCEPTION;
    char *pkg = arg_str(ctx, argc, argv, 0, "");
    int ok = st->host->apps_launch(st->host->ctx, pkg);
    free(pkg);
    return ok ? JS_TRUE : JS_FALSE;
}

static JSValue js_files_list(JSContext *ctx, JSValueConst this_val, int argc, JSValueConst *argv) {
    (void)this_val;
    VqState *st = state_of(ctx);
    if (!require_perm(ctx, st->perm_files, "files")) return JS_EXCEPTION;
    char *path = arg_str(ctx, argc, argv, 0, ".");
    int count = 0;
    char **entries = st->host->files_list(st->host->ctx, path, &count);
    free(path);
    if (!entries) return JS_ThrowTypeError(ctx, "files.list: host error");
    JSValue arr = JS_NewArray(ctx);
    for (int i = 0; i < count; i++) {
        JSValue s = JS_NewString(ctx, entries[i]);
        JS_SetPropertyUint32(ctx, arr, (uint32_t)i, s);
    }
    free_str_array(entries, count);
    return arr;
}

static JSValue js_files_read(JSContext *ctx, JSValueConst this_val, int argc, JSValueConst *argv) {
    (void)this_val;
    VqState *st = state_of(ctx);
    if (!require_perm(ctx, st->perm_files, "files")) return JS_EXCEPTION;
    char *path = arg_str(ctx, argc, argv, 0, "");
    char *content = st->host->files_read(st->host->ctx, path);
    free(path);
    if (!content) return JS_ThrowTypeError(ctx, "files.read: cannot read file");
    JSValue r = JS_NewString(ctx, content);
    free(content);
    return r;
}

static JSValue js_files_write(JSContext *ctx, JSValueConst this_val, int argc, JSValueConst *argv) {
    (void)this_val;
    VqState *st = state_of(ctx);
    if (!require_perm(ctx, st->perm_files, "files")) return JS_EXCEPTION;
    char *path = arg_str(ctx, argc, argv, 0, "");
    char *content = arg_str(ctx, argc, argv, 1, "");
    int ok = st->host->files_write(st->host->ctx, path, content);
    free(path);
    free(content);
    if (!ok) return JS_ThrowTypeError(ctx, "files.write: cannot write file");
    return JS_TRUE;
}

static JSValue js_shell_run(JSContext *ctx, JSValueConst this_val, int argc, JSValueConst *argv) {
    (void)this_val;
    VqState *st = state_of(ctx);
    if (!require_perm(ctx, st->perm_shell, "shell")) return JS_EXCEPTION;
    char *cmd = arg_str(ctx, argc, argv, 0, "");
    if (!cmd[0]) {
        free(cmd);
        return JS_ThrowTypeError(ctx, "shell.run: empty command");
    }
    char *out = st->host->shell_run(st->host->ctx, cmd);
    free(cmd);
    if (!out) return JS_ThrowTypeError(ctx, "shell.run: host error");
    JSValue r = JS_NewString(ctx, out);
    free(out);
    return r;
}

static JSValue js_storage_get(JSContext *ctx, JSValueConst this_val, int argc, JSValueConst *argv) {
    (void)this_val;
    VqState *st = state_of(ctx);
    if (!require_perm(ctx, st->perm_storage, "storage")) return JS_EXCEPTION;
    char *key = arg_str(ctx, argc, argv, 0, "");
    char *val = st->host->storage_get(st->host->ctx, key);
    free(key);
    if (!val) return JS_NULL;
    JSValue r = JS_NewString(ctx, val);
    free(val);
    return r;
}

static JSValue js_storage_set(JSContext *ctx, JSValueConst this_val, int argc, JSValueConst *argv) {
    (void)this_val;
    VqState *st = state_of(ctx);
    if (!require_perm(ctx, st->perm_storage, "storage")) return JS_EXCEPTION;
    char *key = arg_str(ctx, argc, argv, 0, "");
    char *val = arg_str(ctx, argc, argv, 1, "");
    st->host->storage_set(st->host->ctx, key, val);
    free(key);
    free(val);
    return JS_TRUE;
}

static JSValue js_sleep(JSContext *ctx, JSValueConst this_val, int argc, JSValueConst *argv) {
    (void)this_val;
    VqState *st = state_of(ctx);
    int64_t ms = 0;
    if (argc > 0) JS_ToInt64(ctx, &ms, argv[0]);
    if (ms < 0) ms = 0;
    if (ms > 5000) ms = 5000;
    st->host->sleep_ms(st->host->ctx, ms);
    return JS_UNDEFINED;
}

static void register_void(JSContext *ctx) {
    JSValue global = JS_GetGlobalObject(ctx);
    JS_SetPropertyStr(ctx, global, "print", JS_NewCFunction(ctx, js_log, "print", 1));
    JSValue console = JS_NewObject(ctx);
    add_fn(ctx, console, "log", js_log, 1);
    JS_SetPropertyStr(ctx, global, "console", console);
    /* SetProperty consumes its value: console/apps/files/shell/storage/Void
     * must NOT be freed after being set. Only `global` (from
     * GetGlobalObject) is freed below. */

    JSValue v = JS_NewObject(ctx);
    add_fn(ctx, v, "log", js_log, 1);
    add_fn(ctx, v, "notify", js_notify, 2);
    add_fn(ctx, v, "mode", js_mode, 1);
    add_fn(ctx, v, "telemetry", js_telemetry, 0);
    JSValue apps = JS_NewObject(ctx);
    add_fn(ctx, apps, "list", js_apps_list, 0);
    add_fn(ctx, apps, "search", js_apps_search, 1);
    add_fn(ctx, apps, "launch", js_apps_launch, 1);
    JS_SetPropertyStr(ctx, v, "apps", apps);
    JSValue files = JS_NewObject(ctx);
    add_fn(ctx, files, "list", js_files_list, 1);
    add_fn(ctx, files, "read", js_files_read, 1);
    add_fn(ctx, files, "write", js_files_write, 2);
    JS_SetPropertyStr(ctx, v, "files", files);
    JSValue shell = JS_NewObject(ctx);
    add_fn(ctx, shell, "run", js_shell_run, 1);
    JS_SetPropertyStr(ctx, v, "shell", shell);
    JSValue storage = JS_NewObject(ctx);
    add_fn(ctx, storage, "get", js_storage_get, 1);
    add_fn(ctx, storage, "set", js_storage_set, 2);
    JS_SetPropertyStr(ctx, v, "storage", storage);
    add_fn(ctx, v, "sleep", js_sleep, 1);
    JS_SetPropertyStr(ctx, global, "Void", v);
    JS_FreeValue(ctx, global);
}

/* ---------------- manifest parsing ---------------- */

/* True if "name" appears as a quoted string inside [open, close). */
static int perm_in_list(const char *open, const char *close, const char *name) {
    size_t nlen = strlen(name);
    for (const char *p = open; p + nlen + 2 <= close; p++) {
        if (p[0] == '"' && strncmp(p + 1, name, nlen) == 0 && p[1 + nlen] == '"')
            return 1;
    }
    return 0;
}

static void parse_manifest(const char *json, VqState *st) {
    /* Kotlin defaults when no permission list is present. */
    st->perm_notify = st->perm_telemetry = st->perm_storage = st->perm_mode = 1;
    st->perm_files = st->perm_shell = st->perm_apps = 0;
    st->timeout_ms = 5000;
    if (!json) return;
    const char *p = strstr(json, "\"permissions\"");
    if (p) {
        const char *open = strchr(p, '[');
        const char *close = open ? strchr(open, ']') : NULL;
        if (open && close) {
            /* Kotlin always sends the exact set, so trust the list. */
            st->perm_notify = perm_in_list(open, close, "notify");
            st->perm_telemetry = perm_in_list(open, close, "telemetry");
            st->perm_storage = perm_in_list(open, close, "storage");
            st->perm_mode = perm_in_list(open, close, "mode");
            st->perm_files = perm_in_list(open, close, "files");
            st->perm_shell = perm_in_list(open, close, "shell");
            st->perm_apps = perm_in_list(open, close, "apps");
        }
    }
    const char *t = strstr(json, "\"timeout_ms\"");
    if (t) {
        const char *colon = strchr(t, ':');
        if (colon) {
            long ms = strtol(colon + 1, NULL, 10);
            if (ms < VQ_MIN_TIMEOUT_MS) ms = VQ_MIN_TIMEOUT_MS;
            if (ms > VQ_MAX_TIMEOUT_MS) ms = VQ_MAX_TIMEOUT_MS;
            st->timeout_ms = ms;
        }
    }
}

/* ---------------- entry point ---------------- */

char *vq_run(const char *source, const char *manifest_json, const VqHost *host) {
    if (!source || !host) return fmt_result("ERR:", "null input");
    if (strlen(source) > VQ_MAX_SOURCE) return fmt_result("ERR:", "script too large (256 KiB cap)");

    VqState st;
    memset(&st, 0, sizeof(st));
    st.host = host;
    parse_manifest(manifest_json, &st);

    JSRuntime *rt = JS_NewRuntime();
    if (!rt) return fmt_result("ERR:", "cannot create QuickJS runtime");
    JS_SetInterruptHandler(rt, interrupt_handler, &st);
    JSContext *ctx = JS_NewContext(rt);
    if (!ctx) {
        JS_FreeRuntime(rt);
        return fmt_result("ERR:", "cannot create QuickJS context");
    }
    JS_SetContextOpaque(ctx, &st);
    register_void(ctx);
    st.start_ms = now_ms();

    JSValue val = JS_Eval(ctx, source, strlen(source), "<macro>", JS_EVAL_TYPE_GLOBAL);
    char *result = NULL;
    if (JS_IsException(val)) {
        if (st.timed_out) {
            char buf[64];
            snprintf(buf, sizeof(buf), "TIMEOUT:%lld", (long long)st.timeout_ms);
            result = dup_str(buf);
        } else {
            JSValue ex = JS_GetException(ctx);
            JSValue msgv = JS_GetPropertyStr(ctx, ex, "message");
            const char *msg = NULL;
            char *msg_copy = NULL;
            if (JS_IsString(msgv)) {
                const char *s = JS_ToCString(ctx, msgv);
                if (s) {
                    msg_copy = dup_str(s);
                    JS_FreeCString(ctx, s);
                }
            } else {
                /* Thrown non-Error (e.g. throw "x"): stringify the value. */
                const char *s = JS_ToCString(ctx, ex);
                if (s) {
                    msg_copy = dup_str(s);
                    JS_FreeCString(ctx, s);
                }
            }
            msg = msg_copy;
            if (msg && strncmp(msg, "DENIED:", 7) == 0) {
                result = dup_str(msg);
            } else if (msg) {
                result = fmt_result("ERR:", msg);
            } else {
                result = fmt_result("ERR:", "script error");
            }
            free(msg_copy);
            JS_FreeValue(ctx, msgv);
            JS_FreeValue(ctx, ex);
        }
    } else if (JS_IsUndefined(val)) {
        result = dup_str("OK:");
    } else {
        const char *s = JS_ToCString(ctx, val);
        result = fmt_result("OK:", s ? s : "");
        if (s) JS_FreeCString(ctx, s);
    }
    JS_FreeValue(ctx, val);
    JS_FreeContext(ctx);
    JS_FreeRuntime(rt);
    return result ? result : fmt_result("ERR:", "out of memory");
}

#ifndef VQ_NO_JNI
#include <jni.h>

/* ---------------- JNI binding ---------------- */
/* Method IDs for com.polymath.voidlauncher.core.js.JsAdapters. */

typedef struct {
    JNIEnv *env;
    jobject adapters;
    jmethodID m_log, m_notify, m_set_mode, m_telemetry;
    jmethodID m_apps_list, m_apps_search, m_apps_launch;
    jmethodID m_files_list, m_files_read, m_files_write;
    jmethodID m_shell_run, m_storage_get, m_storage_set, m_sleep;
} JniCtx;

static jmethodID jni_method(JNIEnv *env, jclass cls, const char *name, const char *sig) {
    jmethodID m = (*env)->GetMethodID(env, cls, name, sig);
    if (!m) (*env)->ExceptionClear(env);
    return m;
}

/* Returns 0 ok, 1 generic host error, 2 permission denied (perm filled). */
static int jni_check_exc(JNIEnv *env, char *denied, size_t denied_n) {
    if (!(*env)->ExceptionCheck(env)) return 0;
    jthrowable ex = (*env)->ExceptionOccurred(env);
    (*env)->ExceptionClear(env);
    jclass denied_cls = (*env)->FindClass(env, "com/polymath/voidlauncher/core/js/JsPermissionDeniedException");
    if (!denied_cls) {
        (*env)->ExceptionClear(env);
        (*env)->DeleteLocalRef(env, ex);
        return 1;
    }
    if ((*env)->IsInstanceOf(env, ex, denied_cls)) {
        jmethodID get_perm = (*env)->GetMethodID(env, denied_cls, "getPermission", "()Ljava/lang/String;");
        if (get_perm) {
            jstring p = (jstring)(*env)->CallObjectMethod(env, ex, get_perm);
            if (p && !(*env)->ExceptionCheck(env)) {
                const char *s = (*env)->GetStringUTFChars(env, p, NULL);
                if (s) {
                    snprintf(denied, denied_n, "%s", s);
                    (*env)->ReleaseStringUTFChars(env, p, s);
                }
                (*env)->DeleteLocalRef(env, p);
                (*env)->DeleteLocalRef(env, ex);
                return 2;
            }
            (*env)->ExceptionClear(env);
        }
    }
    if (denied_cls) (*env)->DeleteLocalRef(env, denied_cls);
    (*env)->DeleteLocalRef(env, ex);
    return 1;
}

static char *jni_jstring_to_c(JNIEnv *env, jstring s) {
    if (!s) return NULL;
    const char *c = (*env)->GetStringUTFChars(env, s, NULL);
    if (!c) return NULL;
    char *d = dup_str(c);
    (*env)->ReleaseStringUTFChars(env, s, c);
    return d;
}

/* Converts a java.util.List<String> to a malloc'd string array. */
static char **jni_string_list(JNIEnv *env, jobject list, int *count) {
    *count = 0;
    if (!list) return NULL;
    jclass list_cls = (*env)->GetObjectClass(env, list);
    jmethodID m_size = (*env)->GetMethodID(env, list_cls, "size", "()I");
    jmethodID m_get = (*env)->GetMethodID(env, list_cls, "get", "(I)Ljava/lang/Object;");
    if (!m_size || !m_get) {
        (*env)->ExceptionClear(env);
        (*env)->DeleteLocalRef(env, list_cls);
        return NULL;
    }
    jint n = (*env)->CallIntMethod(env, list, m_size);
    if ((*env)->ExceptionCheck(env)) {
        (*env)->ExceptionClear(env);
        (*env)->DeleteLocalRef(env, list_cls);
        return NULL;
    }
    char **out = (char **)calloc((size_t)n + 1, sizeof(char *));
    for (jint i = 0; i < n; i++) {
        jstring item = (jstring)(*env)->CallObjectMethod(env, list, m_get, i);
        if ((*env)->ExceptionCheck(env) || !item) {
            (*env)->ExceptionClear(env);
            if (item) (*env)->DeleteLocalRef(env, item);
            for (jint k = 0; k < i; k++) free(out[k]);
            free(out);
            (*env)->DeleteLocalRef(env, list_cls);
            *count = 0;
            return NULL;
        }
        out[i] = jni_jstring_to_c(env, item);
        (*env)->DeleteLocalRef(env, item);
    }
    *count = n;
    (*env)->DeleteLocalRef(env, list_cls);
    return out;
}

static void jni_log(void *c, const char *message) {
    JniCtx *j = (JniCtx *)c;
    jstring s = (*j->env)->NewStringUTF(j->env, message);
    if (!s) return;
    (*j->env)->CallVoidMethod(j->env, j->adapters, j->m_log, s);
    (*j->env)->DeleteLocalRef(j->env, s);
    char denied[64];
    jni_check_exc(j->env, denied, sizeof(denied)); /* log cannot be denied; ignore errors */
}

static void jni_notify(void *c, const char *title, const char *body) {
    JniCtx *j = (JniCtx *)c;
    jstring t = (*j->env)->NewStringUTF(j->env, title);
    jstring b = (*j->env)->NewStringUTF(j->env, body);
    if (!t || !b) return;
    (*j->env)->CallVoidMethod(j->env, j->adapters, j->m_notify, t, b);
    (*j->env)->DeleteLocalRef(j->env, t);
    (*j->env)->DeleteLocalRef(j->env, b);
}

static void jni_set_mode(void *c, const char *name) {
    JniCtx *j = (JniCtx *)c;
    jstring s = (*j->env)->NewStringUTF(j->env, name);
    if (!s) return;
    (*j->env)->CallVoidMethod(j->env, j->adapters, j->m_set_mode, s);
    (*j->env)->DeleteLocalRef(j->env, s);
}

static void jni_telemetry(void *c, int *battery, int64_t *free_mb, int64_t *total_mb, int64_t *uptime) {
    JniCtx *j = (JniCtx *)c;
    jobject t = (*j->env)->CallObjectMethod(j->env, j->adapters, j->m_telemetry);
    if ((*j->env)->ExceptionCheck(j->env) || !t) {
        (*j->env)->ExceptionClear(j->env);
        return;
    }
    jclass cls = (*j->env)->GetObjectClass(j->env, t);
    jmethodID g_b = (*j->env)->GetMethodID(j->env, cls, "getBatteryPct", "()I");
    jmethodID g_f = (*j->env)->GetMethodID(j->env, cls, "getMemFreeMB", "()J");
    jmethodID g_t = (*j->env)->GetMethodID(j->env, cls, "getMemTotalMB", "()J");
    jmethodID g_u = (*j->env)->GetMethodID(j->env, cls, "getUptimeSec", "()J");
    if (g_b && g_f && g_t && g_u) {
        *battery = (*j->env)->CallIntMethod(j->env, t, g_b);
        *free_mb = (*j->env)->CallLongMethod(j->env, t, g_f);
        *total_mb = (*j->env)->CallLongMethod(j->env, t, g_t);
        *uptime = (*j->env)->CallLongMethod(j->env, t, g_u);
    }
    (*j->env)->ExceptionClear(j->env);
    (*j->env)->DeleteLocalRef(j->env, cls);
    (*j->env)->DeleteLocalRef(j->env, t);
}

static char **jni_apps(void *c, const char *query, int *count) {
    JniCtx *j = (JniCtx *)c;
    jobject list;
    if (query) {
        jstring q = (*j->env)->NewStringUTF(j->env, query);
        list = (*j->env)->CallObjectMethod(j->env, j->adapters, j->m_apps_search, q);
        (*j->env)->DeleteLocalRef(j->env, q);
    } else {
        list = (*j->env)->CallObjectMethod(j->env, j->adapters, j->m_apps_list);
    }
    char denied[64];
    if (jni_check_exc(j->env, denied, sizeof(denied))) return NULL;
    if (!list) return NULL;
    jclass list_cls = (*j->env)->GetObjectClass(j->env, list);
    jmethodID m_size = (*j->env)->GetMethodID(j->env, list_cls, "size", "()I");
    jmethodID m_get = (*j->env)->GetMethodID(j->env, list_cls, "get", "(I)Ljava/lang/Object;");
    if (!m_size || !m_get) {
        (*j->env)->ExceptionClear(j->env);
        (*j->env)->DeleteLocalRef(j->env, list_cls);
        (*j->env)->DeleteLocalRef(j->env, list);
        return NULL;
    }
    jint n = (*j->env)->CallIntMethod(j->env, list, m_size);
    char **out = (char **)calloc((size_t)n + 1, sizeof(char *));
    jclass app_cls = (*j->env)->FindClass(j->env, "com/polymath/voidlauncher/core/js/JsApp");
    jmethodID g_pkg = app_cls ? (*j->env)->GetMethodID(j->env, app_cls, "getPackageName", "()Ljava/lang/String;") : NULL;
    jmethodID g_lbl = app_cls ? (*j->env)->GetMethodID(j->env, app_cls, "getLabel", "()Ljava/lang/String;") : NULL;
    if (!app_cls || !g_pkg || !g_lbl) {
        (*j->env)->ExceptionClear(j->env);
        if (app_cls) (*j->env)->DeleteLocalRef(j->env, app_cls);
        (*j->env)->DeleteLocalRef(j->env, list_cls);
        (*j->env)->DeleteLocalRef(j->env, list);
        free(out);
        *count = 0;
        return NULL;
    }
    for (jint i = 0; i < n; i++) {
        jobject app = (*j->env)->CallObjectMethod(j->env, list, m_get, i);
        if ((*j->env)->ExceptionCheck(j->env) || !app) {
            (*j->env)->ExceptionClear(j->env);
            if (app) (*j->env)->DeleteLocalRef(j->env, app);
            for (jint k = 0; k < i; k++) free(out[k]);
            free(out);
            (*j->env)->DeleteLocalRef(j->env, list_cls);
            (*j->env)->DeleteLocalRef(j->env, list);
            *count = 0;
            return NULL;
        }
        jstring pkg = (jstring)(*j->env)->CallObjectMethod(j->env, app, g_pkg);
        jstring lbl = (jstring)(*j->env)->CallObjectMethod(j->env, app, g_lbl);
        char *p = pkg ? jni_jstring_to_c(j->env, pkg) : dup_str("");
        char *l = lbl ? jni_jstring_to_c(j->env, lbl) : dup_str("");
        size_t len = strlen(p) + strlen(l) + 2;
        out[i] = (char *)malloc(len);
        snprintf(out[i], len, "%s\t%s", p, l);
        free(p);
        free(l);
        if (pkg) (*j->env)->DeleteLocalRef(j->env, pkg);
        if (lbl) (*j->env)->DeleteLocalRef(j->env, lbl);
        (*j->env)->DeleteLocalRef(j->env, app);
    }
    *count = n;
    if (app_cls) (*j->env)->DeleteLocalRef(j->env, app_cls);
    (*j->env)->DeleteLocalRef(j->env, list_cls);
    (*j->env)->DeleteLocalRef(j->env, list);
    return out;
}

static char **jni_apps_list(void *c, int *count) { return jni_apps(c, NULL, count); }
static char **jni_apps_search(void *c, const char *query, int *count) { return jni_apps(c, query, count); }

static int jni_apps_launch(void *c, const char *package_name) {
    JniCtx *j = (JniCtx *)c;
    jstring p = (*j->env)->NewStringUTF(j->env, package_name);
    jboolean r = (*j->env)->CallBooleanMethod(j->env, j->adapters, j->m_apps_launch, p);
    (*j->env)->DeleteLocalRef(j->env, p);
    if ((*j->env)->ExceptionCheck(j->env)) {
        (*j->env)->ExceptionClear(j->env);
        return 0;
    }
    return r == JNI_TRUE;
}

static char **jni_files_list(void *c, const char *path, int *count) {
    JniCtx *j = (JniCtx *)c;
    jstring p = (*j->env)->NewStringUTF(j->env, path);
    jobject list = (*j->env)->CallObjectMethod(j->env, j->adapters, j->m_files_list, p);
    (*j->env)->DeleteLocalRef(j->env, p);
    char denied[64];
    if (jni_check_exc(j->env, denied, sizeof(denied))) return NULL;
    if (!list) return NULL;
    char **out = jni_string_list(j->env, list, count);
    (*j->env)->DeleteLocalRef(j->env, list);
    return out;
}

static char *jni_files_read(void *c, const char *path) {
    JniCtx *j = (JniCtx *)c;
    jstring p = (*j->env)->NewStringUTF(j->env, path);
    jstring r = (jstring)(*j->env)->CallObjectMethod(j->env, j->adapters, j->m_files_read, p);
    (*j->env)->DeleteLocalRef(j->env, p);
    char denied[64];
    if (jni_check_exc(j->env, denied, sizeof(denied))) return NULL;
    if (!r) return NULL;
    char *out = jni_jstring_to_c(j->env, r);
    (*j->env)->DeleteLocalRef(j->env, r);
    return out;
}

static int jni_files_write(void *c, const char *path, const char *content) {
    JniCtx *j = (JniCtx *)c;
    jstring p = (*j->env)->NewStringUTF(j->env, path);
    jstring v = (*j->env)->NewStringUTF(j->env, content);
    (*j->env)->CallVoidMethod(j->env, j->adapters, j->m_files_write, p, v);
    (*j->env)->DeleteLocalRef(j->env, p);
    (*j->env)->DeleteLocalRef(j->env, v);
    char denied[64];
    return jni_check_exc(j->env, denied, sizeof(denied)) == 0;
}

static char *jni_shell_run(void *c, const char *command) {
    JniCtx *j = (JniCtx *)c;
    jstring p = (*j->env)->NewStringUTF(j->env, command);
    jstring r = (jstring)(*j->env)->CallObjectMethod(j->env, j->adapters, j->m_shell_run, p);
    (*j->env)->DeleteLocalRef(j->env, p);
    char denied[64];
    if (jni_check_exc(j->env, denied, sizeof(denied))) return NULL;
    if (!r) return NULL;
    char *out = jni_jstring_to_c(j->env, r);
    (*j->env)->DeleteLocalRef(j->env, r);
    return out;
}

static char *jni_storage_get(void *c, const char *key) {
    JniCtx *j = (JniCtx *)c;
    jstring k = (*j->env)->NewStringUTF(j->env, key);
    jstring r = (jstring)(*j->env)->CallObjectMethod(j->env, j->adapters, j->m_storage_get, k);
    (*j->env)->DeleteLocalRef(j->env, k);
    char denied[64];
    if (jni_check_exc(j->env, denied, sizeof(denied))) return NULL;
    if (!r) return NULL; /* missing key */
    char *out = jni_jstring_to_c(j->env, r);
    (*j->env)->DeleteLocalRef(j->env, r);
    return out;
}

static void jni_storage_set(void *c, const char *key, const char *value) {
    JniCtx *j = (JniCtx *)c;
    jstring k = (*j->env)->NewStringUTF(j->env, key);
    jstring v = (*j->env)->NewStringUTF(j->env, value);
    (*j->env)->CallVoidMethod(j->env, j->adapters, j->m_storage_set, k, v);
    (*j->env)->DeleteLocalRef(j->env, k);
    (*j->env)->DeleteLocalRef(j->env, v);
    char denied[64];
    jni_check_exc(j->env, denied, sizeof(denied));
}

static void jni_sleep(void *c, int64_t ms) {
    JniCtx *j = (JniCtx *)c;
    (*j->env)->CallVoidMethod(j->env, j->adapters, j->m_sleep, (jlong)ms);
    char denied[64];
    jni_check_exc(j->env, denied, sizeof(denied));
}

JNIEXPORT jstring JNICALL
Java_com_polymath_voidlauncher_core_js_QuickJsEngine_nativeRun(
        JNIEnv *env, jobject thiz, jstring jSource, jstring jManifest, jlong jTimeoutMs, jobject jAdapters) {
    (void)thiz;
    (void)jTimeoutMs; /* timeout comes from manifestJson, which Kotlin always fills */
    if (!jSource || !jManifest || !jAdapters) {
        return (*env)->NewStringUTF(env, "ERR: null input");
    }
    jclass cls = (*env)->GetObjectClass(env, jAdapters);
    if (!cls) {
        (*env)->ExceptionClear(env);
        return (*env)->NewStringUTF(env, "ERR: host interface mismatch");
    }
    JniCtx jc;
    memset(&jc, 0, sizeof(jc));
    jc.env = env;
    jc.adapters = jAdapters;
    jc.m_log = jni_method(env, cls, "log", "(Ljava/lang/String;)V");
    jc.m_notify = jni_method(env, cls, "notify", "(Ljava/lang/String;Ljava/lang/String;)V");
    jc.m_set_mode = jni_method(env, cls, "setMode", "(Ljava/lang/String;)V");
    jc.m_telemetry = jni_method(env, cls, "telemetry", "()Lcom/polymath/voidlauncher/core/js/JsTelemetry;");
    jc.m_apps_list = jni_method(env, cls, "appsList", "()Ljava/util/List;");
    jc.m_apps_search = jni_method(env, cls, "appsSearch", "(Ljava/lang/String;)Ljava/util/List;");
    jc.m_apps_launch = jni_method(env, cls, "appsLaunch", "(Ljava/lang/String;)Z");
    jc.m_files_list = jni_method(env, cls, "filesList", "(Ljava/lang/String;)Ljava/util/List;");
    jc.m_files_read = jni_method(env, cls, "filesRead", "(Ljava/lang/String;)Ljava/lang/String;");
    jc.m_files_write = jni_method(env, cls, "filesWrite", "(Ljava/lang/String;Ljava/lang/String;)V");
    jc.m_shell_run = jni_method(env, cls, "shellRun", "(Ljava/lang/String;)Ljava/lang/String;");
    jc.m_storage_get = jni_method(env, cls, "storageGet", "(Ljava/lang/String;)Ljava/lang/String;");
    jc.m_storage_set = jni_method(env, cls, "storageSet", "(Ljava/lang/String;Ljava/lang/String;)V");
    jc.m_sleep = jni_method(env, cls, "sleep", "(J)V");
    if (!jc.m_log || !jc.m_notify || !jc.m_set_mode || !jc.m_telemetry ||
        !jc.m_apps_list || !jc.m_apps_search || !jc.m_apps_launch ||
        !jc.m_files_list || !jc.m_files_read || !jc.m_files_write ||
        !jc.m_shell_run || !jc.m_storage_get || !jc.m_storage_set || !jc.m_sleep) {
        (*env)->DeleteLocalRef(env, cls);
        return (*env)->NewStringUTF(env, "ERR: host interface mismatch (method not found)");
    }
    VqHost host;
    memset(&host, 0, sizeof(host));
    host.log = jni_log;
    host.notify = jni_notify;
    host.set_mode = jni_set_mode;
    host.telemetry = jni_telemetry;
    host.apps_list = jni_apps_list;
    host.apps_search = jni_apps_search;
    host.apps_launch = jni_apps_launch;
    host.files_list = jni_files_list;
    host.files_read = jni_files_read;
    host.files_write = jni_files_write;
    host.shell_run = jni_shell_run;
    host.storage_get = jni_storage_get;
    host.storage_set = jni_storage_set;
    host.sleep_ms = jni_sleep;
    host.ctx = &jc;
    char *source = jni_jstring_to_c(env, jSource);
    char *manifest = jni_jstring_to_c(env, jManifest);
    char *result = vq_run(source, manifest, &host);
    free(source);
    free(manifest);
    (*env)->DeleteLocalRef(env, cls);
    jstring out = (*env)->NewStringUTF(env, result ? result : "ERR: out of memory");
    free(result);
    return out;
}

#endif /* VQ_NO_JNI */
