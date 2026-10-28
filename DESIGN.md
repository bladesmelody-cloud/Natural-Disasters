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
- Owner: "all disasters can count while players are offline" — the random
  timer (and the great-eruption timer) count **server uptime**; if a disaster
  comes due with nobody online it waits for the next player.
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
  M10 EF5. Peak wind 80/110/135/165/200/250 mph, speed 5→3 blocks/s, wanders
  up to 48 + 24·EF blocks from where it touched down.
- Sizes reviewed: owner chose **"Bigger"** cores and **"Longer-lived"**: core
  radius 5/9/12/18/25/35 (was 3/5/7/10/14/20), lifetime 60/90/120/160/200/240 s
  (was 30…120 s). Exact numbers **MINE**.
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
- Numbers reviewed: owner **"Keep"**.

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

- Numbers reviewed (tsunami): owner **"Keep"**.

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
  slightly inward); indoors is safe. Lightning bolts are visual-only with
  their damage (5 + burning) applied directly: real bolts light block fires
  that wake the surge's quiet water (**MINE**). Sizes reviewed: owner **"Keep"** for
  size/strength, **"Stronger push"** for the wind on creatures: push every
  2 ticks (was 5) and gusts over 100 mph lift creatures off their feet
  (10 % per push) — exact numbers **MINE**. Lightning about every 2 s at M1 down to
  every 0.5 s at M10. Thunderstorm weather (rain only for tropical storms).
- Storm surge 1–5 blocks: low land within 6 blocks of open water floods
  (quiet tracked water), drained once the storm passes. Open water itself
  isn't raised (column by column it looked like a bumpy frozen sea).
- Random hurricanes only near an ocean, weight 0.6.

### Volcano — freestyled, all **MINE**
- Cone height 8–72 (M1–M10), base radius 2.2 × height, crater radius height/5
  (min 2), crater floor a quarter of the height below the summit. The cone
  rises centre-out over 20 s + height/2 s with tremors; creatures caught are
  lifted onto the new surface. Rock: stone with blackstone inside, surface
  basalt/blackstone/tuff.
- Then a lava lake fills the crater and it erupts for 30 + 10·M s: 2 + M/2
  lava rivers creep 1 block every 4 ticks down the steepest slope (never
  uphill) for up to 1.3 × base radius, lighting plants and wood they pass;
  volcanic bombs (meteor visual) every 22 − 2·M ticks land within 1.5 × base
  radius in a small magma/blackstone splash with fire, 6 damage and burning;
  ash column (signal smoke + lava sparks) and falling ash around players.
- Afterwards the rivers cool to basalt (20 % magma); the mountain and crater
  lake stay. Lava is placed quietly so it stays where it's put.
- Random volcanoes anywhere with a sky, weight 0.3 (rarest).

- **Rework requested** (owner, numbers review): "Eruption has stages. Use VEI
  scaling. VEI 3+ has minor earth quake nearby, followed by a wave of intense
  heat and ash that massively burns all life and covers a large area around
  the volcano in ash as well as lava streams. perpetual night in the entire
  area for 1000's of blocks for days to weeks. Everything past 3 gets worse by
  10x the amount. And should be very rare. Anything 5+ will make perpetual
  night for the entire world for weeks (all minecraft days)". After an
  eruption: **dormant landmark** (owner's pick).
- Settled with the owner:
  - Magnitude → VEI: M1–2 VEI0, M3 VEI1, M4 VEI2, M5 VEI3, M6 VEI4, M7 VEI5,
    M8 VEI6, M9 VEI7, M10 VEI8.
  - The 10× applies to **ash area, darkness duration, ash depth and heat-wave
    reach**, using this table (proposed by Claude, owner: "Use this table"):

    | VEI | Heat wave | Ash + darkness | Ash depth | Darkness |
    |---|---|---|---|---|
    | 0–2 | – | – | – | – (cone + lava only, as before) |
    | 3 | 64 blocks | radius 1,000 | 1 layer | 2 days |
    | 4 | 200 | radius 3,200 | 3 layers | 20 days |
    | 5 | 640 | whole world | 6 layers near, fading to 1 | 4 weeks |
    | 6 | 2,000 | whole world | 1 block near | 6 weeks |
    | 7 | 6,400 | whole world | 2 blocks near | 8 weeks |
    | 8 | all loaded | whole world | 3 blocks near | 12 weeks |

    (days = Minecraft days.)
  - Stages for VEI 3+: rumble + **minor earthquake** nearby → eruption with
    the **heat-and-ash wave** → **ash fall + lava streams** → **ash darkness**.
  - Heat wave: **kills creatures in the open** (sheltered ones take heavy fire
    damage) and **burns vegetation** (charred trunks, leaves/plants/crops gone,
    grass → coarse dirt). Wooden builds are *not* ignited, water/snow not
    touched (owner didn't pick those).
  - Local darkness: **"Ash sky + real darkness"** — black ash sky and fog for
    players in the area, and the area counts as night for light levels (mobs
    spawn, crops stall).
  - World-wide darkness (VEI5+): **beds don't skip it**.
  - Rarity: "VEI5+ about once per 1–2 real years of play", each step past 3
    rarer. The two clash (with 10× steps a VEI5 would take 3,000+ hours), so
    owner chose a **separate great-eruption timer**: normal random volcanoes
    roll VEI 0–4 (VEI4 10× rarer than VEI3); a saved timer triggers one VEI5+
    about every **800 Minecraft days** (owner's pick; randomized 400–1,200;
    config `greatEruptionDays`), VEI6/7/8 each 10× rarer again. Owner: "should
    be server up time, not only player online" — the timer counts uptime and,
    if due with nobody online, waits for a player.
- Implementation (**MINE** where not above): VEI 3+ starts with an earthquake
  (M3 for VEI3, M4 for VEI4, M5 for VEI5+) at the vent as the cone rises.
  When it erupts, the heat wave rolls out at 1.5 blocks/tick (+0.5 per VEI
  above 3) to its reach (stops past every player's loaded
  area); creatures under the open sky in its front die (new
  `naturaldisasters:pyroclastic` damage), sheltered ones take 8 damage and
  burn 10 s; vegetation in reach: leaves/plants/crops gone, trunks → charred
  (stripped dark oak), grass/podzol/mycelium/moss → coarse dirt; buildings,
  water and snow untouched. A rolling smoke cloud marks the front.
- The eruption leaves an **ash cloud** (its own long-lived event, listed in
  `/disaster list`, saved across restarts): ash settles on every chunk in its
  area — loaded ones now and any loaded later while it lasts — as the new
  **Volcanic Ash** layer block (never melts; shovel drops one per layer) and
  **Block of Volcanic Ash** (falls like sand); full depth out to the heat
  reach (max 2,000), thinning to 1 layer at the edge (world-wide: at 10× the
  reach, 1 layer beyond). Under the cloud monsters spawn as at night and
  don't burn in the sun (mixins), and players see near-black fog (thicker for
  bigger VEI) with ash drifting down. World-wide clouds stop the Overworld
  clock at midnight and restart it at morning when they end; sleeping
  doesn't skip it (message: "The ash cloud blots out the sun. There is no
  morning."). Config `worldDarkness=false` turns VEI 5+ into a 10,000-block
  dark area instead. `/disaster random eruption` triggers a great eruption.

### Wildfire — freestyled, all **MINE**
- 1 + M/2 ignition points within a third of radius 32–256; burns for
  60 + 20·M s with one wind direction. Each burning spot waits 1 s, then can
  light fuel up to 1 block away (2 downwind, embers), much likelier downwind,
  then burns out: logs gone or left as charred (stripped dark oak) trunks,
  leaves/planks/wool/hay/crops/plants gone, grass → coarse dirt.
- Spreads half as fast where it's raining.
- Numbers reviewed: owner **"Smaller area"**: radius now 16–128 (half of
  the first 32–256).
- Owner: **"Grass burns, but slowly"**: grass blocks, grass, ferns, bushes and
  flowers carry fire at a third of the chance of woods and wooden builds.
- Own spread (works with fire tick off, stays inside the radius, capped at
  1500 + 500·M burning spots); vanilla fire may add to it. Smoke and crackle.
- Random wildfires in forests, taigas, savannas, jungles or hot places,
  weight 0.8.
- Engine note: the block writer now wakes fluids only when a block is
  removed to air, so fire next to quiet lava or flood water doesn't set it
  flowing.

### Blizzard — freestyled, all **MINE**
- Radius 48 + 24·M, lasts 60 + 20·M s, buries the ground in snow up to
  2/4/6/8/10/12/14/16/20/24 layers (M1–M10; 8 layers = 1 block, so M10 is three
  blocks deep), building up evenly over the storm. Open water freezes to ice,
  fires go out. Creatures under the sky freeze like in powder snow (vanilla
  freeze damage and frost overlay; leather armour still protects), faster at
  higher magnitude. Dense snowflake whiteout around players, wind sound.
  Snow stays after the storm (melts the vanilla way, i.e. only near light).
- Random blizzards only in cold places, weight 0.8.
- Numbers reviewed: owner **"Longer storms"**: 2× duration (now 160–520 s),
  same snow depth reached more slowly.

### Hailstorm — freestyled, all **MINE**
- Radius 48 + 16·M, lasts 40 + 10·M s, hailstone size 1–5 (M1–2 … M9–10).
  Thunderstorm weather. Hail hits the top of random columns: glass shatters
  (15 % × size per hit), leaves shredded, crops/flowers/grass/saplings/turtle
  eggs flattened, and 3 % of solid tops get a dusting (1 snow layer).
- Creatures under the open sky: every 0.5 s a 35–75 % chance of `size` damage
  from the new `naturaldisasters:hail` damage type (armour soaks it; a roof
  stops it). Snowball-crumb particles and clatter sounds near players.
- Random hailstorms where it rains, weight 0.8.
- Numbers reviewed: owner **"Keep"**.

### Acid Rain — freestyled, all **MINE**
- Radius 64 + 24·M, lasts 60 + 20·M s, potency = M. Rain weather with green
  droplet particles near players. On random exposed tops: leaves wither
  (10 % + 4 %·potency per hit), flowers/crops/grass/saplings die (dead bush
  where it can stand, else gone), grass/mycelium/moss → dirt or coarse dirt,
  farmland/podzol → coarse dirt, copper weathers one stage per hit, mossy
  cobble loses its moss, stone bricks crack; at potency 6+ bare stone pits to
  cobble and cobble crumbles to gravel.
- Creatures under the sky take 0.5 + 0.25·potency damage per second (new
  `naturaldisasters:acid_rain` damage type), unless they wear a helmet, which
  loses 1 + potency/3 durability instead.
- Random acid rain where it rains, weight 0.8.
- Numbers reviewed: owner **"Keep"**.

### Drought — freestyled, all **MINE**
- Radius 96 + 32·M, lasts 120 + 40·M s (2–8.7 min), potency = M. Forces clear
  skies. Ponds (up to 80 × potency blocks of surface) sink a whole layer at
  a time; bigger lakes only lose shallow (≤ 1 + potency/3 deep) shore water;
  never ocean biomes or waterlogged blocks; snow and ice vanish;
  crops/flowers/grass die (some to dead bushes); farmland dries then turns to
  dirt; leaves drop (2 %·potency); grass → dirt, at potency 5+ dirt → coarse
  dirt, mud → packed mud. At potency 6+ dry grass can catch fire.
- Players in the sun get hungry fast (0.3 + 0.15·potency exhaustion per
  second) with white-ash heat shimmer.
- Random droughts in warm places (plains and hotter), weight 0.6.
- Numbers reviewed: owner **"Longer"** ("lasts in-game days, slowly drying the
  land"): now 1 (M1) to 5 (M10) Minecraft days, about twice the old total
  effect spread over that time (**MINE**); hunger softened to
  0.05 + 0.03·potency exhaustion per second in the sun so days of it don't
  starve players (**MINE**).

### Engine fairness (found while testing all 12 together)
- Disasters take turns being first each tick and get equal slices of the
  time cap, and each may always make at least 32 block changes per tick, so a
  heavy disaster can never stall the others.

### Location
- Disasters strike **near a random online player** (within some distance), so
  they are seen and unloaded chunks aren't touched.
- Owner: "natural disasters to have a propensity to avoid builds/buildings
  when spawning near a player. Its still possible but very rare."
  Implementation (**MINE**): up to 16 candidate spots (8 at the normal 16–64
  distance, 8 out to 128); each gets a build score within 48 blocks (POIs such
  as beds/workstations/bells, block entities near the surface, sampled
  planks/glass/wool/bricks/doors/torches/paths/farmland); the lowest score
  wins. Config `ignoreBuildsChance` (default 5 %) skips the check. Big
  disasters can still reach builds at their edges.

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
- **Overworld only** for random disasters (owner: "Overworld only for now");
  `/disaster start` still works in any dimension (Nether treats the roof as
  surface). Nether-specific disasters maybe later.
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

## Visual pass (owner's picks)
- In this pass: **tornado funnel**, **ash-dark sky**, **meteor model**,
  **storm skies** (hurricane bands/eye, blizzard snowfall look, green acid
  rain), and **earthquake camera shake** (owner: "Yes, but also note there
  are other weather mods, you can look at how they did weather effects if
  you need a little inspiration").
- Custom sounds: **later, after visuals**.

### What was built — all **MINE** (judge in play-testing)
- **Sky zones** (`sky/SkyZones`): each storm disaster publishes a circle
  (kind, centre, radius, strength, optional clear hole) that is synced to
  clients. Our layers on 26.1's environment attributes blend sky, fog and
  cloud colour, fog distance, sky light, stars and sunsets toward the
  storm's look over its area, fading over the outer 15 %:
  - Ash: near-black sky, fog 96 − 10·VEI blocks (min 24), dark ground light,
    no sun/sunset; monsters spawn and don't burn in daylight under it.
  - Hurricane: slate grey, fog 96; the eye (radius = eye radius) is clear.
  - Blizzard: whiteout, fog 96 − 6·M (min 32); snow falls instead of rain
    inside the zone.
  - Acid rain: sickly yellow-green, fog 128.
  - Drought: hot washed-out tan haze, fog 192.
- **Tornado funnel** (Weather2-style): big soft cloud puffs that spiral up
  a cone (narrow at the ground, wide at the top), a slow dark wall cloud on
  top, block dust thrown out at the base. Scales with the EF size.
- **Hurricane bands**: cloud puffs around the camera (within ~100 blocks)
  drift round the eye in two spiral arms plus a solid eyewall, ~30–45 blocks
  above the viewer so they show through the storm fog; none in the eye.
- **Meteor**: a spinning magma block, scaled with the meteor and drawn full
  bright, trailing smoke and flame.
- **Camera shake**: earthquakes (players within 1.3 × radius, 0.4 + 0.15·M
  degrees, weaker further out), meteor impacts and volcano booms. Client
  config `cameraShake` (on/off) and `cameraShakeStrength` (0–3×).

## Hurricane polish (after the visual pass)
- Owner: **"Thicker bands + lighter eye"**, and "less rain in the eye too".
- Built (**MINE**): about twice as many band clouds, darker and bigger; in
  the eye (inner 60 % of the eye radius, fading out at its edge) the sky
  turns pale blue, fog and clouds lighten, daylight comes back up, and no
  rain falls in the inner 80 %.

## Sounds
- Owner: **"Vanilla, reworked"**: every disaster has its own sound event
  (`registry/ModSounds`), built in `sounds.json` from vanilla sound files,
  layered and re-pitched. Real recordings can replace them there later
  without code changes.
- The mix (**MINE**): warning hint = distant low thunder and cave sounds;
  quake = rumble plus ground cracks (deepslate, heavy mace smash); meteor = low rocket whoosh,
  deep explosion + far firework blast, a warden sonic boom for big impacts;
  tornado roar = slowed elytra wind + breeze whirl + soul-sand-valley wind;
  hurricane howl and blizzard wind = the same winds at higher pitches; hail =
  high stone/amethyst clicks and glass breaking; volcano = deep rumble,
  blasts (explosion + sonic boom + trident thunder), bubbling lava;
  tsunami = heavy splash + water + rain; flood = rushing water; acid =
  high fizz; wildfire = low fire roar.
- Minecraft clamps pitch to 0.5–2.0, so nothing goes deeper than half pitch,
  and volume to 1, so a quiet vanilla file can't be boosted.
- Owner can't play-test, so I rendered **sound previews** (the events at
  their in-game timing, volume and pitch). That showed two problems, now
  fixed (**MINE**):
  - Long sounds were restarted far more often than they last (the tornado
    started a 14–20 s roar every second: ~15 copies at once, and Minecraft
    only plays 8 long sounds at a time). Each sound now repeats at about
    half its length: tornado and hurricane every 6 s, blizzard 5 s, quake
    rumble 6 s (cracks still every 1.5 s), tsunami 3 s, eruption blasts
    4.5 s, warnings 8 s.
  - The basalt-debris, earth-crack and soul-sand-wind layers are ~20–40 dB
    quieter than the rest and came out as silence. Replaced: quake and
    volcano rumble = slowed thunder + deep explosions (+ warden heartbeat
    for quakes); the wind storms = the elytra wind at three pitches each.
    Wildfire lost its long basalt layers; lava bombs now pop and drip.
- Owner on the previews: **"Passable for now"**. Revisit later (real
  recordings, or more tuning).

## Instruments (warnings and detection)
- Owner picks: **themed instruments**, warning by **redstone signal, chat
  message, alarm sound and earlier warning**; **three** instruments;
  range **scaled by size**; **double the warning**; **mid-game vanilla**
  recipes.
- Seismograph: earthquake, tsunami, volcano. Barometer: tornado, hurricane,
  blizzard, hailstorm, flood, acid rain, wildfire, drought. Sky watcher:
  meteor.
- Range: 2 × the disaster's radius, at least 128 blocks, from its centre.
- Redstone (**MINE**): 1–15, ceil(15 × closeness × (0.5 + M/20)), where
  closeness is 1 at the centre and 0 at the edge of range. M10 on top of it
  gives 15.
- Chat (**MINE**): when an instrument first senses a disaster, players
  within 48 blocks get e.g. "[Barometer] Flood (M1) 22 blocks to the
  northeast, starting in about 40 s".
- Alarm (**MINE**): every 3 s while the sensed disaster is still in its
  warning stage (seismograph: deep bells, barometer: raid horn, sky
  watcher: amethyst chime). Silent once the disaster has started.
- Earlier warning: a disaster that starts with any matching instrument in
  range (loaded or not; positions are saved per dimension) gets twice the
  warning time.
- Models (owner picked "Better instrument models"; shapes **MINE**): the
  seismograph is a stone base with a paper drum and an iron stylus arm (on
  alert: red stylus, jagged red trace); the barometer is an upright copper
  case with a dial on front and back (on alert: the needle swings into the
  red); the sky watcher is a copper telescope tilted up on a dark stand (on
  alert: the lens glows blue). They face the player who places them.
- Recipes (**MINE**): seismograph = iron, paper, redstone, a piston,
  smooth stone; barometer = copper, glass pane, glass bottle, redstone;
  sky watcher = spyglass, copper, redstone, iron. Pickaxe to mine.

## Full check-over (owner picked it; owner can't play-test)
- Fresh world, server only, 9 force-loaded areas 600 blocks apart. Wave 1:
  hurricane M8, two EF4 tornadoes, quake M8, volcano M7 (VEI 5, so a
  world-wide ash cloud and its own quake), flood M7, wildfire M8, meteor M9,
  blizzard M7, hailstorm M7, acid rain M7, drought M6, all at once. Wave 2
  (after a restart mid-disaster): tornado, meteor, hurricane, quake,
  tsunami, flood, wildfire. Then a client joined for random disasters and
  an EF5 lifting a survival player.
- Lag: 39 samples, mean 7.7 ms per tick, worst P95 26 ms, worst P99 48 ms
  (target 50). No errors or warnings from the mod in server or client logs.
- Restart mid-disaster: blizzard, drought and the ash cloud resumed. Ending
  the ash cloud un-paused the clock and moved it to morning.
- Fixed:
  - Meteors launched from (or aimed at) a chunk that is loaded but not
    ticking froze in the sky as visible leftovers. Now they fall straight
    down from above the target, or the visual is skipped if the target
    doesn't tick (the impact still lands on schedule).
  - A tornado funnel or hurricane eye marker that had drifted into a
    non-ticking chunk stayed there after the disaster ended. Both are now
    removed when the disaster ends.
  - Safeguard: players moved by a disaster get vanilla's "floating" counter
    reset (as a teleport does), so being carried for over 4 s can't get them
    kicked where flight isn't allowed. Couldn't make it happen even with an
    EF5 (the tossing up and down resets the counter), but it's one line per
    disaster.
- Left as is (vanilla behaviour): debris thrown into chunks that don't tick
  waits there mid-air until a player comes near, then falls.
- Not a mod issue: /forceload of new chunks on this slow container blocked a
  tick for 60 s (world generation) and the watchdog stopped the server.

## Land claims and safe zones
- Owner picks: respect **Open Parties and Claims + Flan + built-in safe
  zones**; claimed land gets **no block damage but stays dangerous**
  (creatures there are still hurt, the sky still darkens); random disasters
  **prefer unclaimed spots** (claims count like builds); **commands respect
  claims too**, a server config option turns protection off.
- Built (details **MINE**):
  - One check (`protect/Protection`) asks OPAC (whole chunks), Flan (exact
    claim areas) and safe zones; answers are cached per chunk per tick.
  - Blocks: no disaster can change a protected block (all edits go through
    `BlockWriter`). Thrown debris crumbles at the edge of protected land
    instead of landing on it.
  - Creatures and players in claims are still pushed and hurt. Things that
    aren't creatures (armor stands, item frames, dropped items, minecarts)
    are left alone there, since they're part of a build.
  - Placement: each protected chunk within 48 blocks adds 25 to the build
    score, so random disasters pick unclaimed spots (the 5 % "ignore builds"
    chance still applies; blocks stay protected either way).
  - Safe zones: `/disaster safezone add <name> <radius> [pos]`, `remove
    <name>`, `list`. Circles from bedrock to sky, saved per world, always
    protected (also without a claim mod).
  - Config: `[protection] protectClaims = true` (OPAC and Flan).
- Verified: game tests with and without both mods (38/38 with them), and an
  M7 quake under two identical houses, one in a safe zone: that one kept
  all its blocks, the other lost about half.
- Known gaps: water or lava a disaster already placed can't be drained if
  the land is claimed while the disaster runs; fire on unprotected land
  next to a claim can still spread in by vanilla fire spread (claim mods
  have their own fire protection).

## Undo
- Owner picks: restore **blocks including chest contents** (items those
  containers dropped are removed so nothing duplicates; killed creatures
  and lost items are not brought back); keep records for the **last 10
  disasters**; **skip spots players changed** since (undo never overwrites
  repairs or new builds, and reports what it skipped).
- Built (details **MINE**):
  - Every block change a disaster makes is recorded (the block before it was
    first touched, with its block entity data, and what the disaster left),
    including blocks thrown as debris and where that debris landed.
  - Records of running disasters are saved with the world in
    `<world>/naturaldisasters/undo/<id>.dat` (an M7 quake: ~217,000 changes,
    750 KB). Config `[undo] records = 10`, `maxBlocksPerDisaster =
    2,000,000` (beyond that, only partly undoable).
  - `/disaster undo list` and `/disaster undo <id>`. A running disaster is
    stopped first. Restores 4,096 blocks per tick, chunk by chunk (loading
    chunks as needed): an M7 quake took 4 s, no lag. Water and lava don't
    start flowing; leftover flowing water around restored spots drains.
  - "Changed by players" means a player broke or placed a block there after
    the disaster started (tracked near recorded disasters). Everything else
    is overwritten: a first version that skipped any spot that differed
    left 32,000 spots unrestored after an M7 quake (water and lava flowing
    into fissures, gravel falling, grass spreading).
  - Containers: refilled only if every item they dropped is still on the
    ground (those are taken back); otherwise they come back empty, so undo
    never duplicates items.
  - Not restored: killed creatures, broken item frames/paintings, items
    that despawned.

## Advancements
- Owner pick: **own tab, ~15**: survive each disaster type, extremes (EF5,
  cat 5, VEI 6+, M9 quake), build each instrument, find meteorite, smelt
  meteoric iron, try to sleep under world-wide ash, and so on.
- Built (titles and details **MINE**), a "Natural Disasters" tab:
  - Still Standing (survive any disaster) → Seen It All (all 12 kinds,
    challenge), The Big One (M9+ quake), Not in Kansas Anymore (EF5 tornado),
    Category Five (cat 5 hurricane), Year Without a Summer (VEI 6+, hidden),
    Eye of the Storm (stand in a hurricane's eye), Ride the Wind (carried 30
    blocks up by a tornado, hidden).
  - No Rest for the Weary (try to sleep under world-wide ash, hidden).
  - Listening to the Earth / Under Pressure / Eyes on the Sky (get each
    instrument), Forewarned (an instrument warns you).
  - Fallen Star (find meteorite) → Star Metal (smelt meteoric iron).
  - "Survived" = in the disaster's area at some point while it raged
    (tornado: near the funnel; hurricane: within its radius of the eye) and
    alive when it ends on its own; dying in between doesn't count, nor
    does a disaster stopped by command.

## Status (end of the freestyle night)
All 12 disasters are in, each with game tests (28 total, stable over many
runs) and checked in a real world: Earthquake, Meteor, Tornado, Hurricane,
Acid Rain, Drought, Flood, Tsunami, Volcano, Wildfire, Blizzard, Hailstorm.
Lag on the dev server stayed under the 50 ms tick target (worst P99 ~37–50 ms
during an M6 volcano + M6 flood together, and around autosaves; typical
10–25 ms) with up to three big disasters at once.

## Open questions
- Earthquake strength: owner **"Fine, judge after play-testing"**; still to
  judge.
- Sounds are vanilla-made, owner **"Passable for now"**; real recordings
  later?
- Not started (offered, not yet chosen): meteorite uses, scaling with
  player count, other dimensions (Nether), proper textures, release prep,
  in-game config screen.
