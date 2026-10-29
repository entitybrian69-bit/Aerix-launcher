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
10. Copy these two and keep them for Part 4:
    - **Project URL** — looks like `https://abcdefgh.supabase.co`
    - **anon public** — a long string starting with `eyJ`

Both are safe to share: the anon key is meant to be public and is protected by the row-level
security you just installed. **Never** share the `service_role` key.

---

## Part 2 — Google sign-in · ~12 minutes

Two client IDs are needed. The **Android** one tells Google which app is asking; the **Web** one is
what Supabase uses to finish the sign-in.

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

### 2b. Android client

8. **APIs & Services → Credentials → Create credentials → OAuth client ID**.
9. **Application type: Android**.
10. Fill in:
    - **Name:** `Aerix Android debug`
    - **Package name:** `net.kdt.pojavlaunch.debug`
    - **SHA-1 certificate fingerprint:** `17:D6:F8:A1:A3:8E:B2:EF:B7:B2:C7:A7:75:99:9C:F4:0D:46:84:10`
11. **Create**. Nothing to copy here — this one just has to exist.

### 2c. Web client (the one Supabase needs)

12. **Create credentials → OAuth client ID** again.
13. **Application type: Web application**.
14. **Name:** `Aerix Supabase`
15. Under **Authorised redirect URIs** tap **Add URI** and enter exactly:
    `https://YOUR-PROJECT-REF.supabase.co/auth/v1/callback`
    Replace `YOUR-PROJECT-REF` with the subdomain from the Project URL in Part 1b.
    Example: project URL `https://abcdefgh.supabase.co` → redirect `https://abcdefgh.supabase.co/auth/v1/callback`
16. **Create**, then copy the **Client ID** and **Client secret** from the dialog.

### 2d. Hand the Web client to Supabase

17. Back in **Supabase → Authentication → Providers → Google**.
18. Turn **Enable Sign In with Google** on.
19. Paste the **Client ID** and **Client secret** from step 16 → **Save**.
20. Still in Authentication → **URL Configuration**: set **Site URL** to
    `https://YOUR-PROJECT-REF.supabase.co` and add the same address under **Redirect URLs** → **Save**.

---

## Part 3 — Send me three things

Paste these into the chat (in this order, one message is fine):

1. **Supabase Project URL** — `https://....supabase.co`
2. **Supabase anon public key** — `eyJ...`
3. **Google Web Client ID** — `....apps.googleusercontent.com`

Do **not** send: the database password, the Client secret, or anything named `service_role`.
They stay with you.

Within a few hours of that message you get the first APK with real sign-in.

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
| 1 | Tap *Continue with Google* → Google account picker → back in Socials, your name and head in Settings → Account |
| 2 | Add a second account (a friend's phone, or a second Gmail as a test user) → send a request → accept it → both see each other online |
| 3 | Send a message from one account, read it on the other within a couple of seconds |
| 4 | Open the in-game menu during Minecraft → Socials panel → chat with a friend without leaving the game |

---

## Part 5 — When we release 1.5.7

The release build is signed with a different certificate than debug, so Google needs its fingerprint
too:

1. Add a **second Android OAuth client**: package `net.kdt.pojavlaunch`, with the release SHA-1.
2. The release SHA-1 comes from the CI signing job or from **Play Console → Test and release →
   App integrity → App signing key certificate**. Send it to me and I will hand you the exact value to
   paste.

---

## If something goes wrong

| Symptom | Likely cause | Fix |
|---|---|---|
| *Continue with Google* shows "developer error" | package name or SHA-1 does not match the APK | re-check Part 2b; the debug SHA-1 must be exactly the one above |
| Google says *Access blocked: this app has not completed verification* | your Gmail is not in **Test users** | add it in Part 2a step 7 |
| Comes back to the app but still signed out | redirect URI mismatch | Part 2c step 15 must match your project URL exactly, including `https://` and no trailing slash |
| "Social backend not connected yet" inside the app | I have not added the keys to the build yet | that is expected until Part 3 is done |
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
