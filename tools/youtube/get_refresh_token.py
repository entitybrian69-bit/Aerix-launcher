#!/usr/bin/env python3
# Aerix Launcher - one-time helper to get the YouTube refresh token the bot needs.
#
# Run this on your own computer, once. It opens your browser, asks you to sign in to the Google
# account that owns the channel and to agree to let this app upload to it, and then prints the
# refresh token. Paste that token into the repository secret YT_REFRESH_TOKEN (see README.md).
#
#     pip install google-auth-oauthlib
#     python tools/youtube/get_refresh_token.py --client-secrets ~/Downloads/client_secret.json
#
# The token is a password to your channel: it is printed to your terminal, pasted into GitHub
# secrets, and nowhere else. Nobody else needs to see it, and it can be revoked at
# https://myaccount.google.com/permissions at any time.
#
# This program is free software: you can redistribute it and/or modify it under the terms of the
# GNU General Public License as published by the Free Software Foundation, either version 3 of the
# License, or (at your option) any later version.

from __future__ import annotations

import argparse
import json
import sys

SCOPES = [
    "https://www.googleapis.com/auth/youtube",
    "https://www.googleapis.com/auth/youtube.force-ssl",
    "https://www.googleapis.com/auth/youtube.upload",
]


def main() -> int:
    parser = argparse.ArgumentParser(description="Get the YouTube refresh token for the bot")
    parser.add_argument("--client-secrets", default="client_secret.json",
                        help="the OAuth client file downloaded from Google Cloud")
    parser.add_argument("--port", type=int, default=0, help="fixed port for the browser redirect")
    args = parser.parse_args()

    try:
        from google_auth_oauthlib.flow import InstalledAppFlow
    except ImportError:
        print("pip install google-auth-oauthlib first", file=sys.stderr)
        return 1

    flow = InstalledAppFlow.from_client_secrets_file(args.client_secrets, SCOPES)
    # A local server catches the redirect; the browser is the only thing that ever sees the code.
    creds = flow.run_local_server(port=args.port, prompt="consent",
                                  authorization_prompt_message="Opening your browser to sign in…")
    if not creds.refresh_token:
        print("No refresh token came back. Remove this app's access at "
              "https://myaccount.google.com/permissions and run it again.", file=sys.stderr)
        return 1

    print("\nAdd these three repository secrets (Settings -> Secrets and variables -> Actions):\n")
    print(f"YT_CLIENT_ID       {creds.client_id}")
    print(f"YT_CLIENT_SECRET   {creds.client_secret}")
    print(f"YT_REFRESH_TOKEN   {creds.refresh_token}")
    print("\nKeep this window closed afterwards: the refresh token is a password to the channel.")
    print("Note: if the Google Cloud project stays in \"Testing\", this token dies after 7 days.")
    print("Publish the OAuth consent screen (Publish app) so it lasts.")
    del json
    return 0


if __name__ == "__main__":
    sys.exit(main())
