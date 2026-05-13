/*
 * Minimal bytehook.h stub for compilation.
 *
 * The real ByteHook library (libbytehook.so) is loaded dynamically at
 * runtime via dlopen() in exit_hook.c, so no link-time dependency is needed.
 * This header provides only the type definitions, constants, and macro stubs
 * required to compile exit_hook.c without the full ByteHook SDK.
 *
 * BYTEHOOK_CALL_PREV and BYTEHOOK_POP_STACK are intentionally no-ops here
 * because custom_exit() is only ever invoked by the real bytehook hook engine
 * (which installs its own trampoline); if libbytehook.so is absent the hook
 * is never installed and these code paths are never reached.
 */

#pragma once

#ifdef __cplusplus
extern "C" {
#endif

#include <stddef.h>
#include <stdint.h>

/* ── Types ─────────────────────────────────────────────────────────────────── */

/** Opaque handle returned by bytehook_hook_*() */
typedef void *bytehook_stub_t;

/** Callback invoked after a hook is installed/removed */
typedef void (*bytehook_hooked_t)(
        bytehook_stub_t stub,
        const char *callee_path_name,
        const char *sym_name,
        int error_number,
        void *arg);

/* ── Constants ─────────────────────────────────────────────────────────────── */

#define BYTEHOOK_MODE_AUTOMATIC  1
#define BYTEHOOK_MODE_MANUAL     2

#define BYTEHOOK_STATUS_CODE_OK  0

/* ── Hook-trampoline macros ────────────────────────────────────────────────── */

/*
 * These macros are no-ops in the stub.  When the real libbytehook.so is
 * loaded at runtime it installs its own trampoline before calling custom_exit;
 * the no-op stub is never reached in that path because the hook is only
 * active when the real library is present.
 */
#define BYTEHOOK_CALL_PREV(func, func_type, ...) \
    do { (void)sizeof((func_type)NULL); } while (0)

#define BYTEHOOK_POP_STACK() \
    do {} while (0)

#ifdef __cplusplus
}
#endif

