#!/bin/sh
# Gradle wrapper script
exec /usr/bin/env bash -c '
GRADLE_VERSION="8.4"
WRAPPER_DIR="$HOME/.gradle/wrapper/dists/gradle-${GRADLE_VERSION}-all"
WRAPPER_JAR="$WRAPPER_DIR/gradle-${GRADLE_VERSION}-all/lib/gradle-wrapper-${GRADLE_VERSION}.jar"
if [ ! -f "$WRAPPER_JAR" ]; then
    echo "Downloading Gradle ${GRADLE_VERSION}..."
    mkdir -p "$WRAPPER_DIR"
    cd "$WRAPPER_DIR"
    curl -L -o gradle.zip "https://services.gradle.org/distributions/gradle-${GRADLE_VERSION}-all.zip"
    unzip -q gradle.zip
    rm gradle.zip
fi
exec java -cp "$WRAPPER_JAR" org.gradle.wrapper.GradleWrapperMain "$@"
'
