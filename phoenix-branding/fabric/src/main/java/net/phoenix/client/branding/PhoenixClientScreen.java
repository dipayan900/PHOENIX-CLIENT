package net.phoenix.client.branding;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.text.Text;
import java.util.List;
import java.util.function.BooleanSupplier;

/** Phoenix's compact, category-driven in-game client menu. */
public final class PhoenixClientScreen extends Screen {
    private final Screen parent;
    private final String tab;
    private final String category;
    private final int page;

    private record ModuleEntry(String name, String category, BooleanSupplier enabled, Runnable toggle, Runnable open) {}

    public PhoenixClientScreen(Screen parent) { this(parent, "MODULES", "ALL", 0); }
    private PhoenixClientScreen(Screen parent, String tab, String category, int page) {
        super(Text.literal("Phoenix Client"));
        this.parent = parent;
        this.tab = tab;
        this.category = category;
        this.page = Math.max(0, page);
    }

    private List<ModuleEntry> modules() {
        return List.of(
            toggle("Master HUD","HUD",PhoenixHud::isVisible,PhoenixHud::toggle),
            toggle("FPS Counter","HUD",PhoenixHud::isFpsEnabled,PhoenixHud::toggleFps),
            toggle("Coordinates","HUD",PhoenixHud::isCoordinatesEnabled,PhoenixHud::toggleCoordinates),
            toggle("Direction","HUD",PhoenixHud::isDirectionEnabled,PhoenixHud::toggleDirection),
            toggle("Armor Status","HUD",PhoenixHud::isArmorEnabled,PhoenixHud::toggleArmor),
            toggle("Ping Display","HUD",PhoenixHud::isPingEnabled,PhoenixHud::togglePing),
            toggle("Movement Speed","HUD",PhoenixHud::isSpeedEnabled,PhoenixHud::toggleSpeed),
            toggle("Biome Display","HUD",PhoenixHud::isBiomeEnabled,PhoenixHud::toggleBiome),
            toggle("Dimension","HUD",PhoenixHud::isDimensionEnabled,PhoenixHud::toggleDimension),
            toggle("World Time","HUD",PhoenixHud::isWorldTimeEnabled,PhoenixHud::toggleWorldTime),
            toggle("Local Clock","HUD",PhoenixHud::isClockEnabled,PhoenixHud::toggleClock),
            toggle("Attack Indicator","HUD",PhoenixHud::isAttackIndicatorEnabled,PhoenixHud::toggleAttackIndicator),
            toggle("Potion Effects","HUD",PhoenixHud::isPotionEffectsEnabled,PhoenixHud::togglePotionEffects),
            toggle("Held Item Counter","HUD",PhoenixHud::isItemCounterEnabled,PhoenixHud::toggleItemCounter),
            toggle("Keystrokes","HUD",PhoenixHud::isKeystrokesEnabled,PhoenixHud::toggleKeystrokes),
            toggle("Session Timer","HUD",PhoenixHud::isSessionTimeEnabled,PhoenixHud::toggleSessionTime),
            toggle("Waypoint HUD","HUD",PhoenixHud::isWaypointEnabled,PhoenixHud::toggleWaypoint),
            toggle("Inventory Overlay","HUD",PhoenixHud::isInventoryOverlayEnabled,PhoenixHud::toggleInventoryOverlay),
            toggle("Light Level","HUD",PhoenixHud::isLightLevelEnabled,PhoenixHud::toggleLightLevel),
            toggle("Nearby Entities","HUD",PhoenixHud::isEntityOverlayEnabled,PhoenixHud::toggleEntityOverlay),
            toggle("Tool Information","HUD",PhoenixHud::isToolInfoEnabled,PhoenixHud::toggleToolInfo),
            toggle("CPS Counter","HUD",PhoenixHud::isCpsEnabled,PhoenixHud::toggleCps),
            toggle("Fullbright","VISUAL",PhoenixHud::isFullbrightEnabled,PhoenixHud::toggleFullbright),
            toggle("Chat Transparency","VISUAL",PhoenixHud::isChatTransparencyEnabled,PhoenixHud::toggleChatTransparency),
            toggle("Custom Crosshair","VISUAL",PhoenixHud::isCustomCrosshairEnabled,PhoenixHud::toggleCustomCrosshair),
            toggle("Toggle Sprint","UTILITY",PhoenixHud::isSprintToggled,PhoenixHud::toggleSprint),
            action("Performance Center","PERFORMANCE",()->client.setScreen(new PhoenixPerformanceScreen(this))),
            action("Crosshair Studio","VISUAL",()->client.setScreen(new PhoenixCrosshairScreen(this))),
            action("HUD Editor","HUD",()->client.setScreen(new PhoenixHudEditorScreen(this)))
        );
    }

    private static ModuleEntry toggle(String name,String group,BooleanSupplier state,Runnable action) {
        return new ModuleEntry(name,group,state,action,null);
    }
    private static ModuleEntry action(String name,String group,Runnable action) {
        return new ModuleEntry(name,group,()->false,null,action);
    }
    private static String label(ModuleEntry module) {
        return module.name() + "   " + (module.open()!=null ? "OPEN  ›" : (module.enabled().getAsBoolean() ? "ON" : "OFF"));
    }
    private void switchTo(String nextTab,String nextCategory,int nextPage) {
        if (client != null) client.setScreen(new PhoenixClientScreen(parent,nextTab,nextCategory,nextPage));
    }

    @Override protected void init() {
        clearChildren();
        int navY=height-29;
        String[] tabs={"MODULES","SETTINGS","KEYBINDS","PRESETS"};
        int navW=Math.min(86,(width-24)/4), navGap=4;
        int navStart=(width-(navW*4+navGap*3))/2;
        for(int i=0;i<tabs.length;i++){
            final String selected=tabs[i];
            addDrawableChild(ButtonWidget.builder(Text.literal((tab.equals(selected)?"◆ ":"")+selected),
                b->switchTo(selected,category,0)).dimensions(navStart+i*(navW+navGap),navY,navW,20).build());
        }

        if(tab.equals("MODULES")){
            String[] cats={"ALL","HUD","VISUAL","UTILITY","PERFORMANCE"};
            int catGap=3,catW=Math.max(48,(width-20-catGap*4)/5),catX=(width-(catW*5+catGap*4))/2;
            for(int i=0;i<cats.length;i++){
                final String selected=cats[i];
                addDrawableChild(ButtonWidget.builder(Text.literal((category.equals(selected)?"• ":"")+selected),
                    b->switchTo("MODULES",selected,0)).dimensions(catX+i*(catW+catGap),82,catW,19).build());
            }
            List<ModuleEntry> filtered=modules().stream().filter(m->category.equals("ALL")||m.category().equals(category)).toList();
            int rows=Math.max(2,Math.min(5,(height-158)/34));
            int perPage=rows*2;
            int pageCount=Math.max(1,(filtered.size()+perPage-1)/perPage);
            int safePage=Math.min(page,pageCount-1);
            int start=safePage*perPage;
            int cardW=(width-30)/2, left=10, top=108;
            for(int i=start;i<Math.min(filtered.size(),start+perPage);i++){
                ModuleEntry module=filtered.get(i);
                int local=i-start, x=left+(local%2)*(cardW+10), y=top+(local/2)*34;
                ButtonWidget button=ButtonWidget.builder(Text.literal(label(module)),b->{
                    if(module.open()!=null) module.open().run();
                    else {module.toggle().run();b.setMessage(Text.literal(label(module)));}
                }).dimensions(x,y,cardW,28).build();
                addDrawableChild(button);
            }
            addDrawableChild(ButtonWidget.builder(Text.literal("‹"),b->switchTo("MODULES",category,Math.max(0,safePage-1)))
                .dimensions(10,navY-25,38,19).build());
            addDrawableChild(ButtonWidget.builder(Text.literal("PAGE "+(safePage+1)+" / "+pageCount),b->switchTo("MODULES",category,(safePage+1)%pageCount))
                .dimensions(width/2-58,navY-25,116,19).build());
            addDrawableChild(ButtonWidget.builder(Text.literal("›"),b->switchTo("MODULES",category,(safePage+1)%pageCount))
                .dimensions(width-48,navY-25,38,19).build());
        } else if(tab.equals("SETTINGS")){
            int w=Math.min(270,width-28),x=(width-w)/2,y=100;
            addAction("PERFORMANCE & FPS",x,y,w,()->client.setScreen(new PhoenixPerformanceScreen(this)));
            addAction("HUD LAYOUT EDITOR",x,y+34,w,()->client.setScreen(new PhoenixHudEditorScreen(this)));
            addAction("CROSSHAIR STUDIO",x,y+68,w,()->client.setScreen(new PhoenixCrosshairScreen(this)));
            addAction("VISUAL MODULE SETTINGS",x,y+102,w,()->client.setScreen(new PhoenixVisualSettingsScreen(this)));
            addAction("APPEARANCE: "+PhoenixHud.getUiTheme()+"  ·  CHANGE",x,y+136,w,()->{PhoenixHud.cycleUiTheme();switchTo("SETTINGS",category,0);});
            addAction("RESET HUD POSITIONS",x,y+170,w,PhoenixHud::resetElementPositions);
        } else if(tab.equals("KEYBINDS")){
            int x=width/2-125,y=105;
            String[][] keys={{"OPEN PHOENIX MENU","RIGHT SHIFT"},{"HOLD TO ZOOM","C"},{"TOGGLE SPRINT","G"}};
            for(int i=0;i<keys.length;i++){
                int yy=y+i*42;
                addDrawableChild(ButtonWidget.builder(Text.literal(keys[i][0]+"     "+keys[i][1]),b->client.setScreen(new net.minecraft.client.gui.screen.option.OptionsScreen(this,client.options))).dimensions(x,yy,250,30).build());
            }
            addAction("OPEN MINECRAFT OPTIONS",x,y+140,250,()->client.setScreen(new net.minecraft.client.gui.screen.option.OptionsScreen(this,client.options)));
        } else {
            int w=Math.min(270,width-28),x=(width-w)/2,y=105;
            addAction("DEFAULT  ·  ALL-ROUND",x,y,w,()->{PhoenixHud.applyPreset("DEFAULT");switchTo("MODULES","ALL",0);});
            addAction("PVP  ·  HUD & COMBAT INFO",x,y+38,w,()->{PhoenixHud.applyPreset("PVP");switchTo("MODULES","ALL",0);});
            addAction("PERFORMANCE  ·  MINIMAL HUD",x,y+76,w,()->{PhoenixHud.applyPreset("PERFORMANCE");switchTo("MODULES","ALL",0);});
        }
    }

    private void addAction(String title,int x,int y,int width,Runnable action) {
        addDrawableChild(ButtonWidget.builder(Text.literal(title),b->action.run()).dimensions(x,y,width,25).build());
    }

    @Override public void renderBackground(DrawContext draw,int mouseX,int mouseY,float delta){
        boolean light=PhoenixHud.isLightUiTheme();
        int top=light?0xFFF3F4F7:0xFF09070D, bottom=light?0xFFE4E6EC:0xFF25111B;
        int panel=light?0xFFFFFFFF:0xD5100D16, accent=light?0xFFB94D1B:0xFFFF702E;
        int main=light?0xFF25232A:0xFFFF8547, muted=light?0xFF66636D:0xFFB8AEBB;
        draw.fillGradient(0,0,width,height,top,bottom);
        draw.fill(10,10,width-10,height-10,panel);
        draw.fill(10,10,width-10,12,accent);
        draw.drawCenteredTextWithShadow(textRenderer,Text.literal("🔥  PHOENIX"),width/2,24,main);
        String heading=tab.equals("MODULES")?"CLIENT MODULES  /  LIVE CONTROLS":tab.equals("SETTINGS")?"CLIENT SETTINGS":tab.equals("KEYBINDS")?"KEYBIND REFERENCE":"MODULE PROFILES";
        draw.drawCenteredTextWithShadow(textRenderer,Text.literal(heading),width/2,42,muted);
        draw.fill(20,66,width-20,67,accent);
        if(tab.equals("MODULES")) draw.drawCenteredTextWithShadow(textRenderer,Text.literal("FILTER MODULES  ·  TAP TO TOGGLE  ·  AUTO-SAVED"),width/2,103,muted);
        else if(tab.equals("KEYBINDS")) draw.drawCenteredTextWithShadow(textRenderer,Text.literal("QUICK ACCESS CONTROLS"),width/2,78,muted);
        else if(tab.equals("PRESETS")) draw.drawCenteredTextWithShadow(textRenderer,Text.literal("READY-MADE MODULE CONFIGURATIONS"),width/2,78,muted);
        else draw.drawCenteredTextWithShadow(textRenderer,Text.literal("CUSTOMISE THE PHOENIX EXPERIENCE"),width/2,78,muted);
    }

    @Override public void render(DrawContext draw,int mouseX,int mouseY,float delta){
        renderBackground(draw,mouseX,mouseY,delta);
        super.render(draw,mouseX,mouseY,delta);
    }
    @Override public void close(){if(client!=null)client.setScreen(parent);}
}