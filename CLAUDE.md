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
`./gradlew runGameTestServer -Ptests=flood,tsunami` runs only tests whose names
contain one of those words. Game tests live in `src/main/java/.../gametest/ModGameTests.java`: register the
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
- Screenshots: `run/server.properties` with `online-mode=false`,
  `enable-rcon=true`, `rcon.password=...`, `pause-when-empty-seconds=-1`
  (otherwise an empty server pauses and disasters don't run), `run/eula.txt`.
  Start `./gradlew runServer`, then `Xvfb :99 &` and
  `DISPLAY=:99 LIBGL_ALWAYS_SOFTWARE=1 ./gradlew runClient -PjoinServer=localhost:25565`
  (joins as `Tester`). Set scenes over RCON (`gamemode spectator Tester`,
  `tp Tester ... facing ...`, `forceload add ...`), grab frames with
  `DISPLAY=:99 import -window root shot.png`. Measure lag with `tick query`.
  Put `onboardAccessibility:false` in `run/options.txt` to skip the first-run screen.

## Code map
- `engine/`: `BlockWriter` (budgeted block edits; `setQuiet` for water/lava a
  disaster places itself), `Disaster` (base class, warning → active,
  save/load, `keepWeather`), `DisasterType(s)` (registry, weights, `where`
  placement rules), `DisasterData` (per-dimension saved data),
  `DisasterManager` (tick loop, adaptive budget, fair time slices),
  `RandomDisasters` (scheduler).
- `disaster/`: one class per disaster (Earthquake, Meteor, Tornado,
  Hurricane, AcidRain, Drought, Flood, Tsunami, Volcano, Wildfire, Blizzard,
  Hailstorm), each with a `Params` record (`forMagnitude`, saved with it) and a
  `create(...)` for tests. Shared: `Terrain` (columns, heights, discs), `Wind`
  (block resistance in mph), `WaterTracker` (water a disaster placed, to drain
  exactly), `Places` (climate checks for random picks).
- `sky/`: `SkyZones` (storm/ash circles a disaster publishes, server + client
  copy, synced by `SkyZonesPayload`), `SkyLayers` (environment-attribute
  layers: sky/fog/cloud colour, fog distance, light, stars, `MONSTERS_BURN`),
  `SkyEvents` (payloads, sync, ash on chunk load, no sleeping through
  world-wide ash). `ash/AshLayerBlock`. `disaster/AshCloud` is the
  long-lived event a big eruption leaves; `disaster/Builds` scores how
  built-up a spot is (random disasters avoid builds).
- `shake/`: `ShakePayload` + `Shake.near(...)` (server sends camera shake);
  `client/ClientShake` applies it; `ClientConfig` (client toggles).
- `mixin/` (`naturaldisasters.mixins.json`): `EnvironmentAttributeSystemMixin`
  (adds `SkyLayers`), `MonsterMixin` (ash counts as night for spawning),
  client `BiomePrecipitationMixin` (snow in blizzard zones). 26.1 is
  unobfuscated, so no refmap is needed.
- `client/`: `ClientAsh` (zone payload, ash particles), `ClientStorms` +
  `CloudParticle` (tornado funnel, hurricane bands), `MeteorRenderer`,
  `ClientSetup` (renderers, particle sprites).
- `entity/`: `MeteorEntity`, `TornadoEntity`, `HurricaneEntity` (eye marker) —
  visual only, effects on the client, never saved. `registry/ModRegistry`
  (meteorite and ash blocks/items, entities, cloud particle), `command/DisasterCommand`,
  `Config` (server config: budget, random timing, per-type toggles).

## Gotchas learned
- Water/lava a disaster places must be set *and removed* with `setQuiet`, and
  nothing next to it may wake fluids (strip/smash with `setQuiet` too):
  woken quiet water flows and forms permanent new sources.
- Work queues that are filled while being processed (heat wave, ash cloud)
  must track the chunk in progress explicitly; a per-chunk column cursor
  otherwise ends up applied to a different chunk.
- Game tests share one world: keep each test's effects inside its 40×40
  arena (small radii, tornado `throwDistance`/`debrisPerSecond` params), and
  use the `arena(...)` TestData (sky access, else a barrier roof).
- On world day 1 the "day" timeline has no earlier keyframe, so
  `MONSTERS_BURN` is false until the first dawn; tests sample at day-2 noon.
- Don't put doubles that change every tick into a `SkyZones.Zone`: any change
  re-sends the zone list to every player.
