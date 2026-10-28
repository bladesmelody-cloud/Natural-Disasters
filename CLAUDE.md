# Natural Disasters — notes for Claude

Minecraft 26.1.2 · NeoForge 26.1.2.114 · Java 25 · ModDevGradle · mod ID `naturaldisasters`.
Design decisions live in `DESIGN.md`; keep it updated, quote the owner, mark
Claude-only proposals **MINE**.

## Working style
- Step by step; ask multiple-choice questions with the recommendation first,
  marked "(Recommended)".
- Mechanics first, visual polish later.
- Owner play-tests from the jar GitHub Actions builds on each push
  (Actions → Build → artifact `naturaldisasters-<sha>`), but prefers cloud
  verification (game tests, screenshots).

## Build & test
```
./gradlew build              # jar in build/libs/
./gradlew runGameTestServer  # all game tests; exits non-zero on failure
./gradlew runClient / runServer
```
Game tests live in `src/main/java/.../gametest/ModGameTests.java`: register the
function in `FUNCTIONS` and the test in `onRegisterTests`.

## Cloud container setup (not committed)
- Container JDK is 21; install Temurin 25 (Adoptium API) to `~/jdk/` and run
  Gradle with `JAVA_HOME=~/jdk/jdk-25*`; also set
  `org.gradle.java.installations.paths` in `~/.gradle/gradle.properties`.
- Maven Central answers HTTP 429 from the cloud network: create
  `~/.gradle/init.d/central-mirror.gradle` redirecting `repo.maven.apache.org` /
  `repo1.maven.org` to `https://maven-central.storage-download.googleapis.com/maven2/`
  (pluginManagement, buildscript and project repositories). Never commit it.
- First build decompiles Minecraft (~3–4 min).
- Screenshots: real client under Xvfb with Mesa software GL
  (`LIBGL_ALWAYS_SOFTWARE=1 xvfb-run`), joining a dev server, RCON to set scenes.
