package com.mekmp.event;

import com.mekmp.block.TieredElectricPumpBlock;
import com.mekmp.block.entity.TieredElectricPumpBlockEntity;
import com.mekmp.registry.ModBlocks;
import com.mekmp.tier.PumpTier;
import mekanism.api.Upgrade;
import mekanism.api.inventory.IInventorySlot;
import mekanism.api.inventory.IMekanismInventory;
import mekanism.api.security.IBlockSecurityUtils;
import mekanism.common.tile.component.TileComponentUpgrade;
import mekanism.common.tile.interfaces.IRedstoneControl;
import mekanism.common.tile.interfaces.IUpgradeTile;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.Containers;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.Property;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.energy.IEnergyStorage;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;

import javax.annotation.Nullable;
import java.util.List;

public class TierUpgradeHandler {
    private static final ResourceLocation MEKANISM_ELECTRIC_PUMP_ID =
            ResourceLocation.fromNamespaceAndPath("mekanism", "electric_pump");

    public static void onRightClickBlock(PlayerInteractEvent.RightClickBlock event) {
        Player player = event.getEntity();
        if (!player.isShiftKeyDown()) {
            return;
        }

        Level level = event.getLevel();
        BlockPos pos = event.getPos();
        BlockState state = level.getBlockState(pos);
        Block block = state.getBlock();
        ItemStack heldStack = event.getItemStack();

        // If main hand is empty while sneaking with an applicable item in offhand,
        // pass the main-hand event so NeoForge immediately invokes the offhand RightClickBlock event.
        if (event.getHand() == InteractionHand.MAIN_HAND && heldStack.isEmpty()) {
            ItemStack offhandStack = player.getOffhandItem();
            if (!offhandStack.isEmpty() && canHandleWithItem(block, offhandStack)) {
                event.setCanceled(true);
                event.setCancellationResult(InteractionResult.PASS);
            }
            return;
        }

        if (heldStack.isEmpty()) {
            return;
        }

        // 0. Sneak + Right-Click with Mekanism Upgrade items on TieredElectricPumpBlock -> quick-insert upgrades
        if (block instanceof TieredElectricPumpBlock && isMekanismPumpUpgrade(heldStack)) {
            if (!canModifyBlock(level, pos, player, event.getFace(), heldStack, null)) {
                event.setCanceled(true);
                event.setCancellationResult(InteractionResult.FAIL);
                return;
            }
            if (!level.isClientSide && level.getBlockEntity(pos) instanceof TieredElectricPumpBlockEntity pumpBe) {
                pumpBe.tryInsertUpgrade(player, heldStack);
            }
            event.setCanceled(true);
            event.setCancellationResult(InteractionResult.sidedSuccess(level.isClientSide));
            return;
        }

        ResourceLocation heldId = BuiltInRegistries.ITEM.getKey(heldStack.getItem());

        // 1. Mekanism base Electric Pump + mekanism:advanced_tier_installer -> Advanced Electric Pump
        ResourceLocation blockId = BuiltInRegistries.BLOCK.getKey(block);
        if (MEKANISM_ELECTRIC_PUMP_ID.equals(blockId)
                && PumpTier.ADVANCED.getRequiredInstallerId().equals(heldId)
                && PumpTier.ADVANCED.isAvailable()) {
            BlockEntity oldBe = level.getBlockEntity(pos);
            if (!canModifyBlock(level, pos, player, event.getFace(), heldStack, oldBe)) {
                event.setCanceled(true);
                event.setCancellationResult(InteractionResult.FAIL);
                return;
            }
            if (!level.isClientSide) {
                upgradeFromBaseMekanismPump(level, pos, state, oldBe, player, heldStack);
            }
            event.setCanceled(true);
            event.setCancellationResult(InteractionResult.sidedSuccess(level.isClientSide));
            return;
        }

        // 2. mekmp Tiered Electric Pump + nextTier's matching Tier Installer -> nextTier Electric Pump
        if (block instanceof TieredElectricPumpBlock pumpBlock) {
            PumpTier currentTier = pumpBlock.getTier();
            PumpTier nextTier = currentTier.getAvailableNextTier();

            if (nextTier != null && nextTier.getRequiredInstallerId().equals(heldId)) {
                if (!canModifyBlock(level, pos, player, event.getFace(), heldStack, null)) {
                    event.setCanceled(true);
                    event.setCancellationResult(InteractionResult.FAIL);
                    return;
                }
                if (!level.isClientSide) {
                    upgradeTieredPump(level, pos, state, nextTier, player, heldStack);
                }
                event.setCanceled(true);
                event.setCancellationResult(InteractionResult.sidedSuccess(level.isClientSide));
            }
        }
    }

    private static boolean canHandleWithItem(Block block, ItemStack stack) {
        if (block instanceof TieredElectricPumpBlock pumpBlock) {
            if (isMekanismPumpUpgrade(stack)) {
                return true;
            }
            PumpTier nextTier = pumpBlock.getTier().getAvailableNextTier();
            ResourceLocation heldId = BuiltInRegistries.ITEM.getKey(stack.getItem());
            return nextTier != null && nextTier.getRequiredInstallerId().equals(heldId);
        }
        ResourceLocation blockId = BuiltInRegistries.BLOCK.getKey(block);
        ResourceLocation heldId = BuiltInRegistries.ITEM.getKey(stack.getItem());
        return MEKANISM_ELECTRIC_PUMP_ID.equals(blockId)
                && PumpTier.ADVANCED.getRequiredInstallerId().equals(heldId)
                && PumpTier.ADVANCED.isAvailable();
    }

    private static boolean isMekanismPumpUpgrade(ItemStack stack) {
        Item speedItem = TieredElectricPumpBlockEntity.getSpeedUpgradeItem();
        Item energyItem = TieredElectricPumpBlockEntity.getEnergyUpgradeItem();
        Item filterItem = TieredElectricPumpBlockEntity.getFilterUpgradeItem();
        return (speedItem != Items.AIR && stack.is(speedItem))
                || (energyItem != Items.AIR && stack.is(energyItem))
                || (filterItem != Items.AIR && stack.is(filterItem));
    }

    private static boolean canModifyBlock(Level level, BlockPos pos, Player player, @Nullable Direction face,
                                          ItemStack stack, @Nullable BlockEntity mekanismBe) {
        if (!level.mayInteract(player, pos)) {
            return false;
        }
        if (!player.mayUseItemAt(pos, face != null ? face : Direction.UP, stack)) {
            return false;
        }
        if (mekanismBe != null && !IBlockSecurityUtils.INSTANCE.canAccessOrDisplayError(player, level, pos, mekanismBe)) {
            return false;
        }
        return true;
    }

    private static void upgradeFromBaseMekanismPump(Level level, BlockPos pos, BlockState oldState,
                                                    @Nullable BlockEntity oldBe, Player player, ItemStack installerStack) {
        Direction facing = extractHorizontalFacing(oldState);
        FluidStack savedFluid = FluidStack.EMPTY;
        int savedEnergy = 0;
        int speedUpgrades = 0;
        int energyUpgrades = 0;
        int filterUpgrades = 0;
        ItemStack fluidIn = ItemStack.EMPTY;
        ItemStack fluidOut = ItemStack.EMPTY;
        ItemStack extraEnergyItem = ItemStack.EMPTY;
        TieredElectricPumpBlockEntity.RedstoneMode redstoneMode = TieredElectricPumpBlockEntity.RedstoneMode.IGNORED;

        IFluidHandler fluidCap = level.getCapability(Capabilities.FluidHandler.BLOCK, pos, null);
        if (fluidCap != null && fluidCap.getTanks() > 0) {
            savedFluid = fluidCap.getFluidInTank(0).copy();
        }
        IEnergyStorage energyCap = level.getCapability(Capabilities.EnergyStorage.BLOCK, pos, null);
        if (energyCap != null) {
            savedEnergy = energyCap.getEnergyStored();
        }

        if (oldBe != null) {
            // 1) Read installed upgrades directly via Mekanism IUpgradeTile API
            if (oldBe instanceof IUpgradeTile upgradeTile && upgradeTile.supportsUpgrades()) {
                TileComponentUpgrade component = upgradeTile.getComponent();
                if (component != null) {
                    speedUpgrades = component.getUpgrades(Upgrade.SPEED);
                    energyUpgrades = component.getUpgrades(Upgrade.ENERGY);
                    filterUpgrades = component.getUpgrades(Upgrade.FILTER);
                }
            }

            // 2) Read Redstone Control mode directly via Mekanism IRedstoneControl API
            if (oldBe instanceof IRedstoneControl redstoneControl) {
                redstoneMode = switch (redstoneControl.getControlType()) {
                    case HIGH, PULSE -> TieredElectricPumpBlockEntity.RedstoneMode.HIGH;
                    case LOW -> TieredElectricPumpBlockEntity.RedstoneMode.LOW;
                    default -> TieredElectricPumpBlockEntity.RedstoneMode.IGNORED;
                };
            }

            // 3) Atomically extract Fluid Input (0) / Output (1) / Energy (2) slots via IMekanismInventory API
            if (oldBe instanceof IMekanismInventory mekInventory) {
                List<IInventorySlot> slots = mekInventory.getInventorySlots(null);
                if (slots.size() > 0) {
                    fluidIn = takeSlotStack(slots.get(0));
                }
                if (slots.size() > 1) {
                    fluidOut = takeSlotStack(slots.get(1));
                }
                if (slots.size() > 2) {
                    extraEnergyItem = takeSlotStack(slots.get(2));
                }
            }
        }

        BlockState newState = ModBlocks.PUMPS.get(PumpTier.ADVANCED).get()
                .defaultBlockState()
                .setValue(TieredElectricPumpBlock.FACING, facing);
        level.setBlock(pos, newState, Block.UPDATE_ALL);

        if (level.getBlockEntity(pos) instanceof TieredElectricPumpBlockEntity newPump) {
            newPump.loadFromExternalPump(
                    savedFluid,
                    savedEnergy,
                    speedUpgrades,
                    energyUpgrades,
                    filterUpgrades,
                    fluidIn,
                    fluidOut,
                    redstoneMode
            );
        }

        if (!extraEnergyItem.isEmpty()) {
            Containers.dropItemStack(level, pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5, extraEnergyItem);
        }

        finishUpgrade(level, pos, player, installerStack);
    }

    private static ItemStack takeSlotStack(@Nullable IInventorySlot slot) {
        if (slot == null) return ItemStack.EMPTY;
        ItemStack stack = slot.getStack();
        if (stack.isEmpty()) return ItemStack.EMPTY;
        ItemStack taken = stack.copy();
        slot.setStack(ItemStack.EMPTY);
        return taken;
    }

    private static void upgradeTieredPump(Level level, BlockPos pos, BlockState oldState, PumpTier nextTier, Player player, ItemStack installerStack) {
        Direction facing = oldState.getValue(TieredElectricPumpBlock.FACING);

        FluidStack savedFluid = FluidStack.EMPTY;
        int savedEnergy = 0;
        int speedUpgrades = 0;
        int energyUpgrades = 0;
        int filterUpgrades = 0;
        ItemStack fluidIn = ItemStack.EMPTY;
        ItemStack fluidOut = ItemStack.EMPTY;
        TieredElectricPumpBlockEntity.RedstoneMode redstoneMode = TieredElectricPumpBlockEntity.RedstoneMode.IGNORED;

        if (level.getBlockEntity(pos) instanceof TieredElectricPumpBlockEntity oldPump) {
            IFluidHandler fh = oldPump.getFluidHandler(null);
            if (fh != null && fh.getTanks() > 0) {
                savedFluid = fh.getFluidInTank(0).copy();
            }
            IEnergyStorage es = oldPump.getEnergyStorage(null);
            if (es != null) {
                savedEnergy = es.getEnergyStored();
            }
            speedUpgrades = oldPump.getSpeedUpgradeCount();
            energyUpgrades = oldPump.getEnergyUpgradeCount();
            filterUpgrades = oldPump.getFilterUpgradeCount();
            fluidIn = oldPump.getFluidSlotInventory().getItem(TieredElectricPumpBlockEntity.SLOT_FLUID_IN).copy();
            fluidOut = oldPump.getFluidSlotInventory().getItem(TieredElectricPumpBlockEntity.SLOT_FLUID_OUT).copy();
            redstoneMode = oldPump.getRedstoneMode();

            oldPump.getUpgradeInventory().clearContent();
            oldPump.getFluidSlotInventory().clearContent();
        }

        BlockState newState = ModBlocks.PUMPS.get(nextTier).get()
                .defaultBlockState()
                .setValue(TieredElectricPumpBlock.FACING, facing);
        level.setBlock(pos, newState, Block.UPDATE_ALL);

        if (level.getBlockEntity(pos) instanceof TieredElectricPumpBlockEntity newPump) {
            newPump.loadFromExternalPump(
                    savedFluid,
                    savedEnergy,
                    speedUpgrades,
                    energyUpgrades,
                    filterUpgrades,
                    fluidIn,
                    fluidOut,
                    redstoneMode
            );
        }

        finishUpgrade(level, pos, player, installerStack);
    }

    private static void finishUpgrade(Level level, BlockPos pos, Player player, ItemStack installerStack) {
        if (!player.getAbilities().instabuild) {
            installerStack.shrink(1);
        }
        level.playSound(null, pos, SoundEvents.EXPERIENCE_ORB_PICKUP, SoundSource.BLOCKS, 0.8F, 1.2F);
    }

    private static Direction extractHorizontalFacing(BlockState state) {
        for (Property<?> prop : state.getProperties()) {
            if (prop.getName().equals("facing") && state.getValue(prop) instanceof Direction dir && dir.getAxis().isHorizontal()) {
                return dir;
            }
        }
        return Direction.NORTH;
    }
}
