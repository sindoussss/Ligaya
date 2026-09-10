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

## 6. Google / Apple sign-in (blocks: the two social buttons on the account screen)

**Why it's blocked:** the account screen (visual design screen 3) shows "Continue with Google" and "Continue with Apple", per the design. Neither can do anything without OAuth clients registered under your own accounts — Google Sign-In needs a client ID from the Firebase/Google Cloud console (so it also depends on item 1), and Sign in with Apple on Android is a web OAuth flow needing an Apple Developer account and a registered Services ID.

Rather than hide the buttons or leave them dead, tapping either currently says plainly that it isn't set up yet and points at email/password — which **does** fully work.

**What to do:**
1. Finish item 1 (Firebase project), then in the Firebase console enable **Google** under Authentication → Sign-in method, and send me the resulting web client ID.
2. For Apple: an Apple Developer Program membership (~$99/yr), a Services ID configured for "Sign in with Apple", and its redirect URL pointed at your Firebase handler. Enable **Apple** in the same sign-in-method list.
3. Tell me once either is done and I'll wire that provider up. They're independent — Google alone is a perfectly reasonable place to stop.

**Worth knowing:** email/password sign-up and log-in already work today with none of this, against an on-device store (`LocalAuthRepository`) using PBKDF2-hashed credentials with per-account salts. Once item 1 lands, switching the app to real Firebase Auth is a one-line change at the composition root in `MainActivity` — nothing else in the app knows which implementation it has.

---
*Nothing else is currently blocked on your account — everything else flagged as a "limitation" in step reports so far has been either fixed directly or is a design/architecture decision waiting on your input, not an account action. This file will grow as later roadmap steps surface more.*
