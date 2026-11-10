@echo off
set GRADLE_VERSION=8.4
set WRAPPER_DIR=%USERPROFILE%\.gradle\wrapper\dists\gradle-%GRADLE_VERSION%-all
set WRAPPER_JAR=%WRAPPER_DIR%\gradle-%GRADLE_VERSION%-all\lib\gradle-wrapper-%GRADLE_VERSION%.jar
if not exist "%WRAPPER_JAR%" (
    echo Downloading Gradle %GRADLE_VERSION%...
    mkdir "%WRAPPER_DIR%"
    cd /d "%WRAPPER_DIR%"
    curl -L -o gradle.zip "https://services.gradle.org/distributions/gradle-%GRADLE_VERSION%-all.zip"
    powershell -Command "Expand-Archive -Force gradle.zip"
    del gradle.zip
)
java -cp "%WRAPPER_JAR%" org.gradle.wrapper.GradleWrapperMain %*
