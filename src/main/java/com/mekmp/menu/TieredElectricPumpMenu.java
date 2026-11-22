package com.mekmp.menu;

import com.mekmp.block.TieredElectricPumpBlock;
import com.mekmp.block.entity.TieredElectricPumpBlockEntity;
import com.mekmp.registry.ModMenus;
import com.mekmp.tier.PumpTier;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.inventory.SimpleContainerData;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.material.Fluid;
import net.neoforged.neoforge.capabilities.Capabilities;

import javax.annotation.Nullable;
import java.util.function.Supplier;

public class TieredElectricPumpMenu extends AbstractContainerMenu {
    // Upgrade 3 slots column (where Mekanism's energy slot sits)
    public static final int UPGRADE_SLOT_X = 141;
    public static final int UPGRADE_SLOT_Y_START = 17;

    // Bucket / fluid container input & output slots (right next to the fluid tank gauge)
    public static final int BUCKET_SLOT_X = 29;
    public static final int BUCKET_IN_Y = 17;
    public static final int BUCKET_OUT_Y = 51;

    // Button IDs for clickMenuButton
    public static final int BTN_CYCLE_REDSTONE = 0;
    public static final int BTN_DUMP_TANK = 1;

    // ContainerData slot indices (32-bit values split into Lo 16-bit and Hi 16-bit to avoid vanilla short truncation)
    public static final int DATA_ENERGY_LO = 0;
    public static final int DATA_ENERGY_HI = 1;
    public static final int DATA_MAX_ENERGY_LO = 2;
    public static final int DATA_MAX_ENERGY_HI = 3;
    public static final int DATA_FLUID_LO = 4;
    public static final int DATA_FLUID_HI = 5;
    public static final int DATA_MAX_FLUID_LO = 6;
    public static final int DATA_MAX_FLUID_HI = 7;
    public static final int DATA_FLUID_ID = 8;
    public static final int DATA_STATUS = 9;
    public static final int DATA_USAGE_LO = 10;
    public static final int DATA_USAGE_HI = 11;
    public static final int DATA_REDSTONE_MODE = 12;
    public static final int DATA_TIER = 13;
    public static final int DATA_COUNT = 14;

    @Nullable
    private final TieredElectricPumpBlockEntity blockEntity;
    private final ContainerLevelAccess access;
    private final Container upgradeContainer;
    private final Container fluidSlotContainer;
    private final ContainerData data;

    public TieredElectricPumpMenu(int containerId, Inventory playerInv, FriendlyByteBuf extraData) {
        this(containerId, playerInv, extraData.readBlockPos());
    }

    private TieredElectricPumpMenu(int containerId, Inventory playerInv, BlockPos pos) {
        this(containerId, playerInv, getBlockEntity(playerInv, pos),
                ContainerLevelAccess.create(playerInv.player.level(), pos),
                new SimpleContainerData(DATA_COUNT));
    }

    public TieredElectricPumpMenu(int containerId, Inventory playerInv, @Nullable TieredElectricPumpBlockEntity be, ContainerData data) {
        this(containerId, playerInv, be,
                (be != null && be.getLevel() != null)
                        ? ContainerLevelAccess.create(be.getLevel(), be.getBlockPos())
                        : ContainerLevelAccess.NULL,
                data);
    }

    private TieredElectricPumpMenu(int containerId, Inventory playerInv, @Nullable TieredElectricPumpBlockEntity be,
                                   ContainerLevelAccess access, ContainerData data) {
        super(ModMenus.ELECTRIC_PUMP_MENU.get(), containerId);
        this.blockEntity = be;
        this.access = access;
        this.upgradeContainer = be != null ? be.getUpgradeInventory() : new SimpleContainer(3);
        this.fluidSlotContainer = be != null ? be.getFluidSlotInventory() : new SimpleContainer(2);
        this.data = data;

        // Slot 0 (Top Right): Speed Upgrade ONLY, max 8
        this.addSlot(new FixedUpgradeSlot(upgradeContainer, TieredElectricPumpBlockEntity.SLOT_SPEED,
                UPGRADE_SLOT_X, UPGRADE_SLOT_Y_START,
                TieredElectricPumpBlockEntity::getSpeedUpgradeItem, 8));

        // Slot 1 (Middle Right): Energy Upgrade ONLY, max 8
        this.addSlot(new FixedUpgradeSlot(upgradeContainer, TieredElectricPumpBlockEntity.SLOT_ENERGY,
                UPGRADE_SLOT_X, UPGRADE_SLOT_Y_START + 18,
                TieredElectricPumpBlockEntity::getEnergyUpgradeItem, 8));

        // Slot 2 (Bottom Right): Filter Upgrade ONLY, max 1
        this.addSlot(new FixedUpgradeSlot(upgradeContainer, TieredElectricPumpBlockEntity.SLOT_FILTER,
                UPGRADE_SLOT_X, UPGRADE_SLOT_Y_START + 36,
                TieredElectricPumpBlockEntity::getFilterUpgradeItem, 1));

        // Slot 3 (Top Left next to tank): Fluid Container Input (Buckets, Fluid Tanks, etc.)
        this.addSlot(new Slot(fluidSlotContainer, TieredElectricPumpBlockEntity.SLOT_FLUID_IN, BUCKET_SLOT_X, BUCKET_IN_Y) {
            @Override
            public boolean mayPlace(ItemStack stack) {
                return stack.getCapability(Capabilities.FluidHandler.ITEM) != null;
            }
        });

        // Slot 4 (Bottom Left next to tank): Filled Fluid Container Output
        this.addSlot(new Slot(fluidSlotContainer, TieredElectricPumpBlockEntity.SLOT_FLUID_OUT, BUCKET_SLOT_X, BUCKET_OUT_Y) {
            @Override
            public boolean mayPlace(ItemStack stack) {
                return false;
            }
        });

        // Player Inventory (3x9)
        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 9; col++) {
                this.addSlot(new Slot(playerInv, col + row * 9 + 9, 8 + col * 18, 84 + row * 18));
            }
        }
        // Hotbar (1x9)
        for (int col = 0; col < 9; col++) {
            this.addSlot(new Slot(playerInv, col, 8 + col * 18, 142));
        }

        this.addDataSlots(data);
    }

    @Nullable
    private static TieredElectricPumpBlockEntity getBlockEntity(Inventory playerInv, BlockPos pos) {
        if (playerInv.player.level().getBlockEntity(pos) instanceof TieredElectricPumpBlockEntity be) {
            return be;
        }
        return null;
    }

    @Override
    public boolean clickMenuButton(Player player, int id) {
        if (!this.stillValid(player)) {
            return false;
        }
        if (this.blockEntity != null && !player.level().isClientSide) {
            if (id == BTN_CYCLE_REDSTONE) {
                this.blockEntity.cycleRedstoneMode();
                return true;
            } else if (id == BTN_DUMP_TANK) {
                this.blockEntity.dumpFluidTank();
                return true;
            }
        }
        return false;
    }

    private int getInt32(int loIndex, int hiIndex) {
        int lo = data.get(loIndex) & 0xFFFF;
        int hi = data.get(hiIndex) & 0xFFFF;
        return (hi << 16) | lo;
    }

    public int getEnergy() { return getInt32(DATA_ENERGY_LO, DATA_ENERGY_HI); }
    public int getMaxEnergy() { return Math.max(1, getInt32(DATA_MAX_ENERGY_LO, DATA_MAX_ENERGY_HI)); }
    public int getFluidAmount() { return getInt32(DATA_FLUID_LO, DATA_FLUID_HI); }
    public int getMaxFluid() { return Math.max(1, getInt32(DATA_MAX_FLUID_LO, DATA_MAX_FLUID_HI)); }
    public Fluid getFluidType() { return BuiltInRegistries.FLUID.byId(data.get(DATA_FLUID_ID)); }
    public int getStatus() { return data.get(DATA_STATUS); }
    public int getEnergyUsageFe() { return getInt32(DATA_USAGE_LO, DATA_USAGE_HI); }

    public TieredElectricPumpBlockEntity.RedstoneMode getRedstoneMode() {
        return TieredElectricPumpBlockEntity.RedstoneMode.byOrdinal(data.get(DATA_REDSTONE_MODE));
    }

    public PumpTier getPumpTier() {
        PumpTier[] vals = PumpTier.values();
        int ord = data.get(DATA_TIER);
        return (ord >= 0 && ord < vals.length) ? vals[ord] : PumpTier.ADVANCED;
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        ItemStack result = ItemStack.EMPTY;
        Slot slot = this.slots.get(index);
        if (slot.hasItem()) {
            ItemStack stack = slot.getItem();
            result = stack.copy();
            if (index < 5) {
                if (!this.moveItemStackTo(stack, 5, 41, true)) return ItemStack.EMPTY;
            } else {
                Item speedItem = TieredElectricPumpBlockEntity.getSpeedUpgradeItem();
                Item energyItem = TieredElectricPumpBlockEntity.getEnergyUpgradeItem();
                Item filterItem = TieredElectricPumpBlockEntity.getFilterUpgradeItem();
                if (speedItem != Items.AIR && stack.is(speedItem)) {
                    if (!this.moveItemStackTo(stack, 0, 1, false)) return ItemStack.EMPTY;
                } else if (energyItem != Items.AIR && stack.is(energyItem)) {
                    if (!this.moveItemStackTo(stack, 1, 2, false)) return ItemStack.EMPTY;
                } else if (filterItem != Items.AIR && stack.is(filterItem)) {
                    if (!this.moveItemStackTo(stack, 2, 3, false)) return ItemStack.EMPTY;
                } else if (stack.getCapability(Capabilities.FluidHandler.ITEM) != null) {
                    if (!this.moveItemStackTo(stack, 3, 4, false)) return ItemStack.EMPTY;
                } else {
                    return ItemStack.EMPTY;
                }
            }
            if (stack.isEmpty()) slot.set(ItemStack.EMPTY);
            else slot.setChanged();
        }
        return result;
    }

    @Override
    public boolean stillValid(Player player) {
        if (this.blockEntity != null && this.blockEntity.isRemoved()) {
            return false;
        }
        return access.evaluate((level, pos) ->
                        (this.blockEntity == null || level.getBlockEntity(pos) == this.blockEntity)
                                && level.getBlockState(pos).getBlock() instanceof TieredElectricPumpBlock
                                && player.distanceToSqr(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5) <= 64.0,
                true);
    }

    private static class FixedUpgradeSlot extends Slot {
        private final Supplier<Item> allowedUpgradeSupplier;
        private final int maxCount;

        public FixedUpgradeSlot(Container container, int slot, int x, int y, Supplier<Item> allowedUpgradeSupplier, int maxCount) {
            super(container, slot, x, y);
            this.allowedUpgradeSupplier = allowedUpgradeSupplier;
            this.maxCount = maxCount;
        }

        @Override
        public boolean mayPlace(ItemStack stack) {
            Item allowed = allowedUpgradeSupplier.get();
            return allowed != Items.AIR && stack.is(allowed);
        }

        @Override
        public int getMaxStackSize() {
            return maxCount;
        }

        @Override
        public int getMaxStackSize(ItemStack stack) {
            return maxCount;
        }
    }
}
