package town.kibty.enderrelay.block;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.FluidTags;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.CompassItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Explosion;
import net.minecraft.world.level.ExplosionDamageCalculator;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.RespawnAnchorBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.dimension.BuiltinDimensionTypes;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Objects;
import java.util.Optional;
import java.util.stream.Stream;

public class EnderRelayBlock extends Block implements EntityBlock {
    public static final BooleanProperty CHARGED = BooleanProperty.create("charged");

    public EnderRelayBlock(Properties properties) {
        super(properties);
        this.registerDefaultState(this.defaultBlockState().setValue(CHARGED, false));
    }

    public boolean hasAnalogOutputSignal(BlockState blockState) {return true;}
    public static boolean getChargeLevel(BlockState blockState) {
        return blockState.getValue(CHARGED);
    }

    @Override
    public int getAnalogOutputSignal(BlockState blockState, Level level, BlockPos blockPos) {
        return getChargeLevel(blockState) ? 15 : 0;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(CHARGED);
    }

    @Override
    public @NotNull InteractionResult use(BlockState blockState, Level level, BlockPos blockPos, Player player, InteractionHand interactionHand, BlockHitResult blockHitResult) {
        ItemStack itemInHand = player.getItemInHand(interactionHand);
        if (itemInHand.is(Items.END_CRYSTAL) && !blockState.getValue(CHARGED)) {
            light(player, level, blockPos, blockState);
            if (!player.getAbilities().instabuild) {
                itemInHand.shrink(1);
            }
            return InteractionResult.sidedSuccess(level.isClientSide);
        }

        if (level.dimensionTypeId() == BuiltinDimensionTypes.END) {
            BlockEntity blockEntity = level.getBlockEntity(blockPos);
            if (!(blockEntity instanceof EnderRelayBlockEntity enderRelayEntity)) return InteractionResult.FAIL;

            if (!blockState.getValue(CHARGED)) return InteractionResult.FAIL;
            BlockState newState = blockState.setValue(CHARGED, false);
            level.setBlock(blockPos, newState, 3);
            level.gameEvent(GameEvent.BLOCK_CHANGE, blockPos, GameEvent.Context.of(player, newState));

            if(!level.isClientSide) {
                if (enderRelayEntity.hasNoLocation()) {
                    player.displayClientMessage(
                            Component.translatable("enderrelay.unknown_destination"),
                            false
                    );
                    return InteractionResult.FAIL;
                }
                sendToLocation((ServerPlayer) player, (ServerLevel) level, enderRelayEntity.getX(), enderRelayEntity.getY(), enderRelayEntity.getZ());
            }
            return InteractionResult.sidedSuccess(level.isClientSide);
        } else if (blockState.getValue(CHARGED) && !level.isClientSide) {
            explode(blockState, level, blockPos);
            return InteractionResult.sidedSuccess(false);
        }

        return InteractionResult.FAIL;
    }

    // Copied from RespawnAnchorBlock for exact same functionality: downdated? to 1.19 respawn anchor
    private void explode(BlockState blockState, Level level, final BlockPos blockPos) {
        level.removeBlock(blockPos, false);
        Stream<Direction> var10000 = Direction.Plane.HORIZONTAL.stream();
        Objects.requireNonNull(blockPos);
        boolean bl = var10000.map(blockPos::relative).anyMatch((blockPosx) -> isWaterThatWouldFlow(blockPosx, level));
        final boolean bl2 = bl || level.getFluidState(blockPos.above()).is(FluidTags.WATER);
        ExplosionDamageCalculator explosionDamageCalculator = new ExplosionDamageCalculator() {
            public @NotNull Optional<Float> getBlockExplosionResistance(Explosion explosion, BlockGetter blockGetter, BlockPos blockPosx, BlockState blockState, FluidState fluidState) {
                return blockPosx.equals(blockPos) && bl2 ? Optional.of(Blocks.WATER.getExplosionResistance()) : super.getBlockExplosionResistance(explosion, blockGetter, blockPosx, blockState, fluidState);
            }
        };
        level.explode(null, DamageSource.badRespawnPointExplosion(), explosionDamageCalculator, (double)blockPos.getX() + 0.5, (double)blockPos.getY() + 0.5, (double)blockPos.getZ() + 0.5, 5.0F, true, Explosion.BlockInteraction.DESTROY);
    }

    public static boolean isWaterThatWouldFlow(BlockPos blockPos, Level level) {
        FluidState fluidState = level.getFluidState(blockPos);
        if (!fluidState.is(FluidTags.WATER)) {
            return false;
        } else if (fluidState.isSource()) {
            return true;
        } else {
            float f = (float)fluidState.getAmount();
            if (f < 2.0F) {
                return false;
            } else {
                FluidState fluidState2 = level.getFluidState(blockPos.below());
                return !fluidState2.is(FluidTags.WATER);
            }
        }
    }

    public static void light(@Nullable Entity entity, Level level, BlockPos blockPos, BlockState blockState) {
        BlockState newState = blockState.setValue(CHARGED, true);
        level.setBlock(blockPos, newState, 3);
        level.gameEvent(GameEvent.BLOCK_CHANGE, blockPos, GameEvent.Context.of(entity, newState));
        level.playSound(null, blockPos, SoundEvents.END_PORTAL_FRAME_FILL, SoundSource.BLOCKS, 1.0f, 1.0f); // TODO: Better sound effects (if you want to do something and can do some sound effect stuff, dm me)
    }

    @Override
    public void playerWillDestroy(Level level, BlockPos pos, BlockState blockState, Player player) {
        ItemStack itemInMainHand = player.getItemInHand(InteractionHand.MAIN_HAND);
        if (EnchantmentHelper.getItemEnchantmentLevel(Enchantments.SILK_TOUCH, itemInMainHand) == 0) {
            BlockEntity blockEntity = level.getBlockEntity(pos);

            if (!(blockEntity instanceof EnderRelayBlockEntity enderRelayEntity)) {
                super.playerWillDestroy(level, pos, blockState, player);
                return;
            }
            if(enderRelayEntity.hasNoLocation()) {
                super.playerWillDestroy(level, pos, blockState, player);
                return;
            }

            ItemStack compass = new ItemStack(Items.COMPASS, 1);
            ((CompassItem) Items.COMPASS).addLodestoneTags(level.dimension(), new BlockPos(enderRelayEntity.getX(), enderRelayEntity.getY(), enderRelayEntity.getZ()), compass.getOrCreateTag());
            popResource(level, pos, compass);
        }
        super.playerWillDestroy(level, pos, blockState, player);
    }

    public static void sendToLocation(ServerPlayer player, ServerLevel level, int x, int y, int z) {
        BlockPos blockPos = new BlockPos(x, y, z);
        Optional<Vec3> pos = RespawnAnchorBlock.findStandUpPosition(EntityType.PLAYER, level, blockPos);

        if (pos.isEmpty()) {
            player.displayClientMessage(
                    Component.translatable("enderrelay.obstructed_destination"),
                    false
            );
            return;
        }

        // pasted code from ServerList#respawn to make it the most vanilla thing possible
        float g;
        BlockState blockState = level.getBlockState(blockPos);
        boolean isLodestone = blockState.is(Blocks.LODESTONE);
        Vec3 vec3 = pos.get();
        if (isLodestone) {
            Vec3 vec32 = Vec3.atBottomCenterOf(blockPos).subtract(vec3).normalize();
            g = (float) Mth.wrapDegrees(Mth.atan2(vec32.z, vec32.x) * 57.2957763671875 - 90.0);
        } else {
            player.displayClientMessage(
                    Component.translatable("enderrelay.no_lodestone"),
                    false
            );
            return;
        }


        level.playSound(null, player.getOnPos(), SoundEvents.ENDERMAN_TELEPORT, SoundSource.BLOCKS, 1.0f, 1.0f); // TODO: Better sound effects (if you want to do something and can do some sound effect stuff, dm me)

        player.teleportTo(level, vec3.x, vec3.y, vec3.z, g, 0.0f);

        // copied from PlayerList line 427
        while (!level.noCollision(player) && player.getY() < (double)level.getMaxBuildHeight()) {
            player.setPos(player.getX(), player.getY() + 1.0, player.getZ());
        }

        player.teleportTo(level, player.getX(), player.getY(), player.getZ(), g, 0.0f);

        level.playSound(null, vec3.x, vec3.y, vec3.z, SoundEvents.ENDERMAN_TELEPORT, SoundSource.BLOCKS, 1.0f, 1.0f); // TODO: Better sound effects (if you want to do something and can do some sound effect stuff, dm me)
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new EnderRelayBlockEntity(pos, state);
    }

    @Override
    public void animateTick(BlockState blockState, Level level, BlockPos blockPos, RandomSource randomSource) {
        if (blockState.getValue(CHARGED)) {
            // Mostly copied & modified from NetherPortalBlock
            double d = (double)blockPos.getX() + randomSource.nextDouble();
            double e = (double)blockPos.getY() + randomSource.nextDouble();
            double f = (double)blockPos.getZ() + randomSource.nextDouble();
            double g = ((double)randomSource.nextFloat() - 0.5) * 0.5;
            double h = ((double)randomSource.nextFloat() - 0.5) * 0.5;
            double j = ((double)randomSource.nextFloat() - 0.5) * 0.5;

            level.addParticle(ParticleTypes.PORTAL, d, e, f, g, h, j);
        }
    }
}
