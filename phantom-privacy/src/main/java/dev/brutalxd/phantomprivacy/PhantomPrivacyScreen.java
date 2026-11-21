package dev.brutalxd.phantomprivacy;

import net.fabricmc.loader.api.FabricLoader;
import net.fabricmc.loader.api.ModContainer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.text.Text;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

public final class PhantomPrivacyScreen extends Screen {
    private final Screen parent;
    private final Set<String> pendingHiddenIds = new LinkedHashSet<>(PhantomPrivacyConfig.getHiddenIds());
    private final List<ModContainer> allMods = new ArrayList<>();
    private TextFieldWidget searchField;
    private String appliedSearch = "";
    private int page = 0;
    private int pageSize;

    public PhantomPrivacyScreen(Screen parent) {
        super(Text.literal("Phantom Privacy"));
        this.parent = parent;
        allMods.addAll(FabricLoader.getInstance().getAllMods());
        allMods.sort(Comparator.comparing(
                mod -> mod.getMetadata().getName().toLowerCase(Locale.ROOT)
        ));
    }

    private List<ModContainer> filteredMods() {
        String query = appliedSearch.toLowerCase(Locale.ROOT).trim();
        return allMods.stream().filter(mod -> {
            String id = mod.getMetadata().getId();
            String name = mod.getMetadata().getName();
            return !id.equals("phantomprivacy") &&
                    (query.isEmpty() || id.toLowerCase(Locale.ROOT).contains(query)
                            || name.toLowerCase(Locale.ROOT).contains(query));
        }).toList();
    }

    @Override
    protected void init() {
        clearChildren();
        pageSize = Math.max(3, Math.min(9, (this.height - 142) / 24));
        List<ModContainer> visible = filteredMods();
        int pageCount = Math.max(1, (visible.size() + pageSize - 1) / pageSize);
        page = Math.max(0, Math.min(page, pageCount - 1));

        int contentWidth = Math.min(430, this.width - 24);
        int left = (this.width - contentWidth) / 2;
        searchField = new TextFieldWidget(this.textRenderer, left, 39,
                Math.max(90, contentWidth - 66), 20, Text.literal("Search installed mods"));
        searchField.setMaxLength(100);
        searchField.setText(appliedSearch);
        addDrawableChild(searchField);
        addDrawableChild(ButtonWidget.builder(Text.literal("Find"), button -> {
            appliedSearch = searchField.getText();
            page = 0;
            clearAndInit();
        }).dimensions(left + contentWidth - 60, 39, 60, 20).build());

        int start = page * pageSize;
        int end = Math.min(visible.size(), start + pageSize);
        int rowY = 66;
        for (int i = start; i < end; i++) {
            ModContainer mod = visible.get(i);
            String id = mod.getMetadata().getId();
            String name = mod.getMetadata().getName();
            boolean hidden = pendingHiddenIds.contains(id);
            String state = hidden ? "HIDDEN  •  " : "VISIBLE  •  ";
            String label = state + name + "  [" + id + "]";
            addDrawableChild(ButtonWidget.builder(Text.literal(label), button -> {
                if (pendingHiddenIds.contains(id)) {
                    pendingHiddenIds.remove(id);
                } else {
                    pendingHiddenIds.add(id);
                }
                clearAndInit();
            }).dimensions(left, rowY, contentWidth, 21).build());
            rowY += 23;
        }

        int footerY = this.height - 48;
        addDrawableChild(ButtonWidget.builder(Text.literal("Prev"), button -> {
            page = Math.max(0, page - 1);
            clearAndInit();
        }).dimensions(left, footerY, 48, 20).build());

        addDrawableChild(ButtonWidget.builder(Text.literal((page + 1) + "/" + pageCount), button -> {})
                .dimensions(left + 51, footerY, 42, 20).build());

        addDrawableChild(ButtonWidget.builder(Text.literal("Next"), button -> {
            page = Math.min(pageCount - 1, page + 1);
            clearAndInit();
        }).dimensions(left + 96, footerY, 48, 20).build());

        addDrawableChild(ButtonWidget.builder(Text.literal("Save"), button -> {
            PhantomPrivacyConfig.setHiddenIds(pendingHiddenIds);
            close();
        }).dimensions(left + contentWidth - 99, footerY, 48, 20).build());

        addDrawableChild(ButtonWidget.builder(Text.literal("Cancel"), button -> close())
                .dimensions(left + contentWidth - 48, footerY, 48, 20).build());

        if (visible.isEmpty()) {
            // No row buttons are needed when the search has no matches.
        }
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        // Screen.render draws the background once; do not call renderBackground again.
        super.render(context, mouseX, mouseY, delta);
        List<ModContainer> visible = filteredMods();
        int hiddenCount = (int) visible.stream()
                .filter(mod -> pendingHiddenIds.contains(mod.getMetadata().getId()))
                .count();
        context.drawCenteredTextWithShadow(this.textRenderer, this.title,
                this.width / 2, 14, 0xFFFFFF);
        context.drawCenteredTextWithShadow(this.textRenderer,
                Text.literal(visible.size() + " installed mods  •  " + hiddenCount + " selected to hide"),
                this.width / 2, this.height - 68, 0xB8B8B8);
        context.drawCenteredTextWithShadow(this.textRenderer,
                Text.literal("Tap a mod row to switch between VISIBLE and HIDDEN"),
                this.width / 2, this.height - 57, 0x8FE3A0);
        if (visible.isEmpty()) {
            context.drawCenteredTextWithShadow(this.textRenderer,
                    Text.literal("No installed mods match this search."),
                    this.width / 2, this.height / 2, 0xFFFFFF);
        }
    }

    @Override
    public void close() {
        if (this.client != null) this.client.setScreen(parent);
    }
}
