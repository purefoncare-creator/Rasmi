# Contributing to Purefon

Thank you for your interest in contributing to Purefon! This document provides guidelines and information for contributors.

## Getting Started

### Prerequisites

- Android Studio Ladybug (2024.2+) or later
- JDK 17+
- Android SDK 36
- Physical Android device (for testing SMS/call features)

### Setup

1. Fork the repository on GitHub
2. Clone your fork:
   ```bash
   git clone https://github.com/your-username/purefon.git
   cd purefon
   ```
3. Open the project in Android Studio
4. Sync Gradle and build the project
5. Run on a connected device or emulator

## Development Guidelines

### Code Style

- Follow [Kotlin Coding Conventions](https://kotlinlang.org/docs/coding-conventions.html)
- Use meaningful variable and function names
- Add KDoc comments for public APIs
- Keep functions focused and under 50 lines when possible

### Architecture

Purefon uses **Clean Architecture** with MVVM pattern:

- **Domain Layer** (`domain/`) — Business logic, use cases, repository interfaces
- **Data Layer** (`data/`) — Repository implementations, database, network
- **Presentation Layer** (`presentation/`) — Compose UI, ViewModels

### File Organization

```
com.rasmi.purevon/
├── data/           # Data sources, repositories, mappers
├── domain/         # Business logic, models, use cases
├── presentation/   # UI components, screens, viewmodels
├── service/        # Android services
├── receiver/       # Broadcast receivers
├── worker/         # WorkManager workers
├── di/             # Hilt dependency injection
└── util/           # Utility classes
```

### Adding New Features

1. Create domain model in `domain/model/`
2. Define repository interface in `domain/repository/`
3. Implement use case in `domain/usecase/`
4. Implement repository in `data/repository/`
5. Create ViewModel in `presentation/viewmodel/`
6. Create Compose screen in `presentation/screen/`
7. Add navigation route in `presentation/navigation/`
8. Add Hilt module if needed in `di/`

### Database Changes

When modifying Room entities:

1. Update the entity class in `data/local/entity/`
2. Update the DAO in `data/local/dao/`
3. Increment the database version in `data/local/PurevonDatabase.kt`
4. Create a migration in `data/local/DatabaseMigrations.kt`
5. Add the migration to the database builder
6. Export the schema (automatic with KSP)

### Testing

- Write unit tests for use cases and repositories
- Write UI tests for critical user flows
- Test on real devices when possible
- Run `./gradlew test` before submitting

### Commit Messages

Use clear, descriptive commit messages:

```
feat: Add message scheduling support
fix: Fix MMS attachment upload crash
refactor: Extract call recording logic
docs: Update README with new features
test: Add unit tests for SpamFilter
```

## Pull Request Process

1. **Create a feature branch** from `main`:
   ```bash
   git checkout -b feature/your-feature-name
   ```

2. **Make your changes** with clear commits

3. **Test thoroughly** — run the app on a real device

4. **Update documentation** if you're adding user-facing features

5. **Submit your PR** with:
   - Clear title and description
   - Reference any related issues
   - Screenshots/recordings for UI changes

6. **Respond to review feedback** promptly

## Reporting Issues

### Bug Reports

Include:
- Device model and Android version
- Steps to reproduce
- Expected vs actual behavior
- Screenshots if applicable
- Logcat output if available

### Feature Requests

Include:
- Clear description of the feature
- Use case / why it's needed
- Mockups if applicable

## Code of Conduct

- Be respectful and inclusive
- Welcome newcomers and help them get started
- Focus on constructive feedback
- Assume good intent

## Questions?

Open a [GitHub Discussion](https://github.com/purefoncare-creator/purefon/discussions) or comment on an existing issue.
