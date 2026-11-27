# Fishing Catch Timer 1.2.0

## Audio customization

- Added independent sound event identifiers for the particle/start cue, warning/countdown cue, and confirmed bite.
- Added separate 0–100% volume controls for the three channels.
- Added an in-game Audio Settings screen, one local Test button per channel, and Reset audio.
- Sound playback now resolves `namespace:event_id` identifiers through Minecraft's client sound manager, so vanilla events and resource-pack/mod sound events can be used.
- Test buttons intentionally bypass the global automatic-alert toggle; they only play when explicitly clicked.
- The warning sound is used for both configured warning thresholds and the countdown reaching zero, with different pitches.

## Web configurator

- Added editable sound event ID fields, independent channel volume sliders, and three browser-generated Test Sound buttons.
- Export/import includes the new sound identifiers and channel volumes.
- The browser preview uses synthesized representative tones; it cannot load Minecraft resource-pack sounds. Use the in-game Test buttons to verify the exact sound event.

## Compatibility

- Retained the legacy `soundVolume` JSON field for older configs/builds.
- When loading a pre-1.2.0 config without channel-specific volumes, each new channel inherits the old `soundVolume` value.
- Existing adaptive profiles, timing settings and free HUD coordinates are unchanged.
