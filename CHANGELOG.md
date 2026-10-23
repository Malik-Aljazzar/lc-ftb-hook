# Changelog

All notable changes to LC FTB Hook are documented in this file.

## [4.4.1]

### Compatibility
- Tested against Open Parties and Claims 0.33.0 (older supported versions keep working). OPAC's new "claiming paused" / "new force-loads paused" permissions and config options simply refuse the action before this mod charges anything; OPAC's new automatic sub-claim names don't show up on region claims, which keep their team/region name.

### Fixes
- Force-loading: a force-load that was still queued (e.g. not yet affordable) when its chunk was unclaimed stayed queued forever - the chunk kept showing as force-loaded on the maps, the upkeep overview kept listing its cost, and every upkeep period retried it and reported it as unaffordable. The only way out was to claim the chunk again and cancel the force-load. Unclaiming now clears everything queued for the chunk. Reported on CurseForge with Open Parties and Claims + Numismatics; FTB Chunks was affected the same way.
- Claims that disappear or change hands without this mod being told no longer leave data behind. Open Parties and Claims' expiry of inactive players' claims and its `admin clear` / `admin transfer` commands bypass the claim listener, so a chunk's region assignment, marketplace ownership and listing, settlement markers and queued changes stayed with the old team - showing chunks as for sale, privately owned or force-loaded that nobody owns. At server start and before every upkeep period this mod now checks its per-chunk data against the claim mod and drops what belongs to chunks the team no longer owns (logged per team). A privately owned or listed chunk that disappears this way is logged with its owner and price, since no refund is made automatically. If the claim mod reports no claims at all, for any team (most likely it didn't load its data), nothing is touched and an error is logged.

## [4.4.0]

### Compatibility
- Runs on NeoForge 21.1.197 and newer (was 21.1.250). Nothing in this mod needs a newer NeoForge - the old minimum only kept it out of modpacks that sit on an older build (many large 1.21.1 packs use 21.1.248). 21.1.197 is the minimum of Lightman's Currency; with Create: Numismatics, Numismatics itself needs 21.1.228. Verified by building and running the server on 21.1.197.

## [4.3.0]

### Added
- FTB Chunks server teams (admin-run teams without players, e.g. for the spawn - `/ftbteams server create`) are supported: claiming, unclaiming and force-loading for them costs nothing (and pays no refunds), they have no upkeep or wars, and their protection comes from FTB's own team settings (`/ftbteams server settings_for <team> <setting> <value>`) instead of regions. Before, claiming for a server team failed because it has no bank account.

### Fixes
- Lightman's Currency: the first party on a new world couldn't claim past its free chunks ("Missing LC team bank account" in the log). Lightman's Currency numbers its teams from 0, and this mod treated LC team 0 as "no team" - so that party's LC team was created but never kept linked. Team 0 is now a team like any other.
- FTB Chunks: when an admin claims or unclaims for another team, that team pays or gets the refund - not the admin's own team, and the admin's rank in their own team no longer matters for it.
- No more "No Lightman's Currency team account for party ...: not in use" warning right after founding a party (the party is switched over a moment later); the reason still appears in the error if an account is actually missing.

## [4.2.0]

### Fixes
- FTB Chunks: parties whose owner was lost in the 4.0.0 incident get it back. FTB Teams sets a party's owner last while loading it, and 4.0.0 could abort right before that - such a party was saved with the owner `00000000-...` while its rank list still named the real owner. At server start this mod now restores the owner from the rank list (only when it names exactly one owner who is still a member; otherwise an error is logged and nothing is changed).
- Lightman's Currency: a party without a usable owner no longer passes the empty id on to its LC team. 4.0.0/4.1.0 made such an LC team ownerless and demoted the real owner to admin; the link sync now restores the real owner (on the next login or team change). Settling a disbanded party also uses the real owner.
- Lightman's Currency: an LC team linked to several parties is no longer unlinked blindly. 4.1.0 cleared one of the links when loading - whichever came second, possibly the active party's, which then got a new, empty account while its old one stayed with a deleted party. The account now stays with the party it demonstrably belongs to (the active one; then the one whose owner, then whose name matches the LC team); if that can't be told, nothing is changed and an error is logged for an admin.
- A party that gets no LC team account is no longer skipped silently: the server log says once why (party not in use by any member, or no owner), and the "Missing LC team bank account" error names the reason.

## [4.1.0]

### Compatibility
- Tested against Open Parties and Claims 0.32.7, Lightman's Currency 2.3.0.6, Xaero's Minimap 26.6.0 / World Map 1.47.0, FTB Library 2101.1.37 and NeoForge 21.1.256 (older supported versions keep working).
- Open Parties and Claims 0.32 anchor-based claiming: setting or removing a claiming anchor is no longer refused, and claims OPAC removes because they are no longer near an anchor are refunded and cleaned up like any other unclaim. (4.0.0 doesn't know these actions and fails on them - use OPAC 0.31 with 4.0.0.)

### Fixes
- **Critical (FTB Chunks):** since 4.0.0, FTB Teams could only load one team per folder at server start - this mod reacted to FTB's "team loaded" event by touching world data before the worlds existed, and the resulting exception aborted FTB's whole team loading. All other parties and their claims seemed gone, and the startup cleanup then deleted this mod's data (regions, bank account links, marketplace) for every team it thought was deleted. Fixed: nothing touches world data during FTB's team loading anymore, and no exception of this mod can abort FTB's loading again.
- Safety: the startup cleanup refuses to delete anything when many teams vanish at once (that means the claim mod didn't load its data, not that the teams were deleted) and logs an error instead.
- Lightman's Currency: a party whose bank account link was lost takes its old LC team (and balance) back instead of getting a new, empty one - and an unlinked LC team that still holds money is never deleted as a "duplicate" anymore.
- FTB Chunks: removed the "Block edit & interact" protection from regions. FTB Chunks only uses that setting on Fabric - on NeoForge it never protected anything, so it only cost upkeep. Regions now offer 9 FTB protections; a value stored for it before is ignored and no longer billed.
- Hardening against data loss (after the 4.0.0 incident):
  - Every hook into FTB Chunks/Teams, Open Parties and Claims, Lightman's Currency, Numismatics and Sable is guarded: an error inside this mod is logged and falls back to a safe default (claim/protection checks deny with "This action could not be checked right now - nothing was changed.") instead of breaking the other mod or the server tick.
  - World data accessed before the worlds exist now fails loudly with a clear error instead of being read or created half-way.
  - Open Parties and Claims: if many parties or members disappear at once, the party watcher treats it as a loading problem and does not settle money or remove links.
  - Bank account links are not refreshed while the claim mod reports no teams at all.
  - This mod's own world data is checked when it loads: every team has exactly one Default region, the region order lists every region once, chunk assignments and queued changes only point at existing regions, and each LC team is linked to only one party. Problems are repaired and logged. An entry that cannot be read is kept untouched in the file instead of being dropped.
  - A copy of this mod's world data (`world/data/lc_ftb_hook_team_accounts.dat`) is saved to `world/data/lc_ftb_hook_backups/` at every server start; the newest 10 are kept.
- Upkeep breakdown tooltips (Region List and Region Settings): the force-load line now shows the chunk count including queued force-loads, the price per chunk and the total ("Force-load: 1 -> 2 chunk(s) x 2g5i = 2g5i -> 5g"). It used to show only the currently active count next to a total that already included the queued ones, prefixed with a "-" that read like a discount; Region Settings showed no price at all.

## [4.0.0]

### New Features
- Create: Numismatics is now supported as an alternative to Lightman's Currency - Lightman's Currency is no longer required. With both installed, Lightman's Currency is used by default; set `currencyBackend` in the new `[backends]` config section to pick one explicitly (`claimBackend` works the same way for the claim mod; both default to `AUTO`). Open Parties and Claims can now be used as the claim mod instead of FTB Teams/Chunks for claiming, unclaiming, force-loading, upkeep and wars (set `claimBackend = "OPAC"` if both are installed).
- Regions now offer every protection setting the claim mod has. With FTB Chunks that adds "Non-living entity attack", "Block edit & interact", "Fake player protection" and "Fake players by owner" to the existing six (they are now hidden from FTB's own team settings, like the others). With Open Parties and Claims, regions offer all of OPAC's protection options and OPAC enforces them itself - players can no longer change those options in OPAC's own settings, and officers/private chunk owners always keep access.
- **Breaking (config):** protection prices moved to their own config file `lc_ftb_hook-protection-prices.toml` with one section per claim mod, and the old `[protectionPrices]` section of the main config is gone - servers that had changed protection prices in 3.x need to set them again in the new file. In `/lc_ftb_hook config` they are now called `price.ftb.<protection>` / `price.opac.<protection>`.
- The protection dismantle order (which protections are switched off first when upkeep can't be paid) moved there too, as `dismantleOrder` in each claim mod's section - Open Parties and Claims has its own, fitting order now (it used to fall back to an arbitrary one, since the old setting only knew FTB's protections). The config explains the whole mechanism step by step. **Breaking (config):** the old `[protectionDismantle]` section of the main config is gone; a customized 3.x order needs to be set again.
- Every price in the config files is now also written out in the coins of both currency mods ("= 100 copper per chunk; Lightman's Currency: 1 gold, Numismatics: 1 cog 2 sprocket 4 spur"), and the unit explanation covers Numismatics' spur as well as Lightman's copper coin.
- Protections are split into main and advanced ones: the chunk info panel always shows the main protections (build/break, interacting with blocks and entities, item use, PvP, explosions, mob griefing, and with OPAC also fire spread and crop trampling) and advanced ones only while they actually protect something ("+N more protections open"). The settings screens list the main ones first and put the rest behind an expandable "Advanced protections (N)" row; advanced protections that are set always stay visible.
- New claims menu on a key (default **N**, changeable in Controls): Regions, Wars, and everything you can do with the chunk you are standing in (assign to a region, sell, buy, private chunk settings) - works with every claim mod, no FTB or map mod needed.
- All menus of this mod (regions, region settings, private chunk settings, access lists, wars, region picker, buy confirmation) are rebuilt with plain Minecraft widgets and work without FTB. Whitelists/blacklists for private chunks are now available for every protection where they make sense, not only the access levels.
- Xaero's World Map integration (hatch highlights, info panel, right-click region/marketplace entries) now works with Open Parties and Claims too.
- FTB Teams, FTB Chunks and FTB Library are no longer required: the mod now needs one claim mod (FTB Chunks or Open Parties and Claims) and one currency mod (Lightman's Currency or Create: Numismatics), in any combination.
- New `/lc_ftb_hook team_bank balance | deposit <amount> | withdraw <amount>` command to move money between your personal account and your party's team account (amounts in the smallest coin). Needed with Numismatics, which has no way to open a team account on its own; works with Lightman's Currency too.
- The price windows for selling and expropriating chunks are now a simple coin list with -/+ buttons (Shift for steps of 10) instead of Lightman's Currency's own coin input, so they work with any currency mod. Lightman's Emerald Coin is no longer offered there at all (chunks were never allowed to be priced in it).

### Fixes
- Open Parties and Claims: the title OPAC shows when you enter a claim (and on the map) is now the land and its region ("Laputa - Harbour", just "Laputa" for the Default region) instead of "<player>'s Claim" - OPAC claims belong to the player who made them, so it used to name whichever member claimed that chunk.
- Open Parties and Claims: a party renamed via OPAC's "Party Name" setting now shows its new name everywhere (info panel, map, wars, messages) instead of OPAC's default "<owner>'s Party".
- Open Parties and Claims: the chunk info panel over a newly formed party's claims showed only the party name - the party had no region data yet. Parties now get their Default region as soon as they are created (or on server start), and the panel falls back to the Default protections meanwhile.
- Create: Numismatics: party team accounts can now be opened at a Bank Terminal. Officers and the owner hold a bank card and run `/lc_ftb_hook team_bank card` to bind it to the party account; the account is labeled with the party name and trusts exactly the party's current officers and owner (kept in sync on rank/member changes). Existing party accounts are converted automatically, balance included.
- Open Parties and Claims now settles parties like FTB Chunks: joining (or founding) a party dissolves your personal claims with a refund to your personal account, and disbanding a party releases all of its members' claims, refunds them to the party account and pays everything out to the owner. Previously OPAC silently turned personal claims into party land and left them behind as personal claims after a disband.
- Create: Numismatics amounts are shown with the coins' own item icons instead of their names ("3[cog] 2[spur]") - in the map info panel, tooltips, prices, chat messages and the coin rows of the price entry screen.
- The chunk info panels on Xaero's World Map and the FTB Chunks claim screen grow with their content (long coin amounts, OPAC's longer protection names) instead of letting text spill over the edge; the balance panel and the chunk panel below it keep the same width.
- Price entry (listing chunks for sale): +/- keep the amount in the fewest coins - 8 spurs roll over into a bevel, 10 copper into an iron coin, and "-" on an empty coin breaks a bigger one - instead of piling up large counts in one row.
- Open Parties and Claims: requesting a force-load (or un-force-load) on the map no longer shows OPAC's red "prevented by an addon" failure with an X - it shows a green check whose tooltip says the force-load is queued until the next upkeep period. The queued chunk's hatching on Xaero's World Map also appears right away instead of only after the next map redraw.
- Removed the debug test-team commands (`/lc_ftb_hook seed_test_teams`, `clear_test_teams`, `count_test_teams`) and their `[debug] debugTestTeamCommands` config option.
- Open Parties and Claims: area claims (selecting several chunks at once) now work like FTB Chunks' multi-chunk claims - if the team can't pay for every chunk in the area, nothing is claimed and you're told the total price; otherwise you get one summary message instead of one per chunk. Area unclaims report one combined refund.
- Open Parties and Claims: when a member leaves a party, the chunks they claimed stay with the party (handed to the party owner) instead of leaving with them - same as FTB Chunks. The leaving player is told how many chunks stayed.
- Only a party's officers and owner can claim land for it (FTB Chunks and Open Parties and Claims). Regular members could previously still claim chunks that fell within the free-chunk allowance, since the rank check only ran when a claim cost money. The refusal message now also shows proper text instead of a raw translation key.
- Deleting a region no longer leaves its outline on the map: its chunks now really merge back into the Default region (existing worlds are cleaned up automatically on the next sync).

## [3.2.0]

### New Features
- Fixed force-loading a chunk you can't currently afford doing nothing at all (no message, no map marker). It's now queued the same as any other force-load and shows as pending/red on the map and in the info widget, applying automatically once you can afford it.
- Fixed upkeep settlement still processing and reporting wars (e.g. "1 outgoing war(s) queued — not enough balance") even while the war system is completely disabled. The frozen-account message now also mentions force-loading specifically, not just protections and wars, and the "balance sufficient again" message now says the same for force-loading.
- Force-load upkeep is now shown as part of the same combined "Upkeep" total everywhere (Region List, Region Settings) instead of as a separate line/tooltip - it's billed through the exact same cycle as protections and wars now, so a separate figure no longer made sense. Hover the total for the full breakdown.
- Explosion protection now also covers terrain destroyed by Sable Collision Damage (Create: Aeronautics crashes) - that mod was breaking blocks directly, completely bypassing FTB Chunks protection, so a claimed region with explosion protection enabled was just as vulnerable to a crashing airship as unclaimed wilderness. Only real optional integration: has no effect at all if Sable Collision Damage isn't installed.
- Fixed the per-region upkeep formula (both the Region List tooltip and the settlement chat summary, e.g. "5e x 46 chunks = 2d3e") showing a per-chunk rate that was 10x too high - also simplified the wording to drop the internal "/ 10 (rounded down)" arithmetic, since it wasn't meaningful to players and just looked like a leftover bug at a glance.

### Fixes
- Region management (create/rename/reorder/delete regions, edit protection settings, toggle private selling/buyer access, assign chunks to regions) now correctly requires officer or owner rank, both in the menus/screens (hidden entirely for regular members, including the "Protection" button on the team screen) and on the server - it used to only require being a member almost everywhere except deleting a region outright.
- FTB Teams' own team-properties screen (the gear icon) can now still be opened by a regular member, but every setting in it is read-only for them - it used to be fully editable by anyone regardless of rank.
- Fixed a team that's already frozen for unpaid upkeep being unable to even queue a new protection, region-assignment, or force-load change - it was rejected outright instead of going pending like it's supposed to for any team that can't currently afford something.
- Claim/unclaim/force-load/unload options now disappear from the right-click menu for a player who doesn't currently have spending access on their team's account, instead of being shown and then silently failing. Unclaiming a chunk also now actually respects that access level - it previously had no restriction at all, unlike claiming.
- Fixed "Sell as Country" having no permission check whatsoever on the FTB Chunks screen - any player could list another team's chunk for sale. Now requires officer or owner, same as the server already enforced.
- Turning off a region's "allow private selling" now immediately cancels every currently-listed private chunk sale in that region (state/country listings are unaffected) - previously existing listings stayed live and buyable until something unrelated happened to re-trigger the cleanup.
- Upkeep detail messages (what got restored/suspended/queued, top-up reminders, unlock notices, protection/force-load change queued/cancelled, claim visibility relocked) are now shown to officers and the owner only, not every online member - a plain member can't act on any land/region management anyway.
- Fixed a member trying to place a Tax Collector in team land seeing a confusing Lightman's Currency "was destroyed by unpreventable means" message alongside our own denial - the placement is now blocked before it ever happens, so only our own clear message shows.
- Fixed the Region List's combined upkeep total and tooltip showing "no active protections, nothing billed" even when a region had real queued protection changes (new settings, or ones waiting to be restored once affordable again) - it only ever looked at each property's currently-live value, never the pending target, so a region that was fully unprotected but had plenty pending just disappeared from the summary instead of showing what it's about to cost. Now shows both, the same way the individual Region Settings screen already did.
- The Region List's upkeep tooltip now attributes force-loaded chunks to whichever region they're actually in, right alongside that region's own protection cost, instead of one flat "N force-loaded chunks" line for the whole team disconnected from any region.
- Fixed a team getting permanently stuck frozen (unable to restore ANY already-affordable protection) because of a single force-load request that couldn't be activated - the queued-but-not-yet-live force-load was being counted as an already-owed cost in every affordability check, even though it was never actually costing anything yet, and nothing ever gave up on it the way an unaffordable protection or a live force-loaded chunk already could. Force-load billing now follows the same "unbilled until actually live" rule protections already use, and pending force-loads are now activated one at a time (as many as the team can actually sustain) instead of all-or-nothing.
- The claim/map info panel (both the FTB Chunks screen and Xaero's World Map) now only shows the claim price, the team's country balance, and the force-load upkeep price to officers and the owner - a plain member only ever needs their own private balance, and couldn't act on any of the other figures anyway.
- Fixed the balance shown in that same claim/map info panel going stale (e.g. after depositing money at a Lightman's Currency terminal, or after upkeep silently withdrew its payment) until something unrelated happened to refresh it. It now refreshes itself every few seconds while visible, and also refreshes immediately right after every upkeep settlement.
- All prices (claim prices, force-load upkeep, and the 6 protection prices) are now consistently shown and configured as a straightforward per-chunk rate, instead of protection prices being framed as "per 10 chunks" - the actual amounts billed are unchanged, this is a display/config-unit cleanup only. Server admins: every price config value now represents tenths of a copper rather than whole copper (a value of 10 = 1 copper), so a raw copper price should be multiplied by 10 when entering it - all shipped defaults have already been updated to keep today's real prices exactly the same.
- The "queued — not enough balance" upkeep message now also lists force-loads stuck waiting on funds, not just protections and outgoing wars - it was silently omitted even though it's dismantled/restored through that exact same list.
- FTB Teams' own team-color picker (the small square swatch on the team screen) can no longer be changed by a plain member - it had no rank check at all, unlike every other team-management action in this mod. A member can still see the current color, just not open the picker to change it.
- Force-loaded chunks are now dismantled/restored one whole Region at a time (all of that region's force-loaded chunks together, or none of them) instead of one chunk at a time, and always in the same region priority order protections already use - matching how wars are kept-or-dropped as a whole, rather than the finer per-property granularity protections use.
- Fixed a protection row with a pending change (the "currently: X" / "pending" two-line layout) drawing its second line slightly past the bottom edge of its own row background in the Region Settings screen - every row used the same fixed height regardless of whether it needed one line or two.

## [3.1.0]

### New Features
- Protection settings now show a description, price, and (for Public/Allies/Team/Private settings) what each tier means when you hover over them.
- "Next payment" for upkeep and force-loads now shows a live, ticking minutes:seconds countdown instead of a static whole-minutes value.
- Force-loaded chunks now show a red hatch on Xaero's World Map, alongside the existing marketplace hatch.
- Claim visibility (FTB Teams' own Public/Allies/Team map setting) can now be unlocked via config instead of always being forced to Public.
- Added a placeholder 7th protection setting, "Sable Sublevels" (grayed out, coming soon).
- The Region Settings upkeep summary now also breaks down force-load costs, the same way the region list already does.
- Every payment this mod makes (upkeep, claim purchase/refund, marketplace buy/sell, expropriation, party-disband fund transfer) now shows up in the relevant account's Lightman's Currency bank log instead of silently moving money with no record.
- Added a pooled server bank account that collects upkeep and claim payments instead of destroying that money outright - accessible to any op via `/lc_ftb_hook server_account`.
- Protection prices can now be set in a smaller unit (10 = 1 copper, rounded down), so a price can be a fraction of a copper per chunk.
- Added `/lc_ftb_hook config list|get|set`, letting admins view and change most server config values live, without editing the config file or restarting.
- The war system's on/off switch can now be flipped at runtime: turning it off immediately discards every active and pending war and hides the war button for everyone online, no restart needed.
- Region List and Region Settings screens now use consistent icons, spacing, and a priority order that reads top-to-bottom the way people expect.
- The Region List's upkeep tooltip now shows the same per-region, per-property price breakdown as the full `/upkeep_details` command instead of one flat total.
- Default upkeep period changed to 5 minutes.
- The map/claim-screen info panel now shows whether a chunk is currently force-loaded, or has a force load/unload queued that hasn't taken effect yet.
- The private-chunk-override screen now shows your saved preference struck-through in gray next to the actually-enforced value whenever they differ (e.g. the region can't currently afford to deliver it), instead of hiding one or the other - and it's never locked from editing anymore, so you can always change what you want even while it's not currently in effect.
- If a team officer changes a region's protection so it's now looser than a private chunk override in it, that override stops taking effect immediately - matching the region's actual baseline everywhere, including real enforcement - but your stored preference is never deleted, only dormant, and resumes automatically the moment the region can deliver it again (same as the non-payment case above).
- A force-loaded chunk now shows red (not blue) in the map/claim-screen info panel to match the Xaero map hatch, and pending/dropped-for-non-payment force-loads now also show a red hatch on Xaero's World Map, not just currently-active ones.
- A force-load that gets dropped because upkeep can no longer afford it is no longer lost outright - it's queued the same as a brand-new request and silently resumes the moment the team can afford it again.
- Force-loads are now dismantled one chunk at a time (same as protections/wars) and slot into the same priority order, between wars and protections, instead of being dropped all at once as a separate last-resort step. `/upkeep_priority` now lists force-loaded chunks alongside protections and wars in that same combined order.

### Fixes
- Fixed team-account spending permissions (buying/reselling marketplace chunks, force-loading chunks, placing tax collectors, declaring/ending wars, and the `upkeep_details`/`upkeep_priority` commands) being hardcoded to "Officer or better" regardless of the team's own settings. It now follows whatever access level the team owner has actually configured on the linked Lightman's Currency bank account (Owner only / Owner + Admins / everyone) - set a chunk-selling team to "Member" access and members can spend from it, exactly as configured.
- Fixed the Xaero marketplace-chunk hatch still showing team-tinted colors instead of a flat marketplace color, caused by draw order rather than the colors themselves.
- Fixed private chunk overrides being unreachable/uneditable for a chunk owned in another team's territory - affected the chunk override screen and both "Sell..." buttons.
- Fixed a privately-owned chunk's Allies/Team protection tier locking out its own private owner.
- Fixed the private-chunk-override screen showing raw `true`/`false` instead of "Protected"/"Unprotected", with color coding that didn't match the actual protection state.
- Fixed the World Map's private/country balance panels always showing empty regardless of actual balance.
- Fixed a misleading "not yours to sell" message that covered three different, unrelated reasons a chunk couldn't be listed.
- Fixed the map/claim-screen protection info panel showing an inverted color scheme and per-viewer information instead of the chunk's real, shared setting.
- Fixed dismantle/restoration/suspension chat messages not naming which Region (or which war) a line referred to.
- Fixed an outgoing war stuck "queued, not enough balance" never being reported anywhere.
- Fixed expropriating a privately-owned chunk never telling the affected owner - they now get a chat message (if online) naming who expropriated their chunk(s) and how much compensation they received.
- The server bank account can no longer silently overflow into a negative balance from an extreme accumulated total - once it would, further sink payments are burned instead of deposited, same as before the account existed.
- Fixed the server bank account showing up as "Unknown's Bank Account" instead of a proper name.
- Fixed the private chunk override screen's protection tooltip showing a price - loosening your own chunk below the region's baseline has never actually cost anything extra.
- Fixed `/upkeep_priority`'s output being a single uncolored wall of text.
- Fixed "Next payment"/"Period" showing as two separate, often-identical-looking lines in chat and tooltips - merged into one everywhere.
- Fixed the new war-system runtime toggle missing a currently-inactive team's stale war reference.
- Fixed a privately-owned chunk staying protected (both in display and in actual enforcement) after its region's protection was dismantled for non-payment - the chunk's own saved preference is unaffected and resumes automatically once the region's protection is restored.

### Dependencies
- Now built against NeoForge 21.1.251 and FTB Library 2101.1.36. The mod's own minimum required versions are unchanged (NeoForge 21.1.250, FTB Library 2101.1.35), so existing servers aren't forced to update immediately.

## [3.0.0]

### Regions (replaces the old Land/Build chunk split)
- Teams can now create any number of named **Regions** to subdivide their claimed territory, each with its own independent protection settings (mob griefing, explosions, PvP, block interact, block edit, entity interact) instead of one flat team-wide setting.
- New **Region List** screen (opened from a new button on the FTB Teams team screen) supports creating, renaming, deleting, and drag-and-drop reordering regions.
- New **Region Settings** screen shows live chunk count, per-property prices, and pending (queued) changes before they take effect.
- Every team keeps an auto-created, non-deletable "Default" region; existing claims migrate automatically.
- Upkeep dismantle order (which protections get dropped first when upkeep can't be paid) is now computed per-region, in the region list's own order.
- FTB Chunks' and Xaero's World Map now draw a border between two regions belonging to the same team, not just between different teams.

### Chunk Marketplace
- Officers/owners can list state-owned chunks for sale ("Sell as Country"), and players can buy them individually or as a team.
- Players can privately buy a chunk from their own team's territory; the chunk stays part of the team's land (upkeep/dismantle unaffected) but the private owner can loosen (never tighten) that one chunk's protection below the region's baseline.
- Private owners can resell their chunk, set a custom sale price, or add a short custom label shown in the chunk's info panel (e.g. "Region: Village - Shop").
- Officers/owners can expropriate a privately-owned chunk back to the team, with configurable compensation.
- A new **whitelist/blacklist** access list lets a private owner grant or deny specific players access to their chunk, independent of the team's own privacy settings.
- New **Team** privacy tier: alongside Public/Allies/Private, chunk privacy modes can now also be set to "whole team, any rank" — distinct from "Private" (owner/officers only, or the individual chunk owner if privately owned).
- Officers and the team owner always bypass a privately-owned chunk's protection within their own land, even if they're on that chunk's blacklist.
- Whitelist/blacklist settings now also apply when a chunk's own setting is looser than what the region would otherwise require, closing a gap where they could be bypassed entirely.
- A freshly **claimed** chunk can't be listed for sale until the team's next successful upkeep settlement (prevents instantly flipping newly-claimed land). This no longer applies to a chunk that was just privately bought — that one can be resold immediately.
- Real Emerald/Emerald Block currency (Lightman's Currency's separate "Emeralds" chain, distinct from its "Coins" chain's Emerald Coin) can no longer be used to price a chunk sale, matching the existing restriction on Emerald Coins.
- Offline (or never-seen-this-session) chunk owners now show their real name and skin in info panels instead of a raw UUID fragment.
- The always-visible balance panel on the World Map no longer includes a stray Emerald-chain balance alongside the actual (Coins-chain) balance.

### Xaero's Minimap / World Map integration
- Updated for compatibility with Xaero's Minimap 26.5.0 and World Map 1.46.0.
- Added fault tolerance across every Xaero integration point: a broken or incompatible Xaero API call now logs once and disables gracefully instead of crashing the game, and players without Xaero's World Map installed at all no longer risk a hard crash from the integration code.
- New always-visible money panel on the World Map (claim price, personal/private/country balance, force-load upkeep) plus a hover panel per claimed chunk (owner with skin head, region + custom label, sale price, and every protection property).
- New right-click marketplace/region menu on the World Map (Sell/Sell as Country/Buy/Buy as Country/Assign to Region), alongside the existing claim/unclaim/forceload options.

### Fixes
- Fixed a pricing bug where re-editing a region property to a different but same-tier value (e.g. Private → Allies after already queuing Private → Public) incorrectly showed as "pending" instead of applying immediately, because the comparison used the previously-queued value instead of the region's true live value.
- Fixed a crash opening Region Settings from Xaero's World Map (null parent reference on Back/Delete).
- Fixed the dev environment silently keeping stale-version Xaero jars around after a version bump.
- Fixed a missing translation causing raw text (`message.lc_ftb_hook.marketplace_expropriated`) to display after an expropriation.

### Other
- "Next payment due" is now shown in chat, the Region Settings screen, and the Region List screen's tooltips.
