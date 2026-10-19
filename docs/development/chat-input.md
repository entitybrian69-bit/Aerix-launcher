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
  flushing a newly restarted connection. Window focus gain no longer disables the
  editor; focus loss still dismisses it as before.

Tests cover the pure mirror and real Android InputConnection calls under
Robolectric, recording the platform text/key events with a fake game backend.
They do not replace phone testing: verify Space, autocorrect, suggestions,
composition, emoji, Backspace, Done/Enter, keyboard hide/reopen and keyboard
switching on real keyboards/devices. The bridge cannot read Minecraft's actual
chat buffer or observe game-side cursor changes; it mirrors the current Android
input session, not arbitrary external game editing.
