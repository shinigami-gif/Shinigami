# Streamix Backend

Standalone backend and stream-level E2E test harness.

This snapshot excludes the Saikou reference app and unrelated research archives. It keeps the OCE infrastructure/providers and selected Hatsune CloudStream-native providers used by the current host.

## Included
- OCE BaseProvider
- OCE Anichin, Animasu, Animexin, Samehadaku
- Hatsune Alqanime, AnimeSail, Anoboy, Kuramanime, Kuronime, NontonAnimeID, Otakudesu
- Streamix runtime, routing, identity, API, and E2E tests
- Gradle wrapper

The backend runtime is pure Kotlin/JVM and does not depend on the Android framework or a CloudStream Android library. Provider execution is wired through the native JVM runtime in `streamix.runtime.NativeProviderHost`.

## Test
Run the JVM contract tests with:
./gradlew test --stacktrace

The live provider E2E suite remains opt-in:
./gradlew liveE2eTest --stacktrace
