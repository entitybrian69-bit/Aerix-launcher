# Android chat input mirroring

`TouchCharInput` is a hidden Android editor that forwards IME text to Minecraft. Its
initial spaces are **virtual deletion padding**, not part of the game's message.

Previously every TextWatcher replacement sent `lengthBefore` Backspace presses,
then resent the replacement. IMEs can replace an unchanged composing word or the
entire editor when Space commits composition/autocorrect. That produced needless
deletions; replacing the virtual padding could delete text from the game which the
Android editor did not even own.

`ChatTextMirror` tracks the logical text separately from that padding:

- An unchanged commit emits nothing; appending Space emits only Space.
- A changed range preserves both the common prefix and suffix. The bridge moves
  left across the unchanged suffix, deletes only the changed old range, inserts
  only its replacement. The resulting game caret is tracked for later edits;
  unchanged suffixes are neither deleted/retyped nor needlessly traversed twice.
  Comparisons, cursor movement and deletion counts respect Unicode code points.
- Whole-editor rewrites can retain or remove the virtual prefix without sending
  it to Minecraft or treating it as thirty game deletions.
- Explicit IME batch edits are reconciled at the outermost end, including a
  delete-then-reinsert transaction. Transient empty buffers are not refilled in
  the middle of the transaction.
- Single-character Backspace into the virtual padding still deletes earlier game chat
  after reopening the keyboard. Bulk padding removal is treated as an IME reset,
  not permission to delete unknown game text. Internal clear/reset operations never delete game
  text. Send/Done flushes pending text before Enter and consumes the editor action.
- Input-connection generations prevent an old connection's batch completion from
  flushing a newly restarted connection. Transient window focus loss no longer
  disables or clears the editor. Real game-activity pauses explicitly dismiss it.

Tests cover the pure mirror and real Android InputConnection calls under
Robolectric, recording the platform text/key events with a fake game backend.
They do not replace phone testing: verify Space, autocorrect, suggestions,
composition, emoji, Backspace, Done/Enter, keyboard hide/reopen and keyboard
switching on real keyboards/devices. The bridge cannot read Minecraft's actual
chat buffer or observe game-side cursor changes; it mirrors the current Android
input session, not arbitrary external game editing.

## Opening the keyboard: 1.3 regression correction

The 1.3 custom retry/result/inset state machine is removed. It could confuse a
pending open with a visible keyboard, make the next toggle close a rejected
opening, and issue repeated IME requests. An explicit open now enables/focuses
the editor and posts one showSoftInput request with explicit flags (0), without
restartInput, ResultReceiver state, timed retries, or an early visible-inset gate.
Attachment/window focus callbacks finish genuinely pending opens, but do not
re-show a served editor on every focus bounce. Repeated native open notifications
are idempotent. The user toggle checks actual visibility and can retry a rejected
open instead of closing an invisible keyboard.

ChatTextMirror and batched text forwarding are unchanged. Close/Back/Done,
activity pause and detach cancel queued opening. Tests cover rejected openings,
recovery taps, stale visibility, no polling/restart storms, focus, cancellation,
repeated native requests and UI-thread dispatch. Real Android keyboard opening,
typing and frame-time testing are still required; JVM tests cannot prove a
particular keyboard or game/device behaves correctly.


## Single-letter correction / Backspace regression

The old common-prefix-only diff rebuilt everything after a middle correction.
For example, changing `hello world and friends` to `helo world and friends`
sent 20 Backspaces and reinserted `o world and friends`, even though only one
letter changed. This created unnecessary destructive edits and could leave a
word or sentence erased when synthetic retyping was interrupted or suppressed.

The diff now finds a non-overlapping common suffix as well. The same correction
moves left 19 code points, sends **one** Backspace, sends no replacement text,
and leaves the tracked game caret at the correction. A subsequent Android
edit moves from that known caret only as far as necessary. If the caret is before
or inside a replacement range, Forward Delete removes the right-hand changed
portion without deleting/retyping the unchanged tail. Unchanged commits emit no
events and do not silently reset the tracked caret. Avoiding a return across the
suffix also avoids doubling the key burst into GLFW's bounded native input queue.
Android selection changes remain local until the next text edit. This does not
attempt to observe mouse-driven Minecraft caret moves or read the game's actual
input buffer.

Synthetic IME cursor, deletion, Enter and Unicode events now use zero shortcut
modifier flags, rather than borrowing global held Ctrl/Alt/Shift flags from
hardware or touch controls. Physical key forwarding and held-control state are
not changed. This is not a claim that externally held native keys are released.

Regression tests cover middle deletion and reinsertion, selection replacement,
forward deletion, composing-word correction, code-point deletion with trailing
text, unchanged suffix preservation and synthetic event flags with held controls.
The recording backend models a real cursor instead of only appending/deleting
at the end, so it verifies the complete resulting message and deletion count.
Phone checks with the user's keyboard remain necessary; JVM tests are not a
claim of verified in-game behavior on every Minecraft/mod/keyboard combination.
