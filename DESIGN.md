# Natural Disasters — Design

Living record of design decisions. Owner's own words are quoted. Anything that is
only Claude's proposal (not yet confirmed by the owner) is marked **MINE**.

## Overview

A brand-new Minecraft mod about natural disasters. Separate from the owner's other
mods (Ooze Gal-Ore, Golemancy).

## Decisions

### Scope
- Which disasters: "All the disasters, but we need a way to have them happen, be
  massive, and not cause massive lag"
- How many: first answered **4–6 classics**, then picked a longer list:
  "Hurricane, Acid Rain, Drought", plus Earthquake, Tornado, Meteor,
  Flood / tsunami, Volcano / wildfire, Blizzard / hailstorm.
- Pairs are **split**: 12 separate disasters:
  1. Earthquake  2. Tornado  3. Meteor  4. Hurricane  5. Acid Rain  6. Drought
  7. Flood  8. Tsunami  9. Volcano  10. Wildfire  11. Blizzard  12. Hailstorm
- **Earthquake is built first**, on top of the shared engine (block budget,
  random scheduler, warnings, `/disaster` command).
- Size: **varies a lot per disaster** (e.g. meteor small, hurricane huge);
  sizes set per disaster.
- Build order: **Earthquake → Meteor → Tornado**, then decide the rest.

### Random timing
- **Rare**: about once a week in-game (configurable).
- Warning: **sound + subtle hint**: rumbling/wind sound and small particles
  ~20 s before it hits (~20 s is **MINE**); no chat text.

### Earthquake (first disaster)
- Effects — all four chosen:
  - **Fissures + collapse**: long deep cracks open; unsupported blocks (towers,
    cave ceilings) fall in.
  - **Shaking damage**: random blocks crack/break (glass shatters, leaves fall,
    stone → cobble/gravel).
  - **Ground heave**: sections of ground shift up/down a few blocks.
  - **Entity effects**: players/mobs knocked around, camera shake.
- **Magnitude scale**: each quake rolls a magnitude; higher = bigger radius,
  deeper fissures, longer shaking. Small common, huge rare.
- Numbers: owner chose **"Bigger overall"** (~2x) over Claude's first proposal.
  Resulting table is **MINE**, all configurable:

  | Magnitude | Radius | Shaking | Fissures |
  |---|---|---|---|
  | M1–3 | ~32 | ~20 s | small cracks |
  | M4–6 | ~80 | ~40 s | ~30 deep |
  | M7–9 | ~160 | ~80 s | near bedrock + heave |
  | M10 (very rare) | ~256 | ~120 s | everything |

- Collapse: **mostly instant, few falling entities**: most blocks move/break
  directly via the block budget; only a capped number become vanilla
  falling-block entities for the visual.
- Big quakes **only affect loaded chunks**; damage stops at the loaded edge (no
  force-loading).

### Location
- Disasters strike **near a random online player** (within some distance), so
  they are seen and unloaded chunks aren't touched.

### Identity & stack
- Mod ID `naturaldisasters`, display name **Natural Disasters**.
- Minecraft 26.1.2, NeoForge 26.1.2.114, Java 25; jar built by GitHub Actions on
  each push (same as the owner's other mods).

### Core requirement: massive but low-lag
- Owner: "be massive, and not cause massive lag". This is a top-level technical
  goal for the shared disaster system, not per-disaster.
- Strategy: **per-tick block budget**. Every disaster queues its block changes;
  a shared scheduler applies a capped, configurable number per tick, with
  batched network updates, so huge effects spread over seconds instead of
  freezing the server.

### Triggering
- **Random + commands**: disasters happen naturally at random (configurable
  chance, with a warning first), and `/disaster` commands start them on purpose.

### Destruction
- **Fully destructive**: no safety net; builds can be wrecked.

## Open questions
- Rest of the build order after Meteor and Tornado.
