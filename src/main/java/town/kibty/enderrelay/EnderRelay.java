package town.kibty.enderrelay;

import town.kibty.enderrelay.block.EnderRelayBlock;
import town.kibty.enderrelay.block.EnderRelayBlockEntity;
import town.kibty.enderrelay.recipe.EnderRelayRecipe;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.object.builder.v1.block.FabricBlockSettings;
import net.fabricmc.fabric.api.object.builder.v1.block.entity.FabricBlockEntityTypeBuilder;

import net.minecraft.core.BlockPos;
import net.minecraft.core.BlockSource;
import net.minecraft.core.Direction;
import net.minecraft.core.Registry;
import net.minecraft.core.dispenser.OptionalDispenseItemBehavior;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.DispenserBlock;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.Material;

import org.jetbrains.annotations.NotNull;

import static net.minecraft.world.level.material.MaterialColor.COLOR_BLACK;

public class EnderRelay implements ModInitializer {
    public static final String MOD_ID = "enderrelay";

    public static final Block ENDER_RELAY_BLOCK = new EnderRelayBlock(FabricBlockSettings.of(Material.STONE)
            .mapColor(COLOR_BLACK)
            .requiresCorrectToolForDrops()
            .strength(50.0f, 1200.0f)
            .lightLevel(blockStatex -> EnderRelayBlock.getChargeLevel(blockStatex) ? 15 : 0)
    );
    public static final BlockItem ENDER_RELAY_ITEM = new BlockItem(ENDER_RELAY_BLOCK, new Item.Properties());
    public static final BlockEntityType<EnderRelayBlockEntity> ENDER_RELAY_BLOCK_ENTITY = Registry.register(
            Registry.BLOCK_ENTITY_TYPE,
            new ResourceLocation(MOD_ID, "ender_relay"),
            FabricBlockEntityTypeBuilder.create(EnderRelayBlockEntity::new, ENDER_RELAY_BLOCK).build()
    );

    /** Runs the mod initializer. */
    @Override
    public void onInitialize() {
        Registry.register(Registry.BLOCK, new ResourceLocation(MOD_ID, "ender_relay"), ENDER_RELAY_BLOCK);
        Registry.register(Registry.ITEM, new ResourceLocation(MOD_ID, "ender_relay"), ENDER_RELAY_ITEM);
        Registry.register(Registry.RECIPE_SERIALIZER, new ResourceLocation(MOD_ID, "ender_relay"), EnderRelayRecipe.SERIALIZER);

        // Mimic respawn anchor + dispenser functionality
        DispenserBlock.registerBehavior(Items.END_CRYSTAL, new OptionalDispenseItemBehavior(){

            @Override
            public @NotNull ItemStack execute(BlockSource blockSource, ItemStack itemStack) {
                Direction direction = blockSource.getBlockState().getValue(DispenserBlock.FACING);
                BlockPos blockPos = blockSource.getPos().relative(direction);
                ServerLevel level = blockSource.getLevel();
                BlockState blockState = level.getBlockState(blockPos);
                this.setSuccess(true);
                if (blockState.is(ENDER_RELAY_BLOCK)) {
                    if (!blockState.getValue(EnderRelayBlock.CHARGED)) {
                        EnderRelayBlock.light(null, level, blockPos, blockState);
                        itemStack.shrink(1);
                    } else {
                        this.setSuccess(false);
                    }
                    return itemStack;
                }
                return super.execute(blockSource, itemStack);
            }
        });

    }
}
