# Purefon

A full-featured, privacy-focused Android dialer and messaging app built with modern Android architecture. Purefon replaces your default phone app with a powerful, encrypted alternative that supports SMS, MMS, calls, call screening, and more.

## Download

<a href="https://play.google.com/store/apps/details?id=com.rasmi.purevon&pcampaignid=web_share">
  <img alt="Get it on Google Play" src="https://upload.wikimedia.org/wikipedia/commons/7/78/Google_Play_Store_badge_EN.svg" width="200"/>
</a>

## Features

### Calls
- **Full In-Call UI** — Answer, reject, hold, merge, transfer, conference calls
- **Call Screening** — Automatic spam detection and blocking
- **Call Recording** — Record calls with audio codec selection (AAC, AMR, OGG)
- **Fake Call** — Simulate incoming calls with custom caller info and timers
- **Call History** — Detailed call logs with contact integration and statistics
- **Callback Reminders** — Schedule follow-up reminders after calls
- **Voicemail Support** — Visual voicemail with playback controls
- **DTMF Keypad** — In-call DTMF tone generation

### Messaging
- **SMS & MMS** — Full support for text and multimedia messaging
- **Group Messaging** — Send/receive group SMS and MMS conversations
- **Scheduled Messages** — Schedule messages for future delivery
- **Message Templates** — Create and reuse quick-reply templates
- **Reactions** — React to messages with emoji
- **Message Search** — Full-text search across all conversations
- **Spam Filtering** — Automatic spam detection and blocking
- **Contact Notes** — Add personal notes to contacts
- **Export/Import** — Encrypted CSV/JSON export and import of messages and call logs

### Media
- **Image Compression** — Automatic smart compression for MMS attachment size limits
- **Video Compression** — Hardware-accelerated video compression using MediaCodec
- **Audio Messages** — Record and send voice messages with waveform visualization
- **Video Messages** — Record and send short video clips
- **Media Preview** — Full-screen image and video viewer with zoom

### Contacts
- **Contact Management** — View, edit, add, and merge contacts
- **Unified Contact Search** — Search contacts across phone, SMS, and call logs
- **Contact Details** — Full contact detail view with all phone numbers, emails, and notes
- **Contact Avatar** — Circular avatar with initials fallback
- **Contact Filtering** — Filter conversations by contact

### Privacy & Security
- **Encrypted Database** — SQLCipher encryption for all local data
- **Biometric Lock** — Fingerprint/face authentication to open the app
- **Encrypted Export** — AES-256-GCM encryption for exported files
- **Block/Whitelist** — Block unwanted callers and whitelist VIP contacts
- **OTP Auto-Delete** — Automatic one-time password message cleanup

### Customization
- **Per-Conversation Settings** — Custom vibration, notification tones, and wallpapers per contact
- **Message Templates** — Create and manage quick-reply templates
- **Statistics Dashboard** — Detailed usage statistics with charts (calls, messages, contacts)
- **Dark Mode** — Full Material 3 dark mode support
- **Multi-Language** — English, Arabic, and 50+ translation languages

## Architecture

Purefon follows **Clean Architecture** with clear separation of concerns:

```
com.rasmi.purevon/
├── data/                    # Data layer
│   ├── local/              # Room database, DAOs, entities, migrations
│   ├── mapper/             # Entity ↔ Domain mappers
│   ├── model/              # Data models (CountryPhoneRules, etc.)
│   ├── paging/             # ConversationPagingSource
│   ├── repository/         # Repository implementations
│   └── security/           # SQLCipher setup
├── domain/                  # Domain layer
│   ├── model/              # Domain models (Conversation, Message, etc.)
│   ├── repository/         # Repository interfaces
│   └── usecase/            # Use cases (calls, messages, contacts, etc.)
├── presentation/            # UI layer
│   ├── component/          # Reusable Compose components (50+)
│   ├── navigation/         # Navigation graph
│   ├── screen/             # Screen composables
│   │   ├── contacts/       # Contact list and detail screens
│   │   ├── conversation/   # Individual conversation screen
│   │   ├── dialer/         # Dialpad screen
│   │   ├── history/        # Call history screen
│   │   ├── incall/         # In-call UI
│   │   ├── messages/       # Messages inbox
│   │   ├── scheduled/      # Scheduled messages manager
│   │   ├── security/       # Biometric lock screen
│   │   ├── settings/       # App settings
│   │   └── statistics/     # Usage statistics
│   └── viewmodel/          # ViewModels
├── service/                 # Android services
│   └── PurevonConnectionService  # Telecom connection service
├── receiver/                # Broadcast receivers
│   ├── SmsReceiver.kt
│   ├── MmsReceiver.kt
│   ├── PhoneStateReceiver.kt
│   └── ... (10+ receivers)
├── worker/                  # WorkManager workers
│   ├── ScheduledMessageWorker.kt
│   └── OtpAutoDeleteWorker.kt
├── di/                      # Hilt dependency injection modules
└── util/                    # Utility classes
    ├── export/             # Encrypted export/import manager
    ├── mms/                # APN manager, MMS helpers
    ├── media/              # Media codec, compression utilities
    ├── security/           # Encryption, biometric auth
    └── ... (20+ utility files)
```

## Tech Stack

| Category | Technology |
|---|---|
| **Language** | Kotlin |
| **UI** | Jetpack Compose with Material 3 |
| **Architecture** | MVVM + Clean Architecture |
| **DI** | Hilt |
| **Database** | Room + SQLCipher (encrypted) |
| **Async** | Kotlin Coroutines + Flow |
| **Navigation** | Navigation Compose |
| **Paging** | Paging 3 |
| **Image Loading** | Coil |
| **Video** | Media3 (ExoPlayer) + MediaCodec |
| **MMS** | Klinker android-smsmms library |
| **HTML Parsing** | Jsoup |
| **WorkManager** | Background scheduling |
| **Security** | AndroidX Biometric + Security-Crypto |
| **Telecom** | Android ConnectionService |
| **Min SDK** | 26 (Android 8.0) |
| **Target SDK** | 36 |

## Getting Started

### Prerequisites

- Android Studio Ladybug (2024.2+) or later
- JDK 17+
- Android SDK 36
- Physical Android device (for SMS/call features)

### Build

```bash
# Clone the repository
git clone https://github.com/purefoncare-creator/purefon.git
cd purefon

# Build debug APK
./gradlew assembleDebug

# Build release APK (requires keystore setup)
./gradlew assembleRelease
```

### Release Signing

1. Create a release keystore:
```bash
keytool -genkeypair -v -keystore release.keystore \
  -alias purevon -keyalg RSA -keysize 2048 -validity 10000
```

2. Create `keystore.properties` in the project root:
```properties
storeFile=../release.keystore
storePassword=YOUR_STORE_PASSWORD
keyAlias=purevon
keyPassword=YOUR_KEY_PASSWORD
```

### CI/CD Environment Variables

For automated builds, set these environment variables:
- `KEYSTORE_FILE` — Path to the keystore file
- `STORE_PASSWORD` — Keystore password
- `KEY_ALIAS` — Key alias
- `KEY_PASSWORD` — Key password

## Permissions

Purefon requires the following permissions to function:

| Permission | Purpose |
|---|---|
| `READ_PHONE_STATE` | Identify current call state |
| `CALL_PHONE` | Make outgoing calls |
| `ANSWER_PHONE_CALLS` | Answer incoming calls |
| `READ/WRITE_CALL_LOG` | Display and manage call history |
| `READ/WRITE_CONTACTS` | Contact integration |
| `SEND_SMS` / `RECEIVE_SMS` | SMS messaging |
| `RECEIVE_MMS` / `RECEIVE_WAP_PUSH` | MMS messaging |
| `RECORD_AUDIO` | Call recording and voice messages |
| `CAMERA` | Video message recording |
| `ACCESS_FINE_LOCATION` | Location sharing in messages |
| `POST_NOTIFICATIONS` | Message and call notifications |
| `RECEIVE_BOOT_COMPLETED` | Restore scheduled messages after reboot |
| `FOREGROUND_SERVICE` | Background call handling |
| `BIND_TELECOM_CONNECTION_SERVICE` | In-call connection management |
| `USE_FULL_SCREEN_INTENT` | Full-screen incoming call UI |

## Contributing

Contributions are welcome! Please follow these steps:

1. Fork the repository
2. Create a feature branch (`git checkout -b feature/amazing-feature`)
3. Commit your changes (`git commit -m 'Add amazing feature'`)
4. Push to the branch (`git push origin feature/amazing-feature`)
5. Open a Pull Request

### Code Style
- Follow [Kotlin Coding Conventions](https://kotlinlang.org/docs/coding-conventions.html)
- Use meaningful variable and function names
- Add KDoc comments for public APIs
- Write unit tests for new features

## Known Issues

See `fix.md` in the repository for tracked issues and technical debt.

## License

This project is licensed under the **Apache License 2.0** — see the [LICENSE](LICENSE) file for details.

## Contact

For bug reports and feature requests, please open an issue on [GitHub Issues](https://github.com/purefoncare-creator/purefon/issues).
