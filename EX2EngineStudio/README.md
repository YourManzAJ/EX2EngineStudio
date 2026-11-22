# EX2 Engine Studio – Prototype v0.1.0

Realistic engine sound simulator for the **Geely EX2** (IHU629G / Android 9).

## What this prototype does

- Runs as a normal (or privileged) APK on the EX2 head unit
- Landscape automotive UI
- Live RPM simulation that reacts to vehicle speed (and throttle when available)
- Cabin audio output using real-time additive synthesis
- Three output modes in the UI: **INSIDE** / **OUTSIDE** / **BOTH**
  - INSIDE works
  - OUTSIDE and BOTH are present and accepted but **exterior routing is not yet implemented** (clearly marked Experimental)
- Background foreground service so sound continues when the UI is not focused
- Multiple engine character profiles (Ferrari-inspired V8, Lambo-inspired V10, Porsche Flat-6, AMG V8)
- Intensity / volume slider

## What is still limited / honest status

- Exterior speaker (AVAS path) is **not** verified for continuous high-quality audio. The UI is ready; the actual audio routing needs further reverse-engineering on a real unit.
- Accelerator pedal position is best-effort. Many EX2 firmwares do not expose a clean high-rate pedal signal to normal apps. The simulator falls back to a speed-based estimate.
- Synthesis is currently lightweight additive (CPU-friendly on Android 9). Sample-based layering will come in later iterations.

## Requirements

- Android Studio (Hedgehog | 2023.1.1 or newer recommended)
- JDK 17+
- Geely EX2 with ADB access (you already have this)

## Build

1. Open the `EX2EngineStudio` folder in Android Studio.
2. Let Gradle sync (it will download dependencies).
3. Build → Build Bundle(s) / APK(s) → Build APK(s)
4. The APK will be at:
   `app/build/outputs/apk/debug/app-debug.apk`

## Install on the EX2

```bash
# Make sure ADB is connected (USB or Wi-Fi)
adb devices

# Install (replace path if needed)
adb install -r app/build/outputs/apk/debug/app-debug.apk

# Or if you need to grant all permissions at install time:
adb install -r -g app/build/outputs/apk/debug/app-debug.apk
```

Launch **EX2 Engine Studio** from the app drawer.

## First run notes

- Grant any permission dialogs that appear.
- Press **START**. You should hear a synthesized engine in the cabin speakers.
- Drive the car (or just sit and use the intensity slider). RPM should move with speed.
- Switch between INSIDE / OUTSIDE / BOTH – the warning text will appear for the experimental modes.

## Next development steps (after you test this)

1. Confirm what vehicle properties are actually readable on your exact firmware (we can add more logging).
2. Investigate the AVAS / exterior audio path with `dumpsys audio` and mixer controls while the app is running.
3. Replace the simple additive synthesizer with multi-sample layers + better filtering.
4. Add proper gear-shift sound events, startup/shutdown sequences, and exhaust pops.
5. Optional: privileged / system-signed install if we need deeper audio routing.

## Safety

This application only generates sound and reads vehicle data. It does **not** send commands that affect propulsion, braking, steering, or any safety systems.

---

Prototype created for real on-car testing. Feedback from your EX2 (logs + behaviour) will drive the next iteration.
