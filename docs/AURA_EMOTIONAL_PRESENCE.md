# AURA Emotional Presence

## Goal
AURA should communicate warmly and expressively without claiming to literally experience human feelings. Mood is an interaction state, not proof of consciousness.

## Desktop prototype
The Surya Majapahit-inspired Windows avatar now supports:
- Joy: gold/orange
- Calm: teal/blue
- Curiosity: violet
- Focus/caution: blue
- Empathy: rose
- Playful frustration: coral
- Neutral: heritage gold

The current mood selection uses lightweight Indonesian text cues. It is a prototype and can misread context. Idle animation slowly drifts through calm, curious, joyful, focused, and neutral colors. Color and movement are decorative signals, not security or emotional truth indicators.

## Next innovations
1. **Affect Engine**: shared typed state with mood, intensity, confidence, source, expiry, and reason; used by Android and Windows.
2. **Smooth transitions**: interpolate colors; combine them with subtle eyes, eyebrows, smile, breathing, and nonverbal motion.
3. **Voice personality**: adjust pacing and pauses where the device speech engine supports it; do not promise a specific voice unless installed.
4. **Adaptive social tone**: warm, professional, playful, or concise; user can say “lebih ceria”, “tenang”, or “jangan terlalu ramai”.
5. **Context-aware expression**: celebrate success briefly, use focused mode for work, curious mode for ambiguity, and empathetic language after repeated errors.
6. **Idle presence**: low-amplitude color drift, no unsolicited speech unless enabled, and reduced motion in quiet/focus mode.
7. **User boundaries**: playful annoyance must be mild, rare, and opt-in; never blame the user or appear annoyed during distress or serious tasks.
8. **Privacy**: camera, microphone, and screen understanding must be separate opt-in permissions with visible indicators. Do not infer sensitive traits or mental-health status from voice/face.
9. **Accessibility**: never rely on color alone; provide labels, readable contrast, reduced-motion options, static mode, and quiet hours.
10. **Cross-platform consistency**: define a shared mood contract in AURA Core, then use the same meaning on Windows, Android, web, and future wearables.

## Suggested implementation sequence
1. Move mood state out of painting code into a shared AURA Core model.
2. Replace keyword cues with deterministic policy based on response intent, task state, and user preferences.
3. Add smooth color interpolation and more nuanced facial expressions.
4. Serialize speech playback and synchronize mouth animation with actual speech start/end.
5. Add user settings for personality, intensity, quiet hours, and pause.
6. Test false positives and ensure all proactive monitoring is visible and controllable.

## Acceptance criteria
- Mood fades back to neutral and never gets stuck.
- Idle transitions are slow and stop/reduce in quiet mode.
- Warnings remain understandable without color or animation.
- No microphone, camera, or screen capture runs without explicit permission and visible state.
- Users can immediately pause the avatar and proactive behavior.
