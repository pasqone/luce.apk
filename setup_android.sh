#!/usr/bin/env bash
set -euo pipefail

echo "=== 1. Vorbereiten der Verzeichnisse ==="
mkdir -p /home/pasq/.jdks/temurin-21
mkdir -p /home/pasq/Android/Sdk/cmdline-tools

echo "=== 2. Download & Installation: JDK 21 ==="
if [ ! -f /home/pasq/.jdks/temurin-21/bin/javac ]; then
    echo "Lade JDK 21 herunter..."
    JDK_URL="https://github.com/adoptium/temurin21-binaries/releases/download/jdk-21.0.6+7/OpenJDK21U-jdk_x64_linux_hotspot_21.0.6_7.tar.gz"
    if ! curl -fSL -o /tmp/jdk21.tar.gz "$JDK_URL"; then
        echo "Adoptium Download fehlgeschlagen, versuche Azul Zulu..."
        curl -fSL -o /tmp/jdk21.tar.gz "https://cdn.azul.com/zulu/bin/zulu21.40.17-ca-jdk21.0.6-linux_x64.tar.gz"
    fi
    echo "Entpacke JDK 21..."
    tar -xzf /tmp/jdk21.tar.gz -C /home/pasq/.jdks/temurin-21 --strip-components=1
    rm -f /tmp/jdk21.tar.gz
else
    echo "JDK 21 bereits vorhanden."
fi

export JAVA_HOME="/home/pasq/.jdks/temurin-21"
export PATH="$JAVA_HOME/bin:$PATH"
java -version
javac -version

echo "=== 3. Download & Installation: Android Command-line Tools ==="
if [ ! -f /home/pasq/Android/Sdk/cmdline-tools/latest/bin/sdkmanager ]; then
    echo "Lade Android Command-line Tools herunter..."
    curl -fSL -o /tmp/cmdline-tools.zip "https://dl.google.com/android/repository/commandlinetools-linux-11076708_latest.zip"
    echo "Entpacke Android Command-line Tools..."
    rm -rf /tmp/cmdline-tools-extracted
    unzip -q /tmp/cmdline-tools.zip -d /tmp/cmdline-tools-extracted
    rm -rf /home/pasq/Android/Sdk/cmdline-tools/latest
    mv /tmp/cmdline-tools-extracted/cmdline-tools /home/pasq/Android/Sdk/cmdline-tools/latest
    rm -rf /tmp/cmdline-tools-extracted /tmp/cmdline-tools.zip
else
    echo "Android Command-line Tools bereits vorhanden."
fi

SDKMANAGER="/home/pasq/Android/Sdk/cmdline-tools/latest/bin/sdkmanager"
ANDROID_SDK="/home/pasq/Android/Sdk"

echo "=== 4. Android SDK Lizenzen akzeptieren ==="
yes | "$SDKMANAGER" --sdk_root="$ANDROID_SDK" --licenses || true

echo "=== 5. Pakete installieren: platform-tools, platforms;android-34, build-tools;34.0.0 ==="
"$SDKMANAGER" --sdk_root="$ANDROID_SDK" "platform-tools" "platforms;android-34" "build-tools;34.0.0"

echo "=== 6. Erstelle local.properties ==="
echo "sdk.dir=/home/pasq/Android/Sdk" > /home/pasq/ai/projects/luce/local.properties

echo "=== Fertig! Android SDK und JDK 21 erfolgreich eingerichtet. ==="
