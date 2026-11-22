package com.mekmp.block;

import com.mekmp.block.entity.TieredElectricPumpBlockEntity;
import com.mekmp.registry.ModBlockEntities;
import com.mekmp.tier.PumpTier;
import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.Containers;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

import javax.annotation.Nullable;

public class TieredElectricPumpBlock extends BaseEntityBlock {
    public static final DirectionProperty FACING = HorizontalDirectionalBlock.FACING;

    // Model boxes defined in default model orientation (facing=south, blockstate y=0, power port on north face z=0..5)
    private static final double[][] SOUTH_BOXES = {
            {5.0,  0.0,  5.0, 11.0, 15.0, 11.0}, // pumpBase
            {4.0,  1.0,  4.0, 12.0, 12.0, 12.0}, // pumpCasing
            {4.0, 13.0,  4.0, 12.0, 14.0, 12.0}, // pumpRingTop
            {4.0, 15.0,  4.0, 12.0, 16.0, 12.0}, // pumpPortTop
            {4.0,  4.0,  0.0, 12.0, 12.0,  1.0}, // powerPort
            {6.0,  6.0,  1.0, 10.0, 10.0,  4.0}, // powerConnector
            {10.0, 10.0, 1.0, 11.0, 11.0,  5.0}, // powerConnectorFrame1
            {5.0, 10.0,  1.0,  6.0, 11.0,  5.0}, // powerConnectorFrame2
            {5.0,  5.0,  1.0,  6.0,  6.0,  5.0}, // powerConnectorFrame3
            {10.0, 5.0,  1.0, 11.0,  6.0,  5.0}  // powerConnectorFrame4
    };

    private static final VoxelShape SHAPE_SOUTH = buildRotatedShape(Direction.SOUTH);
    private static final VoxelShape SHAPE_WEST  = buildRotatedShape(Direction.WEST);
    private static final VoxelShape SHAPE_NORTH = buildRotatedShape(Direction.NORTH);
    private static final VoxelShape SHAPE_EAST  = buildRotatedShape(Direction.EAST);

    private static VoxelShape buildRotatedShape(Direction facing) {
        VoxelShape combined = Shapes.empty();
        for (double[] b : SOUTH_BOXES) {
            double x1 = b[0], y1 = b[1], z1 = b[2];
            double x2 = b[3], y2 = b[4], z2 = b[5];
            VoxelShape part = switch (facing) {
                case WEST  -> Block.box(16.0 - z2, y1, x1, 16.0 - z1, y2, x2);
                case NORTH -> Block.box(16.0 - x2, y1, 16.0 - z2, 16.0 - x1, y2, 16.0 - z1);
                case EAST  -> Block.box(z1, y1, 16.0 - x2, z2, y2, 16.0 - x1);
                default    -> Block.box(x1, y1, z1, x2, y2, z2);
            };
            combined = Shapes.or(combined, part);
        }
        return combined.optimize();
    }

    private final PumpTier tier;
    private final MapCodec<TieredElectricPumpBlock> codec;

    public TieredElectricPumpBlock(PumpTier tier, Properties properties) {
        super(properties);
        this.tier = tier;
        this.codec = simpleCodec(props -> new TieredElectricPumpBlock(this.tier, props));
        this.registerDefaultState(this.stateDefinition.any().setValue(FACING, Direction.NORTH));
    }

    public PumpTier getTier() {
        return tier;
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return switch (state.getValue(FACING)) {
            case NORTH -> SHAPE_NORTH;
            case WEST  -> SHAPE_WEST;
            case EAST  -> SHAPE_EAST;
            default    -> SHAPE_SOUTH;
        };
    }

    @Override
    protected MapCodec<? extends BaseEntityBlock> codec() {
        return codec;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING);
    }

    @Nullable
    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        return this.defaultBlockState().setValue(FACING, context.getHorizontalDirection().getOpposite());
    }

    @Override
    public RenderShape getRenderShape(BlockState state) {
        return RenderShape.MODEL;
    }

    @Override
    protected ItemInteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hitResult) {
        if (player.isShiftKeyDown() && (!stack.isEmpty() || !player.getOffhandItem().isEmpty())) {
            return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
        }
        return super.useItemOn(stack, state, level, pos, player, hand, hitResult);
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hitResult) {
        if (player.isShiftKeyDown() && (!player.getMainHandItem().isEmpty() || !player.getOffhandItem().isEmpty())) {
            return InteractionResult.PASS;
        }
        if (!level.isClientSide && level.getBlockEntity(pos) instanceof TieredElectricPumpBlockEntity pumpBe) {
            player.openMenu(pumpBe, pos);
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }

    @Override
    protected void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean movedByPiston) {
        if (!state.is(newState.getBlock())) {
            if (level.getBlockEntity(pos) instanceof TieredElectricPumpBlockEntity pumpBe) {
                Containers.dropContents(level, pos, pumpBe.getUpgradeInventory());
                Containers.dropContents(level, pos, pumpBe.getFluidSlotInventory());
            }
        }
        super.onRemove(state, level, pos, newState, movedByPiston);
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new TieredElectricPumpBlockEntity(pos, state);
    }

    @Nullable
    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        if (level.isClientSide) return null;
        return createTickerHelper(type, ModBlockEntities.ELECTRIC_PUMP.get(), TieredElectricPumpBlockEntity::serverTick);
    }
}
