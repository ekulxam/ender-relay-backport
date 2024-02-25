package town.kibty.enderrelay.recipe;

import net.minecraft.inventory.CraftingInventory;
import net.minecraft.item.BlockItem;
import net.minecraft.item.CompassItem;
import net.minecraft.item.Items;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.recipe.RecipeSerializer;
import net.minecraft.recipe.SpecialCraftingRecipe;
import net.minecraft.recipe.SpecialRecipeSerializer;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.GlobalPos;
import net.minecraft.world.World;
import net.minecraft.world.dimension.DimensionTypes;
import org.jetbrains.annotations.NotNull;
import town.kibty.enderrelay.EnderRelay;
import town.kibty.enderrelay.block.EnderRelayBlockEntity;

public class EnderRelayRecipe extends SpecialCraftingRecipe {
    public static final net.minecraft.item.Item[][] RECIPE = new net.minecraft.item.Item[][] {
            {Items.OBSIDIAN, Items.POPPED_CHORUS_FRUIT, Items.OBSIDIAN},
            {Items.POPPED_CHORUS_FRUIT, Items.BARRIER, Items.POPPED_CHORUS_FRUIT},
            {Items.OBSIDIAN, Items.POPPED_CHORUS_FRUIT, Items.OBSIDIAN}
    };

    public static final SpecialRecipeSerializer<EnderRelayRecipe> SERIALIZER = new SpecialRecipeSerializer<>(EnderRelayRecipe::new);
    public EnderRelayRecipe(Identifier resourceLocation) {
        super(resourceLocation);
    }

    @Override
    public boolean matches(CraftingInventory container, World level) {
        int i = 0;
        for (net.minecraft.item.Item[] row : RECIPE) {
            for (net.minecraft.item.Item item : row) {
                net.minecraft.item.ItemStack gotItem = container.getStack(i);
                i++;
                if (item == Items.BARRIER) {
                    if (!gotItem.isOf(Items.COMPASS)) return false;
                    if (!CompassItem.hasLodestone(gotItem)) return false;
                    assert gotItem.getNbt() != null;
                    if (CompassItem.createLodestonePos(gotItem.getNbt()) == null) return false;
                    if (level.isClient) continue;
                    GlobalPos pos = CompassItem.createLodestonePos(gotItem.getNbt());
                    assert pos != null;
                    World lodestoneLevel = level.getServer().getWorld(pos.getDimension());
                    assert lodestoneLevel != null;
                    if (lodestoneLevel.getDimensionKey() != DimensionTypes.THE_END) return false;

                    continue;
                }
                if (!gotItem.isOf(item)) {
                    return false;
                }

            }
        }
        return true;
    }

    @Override
    public @NotNull net.minecraft.item.ItemStack craft(CraftingInventory container) {
        net.minecraft.item.ItemStack compass = container.getStack(4);
        assert compass.getNbt() != null;
        GlobalPos pos = CompassItem.createLodestonePos(compass.getNbt());
        net.minecraft.item.ItemStack relay = new net.minecraft.item.ItemStack(EnderRelay.ENDER_RELAY_ITEM, 1);
        NbtCompound blockTag = new NbtCompound();

        assert pos != null;
        blockTag.putString(EnderRelayBlockEntity.DIMENSION_ID_KEY, pos.getDimension().getValue().toString());
        blockTag.putIntArray(EnderRelayBlockEntity.POSITION_KEY, new int[] { pos.getPos().getX(), pos.getPos().getY(), pos.getPos().getZ() });
        BlockItem.setBlockEntityNbt(relay, EnderRelay.ENDER_RELAY_BLOCK_ENTITY, blockTag);
        return relay;
    }

    @Override
    public boolean fits(int x, int y) {
        return x == 3 && y == 3;
    }

    @Override
    public @NotNull RecipeSerializer<?> getSerializer() {
        return SERIALIZER;
    }
}