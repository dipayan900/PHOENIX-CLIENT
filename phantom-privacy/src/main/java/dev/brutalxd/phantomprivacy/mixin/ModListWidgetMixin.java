package dev.brutalxd.phantomprivacy.mixin;

import com.terraformersmc.modmenu.gui.widget.ModListWidget;
import com.terraformersmc.modmenu.gui.widget.entries.ModListEntry;
import dev.brutalxd.phantomprivacy.PhantomPrivacyConfig;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value = ModListWidget.class, remap = false)
public abstract class ModListWidgetMixin {
    @Inject(method = "addEntry(Lcom/terraformersmc/modmenu/gui/widget/entries/ModListEntry;)I",
            at = @At("HEAD"), cancellable = true, remap = false)
    private void phantomprivacy$filterHiddenEntries(
            ModListEntry entry,
            CallbackInfoReturnable<Integer> cir
    ) {
        if (entry != null && PhantomPrivacyConfig.isHidden(entry.getMod().getId())) {
            cir.setReturnValue(0);
        }
    }
}
