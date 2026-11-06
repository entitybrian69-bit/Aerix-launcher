from pathlib import Path

root = Path(__file__).resolve().parents[1] / 'app_pojavlauncher/src/main/java/net/kdt/pojavlaunch'
launch = (root / 'lifecycle/ContextAwareDoneListener.java').read_text()
game = (root / 'game/GameActivity.java').read_text()
logs = (root / 'aerix/ui/screens/LogScreen.kt').read_text()
gpu = (root / 'utils/GpuUtils.java').read_text()
diag = (root / 'lifecycle/LaunchDiagnostics.java').read_text()
activity = launch.split('public void executeWithActivity(Activity activity)', 1)[1].split('@Override\n    public void executeWithApplication', 1)[0]
assert 'new ResultReceiver(new Handler(Looper.getMainLooper()))' in activity
assert activity.index('onReceiveResult') < activity.index('Process.killProcess') < activity.index('activity.startActivity(gameStartIntent)')
assert 'resultCode != GameActivity.START_ACK_READY || acknowledged' in activity
assert game.index('Logger.begin(') < game.index('instance = instanceId') < game.index('initLayout(R.layout.activity_basemain)')
assert 'if (!mResumedForLaunch || !mStartupPrepared || mServiceBinder == null || isFinishing()) return;' in game
assert game.count('acknowledgeLaunchWhenReady();') == 2 and 'mResumedForLaunch = false;' in game
assert 'Audio capture policy unavailable' in game
assert 'this.vendor = vendor != null ? vendor' in gpu and 'this.renderer = renderer != null ? renderer' in gpu
assert 'LaunchDiagnostics.FILE_NAME' in logs and '"latestcrash.txt"' in logs
assert 'not a crash diagnosis' in diag and 'getAccessToken' not in diag and 'getLaunchArgs' not in diag
print('Game launch handoff wiring smoke test passed')
