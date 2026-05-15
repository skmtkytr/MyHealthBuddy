# Privacy

MyHealthBuddy is designed for personal use and minimizes data movement.

## What stays on your device

- All Health Connect records read by the app are stored only in the app's local Room database (`myhealthbuddy.db` in the app's private storage).
- Your Anthropic API key and other settings are stored in `EncryptedSharedPreferences`, which is backed by the Android Keystore.
- No analytics, no crash reporting, no telemetry of any kind is sent from the app.

## What may leave your device

The only outbound network traffic the app makes is the chat request to the LLM endpoint you configure in Settings → Base URL.

- If you point the app at `https://api.anthropic.com`, chat requests (including the system prompt that contains your health summary and profile) are sent to Anthropic per their terms.
- If you point the app at a local LM Studio / Ollama / other server, requests stay on whatever network reaches that server.

## What we do not do

- We never share your data with the app author or any third party.
- We never store your data on a backend (there is none).
- We never read records beyond the Health Connect data types you granted permission for.

## Removing your data

- Uninstalling the app deletes the local database and encrypted settings.
- Health Connect data is owned by Health Connect, not this app, and can be cleared from the Health Connect app's settings.

## Liability

This is a personal-use, AI-augmented tool. Its outputs are **not medical advice**. The author makes no warranty and is not liable for decisions made based on the app's output. Consult a qualified clinician for medical concerns.
