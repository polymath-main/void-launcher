/*
 * vq_host_test.c — host-side tests for voidquickjs core (no JVM).
 * Stub VqHost + assertions. Build:
 *   gcc -std=gnu11 -O2 -Wall -I. -Iquickjs -DVQ_NO_JNI \
 *       voidquickjs.c quickjs/quickjs.c quickjs/libregexp.c \
 *       quickjs/libunicode.c quickjs/cutils.c quickjs/libbf.c \
 *       vq_host_test.c -lm -ldl -o vq_host_test
 */
#define _GNU_SOURCE
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <strings.h>
#include "voidquickjs.h"

static int checks = 0, failures = 0;

#define CHECK(cond, label) do { \
    checks++; \
    if (cond) printf("PASS %s\n", label); \
    else { printf("FAIL %s\n", label); failures++; } \
} while (0)

#define CHECK_STR(got, want, label) do { \
    checks++; \
    char *_g = (got); \
    if (_g && strcmp(_g, (want)) == 0) printf("PASS %s\n", label); \
    else { printf("FAIL %s: got <%s> want <%s>\n", label, _g ? _g : "(null)", (want)); failures++; } \
    free(_g); \
} while (0)

#define CHECK_PREFIX(got, prefix, label) do { \
    checks++; \
    char *_g = (got); \
    if (_g && strncmp(_g, (prefix), strlen(prefix)) == 0) printf("PASS %s\n", label); \
    else { printf("FAIL %s: got <%s> want prefix <%s>\n", label, _g ? _g : "(null)", (prefix)); failures++; } \
    free(_g); \
} while (0)

/* ---------------- stub host ---------------- */
static char log_buf[4096];
static char last_notify_t[256], last_notify_b[256];
static char last_mode[128];
static long last_sleep_ms = -1;
static char last_write_path[256], last_write_content[256];
static char s_keys[16][64], s_vals[16][256];
static int s_count = 0;

static void stub_log(void *c, const char *m) {
    (void)c;
    size_t n = strlen(m);
    size_t room = sizeof(log_buf) - strlen(log_buf) - 2;
    if (n > room) n = room;
    strncat(log_buf, m, n);
    strcat(log_buf, "\n");
}
static void stub_notify(void *c, const char *t, const char *b) {
    (void)c;
    snprintf(last_notify_t, sizeof(last_notify_t), "%s", t);
    snprintf(last_notify_b, sizeof(last_notify_b), "%s", b);
}
static void stub_mode(void *c, const char *n) {
    (void)c;
    snprintf(last_mode, sizeof(last_mode), "%s", n);
}
static void stub_telemetry(void *c, int *b, int64_t *f, int64_t *t, int64_t *u) {
    (void)c;
    *b = 87; *f = 1024; *t = 4096; *u = 3600;
}
static const char *k_apps[][2] = {
    {"com.polymath.voidlauncher", "VoidLauncher"},
    {"com.android.chrome", "Chrome"},
    {"com.android.settings", "Settings"},
    {NULL, NULL}
};
static char *dup2(const char *s) {
    char *d = malloc(strlen(s) + 1);
    strcpy(d, s);
    return d;
}
static char **apps_entries(const char *query, int *count) {
    char **out = calloc(4, sizeof(char *));
    int n = 0;
    for (int i = 0; k_apps[i][0]; i++) {
        if (query && query[0] &&
            !strcasestr(k_apps[i][0], query) && !strcasestr(k_apps[i][1], query))
            continue;
        char buf[512];
        snprintf(buf, sizeof(buf), "%s\t%s", k_apps[i][0], k_apps[i][1]);
        out[n++] = dup2(buf);
    }
    *count = n;
    return out;
}
static char **stub_apps_list(void *c, int *count) {
    (void)c;
    return apps_entries(NULL, count);
}
static char **stub_apps_search(void *c, const char *q, int *count) {
    (void)c;
    return apps_entries(q, count);
}
static int stub_apps_launch(void *c, const char *pkg) {
    (void)c;
    for (int i = 0; k_apps[i][0]; i++)
        if (!strcmp(k_apps[i][0], pkg)) return 1;
    return 0;
}
static char **stub_files_list(void *c, const char *path, int *count) {
    (void)c; (void)path;
    char **out = calloc(3, sizeof(char *));
    out[0] = dup2("a.txt");
    out[1] = dup2("b.txt");
    *count = 2;
    return out;
}
static char *stub_files_read(void *c, const char *path) {
    (void)c;
    if (!strcmp(path, "a.txt")) return dup2("hello");
    return NULL;
}
static int stub_files_write(void *c, const char *path, const char *content) {
    (void)c;
    snprintf(last_write_path, sizeof(last_write_path), "%s", path);
    snprintf(last_write_content, sizeof(last_write_content), "%s", content);
    return 1;
}
static char *stub_shell(void *c, const char *cmd) {
    (void)c; (void)cmd;
    return dup2("shell-out");
}
static char *stub_storage_get(void *c, const char *key) {
    (void)c;
    for (int i = 0; i < s_count; i++)
        if (!strcmp(s_keys[i], key)) return dup2(s_vals[i]);
    return NULL;
}
static void stub_storage_set(void *c, const char *key, const char *value) {
    (void)c;
    for (int i = 0; i < s_count; i++) {
        if (!strcmp(s_keys[i], key)) {
            snprintf(s_vals[i], sizeof(s_vals[i]), "%s", value);
            return;
        }
    }
    if (s_count < 16) {
        snprintf(s_keys[s_count], sizeof(s_keys[s_count]), "%s", key);
        snprintf(s_vals[s_count], sizeof(s_vals[s_count]), "%s", value);
        s_count++;
    }
}
static void stub_sleep(void *c, int64_t ms) {
    (void)c;
    last_sleep_ms = (long)ms;
}

static VqHost stub_host(void) {
    VqHost h;
    memset(&h, 0, sizeof(h));
    h.log = stub_log;
    h.notify = stub_notify;
    h.set_mode = stub_mode;
    h.telemetry = stub_telemetry;
    h.apps_list = stub_apps_list;
    h.apps_search = stub_apps_search;
    h.apps_launch = stub_apps_launch;
    h.files_list = stub_files_list;
    h.files_read = stub_files_read;
    h.files_write = stub_files_write;
    h.shell_run = stub_shell;
    h.storage_get = stub_storage_get;
    h.storage_set = stub_storage_set;
    h.sleep_ms = stub_sleep;
    h.ctx = NULL;
    return h;
}

#define FULL "{\"name\":\"t\",\"permissions\":[\"notify\",\"telemetry\",\"storage\",\"mode\",\"files\",\"shell\",\"apps\"],\"timeout_ms\":5000}"
#define TEL_ONLY "{\"name\":\"t\",\"permissions\":[\"telemetry\"],\"timeout_ms\":5000}"
#define TIMEOUT1 "{\"name\":\"t\",\"permissions\":[\"notify\",\"telemetry\",\"storage\",\"mode\",\"files\",\"shell\",\"apps\"],\"timeout_ms\":1000}"

int main(void) {
    VqHost h = stub_host();

    CHECK_STR(vq_run("1 + 2", FULL, &h), "OK:3", "arithmetic returns value");
    log_buf[0] = '\0';
    CHECK_STR(vq_run("Void.log(\"hello\"); \"done\"", FULL, &h), "OK:done", "log + return value");
    CHECK(strstr(log_buf, "hello") != NULL, "log captured by host");
    CHECK_STR(vq_run("Void.log(\"x\")", FULL, &h), "OK:", "undefined return is empty OK");
    CHECK_STR(vq_run("print(\"p1\"); console.log(\"c1\"); \"ok\"", FULL, &h), "OK:ok",
              "print and console.log route to host log");
    CHECK(strstr(log_buf, "p1") && strstr(log_buf, "c1"), "print/console captured");
    CHECK_STR(vq_run("Void.notify(\"t\", \"b\")", TEL_ONLY, &h), "DENIED:notify",
              "notify denied without permission");
    CHECK_STR(vq_run("Void.notify(\"t\", \"b\")", FULL, &h), "OK:",
              "notify allowed with permission");
    CHECK(!strcmp(last_notify_t, "t") && !strcmp(last_notify_b, "b"), "notify reached host");
    CHECK_STR(vq_run("Void.telemetry().batteryPct", FULL, &h), "OK:87", "telemetry field");
    CHECK_STR(vq_run("Void.telemetry().memFreeMB", FULL, &h), "OK:1024", "telemetry memFreeMB");
    CHECK_STR(vq_run("Void.storage.set(\"k\", \"v\"); Void.storage.get(\"k\")", FULL, &h), "OK:v",
              "storage set/get roundtrip");
    CHECK_STR(vq_run("Void.storage.get(\"missing\")", FULL, &h), "OK:null",
              "storage missing key is null");
    CHECK_STR(vq_run("Void.apps.search(\"void\").length", FULL, &h), "OK:1", "apps search");
    CHECK_STR(vq_run("Void.apps.launch(\"com.android.chrome\")", FULL, &h), "OK:true", "apps launch hit");
    CHECK_STR(vq_run("Void.apps.launch(\"com.nope\")", FULL, &h), "OK:false", "apps launch miss");
    CHECK_STR(vq_run("Void.files.read(\"a.txt\")", FULL, &h), "OK:hello", "files read");
    CHECK_STR(vq_run("Void.files.list(\".\").length", FULL, &h), "OK:2", "files list length");
    CHECK_STR(vq_run("Void.files.write(\"x.txt\", \"data\")", FULL, &h), "OK:true", "files write");
    CHECK(!strcmp(last_write_path, "x.txt") && !strcmp(last_write_content, "data"),
          "files write reached host");
    CHECK_STR(vq_run("Void.shell.run(\"echo hi\")", FULL, &h), "OK:shell-out", "shell run");
    CHECK_STR(vq_run("Void.mode(\"STILL\"); \"ok\"", FULL, &h), "OK:ok", "mode call");
    CHECK(!strcmp(last_mode, "STILL"), "mode reached host");
    CHECK_STR(vq_run("Void.sleep(50); \"slept\"", FULL, &h), "OK:slept", "sleep call");
    CHECK(last_sleep_ms == 50, "sleep ms passed through");
    CHECK_STR(vq_run("Void.sleep(99999); \"ok\"", FULL, &h), "OK:ok", "sleep clamped");
    CHECK(last_sleep_ms == 5000, "sleep clamped to 5000");
    CHECK_STR(vq_run("throw new Error(\"boom\")", FULL, &h), "ERR:boom", "thrown error message");
    CHECK_STR(vq_run("throw \"plain\"", FULL, &h), "ERR:plain", "thrown plain string");
    CHECK_PREFIX(vq_run("1 +", FULL, &h), "ERR:", "syntax error is ERR");
    CHECK_PREFIX(vq_run("while (true) {}", TIMEOUT1, &h), "TIMEOUT:", "infinite loop times out");
    {
        size_t big_n = 300 * 1024;
        char *big = malloc(big_n + 2);
        memset(big, ' ', big_n);
        big[big_n] = '1';
        big[big_n + 1] = '\0';
        CHECK_PREFIX(vq_run(big, FULL, &h), "ERR:script too large", "oversize script rejected");
        free(big);
    }

    printf("\n%d checks, %d failures: %s\n", checks, failures, failures ? "FAIL" : "ALL PASS");
    return failures ? 1 : 0;
}
