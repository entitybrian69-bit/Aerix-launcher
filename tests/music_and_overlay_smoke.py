#!/usr/bin/env python3
"""Smoke checks for Music's page, Audius client, offline musics/ storage, Discord RPC, and in-game overlay fixes."""
from pathlib import Path

root = Path(__file__).resolve().parents[1]
main = root / 'app_pojavlauncher/src/main'
music_dir = main / 'java/net/kdt/pojavlaunch/aerix/music'

for name in (
    'AerixMusicModels.kt',
    'AerixMusicStorage.kt',
    'AerixAudiusClient.kt',
    'AerixMusicDownloader.kt',
    'AerixMusicService.kt',
    'AerixMusicController.kt',
    'MusicArtworkImage.kt',
    'InGameMusicPanel.kt',
):
    assert (music_dir / name).is_file(), f'Missing {name}'

manifest = (main / 'AndroidManifest.xml').read_text()
assert 'FOREGROUND_SERVICE_MEDIA_PLAYBACK' in manifest
assert 'net.kdt.pojavlaunch.aerix.music.AerixMusicService' in manifest
assert 'mediaPlayback' in manifest

models = (music_dir / 'AerixMusicModels.kt').read_text()
for sort_mode in ('POPULAR', 'RELEVANCE', 'MOST_PLAYED', 'LETTERS'):
    assert sort_mode in models, sort_mode
for tab in ('SONGS', 'PLAYLISTS', 'DOWNLOADED', 'EXPORTED'):
    assert tab in models, tab

storage = (music_dir / 'AerixMusicStorage.kt').read_text()
assert 'musics' in storage
assert 'Exported Music\'s' in storage or 'Exported Music' in storage
assert 'MusicPlaylist.ID_DOWNLOADED' in storage
assert 'MusicPlaylist.ID_EXPORTED' in storage

controller = (music_dir / 'AerixMusicController.kt').read_text()
assert 'selectedTab' in controller
assert 'searchQuery' in controller
assert 'soundCategory_music' in controller
assert 'AerixDiscordPresence.updateMusicState' in controller

musics_screen = (main / 'java/net/kdt/pojavlaunch/aerix/ui/screens/MusicsScreen.kt').read_text()
for expected in (
    'Sort by',
    'Downloads',
    'Previous',
    'Next',
    'Make new playlist',
    'MC Music: OFF',
    'MusicArtworkImage',
    'AerixMusicController.selectedTab = tab',
):
    assert expected in musics_screen, expected

in_game_panel = (music_dir / 'InGameMusicPanel.kt').read_text()
assert 'Dialog(' not in in_game_panel, 'InGameMusicPanel must use inline sheets, not Dialog windows'
assert 'OverlayMusicSubSheet' in in_game_panel
assert 'Mute Vanilla Music' in in_game_panel
assert 'MusicArtworkImage' in in_game_panel
assert '+10s' in in_game_panel

client_overlay = (main / 'java/net/kdt/pojavlaunch/aerix/client/AerixClientOverlayView.kt').read_text()
assert 'ClientMenuSubPage.MUSIC' in client_overlay
assert 'InGameMusicPanel(' in client_overlay
assert 'onClientModalVisibilityChanged' in client_overlay
assert 'dismissKeyboardIfShown' in client_overlay

game_menu = (main / 'java/net/kdt/pojavlaunch/aerix/ui/game/GameMenuScreen.kt').read_text()
assert 'GameMenuPage.MUSIC' in game_menu
assert 'InGameMusicPanel(' in game_menu

game_activity = (main / 'java/net/kdt/pojavlaunch/game/GameActivity.java').read_text()
assert 'onClientModalVisibilityChanged' in game_activity
assert 'mSuppressEscapeUntilMs' in game_activity
assert 'releasePointerCapture' in game_activity
assert 'onTrimMemory' in game_activity and 'clearMusicArtworkCache' in game_activity

game_view = (main / 'java/net/kdt/pojavlaunch/game/GameView.java').read_text()
assert 'if (mPendingCleanEncoderSurface != null || mSurfaceTee != null)' in game_view, \
    'RecordingSurfaceTee must stay lazy until clean recording is active'
java_runner = (main / 'java/net/kdt/pojavlaunch/utils/jre/JavaRunner.java').read_text()
assert 'setLowRamDevice' in java_runner and 'sLowRamDevice' in java_runner

discord = (main / 'java/net/kdt/pojavlaunch/aerix/discord/AerixDiscordPresence.kt').read_text()
assert 'updateMusicState' in discord
assert 'activeMusicLine' in discord
assert 'const val MUSICS' in discord
assert '"MUSICS" -> MUSICS' in discord

print('Music + overlay focus/ESC smoke checks passed')
