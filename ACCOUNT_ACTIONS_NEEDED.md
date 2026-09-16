# Account actions needed

Running checklist of everything in the Ligaya build that's blocked on your own account/credentials — nothing here needs doing until you're ready; I'll keep implementing everything else and adding to this list as more come up.

## 1. Real Firebase project (blocks: real sign-up/login, real Firestore/FCM sync)

**Why it's blocked:** there is no `google-services.json` and no Google Services Gradle plugin anywhere in this repo — `FirebaseApp` is only ever initialized in test code (against the local Firebase emulator). `FirebaseAuthRepository`/`FirestoreUserProfileRepository`/notifications sync all compile and are tested, but have never been wired into the real `:app` build because doing so without a real project would crash the app at launch.

**What to do:**
1. Go to the [Firebase console](https://console.firebase.google.com) and create a new project.
2. Add an Android app to it with package name `com.ligaya.app`.
3. Download the generated `google-services.json` and place it at `app/google-services.json` (this file is safe to commit only if you're OK with it being public — it's not a secret, but you can also gitignore it and hand it to me directly).
4. In the Firebase console, enable **Email/Password** as a sign-in method (Authentication → Sign-in method).
5. Enable **Firestore** (Build → Firestore Database) — the security rules already written in `backend/firestore.rules` are ready to deploy once the project exists (`firebase deploy --only firestore:rules` from `backend/`, using the Firebase CLI logged into your account).
6. Tell me once this is done — I'll add the Google Services Gradle plugin to the build and wire `FirebaseAuthRepository`/`RoomEmergencyProfileRepository` into `MainActivity` so Onboarding (Step 42) actually works end to end, plus anything else that was waiting on this.

## 2. Gemini API quota (blocks: live Gemini responses in the Emergency Companion and voice-intent interpretation)

**Why it's blocked:** confirmed directly (curl against the real endpoint) — your `GEMINI_API_KEY` hit the free-tier daily quota (`generate_content_free_tier_requests`, limit 20/day, `RESOURCE_EXHAUSTED`). Not an invalid key, not a code bug.

**What to do:**
1. Check your plan/billing at [Google AI Studio](https://ai.google.dev) — either upgrade off the free tier, or just wait for the daily quota to reset.
2. Make sure the working key is in `local.properties` as `GEMINI_API_KEY=...` (already gitignored, already wired — `app/build.gradle.kts` reads it into `BuildConfig.GEMINI_API_KEY` automatically, no further code change needed).

## 3. RevenueCat project + Google Play Console listing (blocks: real Ligaya+ purchase/entitlement checks)

**Why it's blocked:** `RevenueCatEntitlementRepository` (core-billing) wraps the real, current RevenueCat Android SDK — verified directly against RevenueCat's own docs and compiled against the real published library, not guessed — but nothing calls `Purchases.configure()` anywhere, because that needs a real RevenueCat project's API key. The architecture doc itself flags the further wrinkle: RevenueCat/Play Billing testing "requires a real Play Console listing even for testing" (a one-time $25 Google Play developer registration), not just a RevenueCat account.

**What to do:**
1. Create a account at [RevenueCat](https://app.revenuecat.com) and a new project for Ligaya.
2. Register (or already have) a [Google Play Console](https://play.google.com/console) developer account, and create at least an internal-testing app listing for `com.ligaya.app` with a subscription product configured.
3. Connect that Play Console app to the RevenueCat project (RevenueCat's dashboard walks through linking the Play service account and product IDs), and set up an **entitlement** (e.g. named `ligaya_plus`) attached to that product — the exact entitlement identifier string is a real design decision at that point; `RevenueCatEntitlementRepository` currently takes it as a constructor parameter defaulted to `"ligaya_plus"` in my own tests, but you can name it anything.
4. Get the public Google Play API key from RevenueCat's dashboard (Project Settings → API keys → App specific keys).
5. Tell me the key (or where you've stored it, e.g. `local.properties` as `REVENUECAT_API_KEY=...`, matching the `GEMINI_API_KEY` pattern already wired) and the entitlement identifier you chose — I'll wire `Purchases.configure()` into `MainActivity` and connect the paywall to `:app`'s real nav graph.

## 4. Google Places API key (blocks: the real "nearest hospital/police/fire station" card during an active emergency)

**Why it's blocked:** `EmergencyServiceFlowCoordinator` (core-places) wraps the real Google Places API (New) — `GooglePlacesNearbySearchSource`/`GooglePlacesDetailsSource` are already compiled and tested against the real API shape — but `MainActivity` only constructs and runs this coordinator when `BuildConfig.PLACES_API_KEY` is non-blank (Step 48). Without a key, the app still works end to end; it just silently skips this one flow rather than firing requests guaranteed to fail.

**What to do:**
1. In the [Google Cloud Console](https://console.cloud.google.com), create (or reuse) a project and enable **Places API (New)**.
2. Enable billing on that project — Places API (New) requires it even to test, though Google's free monthly credit covers light use.
3. Create an API key (APIs & Services → Credentials) and, for safety, restrict it to Places API (New) only.
4. Add it to `local.properties` as `PLACES_API_KEY=...` (already gitignored, already wired — `app/build.gradle.kts` reads it into `BuildConfig.PLACES_API_KEY` automatically, no further code change needed).

**What this unblocks once done:** as soon as EMERGENCY_ACTIVE is reached with location successfully resolved, the app will automatically look up and show the nearest relevant emergency service (hospital/police/fire, based on incident type) — currently the last piece of Step 48's "all five flows fire automatically" wiring that needs a real key to actually run, not just compile.

## 5. Play Console submission itself (blocks: Step 52's pre-launch report)

**Why it's blocked:** Step 52's own acceptance criterion is "passes a Play Console pre-launch report with no policy-violation flags" — that report only runs inside your own Play Console account, against a real uploaded build. I audited every permission this app ships, fixed a real gap (`allowBackup` was on with no exclusion rules — now off, see [STEP52_PLAY_STORE_COMPLIANCE.md](STEP52_PLAY_STORE_COMPLIANCE.md)), and drafted every piece of text Play Console will ask for. What's left is account-only.

**What to do:** full step-by-step checklist, with the exact form text ready to paste, is in [STEP52_PLAY_STORE_COMPLIANCE.md](STEP52_PLAY_STORE_COMPLIANCE.md). Short version: register the Play Console developer account (same one item 3 already asks for), host [PRIVACY_POLICY_DRAFT.md](PRIVACY_POLICY_DRAFT.md) (after your own review/edits) at a public URL, fill in the Foreground Service and Data Safety declarations using the drafted text, upload a build to internal testing, and read back whatever the pre-launch report says.

**Tell me once this is done** — if the report flags anything, send it back and I'll fix whatever's fixable on my end (most categories are); only findings about the Play Console listing itself would come back to you again.

## 6. Google sign-in (blocks: the "Continue with Google" button actually doing anything) — Apple below

**Update: the code is now fully built and tested, both for today's on-device store and for real Firebase once item 1 lands. Google sign-in is one Google Cloud step away from working — it does *not* need the full Firebase project first.**

**What's built:** tapping "Continue with Google" launches Android's real Credential Manager account picker (the current, non-deprecated Google Sign-In API), gets back a real signed ID token for whichever Google account you pick, and hands it to `AuthRepository.signInWithGoogle(...)`:
- `LocalAuthRepository` (what the app runs on today, no Firebase needed) reads the token's own email claim, checks it hasn't expired, and creates or logs into a local account keyed by that email — a genuinely different account from a password-based one for the same address, so Google sign-in can never silently take over a password account it didn't create. It does *not* cryptographically verify the token's signature against Google's rotating public keys (that needs a backend, which this path deliberately doesn't have) — worth knowing, not a blocker for using it.
- `FirebaseAuthRepository` exchanges the token with Firebase the standard way (`GoogleAuthProvider.getCredential` + `signInWithCredential`) — ready for the day item 1 is done and the composition root switches to it, no further code change needed there either.

**Why it's still blocked:** Credential Manager needs a Google Cloud **OAuth 2.0 Web client ID** (not an Android client ID, not a Firebase API key) as the audience it requests a token for — that has to exist under your own Google Cloud project before the picker can issue a real token.

**What to do (the light path — no Firebase project required for this alone):**
1. In the [Google Cloud Console](https://console.cloud.google.com) → APIs & Services → Credentials, create an OAuth 2.0 Client ID of type **Web application** (not Android). You don't need to add any redirect URIs for this use — Credential Manager doesn't use them.
2. Also create (or reuse, if you already made one for item 1) an OAuth 2.0 Client ID of type **Android**, with this app's package name (`com.ligaya.app`) and its signing certificate's SHA-1 fingerprint (get the debug one with `keytool -list -v -keystore ~/.android/debug.keystore -alias androiddebugkey -storepass android -keypass android`, or your release keystore's for a release build). Google Cloud requires this one to exist in the same project even though the app's own code never reads its ID directly — Credential Manager checks it behind the scenes.
3. Add the **Web** client ID (from step 1) to `local.properties` as `GOOGLE_WEB_CLIENT_ID=...` (already gitignored, already wired — `app/build.gradle.kts` reads it into `BuildConfig.GOOGLE_WEB_CLIENT_ID` automatically, no further code change needed). That's it — the button starts working the next time you build.

**If you'd rather go straight to real Firebase Auth instead of the on-device store:** finish item 1 first, then in the Firebase console enable **Google** under Authentication → Sign-in method (Firebase generates the same kind of Web client ID as step 1 above as part of that) and switch `MainActivity`'s one-line `authRepository` expression to `FirebaseAuthRepository` — tell me once item 1 is done and I'll make that switch.

## 6b. Apple sign-in (blocks: the "Continue with Apple" button)

**Why it's blocked:** Sign in with Apple on Android is a web OAuth flow, not an on-device picker like Google's — it genuinely needs a backend to complete (there is no local-only version of this one), so it depends on item 1 (Firebase) either way.

**What to do:**
1. Finish item 1 (Firebase project).
2. Get an Apple Developer Program membership (~$99/yr), configure a Services ID for "Sign in with Apple", and point its redirect URL at your Firebase handler.
3. Enable **Apple** in Firebase's sign-in-method list and tell me — I'll wire that provider up. It's independent of item 6 above; Google alone is a perfectly reasonable place to stop.

**Worth knowing:** email/password sign-up and log-in already work today with none of this, against an on-device store (`LocalAuthRepository`) using PBKDF2-hashed credentials with per-account salts.

---
*Nothing else is currently blocked on your account — everything else flagged as a "limitation" in step reports so far has been either fixed directly or is a design/architecture decision waiting on your input, not an account action. This file will grow as later roadmap steps surface more.*
