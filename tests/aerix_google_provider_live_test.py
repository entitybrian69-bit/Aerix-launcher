#!/usr/bin/env python3
"""Check the public Google OAuth authorization redirect without authenticating."""
import base64, hashlib, urllib.parse, urllib.request, urllib.error
base='https://lktupxfszfefsoyksfkg.supabase.co/auth/v1/authorize'
verifier='A'*43
challenge=base64.urlsafe_b64encode(hashlib.sha256(verifier.encode()).digest()).decode().rstrip('=')
params={'provider':'google','redirect_to':'aerix-social://auth','code_challenge':challenge,'code_challenge_method':'s256',
        'apikey':'sb_publishable_mA8stbSVwAg4Il3xgHDH5g_9a7WwojI'}
class NoRedirect(urllib.request.HTTPRedirectHandler):
    def redirect_request(self, req, fp, code, msg, headers, newurl): return None
try:
    urllib.request.build_opener(NoRedirect).open(base+'?'+urllib.parse.urlencode(params),timeout=25)
    raise RuntimeError('Expected a Google authorization redirect')
except urllib.error.HTTPError as e:
    location=urllib.parse.urlparse(e.headers.get('Location',''))
    if e.code not in (302,303) or location.scheme!='https' or location.hostname!='accounts.google.com':
        raise RuntimeError('::error title=Google OAuth::Expected Google redirect, got HTTP %s host %s' % (e.code, location.hostname))
print('Google provider authorization redirect and PKCE request accepted; interactive login not tested')
