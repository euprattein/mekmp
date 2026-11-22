package com.mekmp.registry;

import com.mekmp.MekMorePump;
import com.mekmp.block.TieredElectricPumpBlock;
import com.mekmp.tier.PumpTier;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredRegister;

import java.util.EnumMap;
import java.util.Map;

public class ModBlocks {
    public static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks(MekMorePump.MODID);
    public static final Map<PumpTier, DeferredBlock<TieredElectricPumpBlock>> PUMPS = new EnumMap<>(PumpTier.class);

    static {
        for (PumpTier tier : PumpTier.values()) {
            PUMPS.put(tier, BLOCKS.register(tier.getPumpRegistryName(),
                    () -> new TieredElectricPumpBlock(tier, BlockBehaviour.Properties.of()
                            .strength(3.5F, 9.0F)
                            .sound(SoundType.METAL)
                            .requiresCorrectToolForDrops()
                            .noOcclusion())));
        }
    }
}
