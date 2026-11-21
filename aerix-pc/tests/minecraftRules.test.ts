import assert from 'node:assert/strict';
import test from 'node:test';
import { AERIX_AUTH_SERVER_URL, createOfflineAccount, createOfflineUuid } from '../src/main/auth';
import { parseJavaMajorVersion } from '../src/main/javaManager';
import { buildJvmAndGameArgs } from '../src/main/launcherProcess';
import { evaluateMojangRules, mavenCoordinateToPath } from '../src/main/minecraftInstaller';
import { compareSemver, normalizeVersionTag } from '../src/main/updater';

test('createOfflineUuid matches standard Mojang OfflinePlayer v3 UUID', () => {
  const uuid = createOfflineUuid('Steve');
  assert.equal(uuid, '5627dd98-e6be-3c21-b8a8-e92344183641');
  assert.throws(() => createOfflineAccount('ab'), /3-16/);
  const acc = createOfflineAccount('AerixPlayer');
  assert.equal(acc.type, 'offline');
  assert.equal(acc.username, 'AerixPlayer');
});

test('evaluateMojangRules respects OS, architecture, and features', () => {
  const macArmRules = [
    { action: 'allow' as const, os: { name: 'osx', arch: 'arm64' } },
  ];
  assert.equal(evaluateMojangRules(macArmRules, 'darwin', 'arm64'), true);
  assert.equal(evaluateMojangRules(macArmRules, 'darwin', 'x64'), false);
  assert.equal(evaluateMojangRules(macArmRules, 'win32', 'x64'), false);

  const allowExceptLinux = [
    { action: 'allow' as const },
    { action: 'disallow' as const, os: { name: 'linux' } },
  ];
  assert.equal(evaluateMojangRules(allowExceptLinux, 'win32', 'x64'), true);
  assert.equal(evaluateMojangRules(allowExceptLinux, 'linux', 'x64'), false);

  const featureRule = [
    { action: 'allow' as const, features: { has_custom_resolution: true } },
  ];
  assert.equal(evaluateMojangRules(featureRule, 'linux', 'x64', { has_custom_resolution: true }), true);
  assert.equal(evaluateMojangRules(featureRule, 'linux', 'x64', { has_custom_resolution: false }), false);
});

test('mavenCoordinateToPath formats group, artifact, version, and classifier', () => {
  assert.equal(
    mavenCoordinateToPath('net.fabricmc:fabric-loader:0.16.10'),
    'net/fabricmc/fabric-loader/0.16.10/fabric-loader-0.16.10.jar'
  );
  assert.equal(
    mavenCoordinateToPath('org.lwjgl:lwjgl:3.3.3:natives-windows'),
    'org/lwjgl/lwjgl/3.3.3/lwjgl-3.3.3-natives-windows.jar'
  );
});

test('parseJavaMajorVersion handles Java 8, 17, 21, and 25 formats', () => {
  assert.deepEqual(parseJavaMajorVersion('openjdk version "1.8.0_412"'), { major: 8, versionString: '1.8.0_412' });
  assert.deepEqual(parseJavaMajorVersion('openjdk version "17.0.12" 2024-07-16'), { major: 17, versionString: '17.0.12' });
  assert.deepEqual(parseJavaMajorVersion('openjdk version "21.0.5"'), { major: 21, versionString: '21.0.5' });
  assert.deepEqual(parseJavaMajorVersion('openjdk version "25-ea"'), { major: 25, versionString: '25-ea' });
});

test('buildJvmAndGameArgs injects authlib-injector for Aerix accounts and substitutes game args', () => {
  const args = buildJvmAndGameArgs({
    resolved: {
      versionId: '1.21.4',
      mainClass: 'net.minecraft.client.main.Main',
      javaMajor: 21,
      clientJarPath: '/mc/versions/1.21.4/1.21.4.jar',
      nativesDir: '/mc/versions/1.21.4/natives',
      libraries: ['/mc/libs/a.jar'],
      assetIndexId: '19',
      gameArgs: ['--username', '${auth_player_name}', '--uuid', '${auth_uuid}', '--gameDir', '${game_directory}'],
      jvmArgs: ['-Djava.library.path=${natives_directory}', '-cp', '${classpath}'],
    },
    instance: {
      id: 'inst-1',
      name: 'Aerix Test',
      mcVersion: '1.21.4',
      loader: 'fabric',
      createdAt: 1,
      playTimeSec: 0,
      fullscreen: true,
    },
    instanceDir: '/mc/instances/inst-1',
    assetsDir: '/mc/assets',
    librariesDir: '/mc/libs',
    account: {
      id: 'aerix:123',
      type: 'aerix',
      username: 'AerixHero',
      uuid: '00000000-0000-4000-8000-000000000123',
      accessToken: 'tok123',
      authServerUrl: AERIX_AUTH_SERVER_URL,
      createdAt: 1,
      lastUsedAt: 1,
    },
    ramMb: 4096,
    width: 1920,
    height: 1080,
    customJvmArgs: '-XX:+UseG1GC',
    authlibInjectorPath: __filename, // existing file path to trigger javaagent injection in test
  });

  assert.ok(args.some((a) => a.startsWith(`-javaagent:${__filename}=${AERIX_AUTH_SERVER_URL}`)));
  assert.ok(args.includes('-Xmx4096M'));
  assert.ok(args.includes('AerixHero'));
  assert.ok(args.includes('--fullscreen'));
});

test('semver updater comparison handles pc-v and v tags accurately', () => {
  assert.equal(normalizeVersionTag('pc-v2.5.0'), '2.5.0');
  assert.equal(compareSemver('v2.5.0', '2.4.5'), 1);
  assert.equal(compareSemver('2.4.5', 'v2.4.5'), 0);
  assert.equal(compareSemver('2.4.4', '2.4.5'), -1);
});
