package com.mekmp.registry;

import com.mekmp.MekMorePump;
import com.mekmp.tier.PumpTier;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

import java.util.EnumMap;
import java.util.Map;

public class ModItems {
    public static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(MekMorePump.MODID);

    public static final Map<PumpTier, DeferredItem<BlockItem>> PUMP_ITEMS = new EnumMap<>(PumpTier.class);

    static {
        for (PumpTier tier : PumpTier.values()) {
            PUMP_ITEMS.put(tier, ITEMS.registerSimpleBlockItem(tier.getPumpRegistryName(), ModBlocks.PUMPS.get(tier)));
        }
    }

    public static Item getPumpItem(PumpTier tier) {
        return PUMP_ITEMS.get(tier).get();
    }
}
