import crypto from 'node:crypto';
import fs from 'node:fs';
import path from 'node:path';

export const AERIX_USER_AGENT = 'Aerix-Launcher-PC/2.4.6 (https://github.com/entitybrian69-bit/Aerix-launcher)';

export async function fetchJson<T>(url: string, init: RequestInit = {}, timeoutMs = 25000): Promise<T> {
  const controller = new AbortController();
  const timer = setTimeout(() => controller.abort(), timeoutMs);
  try {
    const headers = new Headers(init.headers);
    if (!headers.has('User-Agent')) {
      headers.set('User-Agent', AERIX_USER_AGENT);
    }
    if (!headers.has('Accept')) {
      headers.set('Accept', 'application/json');
    }
    const res = await fetch(url, {
      ...init,
      headers,
      signal: controller.signal,
    });
    if (!res.ok) {
      const body = await res.text().catch(() => '');
      throw new Error(`HTTP ${res.status} for ${url}: ${body.slice(0, 240)}`);
    }
    return (await res.json()) as T;
  } finally {
    clearTimeout(timer);
  }
}

export function sha1File(filePath: string): string {
  const hash = crypto.createHash('sha1');
  const buf = fs.readFileSync(filePath);
  hash.update(buf);
  return hash.digest('hex').toLowerCase();
}

export async function downloadFile(
  url: string,
  destPath: string,
  options: {
    expectedSha1?: string;
    headers?: Record<string, string>;
    retries?: number;
    onProgress?: (downloadedBytes: number, totalBytes: number) => void;
  } = {}
): Promise<void> {
  const expectedSha1 = options.expectedSha1?.toLowerCase();
  if (fs.existsSync(destPath)) {
    if (!expectedSha1 || sha1File(destPath) === expectedSha1) {
      return;
    }
  }

  fs.mkdirSync(path.dirname(destPath), { recursive: true });
  const retries = options.retries ?? 2;
  let lastError: unknown;

  for (let attempt = 0; attempt <= retries; attempt++) {
    const tempPath = `${destPath}.part.${process.pid}.${attempt}`;
    try {
      const res = await fetch(url, {
        headers: {
          'User-Agent': AERIX_USER_AGENT,
          ...(options.headers ?? {}),
        },
        redirect: 'follow',
      });
      if (!res.ok || !res.body) {
        throw new Error(`Download failed (${res.status}) for ${url}`);
      }
      const total = Number(res.headers.get('content-length') || 0);
      let downloaded = 0;
      const hash = crypto.createHash('sha1');
      const out = fs.createWriteStream(tempPath);

      try {
        const reader = res.body.getReader();
        while (true) {
          const { done, value } = await reader.read();
          if (done) break;
          if (value) {
            const buf = Buffer.from(value);
            hash.update(buf);
            downloaded += buf.length;
            if (!out.write(buf)) {
              await new Promise<void>((resolve) => out.once('drain', resolve));
            }
            options.onProgress?.(downloaded, total);
          }
        }
        await new Promise<void>((resolve, reject) => {
          out.on('error', reject);
          out.end(resolve);
        });
      } catch (err) {
        out.destroy();
        throw err;
      }

      const actualSha1 = hash.digest('hex').toLowerCase();
      if (expectedSha1 && actualSha1 !== expectedSha1) {
        throw new Error(`SHA-1 mismatch for ${path.basename(destPath)}: expected ${expectedSha1}, got ${actualSha1}`);
      }
      fs.renameSync(tempPath, destPath);
      return;
    } catch (err) {
      lastError = err;
      try {
        if (fs.existsSync(tempPath)) fs.unlinkSync(tempPath);
      } catch {
        // ignore cleanup error
      }
    }
  }

  throw lastError instanceof Error ? lastError : new Error(String(lastError));
}

export async function runWithConcurrency<T>(
  items: T[],
  concurrency: number,
  worker: (item: T, index: number) => Promise<void>
): Promise<void> {
  if (items.length === 0) return;
  let nextIndex = 0;
  const count = Math.max(1, Math.min(concurrency, items.length));
  await Promise.all(
    Array.from({ length: count }, async () => {
      while (true) {
        const idx = nextIndex++;
        if (idx >= items.length) return;
        await worker(items[idx], idx);
      }
    })
  );
}
