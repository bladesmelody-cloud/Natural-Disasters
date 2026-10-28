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
- Default budget: **adaptive, ~2000 block changes/tick max**, automatically cut
  back when server tick time goes over ~40 ms (numbers **MINE**, configurable).

### Commands (v1)
- `/disaster start <type> [magnitude] [player|position]`, `/disaster stop`,
  `/disaster list` (active ones), `/disaster random on|off`. Ops only.

### Persistence
- **Resume after restart**: active disasters (including their queued block
  changes) and the random timer are saved with the world.

### Triggering
- **Random + commands**: disasters happen naturally at random (configurable
  chance, with a warning first), and `/disaster` commands start them on purpose.

### Destruction
- **Fully destructive**: no safety net; builds can be wrecked.

## Implementation notes (v0.1 engine + Earthquake)
Numbers/choices below are **MINE** unless quoted; all tunable.
- Disasters *pull* work: each tick every active disaster gets an even share of
  the block budget and resumes from saved cursors, so nothing huge is queued in
  memory and only cursors/seed are saved. Shapes (fissure paths, heave
  patches) are rebuilt from the seed.
- Block changes skip neighbor updates (main lag source); nearby water/lava and
  sand/gravel above a removed block are woken so water still pours into
  fissures. Bedrock/unbreakable blocks and unloaded chunks are never touched.
- Hard cap of 15 ms of disaster work per tick, on top of the adaptive budget.
- `/disaster start` hits immediately (no warning); `/disaster random now`
  triggers a random disaster with the normal warning (for testing).
- Random disasters land 16–64 blocks from the chosen player; timer only counts
  while players are online; magnitudes rolled with each step ~0.6x as likely
  as the one below (M1 ≈ 40 %, M10 ≈ 0.4 %).
- Earthquake details: shaking damage reaches 12 blocks below the surface;
  glass/panes shatter, leaves fall (50 %), stone/deepslate/bricks crack,
  cobblestone → gravel (30 %). Collapse looks 24 blocks down for blocks with air
  below and < 2 side neighbours; surface edges over a drop crumble in. At most
  8 falling-block entities per second per quake, the rest move instantly.
  Heave moves the top 12 blocks of each column. Fissures open during the first
  60 % of the shaking. Knockback = 0.02 × magnitude every 5 ticks.
- Random disasters skip players in dimensions with a ceiling (the Nether) for
  now; `/disaster start` there still works but treats the roof as surface.
- Warning: low distant boom + ground particles at players' feet every 2 s.
- **Deferred to visual polish**: client camera shake, custom sounds.
- Measured (dev server, 400 chunks loaded, no players): M9 quake (radius 160)
  averaged 8–10 ms/tick, P99 ≤ 26 ms, vs 5 ms idle; M7 on a village peaked at
  P99 16 ms.

## Open questions
- Rest of the build order after Meteor and Tornado.
- Should disasters happen in the Nether / End, and how?
- First screenshots: an M7 quake on a village is visible but fairly subtle
  (fissures, buckled paths, water pouring in); houses mostly stand. Tune up?
