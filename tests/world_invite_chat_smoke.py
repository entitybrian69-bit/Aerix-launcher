from pathlib import Path

root = Path(__file__).resolve().parents[1] / 'app_pojavlauncher/src/main/java/net/kdt/pojavlaunch'
menu = (root / 'aerix/ui/game/GameMenuScreen.kt').read_text()
world = (root / 'aerix/ui/game/GameWorldSharingScreen.kt').read_text()
social = (root / 'aerix/ui/game/GameSocialsScreen.kt').read_text()
chat = (root / 'aerix/ui/screens/SocialChatViews.kt').read_text()
clipboard = (root / 'game/platform/clipboard/AndroidClipboard.java').read_text()
platform = (root / 'game/platform/Platform.java').read_text()
assert 'choosingInviteRecipient = true' in menu and 'page = GameMenuPage.SOCIALS' in menu
assert 'Copy address & open chats' in world and 'ClipData.newPlainText("Minecraft world address", value)' in world
assert 'onOpenChats()' in world and 'Direct Connection' in world
assert 'if (offset < size) return null' in world and 'discardLine = true' in world
assert 'if (chooseInviteRecipient) null' in social and '!chooseInviteRecipient && selectedPartyId == null' in social
assert 'selectedFriendId = friend.id' in social
assert 'Copy message' in chat and 'ClipData.newPlainText("Aerix message", msg.body)' in chat
assert 'WorldSharePolicy.isJoinAddress(msg.body.trim())' in chat
assert 'ClipData.newPlainText("Minecraft world address", address)' in chat
assert 'Direct Connection' in chat and 'AppCompatEditText' in chat
assert 'ClipboardManager' in clipboard and 'GLFW.setClipboardImpl(mClipboard)' in platform and 'SDLActivity.setClipboard(mClipboard)' in platform
# Opening chat or copying must never send a message for the user.
assert 'sendMessage' not in world and 'sendDirectMessage' not in world
print('World invite chat wiring smoke test passed')
