package dev.brutalxd.phantomprivacy.mixin;

import com.terraformersmc.modmenu.gui.widget.entries.ModListEntry;
import dev.brutalxd.phantomprivacy.PhantomPrivacyConfig;
import com.terraformersmc.modmenu.gui.widget.ModListWidget;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(ModListWidget.class)
public abstract class ModListWidgetMixin {
    @Inject(method = "addEntry", at = @At("HEAD"), cancellable = true)
    private void phantomprivacy$filterHiddenEntries(ModListEntry entry, CallbackInfoReturnable<Integer> cir) {
        if (entry != null && PhantomPrivacyConfig.isHidden(entry.getMod().getId())) {
            cir.setReturnValue(0);
        }
    }
}
