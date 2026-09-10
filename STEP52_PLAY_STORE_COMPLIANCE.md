# Step 52: Play Store compliance review

This step's own acceptance criterion — "app passes a Play Console pre-launch report with no
policy-violation flags" — can only be produced by Google, inside your own Play Console account,
against a real uploaded build. I can't run that report myself. What I *can* do without your
account is everything that report depends on: audit every permission this app actually ships,
work out which ones need a Play Console declaration form, draft the exact text those forms ask
for, fix what was fixable in the manifest, and hand you a precise checklist for the rest.

## What I found and fixed myself (no account needed)

**`android:allowBackup` was `true` with no exclusion rules.** [app/AndroidManifest.xml](app/src/main/AndroidManifest.xml)
declared default backup with nothing scoping it, so Android's Auto Backup would have silently
included `ligaya.db` (core-data's Room database) in every device's backup to Google Drive. That
database holds location history, household/family-member records, and emergency event/state
snapshots — the exact category of personal-safety data a Play policy reviewer scrutinizes hardest,
and restoring a stale `emergency_state_snapshot` row onto a new device is also a genuine
correctness risk on its own (`DefaultEmergencyController` treats this database as its resume-state
source of truth). Fixed: `allowBackup="false"`. Verified against the real merged manifest for both
build types — `android:allowBackup="false"` is present in both
`app/build/intermediates/merged_manifest/debug/.../AndroidManifest.xml` and the `release` one, and
the release manifest has no `debuggable` attribute (correctly defaults to false).

**Confirmed `targetSdk`/`compileSdk` are current.** Both are 35 (`gradle/libs.versions.toml`) —
comfortably meets Play Store's minimum target API requirement, nothing to change.

**Confirmed this app avoids two entire categories of restricted-permission scrutiny by design,**
not by omission — worth stating explicitly since it changes what's actually in scope below:
- No `ACCESS_BACKGROUND_LOCATION` anywhere in the merged manifest. This app only ever requests
  foreground fine/coarse location (`core-location`, `core-data`), so it does **not** trigger
  Google's Background Location declaration form at all.
- No `CALL_PHONE`, `SEND_SMS`, `READ_PHONE_STATE`, or any other telephony/SMS permission.
  `core-telephony`'s `IntentUnified911DialAction` places emergency calls via `ACTION_DIAL` (hands
  off to the system dialer), not `ACTION_CALL` — a deliberate Step 16 design choice, confirmed by
  reading the actual dial action and its manifest's own doc comment. So the "SMS/telephony if
  applicable" clause in this step's own definition doesn't apply to this app at all.

## The real, current merged permission list

Confirmed directly from `app/build/intermediates/merged_manifest/{debug,release}/.../AndroidManifest.xml`
(the actual shipped output, not hand-assembled from each module's own manifest — library
dependencies can and do inject their own permissions, so this is the only reliable source):

| Permission | Why | Restricted-permission declaration form required? |
|---|---|---|
| `ACCESS_FINE_LOCATION` / `ACCESS_COARSE_LOCATION` | Locating the user during an active emergency (core feature) | No dedicated form — foreground-only location isn't on Google's declaration-form list. Still needs accurate Data Safety disclosure (below). |
| `RECORD_AUDIO` | Voice-activated SOS + Emergency Companion (core feature) | No dedicated form. Needs Data Safety disclosure (below). |
| `POST_NOTIFICATIONS` | Emergency status/foreground-service notifications | No — standard runtime permission, not restricted. |
| `FOREGROUND_SERVICE`, `FOREGROUND_SERVICE_LOCATION` | `EmergencyForegroundService` keeps tracking location while an emergency is active | Covered by the Foreground Service Types declaration below. |
| `FOREGROUND_SERVICE_SPECIAL_USE` | Same service also drives 911 dial monitoring / family alerts, which don't fit a narrower FGS type | **Yes** — this is the one Google scrutinizes hardest; exact text below. |

Nothing else is declared. No SMS, no telephony, no background location, no storage/media,
no accessibility, no device-admin, no all-files-access — none of the other declaration-form-gated
categories apply to this app.

## Foreground Service Types declaration (Play Console → App content → Foreground service permissions)

Google requires a written justification for `specialUse` specifically — it's a catch-all category
they review by hand, and it needs to match what's already embedded in the manifest's own
`PROPERTY_SPECIAL_USE_FGS_SUBTYPE` value (`"personal-safety emergency in progress"`), not just
restate the permission name. Paste this (or your own edit of it — you know your own business
context better than I do, but this is grounded in what the service actually does, not boilerplate):

> Ligaya is a personal-safety emergency-response app. When a user triggers an SOS (by voice or a
> button), a single foreground service keeps the app alive and visible to the user for the
> duration of that emergency. It tracks the user's location so it can be shared with emergency
> contacts and 911 dispatch (the `location` foreground service type), and it simultaneously
> monitors the outcome of the 911 call and coordinates alerting the user's pre-configured family
> contacts (the `specialUse` type) — a single real-world emergency session with both a location
> and a non-location responsibility that don't map cleanly to any other single declared FGS type.
> The service starts only when the user initiates an emergency and stops when that emergency ends;
> it never runs in the background otherwise.

## Data Safety section (Play Console → App content → Data safety)

Grounded in what `core-data`'s actual Room schema stores (`UserEntity`, `FamilyMemberEntity`,
`LocationEventEntity`, `EmergencyEventEntity`, `NotificationEventEntity`, `SubscriptionEntity`) and
what the account-gated integrations in [ACCOUNT_ACTIONS_NEEDED.md](ACCOUNT_ACTIONS_NEEDED.md) add
once wired up. Google requires the Data Safety form to match the *actual current build's* behavior
— if you submit before Firebase/RevenueCat/Places are wired in, answer only for what that specific
build does; re-check this table once each of those lands.

| Data type | Collected? | Purpose | Shared with third parties? | User can request deletion? |
|---|---|---|---|---|
| Precise location | Yes | App functionality (locating the user during an emergency), shared with the user's own emergency contacts/dispatch | No third-party ad/analytics sharing — only to the user's own configured contacts and, once Places is wired in, to Google Places for nearby-help lookups | Yes |
| Audio / voice | Yes (processed, not stored as raw audio files — transcribed via on-device STT, sent as text to Gemini) | App functionality (voice-activated SOS, Emergency Companion) | Transcript text sent to Google's Gemini API for processing | Yes |
| Personal info — name, phone number, relationship (family/household members) | Yes | App functionality (Safety Circle / family alerting) | No | Yes |
| App activity — emergency event history, notification delivery status | Yes | App functionality (emergency history, delivery confirmation) | No | Yes |
| Financial info — purchase history | Only once RevenueCat is wired in (`ACCOUNT_ACTIONS_NEEDED.md` item 3) | Entitlement checks for Ligaya+ | Processed by Google Play Billing / RevenueCat; this app never sees raw payment details | Via Google Play's own purchase history |

## Privacy policy

Play Console requires a live, hosted privacy policy URL before any track (even internal testing)
can be submitted — this app currently has none. I drafted one, grounded in the real data model
above, at [PRIVACY_POLICY_DRAFT.md](PRIVACY_POLICY_DRAFT.md). I'm not a lawyer and this isn't legal
advice — you're the data controller of record, so it needs your own review before it's real. Once
you're happy with it, it needs to be hosted somewhere with a public URL (a GitHub Pages page, a
simple static site, whatever you prefer) and that URL entered into Play Console's App content →
Privacy policy field.

## What's left — needs your Play Console account, not just review

This expands on item 3 of [ACCOUNT_ACTIONS_NEEDED.md](ACCOUNT_ACTIONS_NEEDED.md) (Google Play
Console developer registration), now that Step 52 has spelled out exactly what to do once that
account exists:

1. Host the privacy policy draft above (or your edited version of it) at a public URL.
2. Create the `com.ligaya.app` app listing in Play Console (internal testing track is enough to
   get a pre-launch report).
3. Fill in App content → Privacy policy with that URL.
4. Fill in App content → Foreground service permissions using the declaration text above.
5. Fill in App content → Data safety using the table above (re-verify against whichever
   integrations are actually wired into the build you upload).
6. Build and upload a signed release APK/AAB to the internal testing track.
7. Wait for Google's automated pre-launch report and review its results for any policy-violation
   flags. If anything unexpected shows up, send it back to me — most categories of finding
   (a manifest gap, a missing permission rationale, an accessibility issue) are things I can fix
   directly; only findings that are themselves about your Play Console account/listing setup would
   bounce back to you again.
