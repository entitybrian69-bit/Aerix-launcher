# Aerix signing policy

**Status: from 2.4.4 every published APK is signed with two certificates, permanently.**

This document exists because the policy was changed by the owner, twice, with the cost stated both
times, and because the first answer was written down with the opposite conclusion. Read the reasoning
before touching `tests/leaked_cert_fingerprint.txt`, the sign step, or the verify step.

## What Android actually requires

`PackageManagerServiceUtils.verifySignatures` asks the installed package's `SigningDetails` to
`checkCapability(incoming)`. That method branches on how many certificates the *installed* app has
(`frameworks/base/core/java/android/content/pm/SigningDetails.java`):

```java
if (oldDetails.mSignatures.length > 1) {
    // multiple-signer packages cannot rotate signing certs, so we must have an exact match
    return signaturesMatchExactly(oldDetails);
} else {
    // rotation history may prove an older certificate
    return hasCertificate(oldDetails.mSignatures[0], flags);
}
```

Two consequences, both of which we learned the hard way:

1. **An overlapping certificate is not enough** when the installed app has more than one signer. For a
   multi-signed install, the incoming APK must carry the *identical* set.
2. **A multi-signed install can never move back to single-signed.** Not with the same current key, not
   with rotation history, not with anything. Android throws `INSTALL_FAILED_UPDATE_INCOMPATIBLE`.

## Why this policy is what it is

The published **2.4.1** is signed with two certificates: the current key and the retired key. It was
built that way so installs from 2.3.2 and older - signed with a key that had been committed to this
public repository - could move to the new key without an uninstall that costs players their worlds.

The consequence was not noticed for three releases. 2.4.2, then 2.4.3, were signed with the current
key alone and could not install over 2.4.1 at all. Neither could any future release.

There were exactly two ways forward, and the owner was shown both:

| Route | Data | Signing |
|---|---|---|
| Double-sign from now on | kept, updates work in place | the retired key is in every future APK |
| Export instances, uninstall, reinstall | app-private data lost without an export | current key only |

**The owner chose to double-sign permanently**, after being told that this is not one bridge APK but
every release from now on. That is the policy. It is a product and security decision, not a bug, and it
must not be reverted by a contributor or an assistant on their own initiative.

## What we accept, in plain words

- The retired private key is **public**. It is committed at `tests/aerix_legacy.p12.b64` with the
  default password in the release workflow, and it was in this repository's history before that.
- Anyone with repository access can therefore build an APK that installs over a player's launcher.
- The fingerprint gate is **inverted**: it now fails the build if the retired certificate is *missing*,
  because a release without it installs over nothing that already exists.

## Rules that still hold

- The **current** key stays a secret: `AERIX_KEYSTORE_P12` / `AERIX_KEYSTORE_PASSWORD`, alias `aerix`,
  never in the tree, never in a commit, never in chat.
- Every APK must carry **exactly two** signers: the current key and the retired one. A third signer is
  an unknown party and fails the build.
- `versionCode` must always increase (`major*10000 + minor*100 + patch`), or the platform refuses the
  update before the signature is ever considered.
- Debug and nightly builds remain `net.al.aerixlauncher.debug` and are never updatable from a release,
  which is correct and permanent: they are a different package.
