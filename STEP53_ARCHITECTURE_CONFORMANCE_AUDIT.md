# Step 53: Final architecture conformance audit

Cross-checks the finished implementation against `LIGAYA_ARCHITECTURE_FINAL_VOICE.md`, section by
section (§1–§29), per this step's own acceptance criteria: every section needs a corresponding,
verifiable implementation artifact, or an explicitly logged, approved deviation. This isn't a
memory-based sign-off — every claim below was checked directly against the current code, the real
Firestore rules, and (for two sections) the real local emulator, not asserted from having built it.

**Net result of this pass: three real conformance gaps found and fixed outright (all fixable
without any account, so — per standing project policy — they're fixed, not just logged). The
third (§5/§21's Home-screen voice indicator) was initially left open pending a design check
rather than rushed — built as its own follow-up once asked for. Details below and in the
per-section table.**

## Fixes made during this audit

1. **§23 (Absolute trust rule): `ResponseValidator`'s claim coverage was incomplete.** It blocked
   unverified "help arrived," "911 contacted," "help is on the way," and "family notified" claims,
   but §23 names eight specific phrases —"location shared" and "emergency-service contacted" had
   no rule at all, even though the engine already carries the state needed to verify them.
   "SMS delivered"/"push delivered" were also unmatched as their own phrasings. Fixed in
   [ResponseValidator.kt](core-ai/src/main/kotlin/com/ligaya/core/ai/ResponseValidator.kt) — two
   new claim rules added, plus expanded pattern coverage on the existing family-notified rule.
   Six new tests added to
   [ResponseValidatorTest.kt](core-ai/src/test/kotlin/com/ligaya/core/ai/ResponseValidatorTest.kt);
   full `core-ai` unit suite passes.

2. **§7 (Ligaya+ family plan): the "up to 5 invited members" cap was never enforced anywhere.**
   Neither `SafetyCircleRepository.inviteMember` (core-backend) nor `firestore.rules`' member
   create rule checked a count — a 6th invite would have silently succeeded. Firestore rules can't
   count a subcollection directly, so this needed the standard fix for that limitation: a Cloud
   Function trigger maintaining a denormalized `member_count` on the household doc
   ([household-membership.js](backend/functions/household-membership.js), registered in
   [index.js](backend/functions/index.js)), checked by
   [firestore.rules](backend/firestore.rules)' create rule. `SafetyCircleRepository.createHousehold`
   now initializes the counter explicitly. Verified against the real local Firestore + Functions
   emulators, not just compiled: `backend`'s rules-test suite (3 new tests: 6th invite rejected,
   5th invite under the cap succeeds, a pre-existing household with no counter defaults to 0
   rather than erroring) and `functions`' unit-test suite (4 new tests: increment, decrement,
   no-op on accept, and concurrent-increment atomicity) both pass clean — 23/23 and 32/32 tests
   respectively (the 7 pre-existing rules-suite failures are a documented, unrelated Node/Firestore-
   SDK flake the test file's own header already describes at length; none of them are new or
   touch anything this audit changed).

## Fixed as a follow-up (§5 / §21)

**The background wake-word listening loop had zero visual presence anywhere on Home — now fixed.**
Correction while writing this up: the original version of this finding cited this app's own
"screen inventory" spec's "a persistent, always-visible visual... idle/ready, listening,
processing, speaking" as architecture-doc §27 — checked directly against the real 29-section doc
extracted for this audit, and that's wrong. The doc's actual §27 is "Security / authorization,"
unrelated to screen layout; the "persistent, always-visible" wording traces to this project's own
implementation-roadmap screen-inventory notes (already embedded in `VoiceStateIndicator.kt`'s own
doc comment from Step 33), not the architecture doc itself. §21's own text is the real, directly-
quotable architecture-doc anchor here — "The UI must clearly tell the user when AI voice
interpretation is unavailable" — independently confirmed by rereading §21 verbatim. `VoiceStateIndicator`
(design-system) already implemented the idle/listening/processing/speaking visual exactly as the
screen-inventory notes describe, but was only ever wired into `EmergencyActiveScreen`/
`EmergencyCompanionScreen`, during an already-active emergency.
`VoiceActivationCoordinator` (the always-on Home-screen wake-word listener, section 5's headline
"hands-free" feature) had no phase tracking of its own, and `HomeScreen.kt` showed nothing about
it — not idle, not listening, not the §21-required "AI voice interpretation unavailable" case
(previously spoken via TTS only — a real gap on its own if TTS itself is what's degraded, which
Step 51 already proved can genuinely happen on real hardware).

Built once asked for, not rushed into the audit itself since it was a real (if contained) new
feature rather than a one-answer bug fix:
- `VoicePipelinePhase` (Step 38's existing IDLE/LISTENING/PROCESSING/SPEAKING vocabulary) moved
  from feature-companion down to `core-voice` — its natural shared home, since
  `VoiceActivationCoordinator` needed it too and couldn't depend on a feature module.
- `VoiceActivationCoordinator` gained its own `phase: StateFlow<VoicePipelinePhase>`, wired at
  each real transition in its existing Flow chain (LISTENING on capture start, PROCESSING before
  the Gemini call, IDLE on completion) — mirroring `EmergencyCompanionCoordinator`'s own pattern.
- `HomeScreen` now renders `VoiceStateIndicator` plus a live text label ("Listening for
  "Ligaya"…"), and a separate `MainActivity`-owned `aiUnavailableNotice` flag drives a distinct
  banner ("Voice assistant unavailable right now — use the SOS button instead.") for exactly the
  window `VOICE_AI_UNAVAILABLE` is being spoken — more precise than stretching `phase` to cover a
  moment the coordinator itself has no visibility into.
- Verified three ways, not just compiled: full unit-test regression, the real instrumented suites
  (`feature-home` 11/11, `core-voice`/`feature-companion`/`feature-emergency-active`/`app` all
  green, zero regressions), and a live screenshot of the real running app confirming the indicator
  and label actually render correctly on Home without crowding out the SOS button's own
  prominence (section 9).

## Section-by-section

| § | Title | Implementation artifact | Status |
|---|---|---|---|
| 1 | System-level architecture | Module graph mirrors the 5 layers exactly: UX (`app`, `feature-*`), AI (`core-ai`, `core-voice`), deterministic engine (`core-emergency-engine`, `core-data`), backend (`core-backend`, `backend/`), external services (`core-places`, `core-telephony`, `core-location`, `core-notifications`, `core-billing`) | ✅ |
| 2 | Ownership rule — RevenueCat vs backend | `core-billing` (`RevenueCatEntitlementRepository`) owns entitlement only; `core-backend` (`SafetyCircleRepository`, Firestore `households`/`members`) owns membership. No cross-contamination — confirmed via module dependencies and `checkNoBillingInCoreScreens` (runs every build) | ✅ |
| 3 | Installation and onboarding | `feature-onboarding/OnboardingScreen.kt` — account creation is the one non-skippable step; every emergency-profile field is explicitly optional ("Skip for now" preserves partial input rather than discarding it) | ✅ |
| 4 | Home / normal mode | `feature-home/HomeScreen.kt` — personal safety, Safety Circle, normal safety, AI assistance sections | ✅ |
| 5 | Voice activation model | `VoiceActivationCoordinator` (core-voice): wake phrase → `IntentProvider.interpret` → deterministic engine, no additional tap. SOS remains the fallback. Home's own always-visible listening indicator — see fix above | 🔧 fixed |
| 6 | Safety Circle | `core-backend/SafetyCircleRepository` + `firestore.rules` — location sharing gated by explicit permission fields (`can_view_location`), never implied by membership alone; verified via `getFamilyEmergencyView` tests (member without permission gets no location field at all) | ✅ |
| 7 | Ligaya+ family plan | `feature-paywall`, RevenueCat → entitlement → backend-enforced 5-member cap. **Cap enforcement was missing — fixed this step**, see above | 🔧 fixed |
| 8 | Free vs Ligaya+ | Core emergency flow (SOS, profile, contacts, 911, basic guidance) never checks entitlement; only family/household features do — structurally enforced by `checkNoBillingInCoreScreens` on every build | ✅ |
| 9 | Emergency activation | `SosScreen.kt`'s own doc comment states the design goal directly ("zero dependency on core-ai"); confirmed at the build-graph level — `core-data`/`core-ui-state` production code never depends on `core-ai` (only `core-data`'s test source set does, for isolation tests) | ✅ |
| 10 | AI interpretation layer | `GeminiIntentProvider`/`HttpGeminiContentGenerator` (core-ai): STT → Gemini → structured `VoiceInterpretationOutcome` | ✅ |
| 11 | AI / deterministic action boundary | `IntentProvider` returns data (`VoiceInterpretationOutcome`), never an action; `VoiceActivationCoordinator` and `DefaultEmergencyController` are the only code that decides/executes | ✅ |
| 12 | Deterministic safety engine | `DefaultEmergencyController` (core-data) + `EmergencyStateMachine`/`ConcurrentSubsystemStates` (core-emergency-engine) — persisted, safety-critical state owner | ✅ |
| 13 | Emergency active — concurrent subsystems | `ConcurrentSubsystemStates` — five independent fields, each updated independently; `EmergencyCompanionCoordinator`'s own doc comment states explicitly it "never reads or branches on any of their success/failure" | ✅ |
| 14 | Location flow | `core-location` (`LocationFlowCoordinator`) — GPS → last-known → `Unavailable`, never invents coordinates | ✅ |
| 15 | Philippines Unified 911 call flow | `core-telephony` (`IntentUnified911DialAction`) — `ACTION_DIAL`, not `ACTION_CALL` (hands off to the system dialer, no unrestricted silent-calling claim); `CallFailed` state supports retry | ✅ |
| 16 | Nearest relevant emergency service flow | `core-places` (`GooglePlacesNearbySearchSource`/`GooglePlacesDetailsSource`) — nullable `phoneNumber`, never invents a number; no code anywhere claims "verified dispatch" | ✅ |
| 17 | Family alerting and notification states | `backend/functions/family-alerts.js` — real PENDING → SENT → CONFIRMED/FAILED per channel, CONFIRMED only ever written from a genuine confirmation source (the recipient's own device for push, Twilio's real delivery webhook for SMS) — never assumed | ✅ |
| 18 | Family emergency screen | `feature-family` + `getFamilyEmergencyView` callable — exposes only what the sender's permissions allow, verified by dedicated rules tests (no-permission member gets no location field, not a redacted one) | ✅ |
| 19 | Emergency companion | `EmergencyCompanionCoordinator` (feature-companion) — runs independently, own doc comment confirms it never blocks on or reads other subsystems' outcomes except through `ResponseValidator`'s claim checks | ✅ |
| 20 | Emergency resolution | `EmergencyStatusMessage.USER_MARKED_SAFE`/`EMERGENCY_RESOLVED` — "You have been marked as safe," never "help arrived" anywhere in shipped strings | ✅ |
| 21 | Failure handling | `VoiceActivationResult.AiUnavailable`, `GeminiFailureIsolationTest`, the three Step 51 timeout fixes (TTS engine-ready, STT listening, snapshot wait) all keep the rest of the system running on a subsystem failure. AI-unavailable feedback is now visual too, not TTS-only — see fix above | 🔧 fixed |
| 22 | Offline vs online / graceful degradation | No "fully offline" claim anywhere in the codebase (checked directly); SOS/location/dial remain on-device-capable, Gemini/Places/backend/push/SMS/RevenueCat correctly require network | ✅ |
| 23 | Absolute trust rule | `ResponseValidator` — see fix above. Now covers all eight §23 phrase categories | 🔧 fixed |
| 24 | Backend data model | Room entities (`UserEntity`, `HouseholdEntity`, `FamilyMemberEntity`, `EmergencyEventEntity`, `LocationEventEntity`, `NotificationEventEntity`, `SubscriptionEntity`) and the Firestore schema both mirror the ER diagram's fields (doc's own "conceptual, not exact schema" caveat noted) | ✅ |
| 25 | Emergency state machine | `EmergencyState` enum: `IDLE, EMERGENCY_DETECTED, EMERGENCY_CONFIRMED, EMERGENCY_ACTIVE, USER_MARKED_SAFE, EMERGENCY_RESOLVED, CLOSED` — exact match to the diagram; `USER_MARKED_SAFE` reachable from `EMERGENCY_ACTIVE` regardless of subsystem completion | ✅ |
| 26 | External service boundaries | Matches the doc's table: Gemini/Places/RevenueCat/dialer/push/SMS each scoped to exactly one module, none granted authority beyond its stated purpose | ✅ |
| 27 | Security / authorization | `firestore.rules` — deny-by-default baseline, backend is source of truth for every listed item; `LOCATION_EVENT`/`NOTIFICATION_EVENT` never directly readable by anyone but the creator, only through the permission-checked `getFamilyEmergencyView` callable | ✅ |
| 28 | Product flow summary | Synthesis of §5/§9/§13 — already independently verified above | ✅ |
| 29 | Final architectural principle | Philosophical summary, not a separate artifact — reflected throughout: `IntentProvider` never executes, `DefaultEmergencyController` is the sole authority, `firestore.rules` is the sole source of truth, external services (Places/Gemini/RevenueCat) never make a safety decision | ✅ |

## What this audit did not (and structurally cannot) verify

Some of this document's claims — e.g. that voice activation genuinely requires no additional tap
on a real device, that TTS/STT genuinely behave as designed under real network conditions — were
already covered by Step 51's real-device field-test handoff
([STEP51_FIELD_TEST.md](STEP51_FIELD_TEST.md)), not re-verified here. This audit checked that the
*code* matches the *documented design*; it isn't a substitute for that real-hardware session, which
remains outstanding on your side.
