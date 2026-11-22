package com.mekmp.block.entity;

import com.mekmp.block.TieredElectricPumpBlock;
import com.mekmp.menu.TieredElectricPumpMenu;
import com.mekmp.registry.ModBlockEntities;
import com.mekmp.tier.PumpTier;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.ContainerHelper;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.BucketPickup;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.level.material.Fluids;
import net.neoforged.neoforge.capabilities.BlockCapabilityCache;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.energy.EnergyStorage;
import net.neoforged.neoforge.energy.IEnergyStorage;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import net.neoforged.neoforge.fluids.capability.IFluidHandlerItem;
import net.neoforged.neoforge.fluids.capability.templates.FluidTank;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

public class TieredElectricPumpBlockEntity extends BlockEntity implements MenuProvider {
    public static final ResourceLocation SPEED_UPGRADE_ID = ResourceLocation.fromNamespaceAndPath("mekanism", "upgrade_speed");
    public static final ResourceLocation ENERGY_UPGRADE_ID = ResourceLocation.fromNamespaceAndPath("mekanism", "upgrade_energy");
    public static final ResourceLocation FILTER_UPGRADE_ID = ResourceLocation.fromNamespaceAndPath("mekanism", "upgrade_filter");
    public static final ResourceLocation HEAVY_WATER_ID = ResourceLocation.fromNamespaceAndPath("mekanism", "heavy_water");

    // Fixed upgrade slot indices
    public static final int SLOT_SPEED = 0;
    public static final int SLOT_ENERGY = 1;
    public static final int SLOT_FILTER = 2;

    // Bucket / fluid container slot indices
    public static final int SLOT_FLUID_IN = 0;
    public static final int SLOT_FLUID_OUT = 1;

    // Status codes for GUI display
    public static final int STATUS_RUNNING = 0;
    public static final int STATUS_NO_ENERGY = 1;
    public static final int STATUS_TANK_FULL = 2;
    public static final int STATUS_REDSTONE = 3;
    public static final int STATUS_IDLE = 4;

    private static final int MAX_CHAIN_FRONTIER = 16384;
    private static final int MAX_SAVED_CHAIN_FRONTIER = 2048;
    private static final int DIRTY_FLUSH_INTERVAL = 20;

    // Precomputed Mekanism upgrade multipliers (0..8 upgrades) to avoid Math.pow per tick
    private static final double[] SPEED_TICK_FACTORS = new double[9];
    private static final double[] ENERGY_CAP_FACTORS = new double[9];
    private static final double[] ENERGY_USE_SPEED_FACTORS = new double[9];
    private static final double[] ENERGY_USE_ENERGY_FACTORS = new double[9];

    static {
        for (int i = 0; i <= 8; i++) {
            SPEED_TICK_FACTORS[i] = Math.pow(10.0, -i / 8.0);
            ENERGY_CAP_FACTORS[i] = Math.pow(10.0, i / 8.0);
            ENERGY_USE_SPEED_FACTORS[i] = Math.pow(10.0, (2.0 * i) / 8.0);
            ENERGY_USE_ENERGY_FACTORS[i] = Math.pow(10.0, -i / 8.0);
        }
    }

    // Cached registry lookups
    @Nullable
    private static volatile Item cachedSpeedUpgradeItem;
    @Nullable
    private static volatile Item cachedEnergyUpgradeItem;
    @Nullable
    private static volatile Item cachedFilterUpgradeItem;
    @Nullable
    private static volatile Fluid cachedHeavyWaterFluid;

    public static Item getSpeedUpgradeItem() {
        Item item = cachedSpeedUpgradeItem;
        if (item == null) {
            item = BuiltInRegistries.ITEM.get(SPEED_UPGRADE_ID);
            cachedSpeedUpgradeItem = item;
        }
        return item;
    }

    public static Item getEnergyUpgradeItem() {
        Item item = cachedEnergyUpgradeItem;
        if (item == null) {
            item = BuiltInRegistries.ITEM.get(ENERGY_UPGRADE_ID);
            cachedEnergyUpgradeItem = item;
        }
        return item;
    }

    public static Item getFilterUpgradeItem() {
        Item item = cachedFilterUpgradeItem;
        if (item == null) {
            item = BuiltInRegistries.ITEM.get(FILTER_UPGRADE_ID);
            cachedFilterUpgradeItem = item;
        }
        return item;
    }

    public static Fluid getHeavyWaterFluid() {
        Fluid fluid = cachedHeavyWaterFluid;
        if (fluid == null) {
            fluid = BuiltInRegistries.FLUID.get(HEAVY_WATER_ID);
            cachedHeavyWaterFluid = fluid;
        }
        return fluid;
    }

    public enum RedstoneMode {
        IGNORED,
        HIGH,
        LOW;

        public RedstoneMode next() {
            RedstoneMode[] vals = values();
            return vals[(this.ordinal() + 1) % vals.length];
        }

        public static RedstoneMode byOrdinal(int ord) {
            RedstoneMode[] vals = values();
            if (ord < 0 || ord >= vals.length) return IGNORED;
            return vals[ord];
        }
    }

    private final FluidTank fluidTank;
    private final IFluidHandler outputOnlyFluidHandler;
    private final CustomEnergyStorage energyStorage;

    @Nullable
    private BlockCapabilityCache<IFluidHandler, Direction> upFluidHandlerCache;

    // Cached upgrade counts (updated on inventory change / load)
    private int cachedSpeedCount = 0;
    private int cachedEnergyCount = 0;
    private int cachedFilterCount = 0;

    // Batched dirty state for high-frequency energy changes
    private boolean energyDirty = false;
    private int periodicMaintenanceCounter = 0;

    private final SimpleContainer upgradeInventory = new SimpleContainer(3) {
        @Override
        public void setChanged() {
            super.setChanged();
            refreshUpgradeCacheAndCapacities();
            TieredElectricPumpBlockEntity.this.setChanged();
        }
    };

    private final SimpleContainer fluidSlotInventory = new SimpleContainer(2) {
        @Override
        public void setChanged() {
            super.setChanged();
            TieredElectricPumpBlockEntity.this.setChanged();
        }
    };

    private int tickCounter = 0;
    private int status = STATUS_IDLE;
    private RedstoneMode redstoneMode = RedstoneMode.IGNORED;

    // Chain state for non-water fluid source extraction (bucketed by exact squared distance for O(1) closest polling)
    @Nullable
    private BlockPos chainedTargetPos = null;
    @Nullable
    private Fluid chainedFluid = null;
    private final TreeMap<Integer, List<BlockPos>> frontierByDist = new TreeMap<>();
    private final Set<BlockPos> frontierSet = new HashSet<>();

    private final ContainerData dataAccess = new ContainerData() {
        @Override
        public int get(int index) {
            return switch (index) {
                case TieredElectricPumpMenu.DATA_ENERGY_LO -> energyStorage.getEnergyStored() & 0xFFFF;
                case TieredElectricPumpMenu.DATA_ENERGY_HI -> (energyStorage.getEnergyStored() >>> 16) & 0xFFFF;
                case TieredElectricPumpMenu.DATA_MAX_ENERGY_LO -> energyStorage.getMaxEnergyStored() & 0xFFFF;
                case TieredElectricPumpMenu.DATA_MAX_ENERGY_HI -> (energyStorage.getMaxEnergyStored() >>> 16) & 0xFFFF;
                case TieredElectricPumpMenu.DATA_FLUID_LO -> fluidTank.getFluidAmount() & 0xFFFF;
                case TieredElectricPumpMenu.DATA_FLUID_HI -> (fluidTank.getFluidAmount() >>> 16) & 0xFFFF;
                case TieredElectricPumpMenu.DATA_MAX_FLUID_LO -> fluidTank.getCapacity() & 0xFFFF;
                case TieredElectricPumpMenu.DATA_MAX_FLUID_HI -> (fluidTank.getCapacity() >>> 16) & 0xFFFF;
                case TieredElectricPumpMenu.DATA_FLUID_ID -> BuiltInRegistries.FLUID.getId(fluidTank.getFluid().getFluid());
                case TieredElectricPumpMenu.DATA_STATUS -> status;
                case TieredElectricPumpMenu.DATA_USAGE_LO -> {
                    int usage = (status == STATUS_RUNNING) ? getEffectiveEnergyUsageFe() : 0;
                    yield usage & 0xFFFF;
                }
                case TieredElectricPumpMenu.DATA_USAGE_HI -> {
                    int usage = (status == STATUS_RUNNING) ? getEffectiveEnergyUsageFe() : 0;
                    yield (usage >>> 16) & 0xFFFF;
                }
                case TieredElectricPumpMenu.DATA_REDSTONE_MODE -> redstoneMode.ordinal();
                case TieredElectricPumpMenu.DATA_TIER -> getTier().ordinal();
                default -> 0;
            };
        }

        @Override
        public void set(int index, int value) {
        }

        @Override
        public int getCount() {
            return TieredElectricPumpMenu.DATA_COUNT;
        }
    };

    public TieredElectricPumpBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.ELECTRIC_PUMP.get(), pos, state);
        PumpTier tier = getTier();
        this.fluidTank = new FluidTank(tier.getTankCapacity()) {
            @Override
            protected void onContentsChanged() {
                setChanged();
            }
        };
        this.outputOnlyFluidHandler = new IFluidHandler() {
            @Override
            public int getTanks() {
                return fluidTank.getTanks();
            }

            @Override
            public FluidStack getFluidInTank(int tank) {
                return fluidTank.getFluidInTank(tank);
            }

            @Override
            public int getTankCapacity(int tank) {
                return fluidTank.getTankCapacity(tank);
            }

            @Override
            public boolean isFluidValid(int tank, FluidStack stack) {
                return false;
            }

            @Override
            public int fill(FluidStack resource, FluidAction action) {
                return 0;
            }

            @Override
            public FluidStack drain(FluidStack resource, FluidAction action) {
                return fluidTank.drain(resource, action);
            }

            @Override
            public FluidStack drain(int maxDrain, FluidAction action) {
                return fluidTank.drain(maxDrain, action);
            }
        };
        this.energyStorage = new CustomEnergyStorage(tier.getEnergyCapacityFe());
    }

    @Override
    public void onLoad() {
        super.onLoad();
        refreshUpgradeCacheAndCapacities();
    }

    public PumpTier getTier() {
        if (getBlockState().getBlock() instanceof TieredElectricPumpBlock pumpBlock) {
            return pumpBlock.getTier();
        }
        return PumpTier.ADVANCED;
    }

    public int getMaxChainBlocksPerOperation() {
        return 1 << (getTier().ordinal() + 1);
    }

    public RedstoneMode getRedstoneMode() {
        return redstoneMode;
    }

    public void cycleRedstoneMode() {
        this.redstoneMode = this.redstoneMode.next();
        this.setChanged();
    }

    public void dumpFluidTank() {
        if (!fluidTank.isEmpty()) {
            fluidTank.setFluid(FluidStack.EMPTY);
            this.setChanged();
        }
    }

    private void clearChainState() {
        this.chainedTargetPos = null;
        this.chainedFluid = null;
        this.frontierByDist.clear();
        this.frontierSet.clear();
    }

    private static int distSqInt(BlockPos a, BlockPos b) {
        int dx = a.getX() - b.getX();
        int dy = a.getY() - b.getY();
        int dz = a.getZ() - b.getZ();
        return dx * dx + dy * dy + dz * dz;
    }

    private void addFrontierPos(BlockPos origin, BlockPos pos) {
        if (frontierSet.size() >= MAX_CHAIN_FRONTIER) return;
        BlockPos immutable = pos.immutable();
        if (frontierSet.add(immutable)) {
            int distSq = distSqInt(origin, immutable);
            frontierByDist.computeIfAbsent(distSq, k -> new ArrayList<>()).add(immutable);
        }
    }

    private void removeFrontierPos(BlockPos origin, BlockPos pos) {
        if (frontierSet.remove(pos)) {
            int distSq = distSqInt(origin, pos);
            List<BlockPos> bucket = frontierByDist.get(distSq);
            if (bucket != null) {
                int idx = bucket.indexOf(pos);
                if (idx >= 0) {
                    int lastIdx = bucket.size() - 1;
                    if (idx != lastIdx) {
                        bucket.set(idx, bucket.get(lastIdx));
                    }
                    bucket.remove(lastIdx);
                }
                if (bucket.isEmpty()) {
                    frontierByDist.remove(distSq);
                }
            }
        }
    }

    @Nullable
    private BlockPos pollClosestRandomValidFrontier(Level level, BlockPos origin, Fluid fluid) {
        while (!frontierByDist.isEmpty()) {
            Map.Entry<Integer, List<BlockPos>> firstEntry = frontierByDist.firstEntry();
            List<BlockPos> bucket = firstEntry.getValue();
            if (bucket.isEmpty()) {
                frontierByDist.pollFirstEntry();
                continue;
            }
            int idx = level.random.nextInt(bucket.size());
            int lastIdx = bucket.size() - 1;
            BlockPos candidate = bucket.get(idx);
            if (idx != lastIdx) {
                bucket.set(idx, bucket.get(lastIdx));
            }
            bucket.remove(lastIdx);
            frontierSet.remove(candidate);
            if (bucket.isEmpty()) {
                frontierByDist.pollFirstEntry();
            }

            if (isValidNonWaterSource(level, origin, candidate, fluid)) {
                return candidate;
            }
        }
        return null;
    }

    private void refreshUpgradeCacheAndCapacities() {
        cachedSpeedCount = computeSpeedUpgradeCount();
        cachedEnergyCount = computeEnergyUpgradeCount();
        cachedFilterCount = computeFilterUpgradeCount();
        recalculateCapacities();
    }

    public void recalculateCapacities() {
        PumpTier tier = getTier();
        int targetTankCap = tier.getTankCapacity();
        if (fluidTank.getCapacity() != targetTankCap) {
            fluidTank.setCapacity(targetTankCap);
        }
        if (!fluidTank.isEmpty() && fluidTank.getFluidAmount() > targetTankCap) {
            fluidTank.getFluid().setAmount(targetTankCap);
            setChanged();
        }
        int energyUpgrades = cachedEnergyCount;
        long scaledEnergy = Math.round(tier.getEnergyCapacityFe() * ENERGY_CAP_FACTORS[energyUpgrades]);
        int maxEnergy = (int) Math.min(Integer.MAX_VALUE, scaledEnergy);
        if (energyStorage.getMaxEnergyStored() != maxEnergy) {
            energyStorage.setCapacity(maxEnergy);
        }
    }

    public boolean tryInsertUpgrade(Player player, ItemStack heldStack) {
        if (heldStack.isEmpty()) return false;

        Item speedItem = getSpeedUpgradeItem();
        Item energyItem = getEnergyUpgradeItem();
        Item filterItem = getFilterUpgradeItem();

        int slotIndex;
        int maxCount;
        Item targetItem;

        if (speedItem != Items.AIR && heldStack.is(speedItem)) {
            slotIndex = SLOT_SPEED;
            maxCount = 8;
            targetItem = speedItem;
        } else if (energyItem != Items.AIR && heldStack.is(energyItem)) {
            slotIndex = SLOT_ENERGY;
            maxCount = 8;
            targetItem = energyItem;
        } else if (filterItem != Items.AIR && heldStack.is(filterItem)) {
            slotIndex = SLOT_FILTER;
            maxCount = 1;
            targetItem = filterItem;
        } else {
            return false;
        }

        ItemStack existing = upgradeInventory.getItem(slotIndex);
        int currentCount = (!existing.isEmpty() && existing.is(targetItem)) ? existing.getCount() : 0;
        int space = maxCount - currentCount;

        if (space <= 0) {
            player.displayClientMessage(
                    Component.literal(heldStack.getHoverName().getString() + ": " + currentCount + " / " + maxCount),
                    true
            );
            return true;
        }

        int toInsert = Math.min(space, heldStack.getCount());
        int newCount = currentCount + toInsert;
        upgradeInventory.setItem(slotIndex, new ItemStack(targetItem, newCount));

        if (!player.getAbilities().instabuild) {
            heldStack.shrink(toInsert);
        }

        if (level != null) {
            level.playSound(null, worldPosition, SoundEvents.ITEM_PICKUP, SoundSource.BLOCKS, 0.6F, 1.2F);
        }
        player.displayClientMessage(
                Component.literal(new ItemStack(targetItem).getHoverName().getString() + ": " + newCount + " / " + maxCount),
                true
        );
        return true;
    }

    private int computeSpeedUpgradeCount() {
        ItemStack stack = upgradeInventory.getItem(SLOT_SPEED);
        Item target = getSpeedUpgradeItem();
        if (target != Items.AIR && !stack.isEmpty() && stack.is(target)) {
            return Math.min(8, stack.getCount());
        }
        return 0;
    }

    private int computeEnergyUpgradeCount() {
        ItemStack stack = upgradeInventory.getItem(SLOT_ENERGY);
        Item target = getEnergyUpgradeItem();
        if (target != Items.AIR && !stack.isEmpty() && stack.is(target)) {
            return Math.min(8, stack.getCount());
        }
        return 0;
    }

    private int computeFilterUpgradeCount() {
        ItemStack stack = upgradeInventory.getItem(SLOT_FILTER);
        Item target = getFilterUpgradeItem();
        if (target != Items.AIR && !stack.isEmpty() && stack.is(target)) {
            return Math.min(1, stack.getCount());
        }
        return 0;
    }

    public int getSpeedUpgradeCount() {
        return cachedSpeedCount;
    }

    public int getEnergyUpgradeCount() {
        return cachedEnergyCount;
    }

    public int getFilterUpgradeCount() {
        return cachedFilterCount;
    }

    public void setUpgrades(int speedCount, int energyCount, int filterCount) {
        Item speedItem = getSpeedUpgradeItem();
        Item energyItem = getEnergyUpgradeItem();
        Item filterItem = getFilterUpgradeItem();

        upgradeInventory.setItem(SLOT_SPEED, (speedItem != Items.AIR && speedCount > 0)
                ? new ItemStack(speedItem, Math.min(8, speedCount)) : ItemStack.EMPTY);
        upgradeInventory.setItem(SLOT_ENERGY, (energyItem != Items.AIR && energyCount > 0)
                ? new ItemStack(energyItem, Math.min(8, energyCount)) : ItemStack.EMPTY);
        upgradeInventory.setItem(SLOT_FILTER, (filterItem != Items.AIR && filterCount > 0)
                ? new ItemStack(filterItem, Math.min(1, filterCount)) : ItemStack.EMPTY);
        refreshUpgradeCacheAndCapacities();
        setChanged();
    }

    public void loadFromExternalPump(FluidStack fluid, int energyFe, int speedUpgrades, int energyUpgrades, int filterUpgrades,
                                     ItemStack fluidInStack, ItemStack fluidOutStack, RedstoneMode mode) {
        this.setUpgrades(speedUpgrades, energyUpgrades, filterUpgrades);
        if (!fluid.isEmpty()) {
            this.fluidTank.setFluid(fluid.copy());
        }
        this.energyStorage.setEnergy(energyFe);
        this.fluidSlotInventory.setItem(SLOT_FLUID_IN, fluidInStack.copy());
        this.fluidSlotInventory.setItem(SLOT_FLUID_OUT, fluidOutStack.copy());
        this.redstoneMode = mode;
        this.setChanged();
    }

    private int getEffectiveTicksPerOperation() {
        double factor = SPEED_TICK_FACTORS[cachedSpeedCount];
        return Math.max(1, (int) Math.round(getTier().getTicksPerOperation() * factor));
    }

    public int getEffectiveEnergyUsageFe() {
        double mult = ENERGY_USE_SPEED_FACTORS[cachedSpeedCount] * ENERGY_USE_ENERGY_FACTORS[cachedEnergyCount];
        long scaled = Math.round(getTier().getEnergyUsageFe() * mult);
        return (int) Math.max(1L, Math.min(Integer.MAX_VALUE, scaled));
    }

    private boolean isRedstoneActive(Level level, BlockPos pos) {
        boolean powered = level.hasNeighborSignal(pos);
        return switch (redstoneMode) {
            case IGNORED -> true;
            case HIGH -> powered;
            case LOW -> !powered;
        };
    }

    public static void serverTick(Level level, BlockPos pos, BlockState state, TieredElectricPumpBlockEntity be) {
        be.periodicMaintenanceCounter++;
        if (be.periodicMaintenanceCounter >= DIRTY_FLUSH_INTERVAL) {
            be.periodicMaintenanceCounter = 0;
            be.recalculateCapacities();
            if (be.energyDirty) {
                be.energyDirty = false;
                be.setChanged();
            }
        }

        be.fillFluidContainerSlot();
        be.ejectFluidUpwards(level, pos);

        if (!be.isRedstoneActive(level, pos)) {
            be.status = STATUS_REDSTONE;
            be.tickCounter = 0;
            return;
        }

        PumpTarget target = be.findPumpTarget(level, pos);
        if (target == null) {
            be.tickCounter = 0;
            return;
        }

        int usage = be.getEffectiveEnergyUsageFe();
        if (be.energyStorage.getEnergyStored() < usage) {
            be.status = STATUS_NO_ENERGY;
            return;
        }

        be.status = STATUS_RUNNING;
        be.energyStorage.consumeEnergy(usage);
        be.tickCounter++;

        if (be.tickCounter >= be.getEffectiveTicksPerOperation()) {
            be.tickCounter = 0;
            be.executePump(level, pos, target);
        }
    }

    private void fillFluidContainerSlot() {
        ItemStack inStack = fluidSlotInventory.getItem(SLOT_FLUID_IN);
        if (inStack.isEmpty()) return;

        ItemStack single = inStack.copyWithCount(1);
        IFluidHandlerItem itemHandler = single.getCapability(Capabilities.FluidHandler.ITEM);
        if (itemHandler == null) return;

        if (isContainerReadyForOutput(itemHandler) && canInsertIntoOutput(single)) {
            inStack.shrink(1);
            if (inStack.isEmpty()) {
                fluidSlotInventory.setItem(SLOT_FLUID_IN, ItemStack.EMPTY);
            } else {
                fluidSlotInventory.setChanged();
            }
            insertIntoOutput(single);
            return;
        }

        if (fluidTank.isEmpty()) return;

        FluidStack probe = fluidTank.getFluid().copy();
        int accepted = itemHandler.fill(probe, IFluidHandler.FluidAction.SIMULATE);
        if (accepted <= 0) return;

        int toTransfer = Math.min(accepted, fluidTank.getFluidAmount());
        FluidStack drainedSim = fluidTank.drain(toTransfer, IFluidHandler.FluidAction.SIMULATE);
        if (drainedSim.isEmpty()) return;

        ItemStack simSingle = inStack.copyWithCount(1);
        IFluidHandlerItem simHandler = simSingle.getCapability(Capabilities.FluidHandler.ITEM);
        if (simHandler == null) return;
        int simFilled = simHandler.fill(drainedSim.copy(), IFluidHandler.FluidAction.EXECUTE);
        if (simFilled <= 0) return;

        ItemStack simResultContainer = simHandler.getContainer();
        boolean willBeFull = isContainerFull(simHandler);

        if (inStack.getCount() == 1) {
            FluidStack actualDrained = fluidTank.drain(simFilled, IFluidHandler.FluidAction.EXECUTE);
            if (actualDrained.isEmpty()) return;
            itemHandler.fill(actualDrained, IFluidHandler.FluidAction.EXECUTE);
            ItemStack filledContainer = itemHandler.getContainer();

            if (willBeFull && canInsertIntoOutput(filledContainer)) {
                fluidSlotInventory.setItem(SLOT_FLUID_IN, ItemStack.EMPTY);
                insertIntoOutput(filledContainer);
            } else {
                fluidSlotInventory.setItem(SLOT_FLUID_IN, filledContainer);
            }
        } else {
            if (!canInsertIntoOutput(simResultContainer)) {
                return;
            }
            if (!willBeFull && ItemStack.isSameItemSameComponents(inStack, simResultContainer)) {
                return;
            }
            FluidStack actualDrained = fluidTank.drain(simFilled, IFluidHandler.FluidAction.EXECUTE);
            if (actualDrained.isEmpty()) return;
            itemHandler.fill(actualDrained, IFluidHandler.FluidAction.EXECUTE);
            ItemStack filledContainer = itemHandler.getContainer();

            inStack.shrink(1);
            fluidSlotInventory.setChanged();
            insertIntoOutput(filledContainer);
        }
    }

    private static boolean isContainerFull(IFluidHandlerItem handler) {
        int tanks = handler.getTanks();
        if (tanks <= 0) return false;
        boolean hasAnyFluid = false;
        for (int i = 0; i < tanks; i++) {
            FluidStack fs = handler.getFluidInTank(i);
            int cap = handler.getTankCapacity(i);
            if (!fs.isEmpty()) {
                hasAnyFluid = true;
            }
            if (cap > 0 && fs.getAmount() < cap) {
                return false;
            }
        }
        return hasAnyFluid;
    }

    private boolean isContainerReadyForOutput(IFluidHandlerItem handler) {
        if (isContainerFull(handler)) {
            return true;
        }
        if (!fluidTank.isEmpty()) {
            boolean hasAnyFluid = false;
            for (int i = 0; i < handler.getTanks(); i++) {
                if (!handler.getFluidInTank(i).isEmpty()) {
                    hasAnyFluid = true;
                    break;
                }
            }
            if (hasAnyFluid) {
                FluidStack oneUnit = fluidTank.getFluid().copyWithAmount(1);
                return handler.fill(oneUnit, IFluidHandler.FluidAction.SIMULATE) <= 0;
            }
        }
        return false;
    }

    private boolean canInsertIntoOutput(ItemStack stack) {
        if (stack.isEmpty()) return false;
        ItemStack outStack = fluidSlotInventory.getItem(SLOT_FLUID_OUT);
        if (outStack.isEmpty()) return true;
        return ItemStack.isSameItemSameComponents(outStack, stack)
                && outStack.getCount() + stack.getCount() <= outStack.getMaxStackSize();
    }

    private void insertIntoOutput(ItemStack stack) {
        ItemStack outStack = fluidSlotInventory.getItem(SLOT_FLUID_OUT);
        if (outStack.isEmpty()) {
            fluidSlotInventory.setItem(SLOT_FLUID_OUT, stack.copy());
        } else if (ItemStack.isSameItemSameComponents(outStack, stack)) {
            outStack.grow(stack.getCount());
            fluidSlotInventory.setChanged();
        }
    }

    private void ejectFluidUpwards(Level level, BlockPos pos) {
        if (fluidTank.isEmpty() || !(level instanceof ServerLevel serverLevel)) return;
        if (upFluidHandlerCache == null) {
            upFluidHandlerCache = BlockCapabilityCache.create(
                    Capabilities.FluidHandler.BLOCK,
                    serverLevel,
                    pos.above(),
                    Direction.DOWN
            );
        }
        IFluidHandler target = upFluidHandlerCache.getCapability();
        if (target != null) {
            FluidStack probe = fluidTank.getFluid().copy();
            int accepted = target.fill(probe, IFluidHandler.FluidAction.SIMULATE);
            if (accepted <= 0) return;

            int toSend = Math.min(accepted, fluidTank.getFluidAmount());
            FluidStack drainedSim = fluidTank.drain(toSend, IFluidHandler.FluidAction.SIMULATE);
            if (drainedSim.isEmpty()) return;

            int filled = target.fill(drainedSim.copy(), IFluidHandler.FluidAction.EXECUTE);
            if (filled > 0) {
                fluidTank.drain(filled, IFluidHandler.FluidAction.EXECUTE);
            }
        }
    }

    @Nullable
    private PumpTarget findPumpTarget(Level level, BlockPos origin) {
        if (chainedTargetPos != null && chainedFluid != null && chainedFluid != Fluids.EMPTY) {
            if (!isValidNonWaterSource(level, origin, chainedTargetPos, chainedFluid)) {
                removeFrontierPos(origin, chainedTargetPos);
                chainedTargetPos = pollClosestRandomValidFrontier(level, origin, chainedFluid);
            }

            if (chainedTargetPos != null) {
                if (!fluidTank.isEmpty() && !fluidTank.getFluid().getFluid().isSame(chainedFluid)) {
                    this.status = STATUS_IDLE;
                    return null;
                }
                int availableBlocksBySpace = fluidTank.getSpace() / 1000;
                if (availableBlocksBySpace <= 0) {
                    this.status = STATUS_TANK_FULL;
                    return null;
                }
                int maxBlocks = Math.min(getMaxChainBlocksPerOperation(), availableBlocksBySpace);
                return new PumpTarget(chainedTargetPos, chainedFluid, 1000, maxBlocks, false);
            } else {
                clearChainState();
            }
        }

        BlockPos targetPos = origin.below();
        FluidState fluidState = level.getFluidState(targetPos);
        if (fluidState.isEmpty() || !fluidState.isSource()) {
            this.status = STATUS_IDLE;
            return null;
        }

        Fluid targetFluid = fluidState.getType();
        boolean isWater = isWaterFluid(fluidState, targetFluid);
        boolean hasFilter = cachedFilterCount > 0;
        Fluid heavyWater = getHeavyWaterFluid();

        Fluid outputFluid = (hasFilter && isWater && heavyWater != Fluids.EMPTY)
                ? heavyWater
                : targetFluid;

        if (!fluidTank.isEmpty() && !fluidTank.getFluid().getFluid().isSame(outputFluid)) {
            this.status = STATUS_IDLE;
            return null;
        }

        if (isWater) {
            clearChainState();
            int multiplier = Math.max(1, getTier().getPumpMultiplier());
            int baseAmount = (outputFluid == heavyWater && !outputFluid.isSame(targetFluid)) ? 10 : 1000;
            long scaledAmount = (long) baseAmount * multiplier;
            int amountToAdd = (int) Math.min(scaledAmount, fluidTank.getCapacity());

            if (fluidTank.getSpace() < amountToAdd) {
                this.status = STATUS_TANK_FULL;
                return null;
            }
            return new PumpTarget(targetPos, outputFluid, amountToAdd, 1, true);
        } else {
            int availableBlocksBySpace = fluidTank.getSpace() / 1000;
            if (availableBlocksBySpace <= 0) {
                this.status = STATUS_TANK_FULL;
                return null;
            }
            int maxBlocks = Math.min(getMaxChainBlocksPerOperation(), availableBlocksBySpace);
            this.chainedTargetPos = targetPos;
            this.chainedFluid = outputFluid;
            return new PumpTarget(targetPos, outputFluid, 1000, maxBlocks, false);
        }
    }

    private void executePump(Level level, BlockPos origin, PumpTarget target) {
        if (!fluidTank.isEmpty() && !fluidTank.getFluid().getFluid().isSame(target.outputFluid)) return;

        if (target.isWater) {
            if (fluidTank.getSpace() < target.amountToAdd) return;
            FluidState currentFs = level.getFluidState(target.sourcePos);
            if (currentFs.isEmpty() || !currentFs.isSource()) return;

            fluidTank.fill(new FluidStack(target.outputFluid, target.amountToAdd), IFluidHandler.FluidAction.EXECUTE);
        } else {
            int extractedBlocks = 0;
            BlockPos currentPos = target.sourcePos;
            Fluid fluid = target.outputFluid;

            while (extractedBlocks < target.maxBlocks && currentPos != null && fluidTank.getSpace() >= 1000) {
                if (!fluidTank.isEmpty() && !fluidTank.getFluid().getFluid().isSame(fluid)) {
                    break;
                }
                if (!isValidNonWaterSource(level, origin, currentPos, fluid)) {
                    removeFrontierPos(origin, currentPos);
                    currentPos = pollClosestRandomValidFrontier(level, origin, fluid);
                    this.chainedTargetPos = currentPos;
                    if (currentPos == null) {
                        clearChainState();
                    }
                    continue;
                }

                if (extractSingleNonWaterBlock(level, currentPos, fluid)) {
                    extractedBlocks++;
                    currentPos = advanceNonWaterChain(level, origin, currentPos, fluid);
                } else {
                    removeFrontierPos(origin, currentPos);
                    currentPos = pollClosestRandomValidFrontier(level, origin, fluid);
                    this.chainedTargetPos = currentPos;
                    if (currentPos == null) {
                        clearChainState();
                    }
                }
            }
        }
    }

    private boolean extractSingleNonWaterBlock(Level level, BlockPos sourcePos, Fluid outputFluid) {
        FluidState currentFs = level.getFluidState(sourcePos);
        if (currentFs.isEmpty() || !currentFs.isSource()) return false;

        BlockState targetState = level.getBlockState(sourcePos);
        if (targetState.getBlock() instanceof BucketPickup pickup) {
            ItemStack bucket = pickup.pickupBlock(null, level, sourcePos, targetState);
            if (!bucket.isEmpty()) {
                fluidTank.fill(new FluidStack(outputFluid, 1000), IFluidHandler.FluidAction.EXECUTE);
                return true;
            }
            return false;
        } else {
            level.setBlock(sourcePos, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
            fluidTank.fill(new FluidStack(outputFluid, 1000), IFluidHandler.FluidAction.EXECUTE);
            return true;
        }
    }

    @Nullable
    private BlockPos advanceNonWaterChain(Level level, BlockPos origin, BlockPos extractedPos, Fluid fluid) {
        removeFrontierPos(origin, extractedPos);

        for (Direction dir : Direction.values()) {
            BlockPos adj = extractedPos.relative(dir);
            if (isValidNonWaterSource(level, origin, adj, fluid)) {
                addFrontierPos(origin, adj);
            }
        }

        BlockPos next = pollClosestRandomValidFrontier(level, origin, fluid);
        if (next != null) {
            this.chainedTargetPos = next;
            this.chainedFluid = fluid;
        } else {
            clearChainState();
        }
        setChanged();
        return next;
    }

    private boolean isValidNonWaterSource(Level level, BlockPos origin, BlockPos pos, Fluid expectedFluid) {
        if (!level.isLoaded(pos)) return false;
        if (origin.distManhattan(pos) > getTier().getMaxRange()) return false;

        FluidState fs = level.getFluidState(pos);
        if (fs.isEmpty() || !fs.isSource()) return false;

        Fluid f = fs.getType();
        if (isWaterFluid(fs, f)) return false;

        return f.isSame(expectedFluid) || f == expectedFluid;
    }

    private static boolean isWaterFluid(FluidState fluidState, Fluid fluid) {
        return fluid == Fluids.WATER || fluid.isSame(Fluids.WATER);
    }

    private record PumpTarget(BlockPos sourcePos, Fluid outputFluid, int amountToAdd, int maxBlocks, boolean isWater) {
    }

    public IFluidHandler getFluidHandler(@Nullable Direction side) {
        if (side == null || side == Direction.UP) {
            return outputOnlyFluidHandler;
        }
        return null;
    }

    public IEnergyStorage getEnergyStorage(@Nullable Direction side) {
        Direction facing = getBlockState().getValue(TieredElectricPumpBlock.FACING);
        if (side == null || side == facing.getOpposite()) {
            return energyStorage;
        }
        return null;
    }

    public SimpleContainer getUpgradeInventory() {
        return upgradeInventory;
    }

    public SimpleContainer getFluidSlotInventory() {
        return fluidSlotInventory;
    }

    public ContainerData getDataAccess() {
        return dataAccess;
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.put("FluidTank", fluidTank.writeToNBT(registries, new CompoundTag()));
        tag.putInt("Energy", energyStorage.getEnergyStored());
        tag.putInt("TickCounter", tickCounter);
        tag.putInt("RedstoneMode", redstoneMode.ordinal());
        ContainerHelper.saveAllItems(tag, upgradeInventory.getItems(), registries);

        CompoundTag fluidSlotsTag = new CompoundTag();
        ContainerHelper.saveAllItems(fluidSlotsTag, fluidSlotInventory.getItems(), registries);
        tag.put("FluidSlots", fluidSlotsTag);

        if (chainedTargetPos != null && chainedFluid != null && chainedFluid != Fluids.EMPTY) {
            tag.putInt("ChainedX", chainedTargetPos.getX());
            tag.putInt("ChainedY", chainedTargetPos.getY());
            tag.putInt("ChainedZ", chainedTargetPos.getZ());
            tag.putString("ChainedFluid", BuiltInRegistries.FLUID.getKey(chainedFluid).toString());
            if (!frontierSet.isEmpty()) {
                int saveCount = Math.min(frontierSet.size(), MAX_SAVED_CHAIN_FRONTIER);
                long[] packed = new long[saveCount];
                int i = 0;
                // Save closest frontier entries first; outer entries are rediscovered naturally on load
                for (List<BlockPos> bucket : frontierByDist.values()) {
                    for (BlockPos p : bucket) {
                        if (i >= saveCount) break;
                        packed[i++] = p.asLong();
                    }
                    if (i >= saveCount) break;
                }
                tag.putLongArray("ChainedFrontier", packed);
            }
        }
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);

        // Load upgrades first so cached counts and capacity scaling are accurate before restoring FluidTank and Energy
        ContainerHelper.loadAllItems(tag, upgradeInventory.getItems(), registries);
        if (tag.contains("FluidSlots")) {
            ContainerHelper.loadAllItems(tag.getCompound("FluidSlots"), fluidSlotInventory.getItems(), registries);
        }
        refreshUpgradeCacheAndCapacities();

        if (tag.contains("FluidTank")) {
            fluidTank.readFromNBT(registries, tag.getCompound("FluidTank"));
        }
        if (tag.contains("Energy")) {
            energyStorage.setEnergy(tag.getInt("Energy"));
        }
        tickCounter = tag.getInt("TickCounter");
        redstoneMode = RedstoneMode.byOrdinal(tag.getInt("RedstoneMode"));

        clearChainState();
        if (tag.contains("ChainedX") && tag.contains("ChainedY") && tag.contains("ChainedZ") && tag.contains("ChainedFluid")) {
            this.chainedTargetPos = new BlockPos(tag.getInt("ChainedX"), tag.getInt("ChainedY"), tag.getInt("ChainedZ"));
            ResourceLocation fId = ResourceLocation.tryParse(tag.getString("ChainedFluid"));
            this.chainedFluid = (fId != null) ? BuiltInRegistries.FLUID.get(fId) : null;
            if (this.chainedFluid != null && this.chainedFluid != Fluids.EMPTY && tag.contains("ChainedFrontier", Tag.TAG_LONG_ARRAY)) {
                for (long packed : tag.getLongArray("ChainedFrontier")) {
                    addFrontierPos(this.worldPosition, BlockPos.of(packed));
                }
            }
        }
    }

    @Override
    public Component getDisplayName() {
        return getBlockState().getBlock().getName();
    }

    @Nullable
    @Override
    public AbstractContainerMenu createMenu(int containerId, Inventory playerInventory, Player player) {
        return new TieredElectricPumpMenu(containerId, playerInventory, this, dataAccess);
    }

    private class CustomEnergyStorage extends EnergyStorage {
        public CustomEnergyStorage(int capacity) {
            super(capacity, capacity, 0);
        }

        @Override
        public int receiveEnergy(int maxReceive, boolean simulate) {
            int received = super.receiveEnergy(maxReceive, simulate);
            if (!simulate && received > 0) {
                TieredElectricPumpBlockEntity.this.energyDirty = true;
            }
            return received;
        }

        public void setCapacity(int capacity) {
            this.capacity = capacity;
            this.maxReceive = capacity;
            if (this.energy > capacity) this.energy = capacity;
        }

        public void setEnergy(int energy) {
            this.energy = Math.min(capacity, Math.max(0, energy));
        }

        public void consumeEnergy(int amount) {
            if (amount > 0 && this.energy > 0) {
                this.energy = Math.max(0, this.energy - amount);
                TieredElectricPumpBlockEntity.this.energyDirty = true;
            }
        }
    }
}
