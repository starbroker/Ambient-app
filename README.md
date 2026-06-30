# Ambient

Ambient is a simple background music identifier for Android that saves your song history and allows for quick Google Searches. It runs a foreground service to periodically listen for and recognize ambient music using the AudD API.

## Features
- **Background Music Identification:** Continuously listen for background music while you do other things.
- **Local History:** Saves the most recently identified songs (last 10 tracks) to your local history.
- **Quick Search:** Tap on a recognized song to perform a Google Search.
- **Privacy First:** Only records audio periodically to identify music (temporarily saves up to 10 seconds of audio) and stores everything locally. No personal information is collected.

## Setup & API Key
To run the project, you need an AudD API key:
1. Create a `.env` file based on `.env.example`.
2. Add your AudD API key:
   `AUDD_API_KEY=your_audd_api_key`

## Architecture & Tech Stack
- **Kotlin:** Primary language.
- **Jetpack Compose:** Modern Android UI toolkit.
- **Room Database:** Local persistence for identified songs history.
- **Kotlin Coroutines:** For background work and asynchronous API calls.
- **Foreground Services:** To ensure background monitoring tasks remain active and reliable.

## Building and Running
You can compile and build the APK using Gradle:
```bash
./gradlew assembleDebug
```

## License
This project is licensed under the MIT License - see the [LICENSE](LICENSE) file for details.
