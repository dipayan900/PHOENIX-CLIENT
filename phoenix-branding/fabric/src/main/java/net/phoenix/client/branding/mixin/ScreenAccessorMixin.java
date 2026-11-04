package net.phoenix.client.branding.mixin;

import net.minecraft.client.gui.Drawable;
import net.minecraft.client.gui.Element;
import net.minecraft.client.gui.Selectable;
import net.minecraft.client.gui.screen.Screen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

/**
 * Exposes Screen's own child-registration method. TitleScreen inherits this
 * method from Screen, so shadowing it on TitleScreen is not valid on newer MC.
 */
@Mixin(Screen.class)
public interface ScreenAccessorMixin {
    @Invoker("addDrawableChild")
    <T extends Element & Drawable & Selectable> T phoenix$addDrawableChild(T drawableElement);
}
