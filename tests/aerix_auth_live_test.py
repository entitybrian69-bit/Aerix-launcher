#!/usr/bin/env python3
"""Read-only public endpoint checks; no real credentials or account creation."""
import json
from urllib.request import Request, urlopen
from urllib.error import HTTPError

base = 'https://lktupxfszfefsoyksfkg.supabase.co/functions/v1/aerix-auth'
def request(path, body=None):
    req = Request(base + path, data=None if body is None else json.dumps(body).encode(), headers={'Content-Type':'application/json'}, method='GET' if body is None else 'POST')
    try:
        with urlopen(req, timeout=25) as r:
            return r.status, r.read()
    except HTTPError as e:
        return e.code, e.read()
status, data = request('/')
if status != 200:
    raise SystemExit(f'::error title=Aerix auth metadata::HTTP {status}: {data[:200].decode(errors="replace")}')
metadata = json.loads(data)
assert metadata['signaturePublickey'].startswith('-----BEGIN PUBLIC KEY-----')
assert 'lktupxfszfefsoyksfkg.supabase.co' in metadata['skinDomains']
status, _ = request('/sessionserver/session/minecraft/join', {'accessToken':'x','selectedProfile':'0'*32,'serverId':'123'})
if status != 403:
    raise SystemExit(f'::error title=Aerix invalid join::HTTP {status}')
status, _ = request('/sessionserver/session/minecraft/hasJoined?username=invalid&serverId=123')
if status != 204:
    raise SystemExit(f'::error title=Aerix unknown session::HTTP {status}')
print('Live auth metadata and invalid-session checks passed')
# The live probe runs before publishing a debug APK.
