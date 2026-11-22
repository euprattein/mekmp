package com.mekmp.registry;

import com.mekmp.MekMorePump;
import com.mekmp.menu.TieredElectricPumpMenu;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.inventory.MenuType;
import net.neoforged.neoforge.common.extensions.IMenuTypeExtension;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public class ModMenus {
    public static final DeferredRegister<MenuType<?>> MENUS =
            DeferredRegister.create(Registries.MENU, MekMorePump.MODID);

    public static final DeferredHolder<MenuType<?>, MenuType<TieredElectricPumpMenu>> ELECTRIC_PUMP_MENU =
            MENUS.register("tiered_electric_pump", () -> IMenuTypeExtension.create(TieredElectricPumpMenu::new));
}
