package dev.malik.lcftbhook.config;

import net.neoforged.neoforge.common.ModConfigSpec;


public final class LCFtbHookConfig {
    public static final ModConfigSpec SERVER_SPEC;
    public static final Server SERVER;

    static {
        ModConfigSpec.Builder builder = new ModConfigSpec.Builder();
        SERVER = new Server(builder);
        SERVER_SPEC = builder.build();
    }

    private LCFtbHookConfig() {
    }

    public static final class Server {
        public final ModConfigSpec.LongValue claimPrice;
        public final ModConfigSpec.EnumValue<ClaimPricingMode> claimPricingMode;
        public final ModConfigSpec.LongValue claimBasePrice;
        public final ModConfigSpec.LongValue claimIncrementPrice;
        public final ModConfigSpec.IntValue claimIncrementSize;
        public final ModConfigSpec.DoubleValue claimPriceGrowthPercent;
        public final ModConfigSpec.IntValue freeChunks;
        public final ModConfigSpec.DoubleValue unclaimRefundRatio;
        public final ModConfigSpec.LongValue forceLoadUpkeepPrice;
        public final ModConfigSpec.IntValue upkeepPeriodMinutes;
        public final ModConfigSpec.BooleanValue settleWhileEmpty;
        public final ModConfigSpec.EnumValue<ForceLoadUpkeepMode> forceLoadUpkeepMode;
        public final ModConfigSpec.EnumValue<ProtectionUpkeepMode> protectionUpkeepMode;
        public final ModConfigSpec.DoubleValue warCostMultiplier;
        public final ModConfigSpec.DoubleValue warOutgoingCostMultiplier;
        public final ModConfigSpec.BooleanValue warEnabled;
        public final ModConfigSpec.BooleanValue lockClaimVisibilityPublic;
        public final ModConfigSpec.ConfigValue<String> claimBackend;
        public final ModConfigSpec.ConfigValue<String> currencyBackend;

        Server(ModConfigSpec.Builder builder) {
            builder.comment(
                    "LC FTB Hook server configuration",
                    "",
                    "\"Copper\" means the smallest coin of the active currency mod:",
                    "  Lightman's Currency: the copper coin (iron = 10, gold = 100, emerald = 1000,",
                    "                       diamond = 10000, netherite = 100000 copper)",
                    "  Create: Numismatics:  the spur (bevel = 8, sprocket = 16, cog = 64,",
                    "                       crown = 512, sun = 4096 spurs)",
                    "The same number therefore buys more in Lightman's coins than in Numismatics'",
                    "coins: 10000 copper is 1 diamond coin, but 2 suns and change. Every default",
                    "below is written out in both currencies.",
                    "",
                    "Every price value below (claim prices, force-load upkeep, and the protection",
                    "prices in " + ProtectionPriceConfig.FILE_NAME + ") is denominated in TENTHS of a",
                    "copper - a value of 10 means 1 copper (1 Lightman's copper coin / 1 Numismatics",
                    "spur), a value of 8 means 0.8 copper. This lets a price be a fraction of a",
                    "copper without needing decimals; the actual bill is always rounded DOWN to",
                    "the nearest whole copper. Protection prices are additionally PER CHUNK that",
                    "has the protection active - multiplied by the chunk count BEFORE dividing by",
                    "10 and rounding down, e.g. a value of 8 (0.8 copper/chunk) on 3 such chunks",
                    "bills floor(0.8 * 3) = floor(2.4) = 2 copper, not floor(0.8) * 3 = 0. Claim",
                    "prices and force-load upkeep are flat amounts - just divide by 10."
            ).push("general");

            claimPrice = builder
                    .comment(
                            "Only used when claimPricingMode = CONSTANT. Cost to claim one chunk, the",
                            "same for every claim regardless of how many the team already has.",
                            "Default: 100000 (= " + CoinExamples.ofTenths(100_000L) + ")."
                    )
                    .defineInRange("claimPrice", 100_000L, 0L, Long.MAX_VALUE);

            claimPricingMode = builder
                    .comment(
                            "How the price of a team's NEXT chunk claim scales with how many chunks they already have.",
                            "The team's currently claimed chunk count (excluding any free-allowance chunks - see freeChunks",
                            "below) determines which \"increment\" (price tier) the next claim falls into; unclaiming lowers",
                            "the count and therefore the tier again, so the price a team pays always reflects their CURRENT",
                            "chunk count, not a running total. Every increment always starts counting from 0.",
                            "  CONSTANT    - flat price for every claim: price = claimPrice.",
                            "  LINEAR      - price grows by a fixed amount every claimIncrementSize claims:",
                            "                increment = floor(chunksAlreadyClaimed / claimIncrementSize)",
                            "                price = claimBasePrice + increment * claimIncrementPrice",
                            "  EXPONENTIAL - price grows by a fixed PERCENTAGE every claimIncrementSize claims:",
                            "                increment = floor(chunksAlreadyClaimed / claimIncrementSize)",
                            "                price = claimBasePrice * (1 + claimPriceGrowthPercent) ^ increment",
                            "All of the above is computed in tenths of a copper (see the note at the top of this",
                            "file); the result is always rounded DOWN to the nearest whole copper before being charged."
                    )
                    .defineEnum("claimPricingMode", ClaimPricingMode.EXPONENTIAL);

            claimBasePrice = builder
                    .comment(
                            "Used by claimPricingMode = LINEAR and EXPONENTIAL only: the price of a claim at increment 0",
                            "(a team's first claimIncrementSize chunks). Ignored under CONSTANT (see claimPrice instead).",
                            "Default: 100000 (= " + CoinExamples.ofTenths(100_000L) + ")."
                    )
                    .defineInRange("claimBasePrice", 100_000L, 0L, Long.MAX_VALUE);

            claimIncrementSize = builder
                    .comment(
                            "Used by claimPricingMode = LINEAR and EXPONENTIAL only: how many chunks make up one price tier",
                            "(\"increment\"). Ignored under CONSTANT. Default: 20 (matches the default EXPONENTIAL 5% step)."
                    )
                    .defineInRange("claimIncrementSize", 20, 1, Integer.MAX_VALUE);

            claimIncrementPrice = builder
                    .comment(
                            "Used by claimPricingMode = LINEAR only: how much the price goes up by per increment",
                            "(every claimIncrementSize claims). Ignored by CONSTANT and EXPONENTIAL.",
                            "Default: 10000 (= " + CoinExamples.ofTenths(10_000L) + ")."
                    )
                    .defineInRange("claimIncrementPrice", 10_000L, 0L, Long.MAX_VALUE);

            claimPriceGrowthPercent = builder
                    .comment(
                            "Used by claimPricingMode = EXPONENTIAL only: the fractional price increase per increment",
                            "(every claimIncrementSize claims), e.g. 0.05 = +5% per increment, compounding. Ignored by",
                            "CONSTANT and LINEAR. Default: 0.05 (5% every 20 claims)."
                    )
                    .defineInRange("claimPriceGrowthPercent", 0.05D, 0.0D, 1.0D);

            freeChunks = builder
                    .comment("The first N claimed chunks per team or player are free to claim and exempt from protection upkeep")
                    .defineInRange("freeChunks", 0, 0, Integer.MAX_VALUE);

            unclaimRefundRatio = builder
                    .comment("Fraction of the claim price refunded when unclaiming a chunk (0 = none, 1 = full refund, 0.8 = 80%)")
                    .defineInRange("unclaimRefundRatio", 0.8D, 0.0D, 1.0D);

            forceLoadUpkeepPrice = builder
                    .comment("Upkeep cost per force-loaded chunk per upkeep period (force-loading itself is free).",
                            "Default: 1000000 (= " + CoinExamples.ofTenths(1_000_000L) + ").")
                    .defineInRange("forceLoadUpkeepPrice", 1_000_000L, 0L, Long.MAX_VALUE);

            upkeepPeriodMinutes = builder
                    .comment("How often upkeep is charged, in real-time minutes")
                    .defineInRange("upkeepPeriodMinutes", 5, 1, 10080);

            settleWhileEmpty = builder
                    .comment(
                            "Run upkeep settlement passes even when no players are online.",
                            "false (default) = the settlement clock pauses while the server is empty",
                            "  (current behavior): nothing is charged or dismantled until someone logs in,",
                            "  and force-loads/protections run unbilled in the meantime.",
                            "true = settlement runs on schedule around the clock. Set this if you rely on",
                            "  forceLoadUpkeepMode=ALWAYS or protectionUpkeepMode=HEAVY - those modes cannot",
                            "  charge while the server is empty without it. Team notifications (restored,",
                            "  suspended, unaffordable) only ever reach online members, so dismantles that",
                            "  happen while nobody is on are visible in the upkeep overview but not messaged."
                    )
                    .define("settleWhileEmpty", false);

            lockClaimVisibilityPublic = builder
                    .comment(
                            "Whether a team's claim_visibility (FTB Chunks' own native setting for who can see a team's",
                            "claims on the map) is force-locked to Public and can't be changed. When true (default), any",
                            "attempt to change it is reverted and the control is disabled in FTB's team settings screen -",
                            "matches the original design intent of always making claims visible (e.g. so wars/marketplace",
                            "targeting can't be dodged by hiding). Set to false to let teams set their own claim visibility",
                            "(Public/Allies/Team) freely again, same as vanilla FTB Chunks."
                    )
                    .define("lockClaimVisibilityPublic", true);

            forceLoadUpkeepMode = builder
                    .comment(
                            "When force-load upkeep is actually charged for a period:",
                            "ALWAYS = charged every period regardless of who is online.",
                            "DEFAULT = charged only when at least one of the team's own members is online."
                    )
                    .defineEnum("forceLoadUpkeepMode", ForceLoadUpkeepMode.DEFAULT);

            protectionUpkeepMode = builder
                    .comment(
                            "When protection upkeep is actually charged (and can therefore be dismantled for non-payment) for a period:",
                            "HEAVY = charged even when nobody is online at all (not recommended).",
                            "DEFAULT = charged when at least one player is online anywhere on the server.",
                            "LIGHT = charged only when at least one of the team's own members is online."
                    )
                    .defineEnum("protectionUpkeepMode", ProtectionUpkeepMode.DEFAULT);

            builder.pop();
            builder.comment(
                    "Which mod backs each of this hook's pluggable systems. AUTO (default) picks the",
                    "highest-priority one that's actually installed - if only one candidate for a given",
                    "system is installed, that one is always used regardless of this setting. Set an",
                    "explicit value to force a specific backend even when a higher-priority one is also",
                    "installed (e.g. to run OPAC on a server that has FTB Chunks installed only for an",
                    "unrelated reason). An unknown or not-installed explicit value falls back to AUTO",
                    "with a startup warning, it never fails to start."
            ).push("backends");

            claimBackend = builder
                    .comment(
                            "Backend for team/chunk-claim management. Priority order under AUTO: FTB (FTB Teams +",
                            "FTB Chunks) first, then OPAC (Open Parties and Claims). Valid values: AUTO, FTB, OPAC."
                    )
                    .define("claimBackend", "AUTO");

            currencyBackend = builder
                    .comment(
                            "Backend for money/banking. Priority order under AUTO: LIGHTMANS_CURRENCY first, then",
                            "NUMISMATICS. Valid values: AUTO, LIGHTMANS_CURRENCY, NUMISMATICS."
                    )
                    .define("currencyBackend", "AUTO");

            builder.pop();
            builder.comment("War declarations between claim teams (teams or solo players with claimed chunks)").push("war");

            warEnabled = builder
                    .comment("Enable the war system. When false, war costs are ignored, war actions are blocked, and the war button is hidden on clients.")
                    .define("warEnabled", true);

            warOutgoingCostMultiplier = builder
                    .comment("Flat multiplier x for outgoing war cost. Declaring war on a team costs x * their base upkeep per period, regardless of how many wars you have declared.")
                    .defineInRange("warOutgoingCostMultiplier", 2.0D, 0.0D, 100.0D);

            warCostMultiplier = builder
                    .comment("Incoming war exponent l. With base upkeep b and k incoming wars, the incoming surcharge is b * sum(l^n for n=0..k-1). First incoming war uses l^0 = 1.")
                    .defineInRange("warCostMultiplier", 1.2D, 1.0D, 100.0D);

            builder.pop();
        }
    }
}
