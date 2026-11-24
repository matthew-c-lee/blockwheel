package dev.blockwheel.client.mixin;

import net.minecraft.client.gui.screens.inventory.AbstractRecipeBookScreen;
import net.minecraft.client.gui.screens.recipebook.RecipeBookComponent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(AbstractRecipeBookScreen.class)
public interface RecipeBookScreenAccessor {
    @Accessor("widthTooNarrow") boolean blockwheel$isNarrow();
    @Accessor("recipeBookComponent") RecipeBookComponent<?> blockwheel$getBook();
}
