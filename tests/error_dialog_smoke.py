#!/usr/bin/env python3
"""Tapping "Show more" on an error dialog must not crash the dialog.

`RuntimeSelectionException` is what a user sees when the Java runtime is missing, incompatible or failed
to install. For the install-failed state it offers a "Show more" button **whether or not a cause was
attached**, and it handed `getCause()` - null - straight to `Tools.showError`, whose body then read
`getMessage()` off it:

    java.lang.NullPointerException: Attempt to invoke virtual method
    'java.lang.String java.lang.Throwable.getMessage()' on a null object reference
        at net.kdt.pojavlaunch.Tools.lambda$showError$5(Tools.java:382)

so the user's report of the crash replaced the explanation of why the runtime did not install, and the
dialog came back every time the launcher was foregrounded. Reported on Android 11 against 2.3.0.

The fix is a single funnel - `Tools.errorBody` - that treats every input as optional, plus the dialog
passing its own text as the rolled-up message. What is pinned here:

  - the body of an error dialog goes through `errorBody`, and `errorBody` handles a null Throwable;
  - `printToString` still answers with text for a null throwable rather than reading it;
  - the ordering of `errorBody`'s guards, because the whole point is which fallback wins;
  - `RuntimeSelectionException` gives the detail dialog real text and keeps the exit behaviour it had.
"""

import re
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
TOOLS = ROOT / "app_pojavlauncher/src/main/java/net/kdt/pojavlaunch/Tools.java"
RUNTIME = ROOT / "app_pojavlauncher/src/main/java/net/kdt/pojavlaunch/utils/jre/RuntimeSelectionException.java"

tools = TOOLS.read_text()
runtime = RUNTIME.read_text()
failures = []


def check(condition: bool, message: str) -> None:
    if not condition:
        failures.append(message)


def run_checks() -> list[str]:
    def method_body(source: str, signature: str) -> str:
        if signature not in source:
            raise AssertionError(f"{signature!r} is gone from the file this test reads")
        start = source.index(signature)
        open_brace = source.index("{", start)
        depth = 0
        for index in range(open_brace, len(source)):
            if source[index] == "{":
                depth += 1
            elif source[index] == "}":
                depth -= 1
                if depth == 0:
                    return source[open_brace:index + 1]
        raise AssertionError(f"unbalanced body for {signature!r}")


    # 1. every showError funnel builds its message through the null-safe helper.
    body = method_body(tools, "private static void showError(final Context ctx, final int titleId,")
    check("errorBody(rolledMessage, e, showMore)" in body,
          "Tools.showError must build errMsg from errorBody(rolledMessage, e, showMore)")
    check("e.getMessage()" not in body,
          "Tools.showError must not read getMessage() directly - a null Throwable is a legal input here")

    error_body = method_body(tools, "static String errorBody(String rolledMessage, Throwable e, boolean showMore)")
    guards = [
        "if (showMore) return printToString(e);",
        "if (rolledMessage != null && !rolledMessage.isEmpty()) return rolledMessage;",
        "if (e == null) return NO_ERROR_DETAIL;",
        "String message = e.getMessage();",
        "return message != null ? message : e.getClass().getName();",
    ]
    positions = []
    for guard in guards:
        check(guard in error_body, f"Tools.errorBody is missing its `{guard}` fallback")
        positions.append(error_body.index(guard) if guard in error_body else -1)
    check(positions == sorted(positions) and -1 not in positions,
          "Tools.errorBody must try the trace, then the rolled-up line, then the Throwable's own message, "
          "then the class name - in that order, so a dialog always says the most it can")

    # 2. the trace printer answers in text for a null throwable, and both paths share one fallback string.
    check('if (throwable == null) return NO_ERROR_DETAIL;' in tools,
          "Tools.printToString must return the shared NO_ERROR_DETAIL text for a null Throwable")
    check('private static final String NO_ERROR_DETAIL = "' in tools,
          "Tools must hold one fallback string for 'nothing to show'")
    check(tools.count("Unknown error (no exception attached)") == 1,
          "the 'no exception attached' text must exist once and be reused, not duplicated per call site")

    # 3. the runtime dialog passes real text and keeps the title and exit behaviour it had.
    dialog = method_body(runtime, "public void executeWithActivity(Activity activity)")
    check("String message = activity.getString(msgString, mRuntimeVersion);" in dialog,
          "RuntimeSelectionException must keep the dialog message in a variable so the detail dialog can reuse it")
    check("builder.setMessage(message);" in dialog,
          "RuntimeSelectionException should show that same message in the dialog itself")

    start = dialog.index("Tools.showError(") + len("Tools.showError(")
    depth, end = 1, start
    while depth:
        if dialog[end] == "(":
            depth += 1
        elif dialog[end] == ")":
            depth -= 1
        end += 1
    arguments = dialog[start:end - 1]
    top_level, buffer, depth = [], "", 0
    for char in arguments:
        if char == "(":
            depth += 1
        elif char == ")":
            depth -= 1
        if char == "," and depth == 0:
            top_level.append(buffer.strip())
            buffer = ""
        else:
            buffer += char
    top_level.append(buffer.strip())

    check(len(top_level) == 5,
          "the detail call must pass ctx, title, message, cause and exitIfOk - got "
          + str(len(top_level)) + " arguments: " + repr(arguments)[:120])
    check(top_level and top_level[2] == "message",
          "the detail dialog must be handed this dialog's own message: with no cause there is nothing else to show")
    check("getCause()" in (top_level[3] if len(top_level) > 3 else ""),
          "the detail dialog must still pass getCause() so a real trace is shown when there is one")
    check("ShowErrorActivity" in (top_level[4] if len(top_level) > 4 else ""),
          "the detail dialog must keep finishing the error activity on OK, as it did before")
    check("throw" not in arguments and "this" not in arguments,
          "the detail call must not hand the exception itself to showError - it is a ContextExecutorTask, "
          "which would re-open this same dialog")

    # 4. that five-argument call needs an overload that exists.
    check("public static void showError(final Context ctx, final int title, final String rolledMessage, "
          "final Throwable e, final boolean exitIfOk)" in tools,
          "Tools needs the (ctx, title, rolledMessage, throwable, exitIfOk) overload the runtime dialog calls")

    return failures


failures = []
try:
    failures = run_checks()
except AssertionError as problem:  # a symbol this test measures is gone
    failures.append(str(problem))  # report it, keep the rest


if failures:
    for failure in failures:
        print(f"  FAIL {failure}")
    sys.exit(1)

print("error dialog: one null-safe body funnel, trace/rolled/message/class fallback in order, "
      "runtime install failure explains itself instead of crashing")
