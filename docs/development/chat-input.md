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
- A changed suffix deletes only the previously mirrored suffix and inserts its
  replacement. Prefix comparison and deletion counts respect Unicode code points.
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

## Opening the keyboard under load

Keyboard visibility is an explicit requested state, not a synonym for View focus.
The editor is focusable in touch mode; opening is posted until it is attached,
visible and window-focused. A busy IME gets at most ten attempts, 200 ms apart.
An IME shown result or visible IME insets stops retries. An inactive input
connection is restarted only once per opening attempt sequence, never repeatedly
while composing. Repeated open requests share pending work; an explicit new open
after the retry budget is exhausted may try again.

Window focus loss cancels queued work while retaining the requested state and
text; focus return resumes opening. Back/Done, explicit Close, activity pause and
view detachment cancel opening so a delayed callback cannot reopen the keyboard
in the background. Callback generations ignore stale IME results. Calls from the
game thread are marshalled to the Android UI thread. No forced-keyboard mode,
continuous polling or per-frame JNI requests are used.

`KeyboardVisibilityTest` exercises busy-IME retries, cancellation, window-focus
transitions, attachment, duplicate/toggle requests, stale results and UI-thread
marshalling. These deterministic tests do not measure multiplayer frame rate or
real low-end-device keyboard latency. Device confirmation remains necessary.
