# Nexus-Eye project audit and voice-control upgrade

## Scope

This build was reviewed across the Android manifest, Gradle configuration, voice/wake-word pipeline, digital-assistant integration, accessibility workflow, device-action routing, permissions, notification access, and the existing app structure.

The existing project architecture and package/application ID were preserved. The changes are additive/targeted rather than a rewrite.

## High-impact issues found

1. **SpeechRecognizer package visibility was incomplete.** Android 11+ requires a manifest `<queries>` entry for `android.speech.RecognitionService` when an app uses the speech recognition service. The manifest previously queried launcher activities only. This can make `SpeechRecognizer.isRecognitionAvailable()`/service discovery fail on some devices.
2. **The Digital Assistant service was declared but the assistant role was never requested.** Declaring `VoiceInteractionService` is not enough to become the system assistant. Nexus-Eye now uses Android `RoleManager.ROLE_ASSISTANT` through the official user-consent flow.
3. **There were two independent voice-listening architectures.** `NexusEyeWakeWordService` is the active wake-word/command pipeline, while `NexusEyeAlwaysListeningService` is a second, unused continuous `SpeechRecognizer` implementation and is not declared in the manifest. The active pipeline remains the source of truth; the unused service is retained to avoid changing project structure but is not used for startup.
4. **The active speech recognizer did not prefer on-device recognition.** The shared recognizer now prefers `createOnDeviceSpeechRecognizer()` when available and falls back to the normal system recognizer.
5. **Recognition retries were too aggressive.** Busy/network/client failures could restart the recognizer almost immediately. A bounded exponential backoff was added.
6. **A dangerous partial-result recovery path existed.** A `NO_MATCH` error could promote the last partial transcript into an executable command. That could turn an incomplete command such as "call..." or "send..." into an unintended action. Partial transcripts are now diagnostic only; only a final result is executed.
7. **Speech recognition can be improved on newer Android versions.** Device-context biasing and optional Android 14+ language detection/switching extras were added while preserving compatibility with recognizers that ignore those extras.
8. **Message parsing destroyed user text.** The old parser normalized the complete command before extracting a message, which lower-cased dictated text and removed punctuation. Parsing now preserves the original dictated message body.
9. **Common colon-separated messages could fail.** Because normalization removed punctuation before parsing, commands such as `message Mom: I'll call you later` could lose the separator. Parsing now uses the original command text.
10. **The system-assistant service did not actually process voice commands.** The Nexus-Eye assistant session now uses the existing `NexusEyeSpeechRecognizer` -> `TaskRouter` -> TTS pipeline instead of being a logging-only shell.
11. **The system assistant and foreground wake service could compete for the microphone.** When Nexus-Eye owns the assistant role, the normal foreground wake service is skipped and the system-managed `VoiceInteractionService` owns hotword capture. When Nexus-Eye is not the system assistant, the existing foreground wake service remains the fallback.
12. **Accessibility automation was too app-specific.** The existing WhatsApp workflow remains intact, but generic accessibility primitives were added for Back, Home, Recents, Notifications, Quick Settings, visible-control clicking, focused-field typing, scrolling, screen reading, and message-search initiation.
13. **Android permission boundaries were being treated as something code could bypass.** The implementation now explicitly uses the legitimate Android mechanisms: runtime permissions, assistant role selection, Accessibility settings, notification access, and system-mediated activity launching. It does not attempt to silently grant restricted privileges.

## New/changed voice capabilities

- `Hey Nexus` hotwording through OpenWakeWord remains the local wake phrase.
- If Nexus-Eye is the system assistant, the system-managed `VoiceInteractionService` owns the hotword microphone.
- Command recognition prefers on-device speech recognition when the device exposes it.
- System speech-recognition service discovery is declared in the manifest.
- Android 14+ language detection/switching extras are enabled opportunistically.
- Recognition failures use bounded backoff instead of a tight retry loop.
- Incomplete partial transcripts are never executed as commands.
- Existing `TaskRouter` remains the central command router.
- Generic accessibility commands are routed through the existing `NexusEyeActionWorkflow`.
- Dictated message text keeps its original case and punctuation where the speech recognizer provides it.

## Android assistant setup

The app now provides an official `RoleManager.ROLE_ASSISTANT` request flow for blind-user setup. Android remains responsible for the final role decision.

Once the role flow completes, the blind-user setup requests the relevant runtime permissions and then opens Android Accessibility settings when accessibility access is not enabled.

## Permissions / roles used

The existing manifest permissions were retained, including microphone, contacts, phone calling, location, Bluetooth, notifications, and foreground microphone service support. Blind-user setup requests the applicable runtime permissions together rather than waiting until the first failure.

System-controlled access still requires the user to explicitly enable:

- Nexus-Eye as the Android Assistant
- Nexus-Eye Accessibility Service
- Nexus-Eye Notification Access

No Android application can legitimately auto-grant those protected system settings.

## Important platform limitation

The goal of "never getting blocked by Android" cannot be implemented literally. Android intentionally blocks arbitrary background activity launches, microphone sharing, protected settings, and privileged data access. The strongest supported implementation is therefore to combine the Assistant role, VoiceInteractionService, AccessibilityService, runtime permissions, NotificationListenerService, and normal Android intents. This is the approach used here.

## Validation status

- ZIP extraction: passed.
- Original archive integrity (`unzip -t`): passed.
- Wake-word model asset: present.
- Static review of changed Kotlin files: no obvious brace/parenthesis imbalance or syntax-only diagnostic was surfaced by the standalone Kotlin parser; Android/AndroidX symbols cannot be resolved without the Android/Gradle dependency classpath.
- Full Gradle build: **not completed in this environment**. The project requires Gradle 9.5, and the runner has no cached Gradle 9.5 distribution and cannot reach `services.gradle.org` to download it. Therefore no APK build success is claimed.

## Recommended device test matrix

Test on at least one Android 12, Android 13, Android 14, and Android 15/16 device or emulator with:

1. Mic permission granted/denied.
2. Assistant role granted/declined.
3. Accessibility enabled/disabled.
4. Notification access enabled/disabled.
5. On-device speech model available/unavailable.
6. English and Hindi speech.
7. Multiple speakers and quiet/noisy environments.
8. `Hey Nexus` -> command -> TTS loop repeated for 20+ cycles.
9. `open WhatsApp`, `search messages for ...`, `read screen`, `click ...`, `type ...`, `scroll down`, `go back`, `home`.
10. WhatsApp message sending and call workflows with contacts permission granted/denied.

## Files materially changed by this upgrade

- `app/src/main/AndroidManifest.xml`
- `app/src/main/java/com/thirdeye/app/MainActivity.kt`
- `app/src/main/java/com/thirdeye/app/NexusEyeWakeWordApplication.kt`
- `app/src/main/java/com/thirdeye/app/device/DeviceActionManager.kt`
- `app/src/main/java/com/thirdeye/app/device/NexusEyeActionWorkflow.kt`
- `app/src/main/java/com/thirdeye/app/voice/NexusEyeAssistantManager.kt` (new)
- `app/src/main/java/com/thirdeye/app/voice/NexusEyeSpeechRecognizer.kt`
- `app/src/main/java/com/thirdeye/app/voice/NexusEyeVoiceInteractionService.kt`
- `app/src/main/java/com/thirdeye/app/voice/NexusEyeWakeWordService.kt`

The project may also contain pre-existing working-tree changes from the uploaded ZIP; those were preserved rather than reset to the repository's older Git commit.
