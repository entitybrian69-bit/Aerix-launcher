#!/usr/bin/env python3
"""Wiring checks for the launcher account page and the browser PKCE handoff."""
from pathlib import Path
root = Path(__file__).resolve().parents[1] / 'app_pojavlauncher/src/main/java/net/kdt/pojavlaunch'
home = (root/'aerix/ui/screens/HomeScreen.kt').read_text()
page = (root/'aerix/ui/screens/AerixAccountSetupScreen.kt').read_text()
cosmetics = (root/'aerix/ui/screens/SocialProfileScreen.kt').read_text()
nav = (root/'aerix/ui/AerixLauncherScreen.kt').read_text()
backend = (root/'aerix/social/SocialBackend.kt').read_text()
dashboard = (root/'aerix/ui/screens/MinecraftHomeDashboard.kt').read_text()
assert 'MinecraftHomeDashboard(' in home
assert '"Accounts" to LauncherSection.ACCOUNTS' in dashboard
assert '"Aerix account" to LauncherSection.AERIX_ACCOUNT' in dashboard
assert 'LauncherSection.AERIX_ACCOUNT -> AerixAccountSetupScreen' in nav
assert 'beginGoogleSignIn(activity, true)' in page and 'ProfileCosmeticsPage(activity, setupFlow = true' in page
assert 'claimed != null && setupFlow' in cosmetics
assert 'oauth_pkce_verifier' in backend and 'code_challenge_method=s256' in backend
assert 'grant_type=pkce' in backend and 'auth_code' in backend and 'code_verifier' in backend
callback = backend.split('fun completeBrowserSession(',1)[1].split('suspend fun resendConfirmation',1)[0]
assert 'getQueryParameter("code")' in callback and 'access_token"]' not in callback
assert 'remove("oauth_pkce_verifier")' in callback
print('Aerix home account page and PKCE sign-in wiring passed')
