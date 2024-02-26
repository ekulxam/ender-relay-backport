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
    public static int getLightLevel(BlockState blockState) {
        return getChargeLevel(blockState) ? 15 : 0;
    }

    public boolean hasComparatorOutput(BlockState blockState) {return true;}
    public static boolean getChargeLevel(BlockState blockState) {
        return blockState.get(CHARGED);
    }
    @Override
    public int getComparatorOutput(BlockState blockState, World world, BlockPos blockPos) {
        return getLightLevel(blockState);
    }

    @Override
    protected void appendProperties(StateManager.Builder<Block, BlockState> builder) {
        builder.add(CHARGED);
    }

    @Override
    public @NotNull ActionResult onUse(BlockState blockState, World world, BlockPos blockPos, PlayerEntity player, Hand interactionHand, BlockHitResult blockHitResult) {
        ItemStack itemInHand = player.getStackInHand(interactionHand);
        if (itemInHand.isOf(Items.END_CRYSTAL) && !blockState.get(CHARGED)) {
            light(player, world, blockPos, blockState);
            if (!player.getAbilities().creativeMode) {
                itemInHand.decrement(1);
            }
            return ActionResult.success(world.isClient);
        }

        if (world.getDimensionKey() == DimensionTypes.THE_END) {
            BlockEntity blockEntity = world.getBlockEntity(blockPos);
            if (!(blockEntity instanceof EnderRelayBlockEntity enderRelayEntity)) return ActionResult.FAIL;

            if (!blockState.get(CHARGED)) return ActionResult.FAIL;
            BlockState newState = blockState.with(CHARGED, false);
            world.setBlockState(blockPos, newState, 3);
            world.emitGameEvent(GameEvent.BLOCK_CHANGE, blockPos, GameEvent.Emitter.of(player, newState));

            if(!world.isClient) {
                if (enderRelayEntity.hasNoLocation()) {
                    useActionbarSendEnderRelayError(player, Text.translatable("enderrelay.unknown_destination"));
                    return ActionResult.FAIL;
                }
                sendToLocation((ServerPlayerEntity) player, (ServerWorld) world, enderRelayEntity.getX(), enderRelayEntity.getY(), enderRelayEntity.getZ());
            }
            return ActionResult.success(world.isClient);
        } else if (blockState.get(CHARGED) && !world.isClient) {
            explode(blockState, world, blockPos);
            return ActionResult.success(false);
        }

        return ActionResult.FAIL;
    }

    // Copied from RespawnAnchorBlock for exact same functionality: downdated? to 1.19 respawn anchor
    private void explode(BlockState state, World world, final BlockPos explodedPos) {
        world.removeBlock(explodedPos, false);
        Stream<Direction> var10000 = Direction.Type.HORIZONTAL.stream();
        Objects.requireNonNull(explodedPos);
        boolean bl = var10000.map(explodedPos::offset).anyMatch((pos) -> {
            return hasStillWater(pos, world);
        });
        final boolean bl2 = bl || world.getFluidState(explodedPos.up()).isIn(FluidTags.WATER);
        ExplosionBehavior explosionBehavior = new ExplosionBehavior() {
            public Optional<Float> getBlastResistance(Explosion explosion, BlockView world, BlockPos pos, BlockState blockState, FluidState fluidState) {
                return pos.equals(explodedPos) && bl2 ? Optional.of(Blocks.WATER.getBlastResistance()) : super.getBlastResistance(explosion, world, pos, blockState, fluidState);
            }
        };
        world.createExplosion(null, DamageSource.badRespawnPoint(), explosionBehavior, (double)explodedPos.getX() + 0.5, (double)explodedPos.getY() + 0.5, (double)explodedPos.getZ() + 0.5, 5.0F, true, Explosion.DestructionType.DESTROY);
    }

    public static boolean hasStillWater(BlockPos blockPos, World world) {
        FluidState fluidState = world.getFluidState(blockPos);
        if (!fluidState.isIn(FluidTags.WATER)) {
            return false;
        } else if (fluidState.isStill()) {
            return true;
        } else {
            float f = (float)fluidState.getLevel();
            if (f < 2.0F) {
                return false;
            } else {
                FluidState fluidState2 = world.getFluidState(blockPos.down());
                return !fluidState2.isIn(FluidTags.WATER);
            }
        }
    }

    public static void light(@Nullable Entity entity, World world, BlockPos blockPos, BlockState blockState) {
        BlockState newState = blockState.with(CHARGED, true);
        world.setBlockState(blockPos, newState, 3);
        world.emitGameEvent(GameEvent.BLOCK_CHANGE, blockPos, GameEvent.Emitter.of(entity, newState));
        world.playSound(null, blockPos, SoundEvents.BLOCK_END_PORTAL_FRAME_FILL, SoundCategory.BLOCKS, 1.0f, 1.0f); // TODO: Better sound effects (if you want to do something and can do some sound effect stuff, dm me)
    }

    @Override
    public void onBreak(World world, BlockPos pos, BlockState blockState, PlayerEntity player) {
        ItemStack itemInMainHand = player.getStackInHand(Hand.MAIN_HAND);
        if (EnchantmentHelper.getLevel(Enchantments.SILK_TOUCH, itemInMainHand) == 0) {
            BlockEntity blockEntity = world.getBlockEntity(pos);

            if (!(blockEntity instanceof EnderRelayBlockEntity enderRelayEntity)) {
                super.onBreak(world, pos, blockState, player);
                return;
            }
            if(enderRelayEntity.hasNoLocation()) {
                super.onBreak(world, pos, blockState, player);
                return;
            }

            ItemStack compass = new ItemStack(Items.COMPASS, 1);
            ((CompassItem) Items.COMPASS).writeNbt(world.getRegistryKey(), new BlockPos(enderRelayEntity.getX(), enderRelayEntity.getY(), enderRelayEntity.getZ()), compass.getOrCreateNbt());
            dropStack(world, pos, compass);
        }
        super.onBreak(world, pos, blockState, player);
    }

    public static void sendToLocation(ServerPlayerEntity player, ServerWorld serverWorld, int x, int y, int z) {
        BlockPos blockPos = new BlockPos(x, y, z);
        Optional<Vec3d> pos = RespawnAnchorBlock.findRespawnPosition(EntityType.PLAYER, serverWorld, blockPos);
        if (pos.isEmpty()) {
            useActionbarSendEnderRelayError(player, Text.translatable("enderrelay.obstructed_destination"));
            return;
        }

        // pasted code from PlayerManager#respawnPlayer to make it the most vanilla thing possible
        float g;
        BlockState blockState = serverWorld.getBlockState(blockPos);
        boolean isLodestone = blockState.isOf(Blocks.LODESTONE);
        Vec3d vec3d = pos.get();
        if (isLodestone) {
            Vec3d vec3d2 = Vec3d.ofBottomCenter(blockPos).subtract(vec3d).normalize();
            g = (float) MathHelper.wrapDegrees(MathHelper.atan2(vec3d2.z, vec3d2.x) * 57.2957763671875 - 90.0);
        } else {
            useActionbarSendEnderRelayError(player, Text.translatable("enderrelay.no_lodestone"));
            return;
        }


        serverWorld.playSound(null, player.getSteppingPos(), SoundEvents.ENTITY_ENDERMAN_TELEPORT, SoundCategory.BLOCKS, 1.0f, 1.0f);

        player.teleport(serverWorld, vec3d.x, vec3d.y, vec3d.z, g, 0.0f);

        // copied from PlayerList line 427
        while (!serverWorld.isSpaceEmpty(player) && player.getY() < (double)serverWorld.getTopY()) {
            player.setPosition(player.getX(), player.getY() + 1.0, player.getZ());
        }

        player.teleport(serverWorld, player.getX(), player.getY(), player.getZ(), g, 0.0f);

        serverWorld.playSound(null, vec3d.x, vec3d.y, vec3d.z, SoundEvents.ENTITY_ENDERMAN_TELEPORT, SoundCategory.BLOCKS, 1.0f, 1.0f);
    }

    @Nullable
    @Override
    public BlockEntity createBlockEntity(BlockPos pos, BlockState state) {
        return new EnderRelayBlockEntity(pos, state);
    }

    @Override
    public void randomDisplayTick(BlockState blockState, World world, BlockPos blockPos, Random randomSource) {
        if (blockState.get(CHARGED)) {
            // Mostly copied & modified from NetherPortalBlock
            if (randomSource.nextInt(100) == 0) {
                world.playSound((PlayerEntity)null, (double)blockPos.getX() + 0.5, (double)blockPos.getY() + 0.5, (double)blockPos.getZ() + 0.5, SoundEvents.BLOCK_RESPAWN_ANCHOR_AMBIENT, SoundCategory.BLOCKS, 1.0F, 1.0F);
            }
            double d = (double)blockPos.getX() + randomSource.nextDouble();
            double e = (double)blockPos.getY() + randomSource.nextDouble();
            double f = (double)blockPos.getZ() + randomSource.nextDouble();
            double g = ((double)randomSource.nextFloat() - 0.5) * 0.5;
            double h = ((double)randomSource.nextFloat() - 0.5) * 0.5;
            double j = ((double)randomSource.nextFloat() - 0.5) * 0.5;

            world.addParticle(ParticleTypes.PORTAL, d, e, f, g, h, j);
        }
    }

    private static void useActionbarSendEnderRelayError(PlayerEntity target, Text text){
        target.sendMessage(text, true);
    }
}
