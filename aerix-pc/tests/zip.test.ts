import assert from 'node:assert/strict';
import fs from 'node:fs';
import os from 'node:os';
import path from 'node:path';
import test from 'node:test';
import {
  createZipBuffer,
  extractZipFile,
  normalizeResourcePackZipBuffer,
  parseZipEntries,
  readZipEntryBuffer,
  safeResolveZipTarget,
} from '../src/main/zip';

test('createZipBuffer and extractZipFile round-trip files cleanly', () => {
  const dir = fs.mkdtempSync(path.join(os.tmpdir(), 'aerix-zip-'));
  try {
    const zipBuf = createZipBuffer([
      { name: 'pack.mcmeta', data: '{"pack":{"pack_format":34,"description":"Test"}}' },
      { name: 'assets/minecraft/texts/splashes.txt', data: 'Aerix PC!' },
    ]);
    const zipPath = path.join(dir, 'test.zip');
    const outDir = path.join(dir, 'out');
    fs.writeFileSync(zipPath, zipBuf);

    const extracted = extractZipFile(zipPath, outDir);
    assert.equal(extracted.length, 2);
    assert.equal(
      fs.readFileSync(path.join(outDir, 'assets/minecraft/texts/splashes.txt'), 'utf8'),
      'Aerix PC!'
    );
  } finally {
    fs.rmSync(dir, { recursive: true, force: true });
  }
});

test('safeResolveZipTarget blocks Zip-Slip path traversal', () => {
  const dest = path.resolve('/tmp/aerix-safe-dest');
  assert.throws(() => safeResolveZipTarget(dest, '../../evil.sh'), /traversal/i);
  assert.throws(() => safeResolveZipTarget(dest, '../outside/file.txt'), /traversal/i);
  const ok = safeResolveZipTarget(dest, 'assets/pack/test.png');
  assert.equal(ok, path.join(dest, 'assets', 'pack', 'test.png'));
});

test('normalizeResourcePackZipBuffer unwraps single-root nested resource pack and strips __MACOSX', () => {
  const nestedZip = createZipBuffer([
    { name: '__MACOSX/._MyPack', data: 'junk' },
    { name: 'MyPack/pack.mcmeta', data: '{"pack":{"pack_format":46,"description":"Nested"}}' },
    { name: 'MyPack/assets/minecraft/textures/block/stone.png', data: Buffer.from([1, 2, 3, 4]) },
  ]);

  const res = normalizeResourcePackZipBuffer(nestedZip);
  assert.equal(res.changed, true);

  const entries = parseZipEntries(res.buffer);
  const names = entries.map((e) => e.name).sort();
  assert.deepEqual(names, ['assets/minecraft/textures/block/stone.png', 'pack.mcmeta']);

  const mcmeta = entries.find((e) => e.name === 'pack.mcmeta')!;
  assert.match(readZipEntryBuffer(res.buffer, mcmeta).toString('utf8'), /Nested/);
});

test('normalizeResourcePackZipBuffer preserves flat packs unchanged', () => {
  const flatZip = createZipBuffer([
    { name: 'pack.mcmeta', data: '{"pack":{"pack_format":34,"description":"Flat"}}' },
    { name: 'pack.png', data: 'png' },
  ]);
  const res = normalizeResourcePackZipBuffer(flatZip);
  assert.equal(res.changed, false);
});
