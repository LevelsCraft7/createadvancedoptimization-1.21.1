# Create: Advanced Optimization V1 Analysis

## Scope

- Target loader: NeoForge only
- Target Minecraft: 1.21.1
- Target Create version: 6.0.10
- Source of truth: `create-1.21.1-6.0.10.jar`
- Reference-only jars analyzed:
  - `CreateLazyTick-2.4.9-6.0.x-neoforge-1.21.1.jar`
  - `createbetterfps-1.21.1-1.1.4.jar`
  - `create_ergonomic-1211-6.0.9-0.5.1-neoforge.jar`
  - `createoptimizer-1.0.0+mc1.20.1.jar`
  - `create-optimizer-1.0.1+1.20.1.jar`

## Create metadata verification

Official `META-INF/neoforge.mods.toml` from `create-1.21.1-6.0.10.jar` confirms:

- `modId = "create"`
- `version = "6.0.10"`
- `minecraft versionRange = "[1.21.1]"`
- `neoforge versionRange = "[21.1.219,)"`
- required mixin config: `create.mixins.json`

This mod should therefore depend on Create explicitly and reject any version other than `6.0.10` during V1.

## Targeted Create classes and methods

### A. Diagnostics / profiling targets

These methods exist in the official jar and are suitable for lightweight timing hooks with `@Inject` at `HEAD` and `RETURN`:

- `com.simibubi.create.foundation.blockEntity.SmartBlockEntityTicker.tick(Level, BlockPos, BlockState, T)`
- `com.simibubi.create.content.fluids.FluidTransportBehaviour.tick()`
- `com.simibubi.create.content.fluids.PipeConnection.manageFlows(Level, BlockPos, FluidStack, Predicate<FluidStack>)`
- `com.simibubi.create.content.fluids.PipeConnection.manageSource(Level, BlockPos, BlockEntity)`
- `com.simibubi.create.content.logistics.funnel.FunnelBlockEntity.tick()`
- `com.simibubi.create.foundation.blockEntity.behaviour.inventory.InvManipulationBehaviour.extract(ExtractionCountMode, int, Predicate<ItemStack>)`
- `com.simibubi.create.content.equipment.armor.DivingBootsItem.affects(LivingEntity)`
- `com.simibubi.create.content.fluids.spout.SpoutBlockEntity.onItemReceived(TransportedItemStack, TransportedItemStackHandlerBehaviour)`
- `com.simibubi.create.content.fluids.spout.SpoutBlockEntity.whenItemHeld(TransportedItemStack, TransportedItemStackHandlerBehaviour)`
- `com.simibubi.create.content.fluids.spout.FillingBySpout.canItemBeFilled(Level, ItemStack)`
- `com.simibubi.create.content.fluids.spout.FillingBySpout.getRequiredAmountForItem(Level, ItemStack, FluidStack)`
- `com.simibubi.create.content.logistics.box.PackageEntity.tick()`
- `com.simibubi.create.content.logistics.packagerLink.GlobalLogisticsManager.tick(Level)`
- `com.simibubi.create.content.trains.GlobalRailwayManager.tick(Level)`
- `com.simibubi.create.content.redstone.link.RedstoneLinkNetworkHandler.updateNetworkOf(LevelAccessor, IRedstoneLinkable)`
- `com.simibubi.create.content.kinetics.chainConveyor.ServerChainConveyorHandler.tick()`
- `com.simibubi.create.compat.trainmap.TrainMapSync.serverTick(ServerTickEvent)`
- `com.simibubi.create.content.trains.graph.TrackGraphSync.serverTick()`

Reasoning:

- These are the hotspots named in the brief or the direct server-side entry points closest to them.
- Additional Spark captures on the real server showed fluid networks, pipe source/flow management, funnels, and smart block entity ticking as meaningful Create hotspots, so they were added as diagnostics targets.
- They can be measured without changing gameplay.
- They are mostly method-level server work and do not require client hooks.

### B. Diving Boots optimization target

Verified official implementation:

- `DivingBootsItem.affects(LivingEntity)` calls:
  - `isWornBy(entity)`
  - if false: `entity.getPersistentData().remove("HeavyBoots")`
  - then returns `false`

Risk and opportunity:

- The `remove("HeavyBoots")` call happens even when the tag is already absent.
- A conservative guard can skip the write when the tag does not exist, while preserving exact behavior when it does exist.

### C. Spout recipe lookup targets

Verified official call chain:

- `SpoutBlockEntity.onItemReceived(...)`
  - calls `FillingBySpout.canItemBeFilled(level, stack)`
  - calls `FillingBySpout.getRequiredAmountForItem(level, stack, fluid)`
- `SpoutBlockEntity.whenItemHeld(...)`
  - calls `FillingBySpout.canItemBeFilled(level, stack)`
  - calls `FillingBySpout.getRequiredAmountForItem(level, stack, fluid.copy())`
  - calls `FillingBySpout.fillItem(level, amount, stack, fluid)`
- `FillingBySpout.canItemBeFilled(...)`
  - checks `SequencedAssemblyRecipe.getRecipe(...)`
  - checks `AllRecipeTypes.FILLING.find(...)`
  - falls back to `GenericItemFilling.canItemBeFilled(...)`
- `FillingBySpout.getRequiredAmountForItem(...)`
  - checks sequenced assembly filling recipe with predicate
  - scans `RecipeManager.getRecipesFor(...)`
  - falls back to `GenericItemFilling.getRequiredAmountForItem(...)`
- `FillingBySpout.fillItem(...)`
  - redoes recipe lookup
  - uses `rollResults(...)`
  - mutates fluid and item stacks

Strategy retained:

- Cache only official Create recipe lookup results that are safe to memoize.
- Do not globally cache `GenericItemFilling` results by default because external capabilities can be dynamic.
- Keep cache local to the spout block entity and bounded by config.
- The current implementation is conservative enough to enable by default because dynamic external capability-based filling remains uncached.

### D. Processing recipe memoization targets

Verified official Create methods:

- `BasinOperatingBlockEntity.getMatchingRecipes()`
  - builds recipe candidates via `RecipeTrieFinder.get(...)`
  - resolves `RecipeTrie.getVariants(itemHandler, fluidHandler)`
  - filters each recipe through `matchBasinRecipe(...)`
  - falls back to `RecipeFinder.get(...)` on error
- `RecipeGridHandler.tryToApplyRecipe(Level, RecipeGridHandler.GroupedItems)`
  - builds `MechanicalCraftingInput`
  - checks normal crafting via `RecipeManager.getRecipeFor(...)`
  - checks Create mechanical crafting via `AllRecipeTypes.MECHANICAL_CRAFTING.find(...)`
  - assembles the result immediately

Strategy retained:

- Use extremely short-lived memoization keyed by stable snapshots.
- Basin memoization must include all relevant item and fluid slots and the current heat state.
- Crafter memoization must include the whole `GroupedItems.grid` state including coordinates and stack data.
- Returned `ItemStack` results must always be copied before reuse.

### E. PackageEntity monitoring target

Verified official class:

- `PackageEntity` is a `LivingEntity`
- server-side hooks of other mods can therefore be amplified by large package counts

Strategy retained:

- Track lifecycle through entity add/remove events instead of global ticking scans.
- Maintain counts by dimension and creation-rate counters.
- Only scan loaded levels on demand for `/cao packages top`.

## Reference mod observations

### Create Lazy Tick

Observed relevant classes:

- `mixin/OptElement/SpoutRecipeMixin`
- `mixin/OptElement/basin/BasinLazyTickMixin`
- `mixin/OptElement/crafter/CrafterRecipeMixin`
- cache bridge classes for Basin, Crafter and Spout

Useful ideas:

- very local signatures for basin/crafter/spout caches
- avoiding repeated recipe lookups for identical short windows

Rejected or risky aspects:

- lazy-tick style behavior changes can alter throughput or tick cadence
- broad behavior changes are too invasive for a conservative server patch
- older assumptions should not be copied blindly into Create 6.0.10

### Create Better FPS

Observed content is entirely client/render oriented:

- `SuperBufferFactoryMixin`
- `SuperByteBufferBuilderMixin`
- `ShadedBlockSbbBuilderMixin`

Decision:

- explicitly out of scope for this mod

### Create Ergonomic

Observed content is focused on QoL / interaction helpers, not the server TPS targets requested here.

Decision:

- not used for V1 logic

### createoptimizer / create-optimizer

Observed content is old and limited, with no trustworthy NeoForge 1.21.1 server optimization base for this project.

Decision:

- treated only as historical reference, not as implementation material

## Retained implementation strategies

- Use `@Inject`, `@Redirect`, accessors and mixin interfaces only
- Keep `defaultRequire = 1`
- No `@Overwrite` in V1
- No async access to live world/Create data
- No client-only features
- No default gameplay throttling
- Prefer small bounded caches with explicit invalidation keys over global long-lived caches
- Keep diagnostics available even when optimizations are disabled

## Rejected strategies

- Global “every other tick” processing for Create systems
- Distance-based pausing of contraptions by default
- Async world or entity analysis
- Unbounded global recipe caches
- Caching mutable `ItemStack` or `FluidStack` references without copying
- Blind caching of external dynamic capability-based filling results
- Any render/FPS/shader/Sodium/Embeddium/Iris optimization
- Copying mixins or logic wholesale from reference mods

## Known compatibility risks

- Any mixin against Create 6.0.10 signatures is intentionally version-pinned and may break on later Create updates.
- `PackageEntity` counts can be influenced by addons or scripts that spawn/despawn packages in bursts.
- Recipe-related memoization must invalidate on recipe reload and never reuse mutable outputs directly.
- Some Create systems such as trains and logistics are complex enough that V1 should default to diagnostics rather than behavior-altering optimizations unless proven safe.

## V1 implementation decisions

Implement in V1:

- Create version guard
- server config
- profiler and report writer
- `PackageEntity` monitor
- Diving Boots NBT removal guard
- bounded spout lookup cache for official Create recipe lookups
- short-lived processing memoization with conservative invalidation
- experimental module scaffolding and counters only where behavior changes are not yet proven safe

Do not implement as active behavior changes in V1:

- redstone link coalescing
- package density limiting or automatic cleanup
- logistics throttling
- train waiting backoff
- train map differential sync
- track graph batching
- chain conveyor inactivity throttling
- contraption dormancy
