# MyHealthBuddy

Personal-use Android health companion. Reads your Health Connect data, summarizes it, and lets you chat with an LLM (Anthropic Claude or any Anthropic-compatible endpoint like LM Studio) about your health trends.

> Not medical advice. AI-generated insights for personal reference only.

## Features

- Reads from **Android Health Connect**: steps, distance, floors, active/total calories, hydration, heart rate (incl. resting & HRV), SpO2, respiratory rate, body temperature, blood glucose, blood pressure, weight, height, body fat, lean body mass, sleep sessions (with stages), exercise sessions
- Persists locally with **Room** + incremental sync via Health Connect `getChanges`
- Chat with an LLM backend:
  - **Anthropic API** (BYO key)
  - **Any Anthropic-compatible endpoint** (LM Studio, Ollama with adapter, etc.)
- Conversation-scoped **full-history snapshot** for prompt caching
- **Editable system prompt** with profile (gender/age/height/weight/focus) injection
- Built-in **prompt preview / clipboard copy** for debugging
- 100% local data: nothing leaves your device except the chat request (to the endpoint you choose)

## Build

Requires JDK 17 + Android SDK 35 + Gradle 8.9. Easiest with [mise](https://mise.jdx.dev):

```bash
mise install        # JDK 17 + Gradle (per .mise.toml)
# Install Android cmdline-tools to ~/Android/Sdk, then accept licenses:
sdkmanager --licenses
sdkmanager "platform-tools" "platforms;android-35" "build-tools;35.0.0"

./gradlew assembleDebug
# APK at app/build/outputs/apk/debug/app-debug.apk
```

## Install

Sideload onto an Android device (Android 9 / API 28+) with **Health Connect** installed:

```bash
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

## First-run setup

1. Launch the app. On the **ヘルス** tab, grant Health Connect permissions for the data types you have sources for. Initial sync may take a few seconds depending on your history size.
2. On the **設定** tab:
   - Enter your **Anthropic API key** (or leave blank and point Base URL to a local LM Studio)
   - Fill out your **profile** (gender / age / height / weight / focus area)
   - Optionally tune the **system prompt**
3. Switch to the **チャット** tab and tap *AI に全体分析を依頼*.

### Using LM Studio (local LLM)

1. Run LM Studio's server with an Anthropic-compatible endpoint (LM Studio 0.3.5+).
2. Make sure your firewall allows the LM Studio port (default 1234) from your phone's network.
3. In MyHealthBuddy → 設定 → Base URL = `http://<your-pc-lan-ip>:1234`, API Key blank.

## Privacy

- Health data is read from Health Connect and stored **only on the device** (encrypted Room DB).
- Your **API key** is stored in `EncryptedSharedPreferences` (Android Keystore-backed).
- The app **does not** phone home, collect analytics, or transmit data to anyone except the LLM endpoint you configure.
- If you use Anthropic's API, your chat requests go to Anthropic per their privacy terms. If you use a local LM Studio, requests stay on your LAN.

See [PRIVACY.md](PRIVACY.md) for details.

## License

[MIT](LICENSE)
