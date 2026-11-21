import crypto from 'node:crypto';
import { fetchJson } from './http';
import { Account } from './types';

export const MSA_CLIENT_ID = '00000000402b5328';
export const MSA_REDIRECT_URI = 'https://login.live.com/oauth20_desktop.srf';
export const MSA_SCOPE = 'service::user.auth.xboxlive.com::MBI_SSL';

export const SUPABASE_URL = 'https://lktupxfszfefsoyksfkg.supabase.co';
export const SUPABASE_PUBLISHABLE_KEY = 'sb_publishable_mA8stbSVwAg4Il3xgHDH5g_9a7WwojI';
export const AERIX_AUTH_SERVER_URL = `${SUPABASE_URL}/functions/v1/aerix-auth`;

export function createOfflineUuid(username: string): string {
  const hash = crypto.createHash('md5').update(`OfflinePlayer:${username}`, 'utf8').digest();
  hash[6] = (hash[6] & 0x0f) | 0x30;
  hash[8] = (hash[8] & 0x3f) | 0x80;
  const hex = hash.toString('hex');
  return `${hex.slice(0, 8)}-${hex.slice(8, 12)}-${hex.slice(12, 16)}-${hex.slice(16, 20)}-${hex.slice(20, 32)}`;
}

export function createOfflineAccount(rawUsername: string): Account {
  const username = rawUsername.trim();
  if (!/^[A-Za-z0-9_]{3,16}$/.test(username)) {
    throw new Error('Offline username must be 3-16 characters (letters, numbers, or underscore).');
  }
  const uuid = createOfflineUuid(username);
  const now = Date.now();
  return {
    id: `offline:${uuid}`,
    type: 'offline',
    username,
    uuid,
    accessToken: '0',
    createdAt: now,
    lastUsedAt: now,
  };
}

export function buildMicrosoftAuthorizeUrl(): string {
  const params = new URLSearchParams({
    client_id: MSA_CLIENT_ID,
    response_type: 'code',
    scope: MSA_SCOPE,
    redirect_uri: MSA_REDIRECT_URI,
    prompt: 'select_account',
  });
  return `https://login.live.com/oauth20_authorize.srf?${params.toString()}`;
}

interface LiveTokenResponse {
  access_token: string;
  refresh_token: string;
  expires_in: number;
}

interface XboxAuthResponse {
  Token: string;
  DisplayClaims?: {
    xui?: Array<{ uhs?: string }>;
  };
}

interface MinecraftAuthResponse {
  access_token: string;
  expires_in: number;
}

interface MinecraftProfileResponse {
  id: string;
  name: string;
  skins?: Array<{ url?: string; state?: string }>;
}

export function formatDashedUuid(raw: string): string {
  const clean = raw.replace(/-/g, '').toLowerCase();
  if (clean.length !== 32) return raw;
  return `${clean.slice(0, 8)}-${clean.slice(8, 12)}-${clean.slice(12, 16)}-${clean.slice(16, 20)}-${clean.slice(20, 32)}`;
}

async function completeMicrosoftChain(liveToken: LiveTokenResponse): Promise<Account> {
  const xbl = await fetchJson<XboxAuthResponse>('https://user.auth.xboxlive.com/user/authenticate', {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({
      Properties: {
        AuthMethod: 'RPS',
        SiteName: 'user.auth.xboxlive.com',
        RpsTicket: liveToken.access_token,
      },
      RelyingParty: 'http://auth.xboxlive.com',
      TokenType: 'JWT',
    }),
  });

  const uhs = xbl.DisplayClaims?.xui?.[0]?.uhs;
  if (!xbl.Token || !uhs) {
    throw new Error('Xbox Live authentication failed: missing user hash.');
  }

  const xsts = await fetchJson<XboxAuthResponse>('https://xsts.auth.xboxlive.com/xsts/authorize', {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({
      Properties: {
        SandboxId: 'RETAIL',
        UserTokens: [xbl.Token],
      },
      RelyingParty: 'rp://api.minecraftservices.com/',
      TokenType: 'JWT',
    }),
  });

  if (!xsts.Token) {
    throw new Error('XSTS authorization failed. Ensure your Microsoft account has an Xbox profile.');
  }

  const mcAuth = await fetchJson<MinecraftAuthResponse>(
    'https://api.minecraftservices.com/authentication/login_with_xbox',
    {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({
        identityToken: `XBL3.0 x=${uhs};${xsts.Token}`,
      }),
    }
  );

  const profile = await fetchJson<MinecraftProfileResponse>(
    'https://api.minecraftservices.com/minecraft/profile',
    {
      headers: {
        Authorization: `Bearer ${mcAuth.access_token}`,
      },
    }
  );

  const uuid = formatDashedUuid(profile.id);
  const now = Date.now();
  const activeSkin = profile.skins?.find((s) => s.state === 'ACTIVE') ?? profile.skins?.[0];

  return {
    id: `msa:${uuid}`,
    type: 'microsoft',
    username: profile.name,
    uuid,
    accessToken: mcAuth.access_token,
    refreshToken: liveToken.refresh_token,
    expiresAt: now + (mcAuth.expires_in || 86400) * 1000,
    avatarUrl: activeSkin?.url,
    createdAt: now,
    lastUsedAt: now,
  };
}

export async function loginMicrosoftWithAuthCode(code: string): Promise<Account> {
  const body = new URLSearchParams({
    client_id: MSA_CLIENT_ID,
    code,
    grant_type: 'authorization_code',
    redirect_uri: MSA_REDIRECT_URI,
    scope: MSA_SCOPE,
  });

  const live = await fetchJson<LiveTokenResponse>('https://login.live.com/oauth20_token.srf', {
    method: 'POST',
    headers: { 'Content-Type': 'application/x-www-form-urlencoded' },
    body: body.toString(),
  });

  return completeMicrosoftChain(live);
}

export async function refreshMicrosoftAccount(account: Account): Promise<Account> {
  if (!account.refreshToken) {
    throw new Error('Microsoft account has no refresh token; please sign in again.');
  }
  const body = new URLSearchParams({
    client_id: MSA_CLIENT_ID,
    refresh_token: account.refreshToken,
    grant_type: 'refresh_token',
    scope: MSA_SCOPE,
  });

  const live = await fetchJson<LiveTokenResponse>('https://login.live.com/oauth20_token.srf', {
    method: 'POST',
    headers: { 'Content-Type': 'application/x-www-form-urlencoded' },
    body: body.toString(),
  });

  const refreshed = await completeMicrosoftChain(live);
  return {
    ...refreshed,
    createdAt: account.createdAt,
  };
}

interface SupabaseAuthResponse {
  access_token: string;
  refresh_token: string;
  expires_in: number;
  user?: {
    id: string;
    email?: string;
  };
}

interface AerixSessionRow {
  game_name: string;
  profile_id: string;
  access_token: string;
  expires_at_ms: number;
}

async function issueAerixGameAccount(
  supabaseAccessToken: string,
  supabaseRefreshToken: string,
  email?: string,
  existingCreatedAt?: number
): Promise<Account> {
  const rows = await fetchJson<AerixSessionRow[]>(
    `${SUPABASE_URL}/rest/v1/rpc/aerix_issue_game_session`,
    {
      method: 'POST',
      headers: {
        apikey: SUPABASE_PUBLISHABLE_KEY,
        Authorization: `Bearer ${supabaseAccessToken}`,
        'Content-Type': 'application/json',
      },
      body: '{}',
    }
  );
  const row = Array.isArray(rows) ? rows[0] : undefined;
  if (!row || !row.game_name || !row.profile_id || !row.access_token) {
    throw new Error('Could not issue an Aerix game session. Set your in-game profile name in Aerix Social first.');
  }

  const now = Date.now();
  const uuid = formatDashedUuid(row.profile_id);
  return {
    id: `aerix:${uuid}`,
    type: 'aerix',
    username: row.game_name,
    uuid,
    accessToken: row.access_token,
    refreshToken: supabaseRefreshToken,
    expiresAt: row.expires_at_ms || now + 12 * 3600 * 1000,
    email,
    authServerUrl: AERIX_AUTH_SERVER_URL,
    createdAt: existingCreatedAt ?? now,
    lastUsedAt: now,
  };
}

export async function loginAerixWithEmail(email: string, password: string): Promise<Account> {
  const auth = await fetchJson<SupabaseAuthResponse>(
    `${SUPABASE_URL}/auth/v1/token?grant_type=password`,
    {
      method: 'POST',
      headers: {
        apikey: SUPABASE_PUBLISHABLE_KEY,
        'Content-Type': 'application/json',
      },
      body: JSON.stringify({ email: email.trim(), password }),
    }
  );
  return issueAerixGameAccount(auth.access_token, auth.refresh_token, auth.user?.email ?? email.trim());
}

export async function refreshAerixAccount(account: Account): Promise<Account> {
  if (!account.refreshToken) {
    throw new Error('Aerix account session expired; please sign in again.');
  }
  const auth = await fetchJson<SupabaseAuthResponse>(
    `${SUPABASE_URL}/auth/v1/token?grant_type=refresh_token`,
    {
      method: 'POST',
      headers: {
        apikey: SUPABASE_PUBLISHABLE_KEY,
        'Content-Type': 'application/json',
      },
      body: JSON.stringify({ refresh_token: account.refreshToken }),
    }
  );
  return issueAerixGameAccount(
    auth.access_token,
    auth.refresh_token,
    auth.user?.email ?? account.email,
    account.createdAt
  );
}

export function buildAerixGooglePkceUrl(): { url: string; codeVerifier: string } {
  const verifier = crypto.randomBytes(32).toString('base64url');
  const challenge = crypto.createHash('sha256').update(verifier).digest('base64url');
  const params = new URLSearchParams({
    provider: 'google',
    redirect_to: 'http://127.0.0.1:43821/aerix-oauth',
    code_challenge: challenge,
    code_challenge_method: 's256',
  });
  return {
    url: `${SUPABASE_URL}/auth/v1/authorize?${params.toString()}`,
    codeVerifier: verifier,
  };
}

export async function exchangeAerixPkceCode(code: string, codeVerifier: string): Promise<Account> {
  const auth = await fetchJson<SupabaseAuthResponse>(
    `${SUPABASE_URL}/auth/v1/token?grant_type=pkce`,
    {
      method: 'POST',
      headers: {
        apikey: SUPABASE_PUBLISHABLE_KEY,
        'Content-Type': 'application/json',
      },
      body: JSON.stringify({ auth_code: code, code_verifier: codeVerifier }),
    }
  );
  return issueAerixGameAccount(auth.access_token, auth.refresh_token, auth.user?.email);
}

export async function ensureFreshAccount(account: Account): Promise<Account> {
  if (account.type === 'offline') {
    return { ...account, lastUsedAt: Date.now() };
  }
  const expiresAt = account.expiresAt ?? 0;
  if (expiresAt - Date.now() > 5 * 60 * 1000) {
    return { ...account, lastUsedAt: Date.now() };
  }
  if (account.type === 'microsoft') {
    return refreshMicrosoftAccount(account);
  }
  return refreshAerixAccount(account);
}
