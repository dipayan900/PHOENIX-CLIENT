package dev.brutalxd.phantomprivacy;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.text.Text;

public final class PhantomPrivacyScreen extends Screen {
    private final Screen parent;
    private TextFieldWidget idsField;

    public PhantomPrivacyScreen(Screen parent) {
        super(Text.literal("Phantom Privacy"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        int fieldWidth = Math.min(360, this.width - 40);
        int fieldX = (this.width - fieldWidth) / 2;

        idsField = new TextFieldWidget(this.textRenderer, fieldX, this.height / 2 - 12,
                fieldWidth, 20, Text.literal("Hidden mod IDs"));
        idsField.setMaxLength(2000);
        idsField.setText(String.join(", ", PhantomPrivacyConfig.getHiddenIds().stream()
                .filter(id -> !id.equals("phantomprivacy"))
                .sorted()
                .toList()));
        addDrawableChild(idsField);

        addDrawableChild(ButtonWidget.builder(Text.literal("Save"), button -> {
            PhantomPrivacyConfig.setHiddenIds(idsField.getText());
            this.close();
        }).dimensions(this.width / 2 - 104, this.height / 2 + 24, 100, 20).build());

        addDrawableChild(ButtonWidget.builder(Text.literal("Cancel"), button -> this.close())
                .dimensions(this.width / 2 + 4, this.height / 2 + 24, 100, 20).build());
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        renderBackground(context, mouseX, mouseY, delta);
        super.render(context, mouseX, mouseY, delta);
        context.drawCenteredTextWithShadow(this.textRenderer, this.title, this.width / 2, this.height / 2 - 64, 0xFFFFFF);
        context.drawCenteredTextWithShadow(this.textRenderer,
                Text.literal("Enter mod IDs separated by commas. Example: modid1, modid2"),
                this.width / 2, this.height / 2 - 36, 0xB8B8B8);
        context.drawCenteredTextWithShadow(this.textRenderer,
                Text.literal("Only Mod Menu entries are filtered; mods remain loaded."),
                this.width / 2, this.height / 2 + 4, 0x8FE3A0);
        context.drawCenteredTextWithShadow(this.textRenderer,
                Text.literal("Shortcut: F9"),
                this.width / 2, this.height / 2 + 58, 0xB8B8B8);
    }

    @Override
    public void close() {
        if (this.client != null) this.client.setScreen(parent);
    }
}
