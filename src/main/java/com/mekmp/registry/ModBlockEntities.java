package com.mekmp.registry;

import com.mekmp.MekMorePump;
import com.mekmp.block.entity.TieredElectricPumpBlockEntity;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public class ModBlockEntities {
    public static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITIES =
            DeferredRegister.create(Registries.BLOCK_ENTITY_TYPE, MekMorePump.MODID);

    @SuppressWarnings("DataFlowIssue")
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<TieredElectricPumpBlockEntity>> ELECTRIC_PUMP =
            BLOCK_ENTITIES.register("tiered_electric_pump", () -> {
                Block[] blocks = ModBlocks.PUMPS.values().stream()
                        .map(DeferredHolder::get)
                        .toArray(Block[]::new);
                return BlockEntityType.Builder.of(TieredElectricPumpBlockEntity::new, blocks).build(null);
            });
}
