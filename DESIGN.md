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

### Meteor (second disaster)
- **Shower + rare big impact**: usually a shower of small meteors over an area
  for a while; at high magnitude one giant meteor leaving a huge crater. Same
  1–10 magnitude scale.
- Aftermath: **crater + fire** (crater lined with scorched blocks such as magma,
  blackstone, obsidian; fires around the rim) and **meteorite ore** (a new
  block in the crater centre that drops a special material).
- **Real falling entity**: a fiery meteor streaks down with a trail and
  explodes on impact; players can see and dodge it (fancy model later).
- Material: **crafting ingredient, uses decided later**: Meteorite Ore drops
  Meteorite Chunk, which smelts to Meteoric Iron Ingot.
- Sizes: owner chose **"Bigger"** (~2x crater sizes and counts) over Claude's
  first proposal. Resulting table is **MINE**, configurable:

  | Magnitude | Area radius | Meteors | Crater radius | Big impact | Shower |
  |---|---|---|---|---|---|
  | M1–4 | 48–96 | 10–32 | 4–8 | – | 30 s |
  | M5–7 | 128 | 30–50 | 8–14 | – | 40 s |
  | M8–9 | 160 | 40–50 | 8–12 | radius ~32 | 50 s |
  | M10 | 192 | 40 | ~10 | radius ~64 | 60 s |

- **Blast damage + knockback**: explosion-like damage falling off with distance
  from the impact; a direct hit is usually lethal at big sizes.

- Implementation (**MINE**): meteors fly 3 s (giant 5 s) from ~120+ blocks up
  at an angle; the entity is only the visual (fire/smoke/lava particle trail,
  no model yet), the disaster's seeded schedule decides impacts so showers
  resume after restarts. Craters are bowls half as deep as wide, dug centre-out
  through the block budget; floor lined with magma/blackstone/obsidian
  (denser near the middle), meteorite ore in the middle (more in bigger
  craters), a lava pool in craters of radius 24+, fire around the rim. Blast:
  damage up to 2 + 1.5 × crater radius, reach 1.5 × radius, knockback.
  Warning: faint thunder and shooting-star particles. Meteorite ore needs an
  iron pickaxe; ore and chunk smelt/blast to the ingot.

### Tornado (third disaster) — freestyled by Claude, all **MINE**
Owner (going to bed): "just go freestyle on the other disasters, and we can go
over the numbers and magnitude later. Look at weather mods in minecraft as some
have done tornados pretty well".
- Ideas taken from weather mods: Weather2 (Corosus) draws its funnel and
  debris with particles for performance; tornado addons tie block breaking
  to EF-scale wind speed vs per-material thresholds (leaves 75, planks 95,
  logs 103, cobble 120, stone 144 mph); "Realistic Tornado Disasters" spins
  falling-block debris around the funnel and flings it out. Their lag comes
  from uncapped debris entities and chunk loading, so here debris is capped
  and everything goes through the block budget.
- EF scale from magnitude: M1–2 EF0, M3–4 EF1, M5–6 EF2, M7 EF3, M8–9 EF4,
  M10 EF5. Core radius 3/5/7/10/14/20, peak wind 80/110/135/165/200/250 mph,
  lifetime 30/45/60/80/100/120 s, speed 5→3 blocks/s, wanders up to
  48 + 24·EF blocks from where it touched down.
- Wind falls off outside the core (∝ (core/d)^1.5, felt to 1.8 cores). Each
  tick random columns in reach are stripped top-down while the wind beats
  each block's resistance (shared `Wind` table: plants/leaves 45, glass 60,
  wool/hay 70, wood blocks 95, logs 105, snow/ice 110, dirt/sand/gravel 140,
  cobble/bricks/terracotta 175, else 120 + 60 × hardness so stone ≈ 210; block
  entities never). So EF0 strips leaves and plants, EF1–2 wreck wooden
  buildings and trees, EF3+ scour the ground to stone, EF5 scours stone.
- Torn blocks: up to 8 falling-block debris per second (spinning out, hurting
  what they hit), 25 % of the rest thrown 8–30+ blocks ahead as rubble (sinks
  if it lands in water), the rest destroyed.
- Creatures, items and debris within reach are pulled in, spun, lifted inside
  the core (updraft beats gravity) and flung out near the top; creative
  fliers and spectators are left alone.
- Storm weather (rain; thunder from EF1) for the warning and the tornado.
  Wind roar = elytra sound pitched down. Funnel = particle cone (dark smoke,
  cloud, dirt at the base, wall cloud on top). **Visual pass later**: custom
  big funnel particles, real wind sound.

### Flood — freestyled, all **MINE**
- Water rises over the lowest ground near the middle (lowest of 48 sampled
  columns) by 2–18 blocks (M1–M10) across radius 24–224, sloping down over
  the outer quarter so there's no wall of water. Rises over 20 s + 8 s/block,
  holds 30 s + 2 s/block, drains over 30 s + 5 s/block. Rain throughout.
- Water drowns plants and fills ground floors; draining leaves 25 % of the
  soaked grass/dirt as mud. Only water the flood placed is drained
  (`WaterTracker`, one y-range per column), and that water is placed and
  removed *quietly* (no flowing): flowing flood water would make permanent
  new sources. Grass under the flood turns to dirt at once for the same reason
  (its random tick would wake the water).
- Random floods only where it rains (not deserts or snow), weight 0.8.

### Tsunami — freestyled, all **MINE**
- Comes from the side with the most open water near the strike (16
  directions sampled); random tsunamis only near an ocean, weight 0.5.
- Wave height 3–22 blocks above sea level (M1–M10), front 49–214 wide, runs
  24 blocks of sea then 48–200 blocks inland, losing height linearly inland;
  speed 10–19 blocks/s (but never faster than the block budget).
- At the front it smashes blocks weaker than its force (Wind table, force =
  70 + 8 × height mph: M5 wrecks wooden buildings, M10 strips cobble), fills
  with quiet water, sweeps creatures along with some drowning damage and
  splash particles; 10 rows behind the front it drains, leaving sand on 30 % of
  the land it covered and puddles in 8 % of spots.

### Hurricane — freestyled, all **MINE**
- Saffir-Simpson from magnitude: M1–2 tropical storm (60 mph), M3–4 cat 1
  (85), M5 cat 2 (100), M6 cat 3 (120), M7–8 cat 4 (145), M9–10 cat 5 (170).
  Radius 128 + 32·M (M10: 448), lasts 90 + 25·M s, calm eye of radius 12 + 2·M
  drifting 1.3–2 blocks/s so it passes over the strike point halfway through.
- Wind: calm in the eye, full in the eyewall (up to 2.5 eye radii), easing
  off ∝ (eyewall/d)^0.6. Sparse random columns over the whole storm lose up to
  two weak top blocks (Wind table, 70–100 % of local wind) — roofs, leaves,
  crops, fences over a huge area rather than a tornado's total wreck.
- Creatures under open sky are pushed around the storm (counter-clockwise,
  slightly inward); indoors is safe. Lightning about every 2 s at M1 down to
  every 0.5 s at M10. Thunderstorm weather (rain only for tropical storms).
- Storm surge 1–5 blocks: low land within 6 blocks of open water and the
  water itself rise (quiet tracked water), drained once the storm passes.
- Random hurricanes only near an ocean, weight 0.6.

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
- Building damage (owner chose **"More building damage"** after the first
  screenshots): from M5 an extra shaking pass hits the top 8 blocks; wood
  (planks, stairs, slabs, fences, trapdoors), wool, terracotta, bricks, mud
  bricks and hay are knocked loose and fall as rubble with chance 30 % at M6,
  60 % at M8, 90 % at M10 (logs half that, so trees mostly survive). Blocks over
  air whose side neighbours don't rest on anything (roof middles, cave
  ceilings) fall with chance 15 % at M4 rising to 100 % at M10, so roofs cave in
  from the middle.
- Warning: low distant boom + ground particles at players' feet every 2 s.
- **Deferred to visual polish**: client camera shake, custom sounds.
- Measured (dev server, 400 chunks loaded, no players): M9 quake (radius 160)
  averaged 8–10 ms/tick, P99 ≤ 26 ms, vs 5 ms idle; M7 on a village peaked at
  P99 16 ms.

## Open questions
- Owner said to freestyle the rest; review all **MINE** numbers together.
- Should disasters happen in the Nether / End, and how?
- After the building-damage pass an M7 quake leaves village houses holed with
  rubble around them but still recognisable; higher magnitudes hit harder.
  Owner to judge from play-testing.
