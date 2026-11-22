package com.mekmp.tier;

import com.mekmp.config.MekmpServerConfig;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.fml.ModList;

import javax.annotation.Nullable;

public enum PumpTier {
    // Base Electric Pump = 1x (1,000 mB), 10,000 mB tank, 40 FE/t usage, 16,000 FE capacity
    // Pump amount & tank capacity scale by 4x per tier; energy usage & capacity scale by 2x per tier
    // requiredInstallerId = the tier installer required to upgrade INTO this tier
    ADVANCED("advanced", 4,     19, 40_000,      80,    32_000,    80, ResourceLocation.fromNamespaceAndPath("mekanism", "advanced_tier_installer")),
    ELITE("elite",       16,    19, 160_000,     160,   64_000,    80, ResourceLocation.fromNamespaceAndPath("mekanism", "elite_tier_installer")),
    ULTIMATE("ultimate", 64,    19, 640_000,     320,   128_000,   80, ResourceLocation.fromNamespaceAndPath("mekanism", "ultimate_tier_installer")),
    ABSOLUTE("absolute", 256,   19, 2_560_000,   640,   256_000,   80, ResourceLocation.fromNamespaceAndPath("mekanism_extras", "absolute_tier_installer")),
    SUPREME("supreme",   1024,  19, 10_240_000,  1_280, 512_000,   80, ResourceLocation.fromNamespaceAndPath("mekanism_extras", "supreme_tier_installer")),
    COSMIC("cosmic",     4096,  19, 40_960_000,  2_560, 1_024_000, 80, ResourceLocation.fromNamespaceAndPath("mekanism_extras", "cosmic_tier_installer")),
    INFINITE("infinite", 16384, 19, 163_840_000, 5_120, 2_048_000, 80, ResourceLocation.fromNamespaceAndPath("mekanism_extras", "infinite_tier_installer"));

    private final String id;
    private final int defaultPumpMultiplier;
    private final int defaultTicksPerOperation;
    private final int defaultTankCapacity;
    private final int defaultEnergyUsageFe;
    private final int defaultEnergyCapacityFe;
    private final int defaultMaxRange;
    private final ResourceLocation requiredInstallerId;

    PumpTier(String id, int defaultPumpMultiplier, int defaultTicksPerOperation, int defaultTankCapacity,
             int defaultEnergyUsageFe, int defaultEnergyCapacityFe, int defaultMaxRange,
             ResourceLocation requiredInstallerId) {
        this.id = id;
        this.defaultPumpMultiplier = defaultPumpMultiplier;
        this.defaultTicksPerOperation = defaultTicksPerOperation;
        this.defaultTankCapacity = defaultTankCapacity;
        this.defaultEnergyUsageFe = defaultEnergyUsageFe;
        this.defaultEnergyCapacityFe = defaultEnergyCapacityFe;
        this.defaultMaxRange = defaultMaxRange;
        this.requiredInstallerId = requiredInstallerId;
    }

    public String getId() { return id; }
    public String getPumpRegistryName() { return id + "_electric_pump"; }

    /**
     * Returns the tier installer item ID required to upgrade the previous tier into THIS tier.
     * e.g. Electric Pump + advanced_tier_installer -> Advanced Electric Pump.
     */
    public ResourceLocation getRequiredInstallerId() { return requiredInstallerId; }

    public boolean requiresMekanismExtras() {
        return "mekanism_extras".equals(requiredInstallerId.getNamespace());
    }

    public boolean isAvailable() {
        return !requiresMekanismExtras() || ModList.get().isLoaded("mekanism_extras");
    }

    public int getDefaultPumpMultiplier() { return defaultPumpMultiplier; }
    public int getDefaultTicksPerOperation() { return defaultTicksPerOperation; }
    public int getDefaultTankCapacity() { return defaultTankCapacity; }
    public int getDefaultEnergyUsageFe() { return defaultEnergyUsageFe; }
    public int getDefaultEnergyCapacityFe() { return defaultEnergyCapacityFe; }
    public int getDefaultMaxRange() { return defaultMaxRange; }

    public int getPumpMultiplier() { return MekmpServerConfig.getPumpMultiplier(this); }
    public int getTicksPerOperation() { return MekmpServerConfig.getTicksPerOperation(this); }
    public int getTankCapacity() { return MekmpServerConfig.getTankCapacity(this); }
    public int getEnergyUsageFe() { return MekmpServerConfig.getEnergyUsageFe(this); }
    public int getEnergyCapacityFe() { return MekmpServerConfig.getEnergyCapacityFe(this); }
    public int getMaxRange() { return MekmpServerConfig.getMaxRange(this); }

    @Nullable
    public PumpTier getNextTier() {
        int next = this.ordinal() + 1;
        return next < values().length ? values()[next] : null;
    }

    @Nullable
    public PumpTier getAvailableNextTier() {
        PumpTier next = getNextTier();
        return (next != null && next.isAvailable()) ? next : null;
    }
}
