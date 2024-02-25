package town.kibty.enderrelay.block;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Objects;
import java.util.Optional;
import java.util.stream.Stream;
import net.minecraft.block.Block;
import net.minecraft.block.BlockEntityProvider;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.RespawnAnchorBlock;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.enchantment.EnchantmentHelper;
import net.minecraft.enchantment.Enchantments;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.fluid.FluidState;
import net.minecraft.item.CompassItem;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.state.StateManager;
import net.minecraft.state.property.BooleanProperty;
import net.minecraft.tag.FluidTags;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.math.random.Random;
import net.minecraft.world.BlockView;
import net.minecraft.world.World;
import net.minecraft.world.dimension.DimensionTypes;
import net.minecraft.world.event.GameEvent;
import net.minecraft.world.explosion.Explosion;
import net.minecraft.world.explosion.ExplosionBehavior;

public class EnderRelayBlock extends Block implements BlockEntityProvider {
    public static final BooleanProperty CHARGED = BooleanProperty.of("charged");

    public EnderRelayBlock(Settings properties) {
        super(properties);
        this.setDefaultState(this.getDefaultState().with(CHARGED, false));
    }

    public boolean hasComparatorOutput(BlockState blockState) {return true;}
    public static boolean getChargeLevel(BlockState blockState) {
        return blockState.get(CHARGED);
    }

    @Override
    public int getComparatorOutput(BlockState blockState, World level, BlockPos blockPos) {
        return getChargeLevel(blockState) ? 15 : 0;
    }

    @Override
    protected void appendProperties(StateManager.Builder<Block, BlockState> builder) {
        builder.add(CHARGED);
    }

    @Override
    public @NotNull ActionResult onUse(BlockState blockState, World level, BlockPos blockPos, PlayerEntity player, Hand interactionHand, BlockHitResult blockHitResult) {
        ItemStack itemInHand = player.getStackInHand(interactionHand);
        if (itemInHand.isOf(Items.END_CRYSTAL) && !blockState.get(CHARGED)) {
            light(player, level, blockPos, blockState);
            if (!player.getAbilities().creativeMode) {
                itemInHand.decrement(1);
            }
            return ActionResult.success(level.isClient);
        }

        if (level.getDimensionKey() == DimensionTypes.THE_END) {
            BlockEntity blockEntity = level.getBlockEntity(blockPos);
            if (!(blockEntity instanceof EnderRelayBlockEntity enderRelayEntity)) return ActionResult.FAIL;

            if (!blockState.get(CHARGED)) return ActionResult.FAIL;
            BlockState newState = blockState.with(CHARGED, false);
            level.setBlockState(blockPos, newState, 3);
            level.emitGameEvent(GameEvent.BLOCK_CHANGE, blockPos, GameEvent.Emitter.of(player, newState));

            if(!level.isClient) {
                if (enderRelayEntity.hasNoLocation()) {
                    player.sendMessage(
                            Text.translatable("enderrelay.unknown_destination"),
                            false
                    );
                    return ActionResult.FAIL;
                }
                sendToLocation((ServerPlayerEntity) player, (ServerWorld) level, enderRelayEntity.getX(), enderRelayEntity.getY(), enderRelayEntity.getZ());
            }
            return ActionResult.success(level.isClient);
        } else if (blockState.get(CHARGED) && !level.isClient) {
            explode(blockState, level, blockPos);
            return ActionResult.success(false);
        }

        return ActionResult.FAIL;
    }

    // Copied from RespawnAnchorBlock for exact same functionality: downdated? to 1.19 respawn anchor
    private void explode(BlockState blockState, World level, final BlockPos blockPos) {
        level.removeBlock(blockPos, false);
        Stream<Direction> var10000 = Direction.Type.HORIZONTAL.stream();
        Objects.requireNonNull(blockPos);
        boolean bl = var10000.map(blockPos::offset).anyMatch((blockPosx) -> isWaterThatWouldFlow(blockPosx, level));
        final boolean bl2 = bl || level.getFluidState(blockPos.up()).isIn(FluidTags.WATER);
        ExplosionBehavior explosionDamageCalculator = new ExplosionBehavior() {
            public @NotNull Optional<Float> getBlastResistance(Explosion explosion, BlockView blockGetter, BlockPos blockPosx, BlockState blockState, FluidState fluidState) {
                return blockPosx.equals(blockPos) && bl2 ? Optional.of(Blocks.WATER.getBlastResistance()) : super.getBlastResistance(explosion, blockGetter, blockPosx, blockState, fluidState);
            }
        };
        level.createExplosion(null, DamageSource.badRespawnPoint(), explosionDamageCalculator, (double)blockPos.getX() + 0.5, (double)blockPos.getY() + 0.5, (double)blockPos.getZ() + 0.5, 5.0F, true, Explosion.DestructionType.DESTROY);
    }

    public static boolean isWaterThatWouldFlow(BlockPos blockPos, World level) {
        FluidState fluidState = level.getFluidState(blockPos);
        if (!fluidState.isIn(FluidTags.WATER)) {
            return false;
        } else if (fluidState.isStill()) {
            return true;
        } else {
            float f = (float)fluidState.getLevel();
            if (f < 2.0F) {
                return false;
            } else {
                FluidState fluidState2 = level.getFluidState(blockPos.down());
                return !fluidState2.isIn(FluidTags.WATER);
            }
        }
    }

    public static void light(@Nullable Entity entity, World level, BlockPos blockPos, BlockState blockState) {
        BlockState newState = blockState.with(CHARGED, true);
        level.setBlockState(blockPos, newState, 3);
        level.emitGameEvent(GameEvent.BLOCK_CHANGE, blockPos, GameEvent.Emitter.of(entity, newState));
        level.playSound(null, blockPos, SoundEvents.BLOCK_END_PORTAL_FRAME_FILL, SoundCategory.BLOCKS, 1.0f, 1.0f); // TODO: Better sound effects (if you want to do something and can do some sound effect stuff, dm me)
    }

    @Override
    public void onBreak(World level, BlockPos pos, BlockState blockState, PlayerEntity player) {
        ItemStack itemInMainHand = player.getStackInHand(Hand.MAIN_HAND);
        if (EnchantmentHelper.getLevel(Enchantments.SILK_TOUCH, itemInMainHand) == 0) {
            BlockEntity blockEntity = level.getBlockEntity(pos);

            if (!(blockEntity instanceof EnderRelayBlockEntity enderRelayEntity)) {
                super.onBreak(level, pos, blockState, player);
                return;
            }
            if(enderRelayEntity.hasNoLocation()) {
                super.onBreak(level, pos, blockState, player);
                return;
            }

            ItemStack compass = new ItemStack(Items.COMPASS, 1);
            ((CompassItem) Items.COMPASS).writeNbt(level.getRegistryKey(), new BlockPos(enderRelayEntity.getX(), enderRelayEntity.getY(), enderRelayEntity.getZ()), compass.getOrCreateNbt());
            dropStack(level, pos, compass);
        }
        super.onBreak(level, pos, blockState, player);
    }

    public static void sendToLocation(ServerPlayerEntity player, ServerWorld level, int x, int y, int z) {
        BlockPos blockPos = new BlockPos(x, y, z);
        Optional<Vec3d> pos = RespawnAnchorBlock.findRespawnPosition(EntityType.PLAYER, level, blockPos);

        if (pos.isEmpty()) {
            player.sendMessage(
                    Text.translatable("enderrelay.obstructed_destination"),
                    false
            );
            return;
        }

        // pasted code from ServerList#respawn to make it the most vanilla thing possible
        float g;
        BlockState blockState = level.getBlockState(blockPos);
        boolean isLodestone = blockState.isOf(Blocks.LODESTONE);
        Vec3d vec3 = pos.get();
        if (isLodestone) {
            Vec3d vec32 = Vec3d.ofBottomCenter(blockPos).subtract(vec3).normalize();
            g = (float) MathHelper.wrapDegrees(MathHelper.atan2(vec32.z, vec32.x) * 57.2957763671875 - 90.0);
        } else {
            player.sendMessage(
                    Text.translatable("enderrelay.no_lodestone"),
                    false
            );
            return;
        }


        level.playSound(null, player.getSteppingPos(), SoundEvents.ENTITY_ENDERMAN_TELEPORT, SoundCategory.BLOCKS, 1.0f, 1.0f); // TODO: Better sound effects (if you want to do something and can do some sound effect stuff, dm me)

        player.teleport(level, vec3.x, vec3.y, vec3.z, g, 0.0f);

        // copied from PlayerList line 427
        while (!level.isSpaceEmpty(player) && player.getY() < (double)level.getTopY()) {
            player.setPosition(player.getX(), player.getY() + 1.0, player.getZ());
        }

        player.teleport(level, player.getX(), player.getY(), player.getZ(), g, 0.0f);

        level.playSound(null, vec3.x, vec3.y, vec3.z, SoundEvents.ENTITY_ENDERMAN_TELEPORT, SoundCategory.BLOCKS, 1.0f, 1.0f); // TODO: Better sound effects (if you want to do something and can do some sound effect stuff, dm me)
    }

    @Nullable
    @Override
    public BlockEntity createBlockEntity(BlockPos pos, BlockState state) {
        return new EnderRelayBlockEntity(pos, state);
    }

    @Override
    public void randomDisplayTick(BlockState blockState, World level, BlockPos blockPos, Random randomSource) {
        if (blockState.get(CHARGED)) {
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
