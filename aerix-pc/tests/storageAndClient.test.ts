import assert from 'node:assert/strict';
import fs from 'node:fs';
import os from 'node:os';
import path from 'node:path';
import test from 'node:test';
import {
  AERIX_CLIENT_PACK_NAME,
  AERIX_CLIENT_PACK_TOKEN,
  AERIX_PACK_STAMP_REL,
  ensureAerixClientPack,
  getPackFormatForVersion,
} from '../src/main/aerixClient';
import { createOfflineAccount } from '../src/main/auth';
import {
  deleteContentEntry,
  importContentFile,
  listInstanceContent,
  mergeResourcePackOrder,
  parseMinecraftOptionsResourcePacks,
  toggleContentEntry,
} from '../src/main/contentManager';
import { resolveAerixPaths } from '../src/main/paths';
import { LauncherStore } from '../src/main/storage';
import { createZipBuffer } from '../src/main/zip';

test('LauncherStore creates instances, manages accounts, and cleans up on delete', () => {
  const tempRoot = fs.mkdtempSync(path.join(os.tmpdir(), 'aerix-store-'));
  try {
    const store = new LauncherStore(resolveAerixPaths(tempRoot));
    const acc = createOfflineAccount('TesterOne');
    store.upsertAccount(acc);
    assert.equal(store.listAccounts().length, 1);
    assert.equal(store.getSettings().selectedAccountId, acc.id);

    const inst = store.createInstance({
      name: 'Aerix Client 1.21.4',
      mcVersion: '1.21.4',
      loader: 'fabric',
      aerixClient: true,
    });
    assert.equal(inst.aerixClient, true);
    assert.equal(store.getSettings().selectedInstanceId, inst.id);
    assert.equal(store.listInstances().length, 1);

    const updated = store.updateInstance(inst.id, { ramMb: 6144 });
    assert.equal(updated.ramMb, 6144);

    store.deleteInstance(inst.id);
    assert.equal(store.listInstances().length, 0);
  } finally {
    fs.rmSync(tempRoot, { recursive: true, force: true });
  }
});

test('Aerix Client pack places Aerix-Client-Pack.zip strictly below user packs and stores stamp under config/', () => {
  const tempRoot = fs.mkdtempSync(path.join(os.tmpdir(), 'aerix-client-'));
  try {
    const store = new LauncherStore(resolveAerixPaths(tempRoot));
    const inst = store.createInstance({
      name: 'Client Pack Test',
      mcVersion: '1.21.11',
      loader: 'fabric',
      aerixClient: true,
    });
    const instDir = store.getInstanceDir(inst.id);

    // Pre-populate options.txt with user resource pack
    fs.writeFileSync(
      path.join(instDir, 'options.txt'),
      'fov:0.5\nresourcePacks:["vanilla","file/UserFaithful.zip"]\n',
      'utf8'
    );

    ensureAerixClientPack(instDir, '1.21.11');

    assert.ok(fs.existsSync(path.join(instDir, 'resourcepacks', AERIX_CLIENT_PACK_NAME)));
    assert.ok(fs.existsSync(path.join(instDir, AERIX_PACK_STAMP_REL)));
    // Must never leave dotfile inside resourcepacks/
    assert.equal(fs.existsSync(path.join(instDir, 'resourcepacks', '.aerix_pack_stamp')), false);

    const packs = parseMinecraftOptionsResourcePacks(
      fs.readFileSync(path.join(instDir, 'options.txt'), 'utf8')
    );
    assert.deepEqual(packs, ['vanilla', AERIX_CLIENT_PACK_TOKEN, 'file/UserFaithful.zip']);

    assert.equal(getPackFormatForVersion('1.21.11').format, 75);
    assert.equal(getPackFormatForVersion('26.3').format, 97);
    assert.equal(getPackFormatForVersion('26.3').minor, 1);
  } finally {
    fs.rmSync(tempRoot, { recursive: true, force: true });
  }
});

test('Content manager toggles mods, unwraps nested imported resource packs, and deletes cleanly', () => {
  const tempRoot = fs.mkdtempSync(path.join(os.tmpdir(), 'aerix-content-'));
  try {
    const store = new LauncherStore(resolveAerixPaths(tempRoot));
    const inst = store.createInstance({ name: 'Modded', mcVersion: '1.21.1', loader: 'fabric' });
    const instDir = store.getInstanceDir(inst.id);

    // Import mod
    const srcMod = path.join(tempRoot, 'sodium.jar');
    fs.writeFileSync(srcMod, 'jar-bytes');
    importContentFile(instDir, 'mods', srcMod);

    let mods = listInstanceContent(instDir, 'mods');
    assert.equal(mods.length, 1);
    assert.equal(mods[0].enabled, true);

    toggleContentEntry(instDir, 'mods', mods[0].fileName, false);
    mods = listInstanceContent(instDir, 'mods');
    assert.equal(mods[0].enabled, false);
    assert.equal(mods[0].fileName, 'sodium.jar.disabled');

    deleteContentEntry(instDir, 'mods', mods[0].fileName);
    assert.equal(listInstanceContent(instDir, 'mods').length, 0);

    // Import nested resource pack ZIP
    const nestedPack = path.join(tempRoot, 'CoolPack.zip');
    fs.writeFileSync(
      nestedPack,
      createZipBuffer([
        { name: 'CoolPack/pack.mcmeta', data: '{"pack":{"pack_format":34,"description":"Cool"}}' },
      ])
    );
    importContentFile(instDir, 'resourcepacks', nestedPack);

    const rps = listInstanceContent(instDir, 'resourcepacks');
    assert.equal(rps.length, 1);
    assert.equal(rps[0].enabled, true);

    // User-added resource pack sits above Aerix-Client-Pack.zip
    const merged = mergeResourcePackOrder(
      ['vanilla', AERIX_CLIENT_PACK_TOKEN],
      'file/CoolPack.zip',
      { enabled: true }
    );
    assert.deepEqual(merged, ['vanilla', AERIX_CLIENT_PACK_TOKEN, 'file/CoolPack.zip']);
  } finally {
    fs.rmSync(tempRoot, { recursive: true, force: true });
  }
});
