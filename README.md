# BlueChat PRO — GitHub APK project

## Build
Upload the **contents** of this folder to the root of a GitHub repository (including hidden `.github`). Go to **Actions → Android APK → Run workflow**. Download the `BlueChatPro-debug-apk` artifact when the build succeeds.

Uses Gradle 8.9, Android Gradle Plugin 8.7.3, Kotlin 2.0.21, Java 17, SDK 35. Workflow installs SDK packages using the runner SDK rather than the previously failing `android-actions/setup-android@v3` step.

## Usage
Install on two physical Android phones. Enable Bluetooth and grant Nearby Devices permissions. HOST on one phone, SCAN on the other, then tap the host device in the device list. Both devices should be paired if necessary. Grant microphone permission before calling. Host listens for a separate Bluetooth voice connection; the client taps the call icon to start. Tap again to end.

## Limitations
- Bluetooth voice calling is **experimental and not tested on physical phones**. It uses a separate RFCOMM connection and uncompressed PCM audio; latency and device compatibility vary.
- **No end-to-end encryption.** Do not send sensitive messages or voice.
- Chat is one-to-one. Photos are transferred as Base64 but shown as placeholder text, not rendered thumbnails. Large photos are rejected.
- Chat tick marks (if shown) are decorative, not delivery receipts.
- No Gradle build was run in this environment; GitHub Actions will perform the first real build. Check its logs if it fails.
