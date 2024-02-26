package town.kibty.enderrelay;

import net.minecraft.block.*;
import net.minecraft.block.dispenser.FallibleItemDispenserBehavior;
import net.minecraft.block.entity.BlockEntityType;
import net.minecraft.item.*;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPointer;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.registry.Registry;
import town.kibty.enderrelay.block.EnderRelayBlock;
import town.kibty.enderrelay.block.EnderRelayBlockEntity;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.object.builder.v1.block.FabricBlockSettings;
import net.fabricmc.fabric.api.object.builder.v1.block.entity.FabricBlockEntityTypeBuilder;
import org.jetbrains.annotations.NotNull;
import town.kibty.enderrelay.recipe.EnderRelayRecipe;

public class EnderRelay implements ModInitializer {
    public static final String MOD_ID = "enderrelay";

    public static final Block ENDER_RELAY_BLOCK = new EnderRelayBlock(FabricBlockSettings.of(Material.STONE)
            .mapColor(MapColor.BLACK)
            .requiresTool()
            .strength(50.0f, 1200.0f)
            .luminance(blockStatex -> EnderRelayBlock.getChargeLevel(blockStatex) ? 15 : 0)
    );
    public static final BlockItem ENDER_RELAY_ITEM = new BlockItem(ENDER_RELAY_BLOCK, new Item.Settings().group(ItemGroup.DECORATIONS));
    public static final BlockEntityType<EnderRelayBlockEntity> ENDER_RELAY_BLOCK_ENTITY = Registry.register(
            Registry.BLOCK_ENTITY_TYPE,
            new Identifier(MOD_ID, "ender_relay"),
            FabricBlockEntityTypeBuilder.create(EnderRelayBlockEntity::new, ENDER_RELAY_BLOCK).build()
    );

    /** Runs the mod initializer. */
    @Override
    public void onInitialize() {
        Registry.register(Registry.BLOCK, new Identifier(MOD_ID, "ender_relay"), ENDER_RELAY_BLOCK);
        Registry.register(Registry.ITEM, new Identifier(MOD_ID, "ender_relay"), ENDER_RELAY_ITEM);
        Registry.register(Registry.RECIPE_SERIALIZER, new Identifier(MOD_ID, "ender_relay"), EnderRelayRecipe.SERIALIZER);

        // Mimic respawn anchor + dispenser functionality
        DispenserBlock.registerBehavior(Items.END_CRYSTAL, new FallibleItemDispenserBehavior(){

            @Override
            public @NotNull ItemStack dispenseSilently(BlockPointer blockSource, ItemStack itemStack) {
                Direction direction = blockSource.getBlockState().get(DispenserBlock.FACING);
                BlockPos blockPos = blockSource.getPos().offset(direction);
                ServerWorld level = blockSource.getWorld();
                BlockState blockState = level.getBlockState(blockPos);
                this.setSuccess(true);
                if (blockState.isOf(ENDER_RELAY_BLOCK)) {
                    if (!blockState.get(EnderRelayBlock.CHARGED)) {
                        EnderRelayBlock.light(null, level, blockPos, blockState);
                        itemStack.decrement(1);
                    } else {
                        this.setSuccess(false);
                    }
                    return itemStack;
                }
                return super.dispenseSilently(blockSource, itemStack);
            }
        });

    }
}
