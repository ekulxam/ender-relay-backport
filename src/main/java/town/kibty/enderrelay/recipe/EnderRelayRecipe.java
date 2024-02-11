package town.kibty.enderrelay.recipe;

import com.google.gson.JsonObject;
import net.minecraft.core.GlobalPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.inventory.CraftingContainer;
import net.minecraft.world.item.*;
import net.minecraft.world.item.crafting.CustomRecipe;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.dimension.BuiltinDimensionTypes;
import org.jetbrains.annotations.NotNull;
import town.kibty.enderrelay.EnderRelay;
import town.kibty.enderrelay.block.EnderRelayBlockEntity;

public class EnderRelayRecipe extends CustomRecipe {
    public static final Item[][] RECIPE = new Item[][] {
            {Items.OBSIDIAN, Items.POPPED_CHORUS_FRUIT, Items.OBSIDIAN},
            {Items.POPPED_CHORUS_FRUIT, Items.BARRIER, Items.POPPED_CHORUS_FRUIT},
            {Items.OBSIDIAN, Items.POPPED_CHORUS_FRUIT, Items.OBSIDIAN}
    };

    public static final RecipeSerializer<EnderRelayRecipe> SERIALIZER = new RecipeSerializer<>() {
        @Override
        public EnderRelayRecipe fromJson(ResourceLocation resourceLocation, JsonObject jsonObject) {
            return null;
        }

        @Override
        public EnderRelayRecipe fromNetwork(ResourceLocation resourceLocation, FriendlyByteBuf friendlyByteBuf) {
            return null;
        }

        @Override
        public void toNetwork(FriendlyByteBuf friendlyByteBuf, EnderRelayRecipe recipe) {

        }
    };


    public EnderRelayRecipe(ResourceLocation resourceLocation) {
        super(resourceLocation);
    }

    @Override
    public boolean matches(CraftingContainer container, Level level) {
        int i = 0;
        for (Item[] row : RECIPE) {
            for (Item item : row) {
                ItemStack gotItem = container.getItem(i);
                i++;
                if (item == Items.BARRIER) {
                    if (!gotItem.is(Items.COMPASS)) return false;
                    if (!CompassItem.isLodestoneCompass(gotItem)) return false;
                    assert gotItem.getTag() != null;
                    if (CompassItem.getLodestonePosition(gotItem.getTag()) == null) return false;
                    if (level.isClientSide) continue;
                    GlobalPos pos = CompassItem.getLodestonePosition(gotItem.getTag());
                    assert pos != null;
                    Level lodedstoneLevel = level.getServer().getLevel(pos.dimension());
                    assert lodedstoneLevel != null;
                    if (lodedstoneLevel.dimensionTypeId() != BuiltinDimensionTypes.END) return false;

                    continue;
                }
                if (!gotItem.is(item)) {
                    return false;
                }

            }
        }
        return true;
    }



    @Override
    public @NotNull ItemStack assemble(CraftingContainer container) {
        ItemStack compass = container.getItem(4);
        assert compass.getTag() != null;
        GlobalPos pos = CompassItem.getLodestonePosition(compass.getTag());
        ItemStack relay = new ItemStack(EnderRelay.ENDER_RELAY_ITEM, 1);
        CompoundTag blockTag = new CompoundTag();

        assert pos != null;
        blockTag.putString(EnderRelayBlockEntity.DIMENSION_ID_KEY, pos.dimension().location().toString());
        blockTag.putIntArray(EnderRelayBlockEntity.POSITION_KEY, new int[] { pos.pos().getX(), pos.pos().getY(), pos.pos().getZ() });
        BlockItem.setBlockEntityData(relay, EnderRelay.ENDER_RELAY_BLOCK_ENTITY, blockTag);
        return relay;
    }

    @Override
    public boolean canCraftInDimensions(int x, int y) {
        return x == 3 && y == 3;
    }

    @Override
    public @NotNull RecipeSerializer<?> getSerializer() {
        return SERIALIZER;
    }
}
