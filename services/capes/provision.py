"""Run locally as the service administrator after verifying ownership of the UUID."""
import hashlib
import json
import secrets
import sys
from uuid import UUID

if __name__ == "__main__":
    uuid = str(UUID(sys.argv[1]))
    token = secrets.token_urlsafe(32)
    print("Give this upload key privately to the verified account owner:", token)
    print("Merge this entry into AERIX_CAPE_KEYS (only the digest is stored):")
    print(json.dumps({uuid: hashlib.sha256(token.encode()).hexdigest()}))
