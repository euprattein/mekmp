package com.mekmp.registry;

import com.mekmp.MekMorePump;
import com.mekmp.tier.PumpTier;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public class ModCreativeTabs {
    public static final DeferredRegister<CreativeModeTab> TABS =
            DeferredRegister.create(Registries.CREATIVE_MODE_TAB, MekMorePump.MODID);

    public static final DeferredHolder<CreativeModeTab, CreativeModeTab> MAIN_TAB =
            TABS.register("main", () -> CreativeModeTab.builder()
                    .title(Component.translatable("itemGroup.mekmp"))
                    .icon(() -> new ItemStack(ModItems.PUMP_ITEMS.get(PumpTier.ADVANCED).get()))
                    .displayItems((params, output) -> {
                        for (PumpTier tier : PumpTier.values()) {
                            if (tier.isAvailable()) {
                                output.accept(ModItems.PUMP_ITEMS.get(tier).get());
                            }
                        }
                    })
                    .build());
}
