package com.mekmp.config;

import com.mekmp.tier.PumpTier;
import net.neoforged.neoforge.common.ModConfigSpec;

import java.util.EnumMap;
import java.util.Locale;
import java.util.Map;

public final class MekmpServerConfig {
    public static final ModConfigSpec SPEC;

    public static final ModConfigSpec.IntValue WATER_BASE_AMOUNT_MB;
    public static final ModConfigSpec.IntValue HEAVY_WATER_BASE_AMOUNT_MB;

    private static final Map<PumpTier, TierConfigEntry> TIER_ENTRIES = new EnumMap<>(PumpTier.class);

    static {
        ModConfigSpec.Builder builder = new ModConfigSpec.Builder();

        builder.comment("General settings for MekMorePump").push("general");

        WATER_BASE_AMOUNT_MB = builder
                .comment("Base water amount pumped per operation (mB) before tier multiplier.")
                .defineInRange("waterBaseAmountMb", 1000, 1, 1_000_000);

        HEAVY_WATER_BASE_AMOUNT_MB = builder
                .comment("Base heavy water amount pumped per operation (mB) with Filter Upgrade before tier multiplier.")
                .defineInRange("heavyWaterBaseAmountMb", 10, 1, 1_000_000);

        builder.pop();

        builder.comment("Per-tier specifications for Tiered Electric Pumps").push("tiers");

        for (PumpTier tier : PumpTier.values()) {
            String sectionName = tier.name().toLowerCase(Locale.ROOT);
            builder.push(sectionName);

            int defaultTankCap = Math.max(1000, tier.getDefaultTankCapacity());
            int defaultEnergyCap = Math.max(1000, tier.getDefaultEnergyCapacityFe());
            int defaultEnergyUse = Math.max(1, tier.getDefaultEnergyUsageFe());
            int defaultTicks = Math.max(1, tier.getDefaultTicksPerOperation());
            int defaultMultiplier = Math.max(1, tier.getDefaultPumpMultiplier());
            int defaultRange = Math.max(1, tier.getDefaultMaxRange());
            int defaultChainBlocks = Math.max(1, 1 << (tier.ordinal() + 1));

            ModConfigSpec.IntValue tankCapacity = builder
                    .comment("Internal fluid tank capacity (mB).")
                    .defineInRange("tankCapacity", defaultTankCap, 1000, Integer.MAX_VALUE);

            ModConfigSpec.IntValue energyCapacityFe = builder
                    .comment("Internal energy buffer capacity (FE) before Energy Upgrades.")
                    .defineInRange("energyCapacityFe", defaultEnergyCap, 1000, Integer.MAX_VALUE);

            ModConfigSpec.IntValue energyUsageFe = builder
                    .comment("Base energy consumption per tick while running (FE/t) before Upgrades.")
                    .defineInRange("energyUsageFe", defaultEnergyUse, 1, Integer.MAX_VALUE);

            ModConfigSpec.IntValue ticksPerOperation = builder
                    .comment("Ticks required per pumping operation before Speed Upgrades (lower = faster).")
                    .defineInRange("ticksPerOperation", defaultTicks, 1, 1200);

            ModConfigSpec.IntValue pumpMultiplier = builder
                    .comment("Multiplier applied to base water/heavy water amount per operation.")
                    .defineInRange("pumpMultiplier", defaultMultiplier, 1, 10_000);

            ModConfigSpec.IntValue maxRange = builder
                    .comment("Maximum Manhattan distance for chaining non-water fluid sources.")
                    .defineInRange("maxRange", defaultRange, 1, 512);

            ModConfigSpec.IntValue maxChainBlocks = builder
                    .comment("Maximum non-water source blocks extracted per single operation.")
                    .defineInRange("maxChainBlocksPerOperation", defaultChainBlocks, 1, 256);

            TIER_ENTRIES.put(tier, new TierConfigEntry(
                    tankCapacity,
                    energyCapacityFe,
                    energyUsageFe,
                    ticksPerOperation,
                    pumpMultiplier,
                    maxRange,
                    maxChainBlocks
            ));

            builder.pop();
        }

        builder.pop();

        SPEC = builder.build();
    }

    private MekmpServerConfig() {
    }

    public static int getWaterBaseAmountMb() {
        return SPEC.isLoaded() ? WATER_BASE_AMOUNT_MB.get() : 1000;
    }

    public static int getHeavyWaterBaseAmountMb() {
        return SPEC.isLoaded() ? HEAVY_WATER_BASE_AMOUNT_MB.get() : 10;
    }

    public static int getTankCapacity(PumpTier tier) {
        TierConfigEntry entry = TIER_ENTRIES.get(tier);
        if (SPEC.isLoaded() && entry != null) {
            return entry.tankCapacity().get();
        }
        return tier.getDefaultTankCapacity();
    }

    public static int getEnergyCapacityFe(PumpTier tier) {
        TierConfigEntry entry = TIER_ENTRIES.get(tier);
        if (SPEC.isLoaded() && entry != null) {
            return entry.energyCapacityFe().get();
        }
        return tier.getDefaultEnergyCapacityFe();
    }

    public static int getEnergyUsageFe(PumpTier tier) {
        TierConfigEntry entry = TIER_ENTRIES.get(tier);
        if (SPEC.isLoaded() && entry != null) {
            return entry.energyUsageFe().get();
        }
        return tier.getDefaultEnergyUsageFe();
    }

    public static int getTicksPerOperation(PumpTier tier) {
        TierConfigEntry entry = TIER_ENTRIES.get(tier);
        if (SPEC.isLoaded() && entry != null) {
            return entry.ticksPerOperation().get();
        }
        return tier.getDefaultTicksPerOperation();
    }

    public static int getPumpMultiplier(PumpTier tier) {
        TierConfigEntry entry = TIER_ENTRIES.get(tier);
        if (SPEC.isLoaded() && entry != null) {
            return entry.pumpMultiplier().get();
        }
        return tier.getDefaultPumpMultiplier();
    }

    public static int getMaxRange(PumpTier tier) {
        TierConfigEntry entry = TIER_ENTRIES.get(tier);
        if (SPEC.isLoaded() && entry != null) {
            return entry.maxRange().get();
        }
        return tier.getDefaultMaxRange();
    }

    public static int getMaxChainBlocksPerOperation(PumpTier tier) {
        TierConfigEntry entry = TIER_ENTRIES.get(tier);
        if (SPEC.isLoaded() && entry != null) {
            return entry.maxChainBlocks().get();
        }
        return 1 << (tier.ordinal() + 1);
    }

    private record TierConfigEntry(
            ModConfigSpec.IntValue tankCapacity,
            ModConfigSpec.IntValue energyCapacityFe,
            ModConfigSpec.IntValue energyUsageFe,
            ModConfigSpec.IntValue ticksPerOperation,
            ModConfigSpec.IntValue pumpMultiplier,
            ModConfigSpec.IntValue maxRange,
            ModConfigSpec.IntValue maxChainBlocks
    ) {
    }
}
