# Projekt-Status: Luce

**Stand:** 7. November 2026  
**Ziel:** Native Android-App „Luce“ (Minimalist Display & LED Torch) gemäß `SPEC.md` kompilieren (`Luce.apk`).

---

## 1. Analyse der Vorgaben (`SPEC.md`)
- **App-Name & ID:** Luce (`com.luce.torch`)
- **Tech-Stack:** Kotlin, Jetpack Compose (Material 3), Min SDK 26, Target/Compile SDK 34.
- **Architektur:** Kompakte Single-File-Architektur in `MainActivity.kt`, keine externen Drittanbieter-Bibliotheken.
- **Funktionen:**
  - **Modus A (Display-Leuchte):** Vollbild-Farbe via HSV, vertikaler Wischgeste für Hardware-Helligkeit (`screenBrightness`), UI-Sperre (Lock-Button), kompakter Farb-Picker (Farbton- & Sättigungs-Slider, keine Wert/Helligkeits-Schieber), 4 speicherbare Presets mit haptischem Feedback.
  - **Modus B (Hardware-LED):** Doppeltipp wechselt zur Kamera-Taschenlampe (`CameraManager.setTorchMode`), Bildschirm wird OLED-Schwarz (`#000000`) bei minimaler Helligkeit (`0.01f`), zentrales 30%-graues Vektor-Taschenlampen-Symbol nach oben gerichtet mit Lichtkegel.
  - **Persistenz:** Speichern von Helligkeit, Farbe, Presets und Sperrstatus via `SharedPreferences`.
  - **App-Icon:** Minimalistisches adaptives Icon (`ic_launcher`) mit warm leuchtendem Lichtsymbol auf dunklem Grund (`#121212`).

---

## 2. Systemprüfung (Schritt 7.1 der Spezifikation)

| Komponente | Gefundener Status | Erforderlich / Bewertung |
|---|---|---|
| **Betriebssystem** | Linux (Fedora 44, x86_64) | Unterstützt |
| **Java / JDK** | `java-25-openjdk-headless` (nur JRE 25) | ❌ **Fehlt:** Es ist kein vollwertiges JDK (`javac` fehlt) vorhanden. Zudem erfordert Android Gradle Plugin (AGP) JDK 17 oder JDK 21 (Java 25 ist inkompatibel). |
| **Android SDK** | Nicht vorhanden (`$ANDROID_HOME` leer, kein `sdkmanager`) | ❌ **Fehlt:** Android Command-Line Tools, `platforms;android-34` und `build-tools;34.0.0` müssen lokal eingerichtet werden. |
| **Gradle** | Kein systemweites `gradle` installiert | ⚠️ Wird über den Gradle Wrapper (`gradlew`) direkt im Projekt bereitgestellt. |
| **Git-Repository** | Projektordner ist noch kein Git-Repo | ⚠️ `git init` wird beim ersten Schritt ausgeführt (gemäß Projektregel). |

---

## 3. Nächste Schritte

1. **JDK & Android SDK bereitstellen:**
   - Herunterladen und Bereitstellen eines kompatiblen JDKs (Eclipse Temurin JDK 17 oder 21 für Linux x86_64).
   - Herunterladen der Android Command-Line Tools und Installieren von `platform-tools`, `platforms;android-34` und `build-tools;34.0.0`.
   - Lizenzen akzeptieren (`sdkmanager --licenses`).
   - Erstellen der `local.properties` mit Pfad zum SDK (`sdk.dir=...`).
2. **Projektdateien generieren:**
   - `build.gradle.kts`, `settings.gradle.kts`, `app/build.gradle.kts`, `gradle.properties`, Gradle Wrapper.
   - `AndroidManifest.xml` und adaptive Icons.
   - `MainActivity.kt` mit der kompletten App-Logik und Compose-UI.
3. **Build & Bereitstellung:**
   - Kompilierung mit `./gradlew assembleDebug`.
   - Fertige APK als `Luce.apk` ins Hauptverzeichnis kopieren.
