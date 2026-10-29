# Aerix Socials — setup guide (phone only)

Everything here can be done from the phone browser. Nothing needs a computer.
Total time: **about 25 minutes**. Do the steps in order; the later ones depend on the earlier ones.

What you end up with: Google sign-in that works for every user of the launcher, a free database that
holds profiles, friend lists and messages, and a debug APK on your phone that you can test in.

---

## Part 1 — The database (Supabase) · ~6 minutes

1. Open **https://supabase.com** in the phone browser.
2. Tap **Start your project** → **Continue with GitHub** → authorise it.
3. Tap **New project**.
   - **Name:** `aerix`
   - **Database password:** tap *Generate a password*, then **copy it somewhere safe**. (You will not
     need it for the launcher, but Supabase will not show it again.)
   - **Region:** the closest to you — for Nepal pick **Mumbai (ap-south-1)** or **Singapore**.
   - **Plan:** Free. No card is asked for.
4. Tap **Create new project** and wait ~2 minutes until the dashboard finishes provisioning.

### 1a. Create the tables

5. In the left sidebar tap **SQL Editor** → **New query**.
6. Open this file in another tab and copy all of it:
   `https://github.com/entitybrian69-bit/Aerix-launcher/blob/arena/2f1249a3-aerix-launcher/supabase/schema.sql`
   — on the GitHub page tap the **copy** icon at the top right of the file box.
7. Paste it into the SQL editor and tap **Run**.
8. You should see **Success. No rows returned**. That means profiles, friendships and messages now
   exist, each locked so a user can only read their own conversations.

### 1b. Copy the two launcher values

9. Left sidebar → **Project Settings** (the gear) → **API**.
10. Copy these two and keep them for Part 3:
    - **Project URL** — looks like `https://abcdefgh.supabase.co`. If the page does not show it,
      it is under **Settings → General → Project ID**: the URL is `https://<Project ID>.supabase.co`.
    - **The publishable key** — Supabase's current name for the public client key. It starts with
      `sb_publishable_`. (Older projects show it under the **Legacy anon, service_role** tab as the
      `anon` key, a long string starting with `eyJ`. Either one works.)

Both are safe to share: the publishable key is public by design and is protected by the row-level
security you just installed. **Never** share the **secret** key or anything named `service_role` —
that one bypasses the security rules.

---

## Part 2 — Google sign-in · ~12 minutes

The launcher has no Google code in it and never asks me for a client ID. When you tap **Continue with
Google**, the phone opens the browser on your own Supabase project, Supabase does the talking to
Google, and the phone comes back into the launcher on `aerix-social://auth`. So everything below is
configured **in your Google account and your Supabase dashboard only**.

1. Open **https://console.cloud.google.com** and sign in with the Google account you want to own the
   project.
2. Top bar → project selector → **New project** → name `Aerix Launcher` → **Create** → make sure it is
   selected afterwards.

### 2a. Consent screen

3. Search bar → **OAuth consent screen** (under *APIs & Services*).
4. Choose **External** → **Create**.
5. Fill in:
   - **App name:** `Aerix Launcher`
   - **User support email:** your email
   - **Developer contact email:** your email
6. **Save and continue** through *Scopes* (add nothing) and *Test users*.
7. On **Test users** tap **Add users** and add **your own Gmail address**, then save.
   (Keeping the app in *Testing* means no Google review is needed. Up to 100 test accounts can sign
   in. Publish later when you want everyone to be able to.)

### 2b. Web client — the only one you need

8. **APIs & Services → Credentials → Create credentials → OAuth client ID**.
9. **Application type: Web application**.
10. **Name:** `Aerix Supabase`
11. Under **Authorised redirect URIs** tap **Add URI** and enter exactly this, with no slash on the
    end:

    ```
    https://lktupxfszfefsoyksfkg.supabase.co/auth/v1/callback
    ```

12. **Create**, then copy the **Client ID** and the **Client secret**. Both go straight into Supabase
    in the next step — they are never sent to me, and there is no Android client ID to create.

### 2c. Hand it to Supabase

13. **Supabase → Authentication → Sign In / Providers → Google**.
14. Turn **Enable Sign In with Google** on.
15. Paste the **Client ID** and **Client secret** from step 12 → **Save**.
16. Still in Authentication → **URL Configuration**:
    - **Site URL:** `https://lktupxfszfefsoyksfkg.supabase.co`
    - **Redirect URLs → Add URL:** `aerix-social://auth`
    - **Save**.

That last line is what brings the phone back into the launcher. Without it Google finishes the
sign-in in the browser and the launcher never hears about it.

### 2d. If the browser says "provider is not enabled"

That message means step 14 or 15 is not saved yet. Nothing to reinstall — save it, then tap the
Google button again. If it says "redirect_uri_mismatch", the address in step 11 has a typo (a
trailing `/` is the usual one).

---

## Part 3 — What I already have

Both values are already in the build, so there is nothing to send:

- **Project URL** — `https://lktupxfszfefsoyksfkg.supabase.co`
- **Publishable key** — `sb_publishable_...` (it is public; row-level security is the real boundary)

Never send the database password, the Google **Client secret**, or anything named `service_role`.

**Email sign-in works right now** — Parts 1 and 3 are done, so the card's Sign in / Create account
works with any email. Part 2 only adds the Google button on top.

---

## Part 4 — Testing each stage · ~5 minutes each

1. Tap the APK link I send →
   `https://github.com/entitybrian69-bit/Aerix-launcher/raw/arena/2f1249a3-aerix-launcher/apk/Aerix-Launcher-debug.apk`
2. When Android says the app is already installed, choose **Update / Install anyway**. Do **not**
   uninstall — uninstalling deletes your instances and accounts.
3. **Debug builds install next to your normal Aerix** (package `net.kdt.pojavlaunch.debug`), so you can
   keep playing on the normal one while testing.
4. Open the debug app → **Socials** on Home or the rail.
5. Tell me, in plain words, what happened. If something is broken, also send the launcher log:
   **Settings → Logs → share**, or the crash dialog's copy button.

What each stage should look like when it works:

| Stage | You should see |
|---|---|
| 1 | The sign-in card fits the screen with **Sign in** and **Continue with Google** both visible → tap Google → account picker → back in Socials, your name and head under Settings → Account |
| 2 | Add a second account (a friend's phone, or a second Gmail as a test user) → send a request → accept it → both see each other online |
| 3 | Send a message from one account, read it on the other within a couple of seconds |
| 4 | Open the in-game menu during Minecraft → Socials panel → chat with a friend without leaving the game |

---

## Part 5 — When we release 1.5.7

Nothing changes for Google. The browser flow in Part 2 has no package name and no certificate
fingerprint in it, so the release build signs in exactly like the debug one — same project, same
client, same redirect. The only thing to do at release time is the normal signing ritual you already
have.

---

## If something goes wrong

| Symptom | Likely cause | Fix |
|---|---|---|
| *Continue with Google* shows "provider is not enabled" | the Google provider is not saved in Supabase | Part 2c steps 14–15 |
| *redirect_uri_mismatch* on Google's page | the callback address has a typo | Part 2b step 11 — `https://lktupxfszfefsoyksfkg.supabase.co/auth/v1/callback`, no trailing slash |
| Google says *Access blocked: this app has not completed verification* | your Gmail is not in **Test users** | add it in Part 2a step 7 |
| Google page finishes but the app stays signed out | `aerix-social://auth` is missing from the redirect list | Part 2c step 16 |
| Buttons cut off at the bottom of the card | old build | install the current APK — the card is a single row now and nothing scrolls |
| SQL editor says *relation already exists* | you ran the schema twice | harmless, the file is idempotent — it is done |

---

## What this costs

- Supabase free tier: **₹0** — 500 MB database, 50 000 monthly sign-ins. The schema prunes messages
  after 90 days, so it stays flat.
- Google sign-in: **free**, forever.
- If the project sits unused for 7 days Supabase pauses it; opening the app resumes chatting, and a
  weekly ping in CI keeps it awake.

The launcher and Minecraft never depend on any of this. If you stop paying or the project is paused,
you lose the friends tab — not the game.
