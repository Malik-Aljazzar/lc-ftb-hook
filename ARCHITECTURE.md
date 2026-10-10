# Architecture & Code Guidelines

This document is the standing architectural standard for lc-ftb-hook. It applies
to new code and to any refactor that touches an external mod integration point.
It is **not** a mandate to retroactively rewrite everything that already exists -
it's the direction every relevant change should move in.

## Why this matters

lc-ftb-hook currently depends directly on several external mods' APIs - and in
places, on their *internal* implementation classes via Mixin:

- **FTB Teams / FTB Chunks** for the team and claim model.
- **Lightman's Currency** for banking (bank accounts, deposits/withdrawals).
- **Xaero's Minimap/World Map** (plus `ftbxaerocompat`) for map integration.

We want to keep the door open for supporting alternatives to each of these
later, without a full rewrite:

- **Open Parties and Claims (OPAC)** as an alternative to FTB Teams/Chunks.
- **Other currency mods** as an alternative to Lightman's Currency.

Not planned: JourneyMap (decided 2026-09-29 - Xaero stays the only map integration).

Today, `Team`, `ClaimedChunk`, `ChunkTeamData`, `FTBTeamsAPI`, `FTBChunksAPI`,
`PlayerBankReference`, and Xaero-specific classes are imported directly into
business-logic classes throughout `service/`, `handler/`, and `command/`.
Swapping in a second backend today would mean touching nearly every file -
effectively a rewrite, not an extension. Every guideline below exists to move
away from that.

## SOLID, applied to this codebase

- **S - Single Responsibility.** Keep "resolve who owns/can-touch this
  chunk", "compute a price", "enforce a protection rule", and "talk to
  FTB/LC/Xaero's actual API" as separate classes. A class should have one
  reason to change - a change to FTB Chunks' API should never also require
  touching billing math, and vice versa.
- **O - Open/Closed.** Add a new backend (OPAC, a different currency mod)
  by adding a new adapter class that implements an existing
  abstraction - never by adding a branch/if-chain/switch inside existing
  business logic that checks "which mod is installed."
- **L - Liskov Substitution.** Any implementation of an abstraction (e.g. a
  claim-backend interface) must be fully substitutable for another - no
  backend-specific quirk or assumption should leak through the interface
  contract and force a caller to know which concrete backend it's talking to.
- **I - Interface Segregation.** Don't design one fat "TeamService"/
  "MapService" interface that bundles claim-resolution, banking, war logic,
  and rendering together. Split into small, focused interfaces per concern,
  so an adapter only has to implement what it actually supports.
- **D - Dependency Inversion.** High-level business logic (Regions,
  Marketplace, Wars, Upkeep, protection resolution) should depend on
  abstractions that *this mod* defines, never directly on FTB Teams/FTB
  Chunks/Lightman's Currency/Xaero concrete types. Concrete adapters depend
  on and implement those abstractions - the dependency arrow points from the
  third-party API toward this mod's own interfaces, not the other way
  around.

## Clean Architecture's 4 layers, mapped onto this project

Dependencies only ever point **inward**. An inner layer must never import a
class from an outer layer.

1. **Entities** (innermost - pure domain data, zero mod/framework imports).
   Already mostly clean here today: `Region`, `ChunkOwnership`,
   `ProtectionProperty`, `TeamPendingState`, `PrivacyLevel`,
   `RegionPropertyKey`. Keep new domain data types this pure - no `Team`,
   `ClaimedChunk`, or Lightman's Currency types allowed in this layer, ever.
2. **Use Cases / Application** (this mod's actual business rules,
   backend-agnostic). `RegionService`, `MarketplaceService`,
   `UpkeepSettlementService`, `WarService`, `ProtectionResolution`,
   `ProtectionRollbackService`, and friends belong here conceptually - but
   today they reach directly into FTB's concrete API. Going forward, this
   layer should depend only on the ports in `dev.malik.lcftbhook.port.*`
   (`port.currency.CurrencyPort` for "withdraw/deposit/check balance",
   `port.claim.ClaimPort` for "who owns this chunk, who's on this team,
   what's their rank", `port.map.ChunkHighlightResolver` for "what should
   this chunk's map highlight show") - never on `FTBTeamsAPI`, `FTBChunksAPI`,
   `PlayerBankReference`, or Xaero classes directly. Business logic asks for
   the active implementation via `dev.malik.lcftbhook.port.Backends`.
3. **Interface Adapters** (the translation layer). Concrete classes under
   `dev.malik.lcftbhook.adapter.*` that implement the ports above, each
   wrapping exactly one external mod's real API - e.g.
   `adapter.lightmanscurrency.LightmansCurrencyPort implements
   port.currency.CurrencyPort`. This is the *only* place FTB/LC/Xaero-specific
   types should be referenced outside layer 4 itself. `port.map` is a partial
   exception today: `ChunkHighlightResolver` reads FTB Chunks' own client map
   API directly, since that's the claim/team DATA source (not yet abstracted
   behind `ClaimPort` - see Migration status below), not the map-*rendering*
   integration this class exists to keep map-mod-agnostic.
4. **Frameworks & Drivers** (outermost - the actual third-party mods/engine).
   FTB Teams, FTB Chunks, Lightman's Currency, Xaero Minimap/Worldmap,
   NeoForge's event bus, and any Mixin injecting into their internals.
   Treated as a plugged-in, swappable implementation detail behind layer 3's
   adapters - never referenced from layers 1-2.

## Size limits and Single Level of Abstraction (SLA)

Concrete, hard-target size guidelines keep Single Responsibility actually
enforceable - a class that's "one responsibility" but 900 lines long is a
sign the responsibility itself is too coarse.

- **Classes: aim for ≤250 lines.** A class creeping past that is a signal to
  look for a second responsibility hiding inside it and split it out (own
  file, own class).
- **Methods: aim for ≤25 lines.** A longer method is almost always doing
  more than one thing at one level of detail - extract named private helpers
  for each distinct step.
- **Single Level of Abstraction (SLA) in methods (and elsewhere).** Every
  statement in a method should sit at the same level of detail. A method
  that mixes "high-level orchestration" (call A, call B, call C) with
  "low-level mechanics" (raw loop bodies, field-by-field object
  construction, inline conditionals full of business detail) should have the
  low-level parts extracted into well-named helper methods, so the top-level
  method reads like a short, readable list of steps - and each extracted
  helper is itself internally single-level, recursively.

These aren't hard CI-enforced limits - no build failure at 251 lines - they're
a target/smell-detector to reach for whenever writing or touching a class,
biasing toward extraction over accretion.

**Reference example:** `WarService.java` used to be 699 lines with several
methods 30-70 lines long. It's now split by responsibility into `WarService`
(existence/lifecycle), `WarCostService` (upkeep/war cost math),
`WarViewBuilder` (outgoing/available-target UI views),
`WarIncomingViewBuilder` (incoming UI views - split further because incoming
cost scales with declare order across the whole list, a wrinkle the other
views don't share), `WarDismantleOrder` (declare/dismantle ordering, mirrors
`ProtectionDismantleOrder`), and `WarTeamView` (the shared view record, own
file). All six files are ≤250 lines, every method ≤25 lines. Use this as the
template when splitting up another oversized file.

## Migration status

Where each of the three ports actually stands, so a future session knows
what's real vs. designed-only without re-deriving it:

| Port | Interface | Adapter | Business logic migrated |
|---|---|---|---|
| Currency | `port.currency.CurrencyPort` + `CurrencyAccount` - done (accounts, spending rights, formatting, denominations, server sink account, team mirror hooks) | `adapter.lightmanscurrency.LightmansCurrencyPort`, `adapter.numismatics.NumismaticsCurrencyPort` - both done, neither depends on any claim mod (team info only via `ClaimPort`) | **Done (stage A, 2026-09-28).** No core class references Lightman's Currency anymore; LC-only features (LC team mirroring `LcTeamSync`/`LcTeamLinks`, Tax Collector rule, server-account ATM access) live in `adapter/lightmanscurrency` + `mixin/lc` and load only when LC is installed. LC is `optional` in mods.toml. Verified by booting the dev server with FTB + Numismatics and no LC. |
| Map | `port.map.MarketplaceHighlight` + `ChunkRegionInfo` + `ChunkHighlightResolver` - done | N/A (the resolver *is* the map-mod-agnostic layer; Xaero's `BuyableChunkHighlighter` calls it directly and owns only the Xaero-specific rendering) | Done for the World Map hatch/border; claim data from `client.ClientClaims`, so it works with any claim mod. Xaero is the only map integration (JourneyMap not planned). |
| Claim/Team | `port.claim.ClaimPort` + `ClaimRank` - done, expanded beyond the original query-only design to also cover claimed-chunk enumeration (`claimedChunks`), online-member lookup (`onlineMembers`), and force-load read/write (`isForceLoaded`/`setForceLoaded`) - the actions/queries real call sites (`ForceLoadHandler`, `RegionService`, `PendingChangeService`, etc.) actually need | `adapter.ftb.FtbClaimPort` - done, wraps `FTBTeamsAPI`/`FTBChunksAPI` (the exact calls business logic already made directly - a zero-behavior-change wrapper). `adapter.opac.OpacClaimPort` - done, wraps OPAC's real `OpenPACServerAPI`/`IServerClaimsManagerAPI`/`IPartyManagerAPI`/`IServerPartyAPI` (every method signature verified against the actual 0.31.6 NeoForge jar via `javap` plus OPAC's own GitHub source, branch `1.21` - not guessed; see the adapter's own javadoc for the OPAC/FTB Teams model-mismatch it bridges: OPAC claims are always player-owned, never team-owned, so a "team" is either a real OPAC party or, for a solo player, that player's own UUID used directly). Both adapters compile clean against their real jars. | **Server side done (stage B, 2026-09-29).** Core services use only `ClaimPort` + team UUIDs; FTB events/mixins live in `adapter/ftb` + `mixin/ftb`, OPAC is wired via `adapter/opac` (`OpacIntegration`, `OpacClaimListener`, `OpacPartyWatcher`). Still open: region protection enforcement under OPAC (stage C), client/GUI (stage D), FTB still mods.toml-required (stage E). |

### Roadmap: full swappability (decided 2026-09-28)

Goal (explicit user requirement): the mod runs with ANY claim backend
(FTB Teams+Chunks or OPAC) combined with ANY currency backend (Lightman's
Currency or Numismatics) - including the cross combinations (FTB +
Numismatics, OPAC + Lightman's Currency) - with neither FTB nor LC required
when the alternative is installed. Decisions: all GUIs are rebuilt on vanilla
Minecraft widgets (FTB Library is NOT kept as a UI dependency); under a
backend without FTB Teams' team screen, menus open via our own keybind (a
small main menu: Regions / Wars).

Progress is measured by `BackendIsolationTest` (ratchet list in
`src/test/resources/architecture/unmigrated-backend-references.txt`, baseline
114 files). Stages, in order:

- **A. Currency fully swappable** (DONE 2026-09-28) - money formatting + denominations +
  server sink account behind `CurrencyPort`; every LC call site migrated; LC-only
  features (LC team mirroring, Tax Collector, server-account op access) moved
  into the `adapter/lightmanscurrency` / `mixin/lc` zone and only active when LC
  is the active currency backend. Unlocks FTB + Numismatics.
- **B. Claim/team core decoupled** (DONE 2026-09-29, ratchet 93 -> 41) - core services take `UUID teamId` instead of
  FTB `Team`; backend events (claim/unclaim/force-load requests, team
  lifecycle, rank changes) translated by each claim adapter into
  backend-agnostic hooks the core implements (`ClaimLifecycleService`,
  `ForceLoadRequestService`, `TeamLifecycleService`, `TeamChangeHooks`).
  FTB side: `adapter/ftb` (`FtbClaimEvents`, `FtbTeamEvents`) + `mixin/ftb`.
  OPAC side: `adapter/opac/OpacClaimListener` (OPAC claim action listener;
  money only moves in the "successful" callback, an unaffordable claim is
  undone silently) and `OpacPartyWatcher` (OPAC has no party events - party
  snapshots are diffed every 100 ticks). Verified by booting the OPAC dev
  server with `claimBackend = "OPAC"` + Numismatics.
- **C. Protection enforcement under OPAC** (server side DONE 2026-09-29) -
  protection fields come from the claim backend: `ClaimPort.protectionFields()`
  returns a `port.protection.ProtectionFieldSet` (pure data in
  `adapter/ftb/FtbProtectionFields` - every FTB Chunks protection incl.
  non-living entity attack, edit+interact and the fake-player switches - and
  `adapter/opac/OpacProtectionFields` - every OPAC protection option).
  `data.ProtectionProperty` is a value class over the active catalog
  (`data.ProtectionCatalog`: server = backend + `config.ProtectionPriceConfig`,
  client = synced with `SyncClaimPricesPayload`); backend-neutral features
  use roles (`ProtectionFieldSpec.Role`: war vulnerability, Sable explosions).
  Prices live in their own file `lc_ftb_hook-protection-prices.toml`, one
  section per installed claim mod, each with its own `dismantleOrder`.
  OPAC enforces itself, fed through its public API: `OpacProtectionSync` points
  every claim at an auto-managed per-owner sub-config ("lcfh<n>",
  `OpacSubConfigSlots`) holding the chunk's effective values
  (`OpacProtectionProfile`); `OpacConfigLock` moves those options to OPAC's
  op-configurable list so players can't change them; `OpacChunkAccessOverrider`
  gives officers and private chunk owners full access (= our "Private" level).
  The ONLY OPAC-internal hook is `mixin/opac/ChunkProtectionAccessListMixin`
  (per-field whitelist/blacklist, `require = 0` - verified that a broken target
  only logs a warning and protection keeps working).
- **D. Client** (DONE 2026-09-29, ratchet 41 -> 0) - client claim data comes
  from our own sync, not from any claim mod's client API: `service.ClaimMapSync`
  sends every claim (team, force-loaded) plus team names/colors
  (`SyncClaimMapPayload`) and each player's standing per team
  (`SyncViewerRanksPayload`) into `client.ClientClaims`; `port.map.ChunkHighlightResolver`,
  the info panel and the Xaero adapter read only that. All screens are plain
  vanilla (`client.gui.LcScreen` + `RowListWidget`, no UI library), opened via
  `client.ScreenOpener`; the region/marketplace actions for a chunk selection
  live in `client.ChunkActions` (used by Xaero's right-click menu and the main
  menu). Entry point without any claim mod UI: keybind N ->
  `client.gui.MainMenuScreen` (Regions, Wars, actions for the chunk you stand
  in). Code that extends FTB's own screens (chunk screen, team screen, config
  screen) lives in `client/ftb` + `mixin/ftb/client` and only loads with FTB.
- **E. Dependency flip** (DONE 2026-09-29, pulled ahead of C/D) - FTB/LC become optional in `neoforge.mods.toml` and
  `compileOnly` in Gradle, mixin configs split per backend behind a mixin
  plugin that checks mod presence, `ModCompatibility` requires "at least one
  claim + one currency backend", then real test runs on the OPAC dev server
  without FTB/LC.
  Done ahead of stage D: the still-FTB-based client is kept from loading
  without FTB by `client.ClientUiRefresh` (neutral facade, delegates to
  `client.ftb.FtbClientRefresh` only when FTB Chunks is loaded), the
  `ftbteams` guard around `ClientPendingRefreshHandler`, and a mixin-package
  rule in `BackendMixinPlugin` that skips the Xaero mixins without FTB Chunks
  (they still read FTB's client map data). FTB + Architectury are
  `compileOnly` and only copied into the FTB dev environment (`ftbTestMods`),
  so `runServerOpac` really runs without FTB and LC.

### Backend selection (auto-detect + priority + config)

`port.Backends` resolves each port's implementation lazily on first use via
`port.BackendSelection`, against a priority-ordered candidate list per port
(`port.ClaimBackends`: FTB → OPAC; `port.CurrencyBackends`: Lightman's Currency
→ Numismatics). Each candidate (`port.BackendCandidate`) is just a config id,
the mod id it needs (`ModList.isLoaded`), and a factory. The server config's
`[backends]` section (`claimBackend`, `currencyBackend`, default `AUTO`) picks
the highest-priority installed candidate, or pins one explicitly; an unknown
or not-installed explicit value logs a warning and falls back to `AUTO`
instead of failing startup. The chosen backend is logged on first use.
**Adding another backend later** (a third claim mod, another currency mod, a
map mod once a map port exists) = write its adapter + add one
`BackendCandidate` line to the relevant list - nothing else changes.

Real mod ids (verified from each jar's own `neoforge.mods.toml`, not guessed):
`ftbchunks`, `openpartiesandclaims`, `lightmanscurrency`, `numismatics`.

`ClaimBatchContext` (`bank/`) also still takes `IBankAccount` directly in its
API (`recordClaimInsufficientFunds`) - migrating `ChunkClaimHandler.handlePurchase`
fully would mean touching that too, which is why it wasn't done in the first
pass alongside the refund path.

### Dev environments (every claim x currency combination)

`devEnvs` in `build.gradle` defines one dev environment per combination, each
with its own server directory, clients under `clients/<env>-<n>` and only its
own two backend mods (copied into the mods folders by `copyDevMods_*`; FTB, LC,
OPAC and Numismatics are never on a run's classpath):

| Env | Server dir | Port / RCON | Clients | Start |
|---|---|---|---|---|
| `ftb-lc` | `run` | 25565 / 25575 | `clients/ftb-lc-1..3` | `scripts/run-server.ps1`, `run-client.ps1`, `run-client2/3.ps1` |
| `opac-num` | `run-opac-server` | 25566 / 25576 | `clients/opac-num-1..3` | `run-server-opac.ps1`, `run-client-opac1..3.ps1` |
| `ftb-num` | `run-ftb-num-server` | 25567 / 25577 | `clients/ftb-num-1..2` | `run-server-ftb-num.ps1`, `run-client-ftb-num1/2.ps1` |
| `opac-lc` | `run-opac-lc-server` | 25568 / 25578 | `clients/opac-lc-1..2` | `run-server-opac-lc.ps1`, `run-client-opac-lc1/2.ps1` |

All wrappers call `scripts/start-server.ps1 -Env <env>` / `start-client.ps1 -Env <env> -Number <n>`
(table in `scripts/dev-envs.ps1`, must match `devEnvs`). Stop a server
gracefully with `python3 scripts/dev/rcon.py --port <rcon> stop`.

## How to apply

- Not a mandate to stop and refactor the whole mod right now - apply this
  incrementally, biased toward it whenever a task already touches one of
  these integration points or adds new business logic.
- When adding genuinely new business logic, put it in terms of the
  ports/abstractions (even before a second backend adapter exists) rather
  than the concrete FTB/LC/Xaero type, so the seam is already there when a
  second backend is eventually needed.
- When a task specifically involves the team/claim system, the banking
  system, or map integration, call out the relevant abstraction opportunity
  even if not implementing it immediately.
- When touching a file that's already oversized, prefer splitting it (by
  responsibility, following the reference example above) over adding more to
  it.
