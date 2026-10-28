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
`./gradlew runGameTestServer -PwithClaims` also loads Open Parties and Claims
and Flan (adds their tests). `-Ptests=flood,tsunami` runs only tests whose names
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
- `instrument/`: `Instrument` (seismograph/barometer/sky watcher, which
  disasters each senses, range), `InstrumentBlock` (redstone power),
  `InstrumentData` (saved positions per dimension), `Instruments` (once-a-second
  sensing, chat, alarm; `anyInRange` doubles a new disaster's warning in
  `DisasterManager.add`).
- `protect/`: `Protection` (is a block protected: OPAC/Flan claims and safe
  zones, cached per chunk per tick; `spares(entity)`; debris crumbling),
  `SafeZones` (saved, `/disaster safezone`), `OpacClaims`/`FlanClaims` (only
  touched when that mod is loaded). Claim mods are compileOnly from the
  Modrinth maven; `-PwithClaims` loads them in dev runs (`gametest/ClaimTests`).
- `undo/`: `UndoLog` (one disaster's changes: before/after per spot,
  block entity data, which items each broken container dropped), `Undo`
  (last N records, files in `<world>/naturaldisasters/undo/`, player edits,
  item tagging, debris landings), `UndoTask` (restores chunk by chunk).
  `BlockWriter` records into the disaster's log (`DisasterManager` passes
  it); other code that removes blocks must call `w.recordFall(...)`.
- `advancement/`: `SurvivedTrigger` (`naturaldisasters:survived`, type +
  min_magnitude), `EventTrigger` (`naturaldisasters:event`), `ModTriggers`
  (registration, `disasterSurvived`, death listener). `Disaster` tracks
  witnesses (`caughtIn`). Advancement JSONs in `data/naturaldisasters/advancement`.
- `registry/ModSounds` + `assets/.../sounds.json`: disaster sounds made from
  vanilla files (pitch is clamped to 0.5–2.0).
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
- `Protection` caches answers per tick: anything that changes protection
  (e.g. adding a safe zone) must call `Protection.invalidate()`.
- Payloads are optional (vanilla clients can join): check
  `player.connection.hasChannel(TYPE)` before sending, or it throws.
- Game tests with mock players go in the `with_players` environment (own
  batch) and must remove the players at the end: disasters react to
  players and slow the shared test world down otherwise.
- Don't put doubles that change every tick into a `SkyZones.Zone`: any change
  re-sends the zone list to every player.
