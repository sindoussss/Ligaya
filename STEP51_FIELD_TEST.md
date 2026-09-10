# Step 51 field test: battery drain + pipeline latency on a real device

Not an account action — this needs real hardware, not credentials, so it lives in its own
file rather than `ACCOUNT_ACTIONS_NEEDED.md`. Do this whenever you have a spare 30-60 minutes
with a real Android phone.

## Why this can't be done from the emulator

The emulator's `ligaya_test` AVD has no working on-device speech recognizer and unreliable
TextToSpeech init, and reports a synthetic (usually 100%, non-draining) battery level. Real
drain rate and real pipeline latency both require actual hardware. The roadmap itself also
specifically calls out testing "a lower-tier device representative of the Philippine market" —
if you have access to one, prefer it (or test it in addition to a flagship), since that's the
device class most likely to expose slow TTS/STT or aggressive battery throttling.

## What was built to measure this

Every pipeline stage now logs its own duration (or, for battery, level) to logcat under one
tag, `LigayaLatency`, at `Log.i` level:

- `battery: N%` — sampled every 5 minutes while the app is in the foreground
- `stt: <ms>` — time from starting to listen to the first final speech-recognition result
- `gemini_intent: <ms>` — wake-word intent classification call
- `gemini_companion: <ms>` — Emergency Companion conversational reply call
- `tts_status: <ms>` — time to speak a status message (e.g. "Voice assistant is unavailable")
- `tts_companion: <ms>` — time to speak a companion reply

No new UI or persistence was added for this — logcat is the deliberate mechanism, matching
the roadmap's own acceptance bar for this step.

## Steps

1. Connect the real device via USB, enable USB debugging if not already on, and confirm it's
   visible:
   ```bash
   adb devices
   ```
2. Install the app (from the repo root, with `JAVA_HOME` pointed at JDK 17 as usual):
   ```bash
   ./gradlew.bat installDebug
   ```
3. Launch Ligaya on the device and grant the microphone and notifications permissions when
   prompted.
4. Start capturing the latency log to a file, then leave it running for the rest of the
   session:
   ```bash
   adb logcat -s LigayaLatency:I > ligaya_field_test.log
   ```
5. For 30-60 minutes, use the app the way a real user would during that window: trigger the
   wake word a number of times, hold a few Emergency Companion conversations (both spoken and
   typed), and leave some idle gaps in between. The goal is a realistic mix of activity and
   idle time, not a stress test.
6. Stop the logcat capture (Ctrl+C).

## Reading the results

- **Battery drain rate**: find the `battery: N%` lines in `ligaya_field_test.log` (each has a
  logcat timestamp prefix). Take the first and last sample: `(start% - end%) / elapsed hours`
  gives an approximate %/hour figure for this app's foreground listening loop.
- **Latency**: find the `stt:` / `gemini_intent:` / `gemini_companion:` / `tts_status:` /
  `tts_companion:` lines. Each is one real measured duration in milliseconds for that turn.
  Look at the typical and worst-case values across however many turns happened.

There's no pass/fail threshold defined anywhere in the architecture doc for these numbers —
it asks for them to be documented, not gated against a specific figure. So the deliverable
here is the log file itself plus what you observe skimming it, not a computed verdict. If any
stage is consistently multi-second on a lower-tier device specifically, flag it back to me —
that would be a real UX finding (e.g. needing an explicit "still working..." indicator) worth
its own follow-up step, not something Step 51 itself claims to solve.

Feel free to send me `ligaya_field_test.log` (or just paste the numbers you see) once you've
run this, and I'll fold the findings into the record.
