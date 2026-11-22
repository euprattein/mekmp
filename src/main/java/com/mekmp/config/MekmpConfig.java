package com.mekmp.config;

import com.mekmp.tier.PumpTier;
import net.neoforged.neoforge.common.ModConfigSpec;

import java.util.EnumMap;
import java.util.Map;

public class MekmpConfig {
    public static final ModConfigSpec SPEC;
    private static final Map<PumpTier, TierConfig> TIER_CONFIGS = new EnumMap<>(PumpTier.class);

    static {
        ModConfigSpec.Builder builder = new ModConfigSpec.Builder();
        builder.comment("Mekanism: More Pump Configuration");

        for (PumpTier tier : PumpTier.values()) {
            builder.push(tier.getId());
            ModConfigSpec.IntValue pumpMultiplier = builder
                    .comment("Fluid amount multiplier per pumping operation (1 = 1,000 mB, 2 = 2,000 mB, etc.). Default: " + tier.getDefaultPumpMultiplier())
                    .defineInRange("pumpMultiplier", tier.getDefaultPumpMultiplier(), 1, 1_000_000);
            ModConfigSpec.IntValue ticksPerOp = builder
                    .comment("Base ticks per pumping operation (lower = faster). Default: " + tier.getDefaultTicksPerOperation())
                    .defineInRange("ticksPerOperation", tier.getDefaultTicksPerOperation(), 1, 1200);
            ModConfigSpec.IntValue tankCap = builder
                    .comment("Internal fluid tank capacity in mB. Default: " + tier.getDefaultTankCapacity())
                    .defineInRange("tankCapacity", tier.getDefaultTankCapacity(), 1000, Integer.MAX_VALUE);
            ModConfigSpec.IntValue energyUsage = builder
                    .comment("Base energy usage in FE/t while pumping. Default: " + tier.getDefaultEnergyUsageFe())
                    .defineInRange("energyUsageFe", tier.getDefaultEnergyUsageFe(), 1, Integer.MAX_VALUE);
            ModConfigSpec.IntValue energyCap = builder
                    .comment("Internal energy buffer capacity in FE. Default: " + tier.getDefaultEnergyCapacityFe())
                    .defineInRange("energyCapacityFe", tier.getDefaultEnergyCapacityFe(), 1000, Integer.MAX_VALUE);
            ModConfigSpec.IntValue maxRange = builder
                    .comment("Maximum pumping range in blocks. Default: " + tier.getDefaultMaxRange())
                    .defineInRange("maxRange", tier.getDefaultMaxRange(), 1, 512);
            builder.pop();

            TIER_CONFIGS.put(tier, new TierConfig(pumpMultiplier, ticksPerOp, tankCap, energyUsage, energyCap, maxRange));
        }

        SPEC = builder.build();
    }

    public static int getPumpMultiplier(PumpTier tier) {
        TierConfig cfg = TIER_CONFIGS.get(tier);
        return (cfg != null && SPEC.isLoaded()) ? cfg.pumpMultiplier.get() : tier.getDefaultPumpMultiplier();
    }

    public static int getTicksPerOperation(PumpTier tier) {
        TierConfig cfg = TIER_CONFIGS.get(tier);
        return (cfg != null && SPEC.isLoaded()) ? cfg.ticksPerOperation.get() : tier.getDefaultTicksPerOperation();
    }

    public static int getTankCapacity(PumpTier tier) {
        TierConfig cfg = TIER_CONFIGS.get(tier);
        return (cfg != null && SPEC.isLoaded()) ? cfg.tankCapacity.get() : tier.getDefaultTankCapacity();
    }

    public static int getEnergyUsageFe(PumpTier tier) {
        TierConfig cfg = TIER_CONFIGS.get(tier);
        return (cfg != null && SPEC.isLoaded()) ? cfg.energyUsageFe.get() : tier.getDefaultEnergyUsageFe();
    }

    public static int getEnergyCapacityFe(PumpTier tier) {
        TierConfig cfg = TIER_CONFIGS.get(tier);
        return (cfg != null && SPEC.isLoaded()) ? cfg.energyCapacityFe.get() : tier.getDefaultEnergyCapacityFe();
    }

    public static int getMaxRange(PumpTier tier) {
        TierConfig cfg = TIER_CONFIGS.get(tier);
        return (cfg != null && SPEC.isLoaded()) ? cfg.maxRange.get() : tier.getDefaultMaxRange();
    }

    private record TierConfig(
            ModConfigSpec.IntValue pumpMultiplier,
            ModConfigSpec.IntValue ticksPerOperation,
            ModConfigSpec.IntValue tankCapacity,
            ModConfigSpec.IntValue energyUsageFe,
            ModConfigSpec.IntValue energyCapacityFe,
            ModConfigSpec.IntValue maxRange
    ) {}
}
