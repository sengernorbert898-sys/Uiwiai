# Fishing Catch Timer 1.2.0

A client-only Fabric mod and matching standalone browser configurator for Minecraft Java 26.3.

## What changed in 1.2.0

- **Independent audio channels:** configure separate start, warning/countdown, and confirmed-bite sound event IDs.
- **Per-event volume sliders:** each of the three audio channels has its own 0–100% volume.
- **Custom Minecraft sound IDs:** accepts `namespace:event_name` identifiers. Resource-pack events declared in `sounds.json` can be used; enter the event ID, not an `.ogg` filename or path.
- **In-game sound test buttons:** each channel has a Test button that plays the selected sound locally, even when normal sound alerts are globally disabled.
- **Web audio controls:** the browser companion edits all sound IDs and volumes, and offers representative synthesized test tones. The browser cannot resolve the Minecraft sound event itself; test the actual event in-game.
- **Backwards-compatible settings migration:** old `soundVolume` configs populate the three new per-event volumes when the new fields are absent.

## What changed in 1.1.0

- **Adaptive per-server learning:** records elapsed time between a validated fishing-wake cue and the bobber's synchronized bite flag. Each server + dimension gets an independent profile. After three confirmed catches, the mod uses the rolling median of up to 24 recent samples as its baseline.
- **Wake-direction filtering:** validates the `minecraft:fishing` particle's offset signature and checks its directional angles against the local player's bobber, reducing unrelated particle triggers.
- **Trajectory refinement:** tracks successive valid wake positions and their change in distance to the bobber. When several samples show consistent closing movement, it gently blends that estimate into the live countdown.
- **Accuracy statistics:** displays profile sample count, learned median, and mean absolute error in the HUD and settings screen.
- **Free HUD placement:** the **Move HUD** screen lets you drag the HUD card to any point on the screen. Its normalized X/Y coordinates and scale are saved and exported by the web configurator.
- **Persistent bite-window state:** when the estimate reaches zero, the HUD remains at zero until the synchronized bite is detected or the bobber is reeled in; it does not silently restart another countdown.
- **Web companion v2:** adds adaptive-learning controls, trajectory controls, strict-filter toggle, X/Y position sliders, and a directly draggable HUD preview.

## Detection and accuracy

The client mixin observes both `ClientLevel.addParticle` overloads and forwards the `minecraft:fishing` particle's spawn coordinates and directional offsets. The classifier uses the vanilla fishing-wake vertical offset signature and angle candidates to check that the wake is directed toward the player's own bobber. A configurable radius and vertical bound are applied after that direction test.

The trajectory estimator compares the horizontal distance of successive accepted wake particles to the bobber. It only refines the countdown after at least three accepted samples and a plausible positive closing speed; it rejects large jumps and implausible speeds. The result is blended gradually with the current remaining time to reduce sudden display changes.

When the bobber's synchronized `DATA_BITING` flag turns true, the mod records the actual elapsed cue-to-bite duration against the final live prediction (including any trajectory refinements). Samples are kept in a rolling window of 24 for each server/dimension. Three samples are required before adaptive median timing replaces the configured fallback. The reported mean error is based on all accepted catches for that profile.

**Important limitation:** Minecraft's fishing timing is not fully deterministic, and custom servers may change fishing mechanics. Network delay also affects when the client sees particles and synchronized bite state. This remains an estimate, not a guarantee. If a server suppresses or changes the vanilla fishing particles, the classifier may not see a cue.

## Controls

- Press **O** in-game to open settings.
- **Adaptive per-server learning:** toggles learned median timing. It is enabled by default.
- **Particle trajectory tracking:** toggles live wake-distance refinement. It is enabled by default.
- **Strict particle signature filter:** checks the vanilla vertical offset signature in addition to direction. It is enabled by default.
- **Sound alerts:** toggles automatic alert playback. Explicit Test buttons remain available while alerts are off.
- **Audio settings:** opens the dedicated audio screen. Set the Start, Warning and Bite event IDs, adjust each independent volume slider, and press Test to audition the actual Minecraft sound. Warning thresholds and the timer reaching zero share the Warning channel but use different pitches.
- **Custom sound event IDs:** enter `namespace:event_id`, for example `minecraft:entity.experience_orb.pickup` or `my_pack:fish_alarm`. A custom ID must be declared as an event in a resource pack's `sounds.json`; do not enter the underlying `.ogg` file path.
- **Move HUD:** opens a full-screen placement view. Left-click and drag the card anywhere, then press Done. Center HUD is available in that view. The HUD stays at zero while it waits for the real bite flag; reeling in clears the pending state.
- **Clear data:** removes the learning profile for the currently active server/dimension only.
- HUD X/Y are normalized (0..1) fractions of the available screen area; HUD scale is applied when computing bounds.

## Requirements

- Minecraft Java **26.3**
- Java **25** for compiling and running the target game
- Fabric Loader **0.20.1 or newer**
- Fabric API for Minecraft 26.3

## Build

Open PowerShell in the project directory (the directory containing `build.gradle` and `gradlew.bat`). Run:

```powershell
java -version
.\gradlew.bat clean build
```

The Java output must show version 25. Keep the `Set-Location` command separate from the build command. Do not append `clean build` inside a quoted folder path.

The first build needs an internet connection to download Gradle and the Minecraft/Fabric dependencies. If the build succeeds, the JAR is placed in:

```text
build/libs/fishing-catch-timer-1.2.0.jar
```

## Install

1. Build the project.
2. Remove older `fishing-catch-timer-*.jar` files from the target instance's `mods` folder.
3. Copy `build/libs/fishing-catch-timer-1.2.0.jar` into that `mods` folder.
4. Install Fabric API for Minecraft 26.3 in the same instance.
5. Start Minecraft with Fabric 26.3 and press **O** in a world.

The mod is client-side; it does not need to be installed on the server and does not change loot, server state, fishing mechanics, or packet traffic.

## Configuration file

The mod stores preferences and learned profiles in:

```text
.minecraft/config/fishingcatchtimer.json
```

Relevant preference keys:

```json
{
  "biteWindowSeconds": 2.5,
  "warningSeconds": [1.5, 0.5],
  "visualAlerts": true,
  "soundAlerts": true,
  "soundVolume": 0.75,
  "soundStartId": "minecraft:entity.experience_orb.pickup",
  "soundWarningId": "minecraft:block.bell.resonate",
  "soundBiteId": "minecraft:ui.toast.challenge_complete",
  "soundStartVolume": 0.75,
  "soundWarningVolume": 0.75,
  "soundBiteVolume": 0.75,
  "hudX": 0.72,
  "hudY": 0.06,
  "hudScale": 1.0,
  "particleRadius": 7.0,
  "strictParticleFilter": true,
  "adaptiveLearning": true,
  "trajectoryTracking": true,
  "serverProfiles": {}
}
```

`serverProfiles` is written by the game client. The browser configurator exports user preferences but does not fabricate or overwrite game-observed learning samples. `soundVolume` remains in the JSON as a compatibility average for older builds; 1.2.0 uses the three channel-specific volume values.

## Web configurator

Open `web/index.html` in a modern browser. It is standalone and needs no server, login, Node.js, or external libraries. It offers fallback bite-window and warning settings; adaptive-learning, trajectory and strict-filter toggles; visual alerts; independent Start/Warning/Bite sound event IDs and volume sliders; representative browser test tones; HUD scale and free horizontal/vertical position; direct drag placement in the preview; simulated countdown/audio previews; and JSON import, copy and download. The browser-generated Test Sound tones are representative previews only; the mod's in-game Test buttons resolve and play the actual Minecraft event ID.

Place an exported `fishingcatchtimer.json` in the `config` folder for the specific Minecraft instance. The actual per-server learning data is collected by the in-game mod.

## Source map

- `CatchTimerConfig.java` — preferences, three audio channels and per-server rolling samples
- `CatchTimerController.java` — cue classification, state machine, trajectory estimation and adaptive learner
- `mixin/ClientLevelParticleMixin.java` — particle observer including directional offsets
- `mixin/FishingHookAccessor.java` — synchronized bite flag accessor
- `HudRenderer.java` — HUD drawing and free-position calculations
- `HudPositionScreen.java` — draggable in-game HUD placement
- `FishingCatchTimerScreen.java` — in-game settings and current-server stats
- `FishingCatchTimerAudioScreen.java` — audio event IDs, independent volume sliders and test playback
- `web/index.html` — browser configurator and draggable preview

## Verification status

The embedded web JavaScript and JSON resources were syntax-checked in the packaging environment. This source archive has not been compiled or launched in Minecraft by the packaging environment, so a successful Gradle build and in-game test are still required before treating the JAR as verified. Test particle classification and trajectory refinement on your server; custom server fishing changes can alter cue timing.

## License

MIT. See `LICENSE`.
