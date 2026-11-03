package net.phoenix.client.branding;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.text.Text;

/** Phoenix native module and quick-tool panel. */
public final class PhoenixClientScreen extends Screen {
    private final Screen parent;
    public PhoenixClientScreen(Screen parent){super(Text.literal("Phoenix Client"));this.parent=parent;}
    @Override public void renderBackground(DrawContext d,int mx,int my,float delta){
        d.fillGradient(0,0,width,height,0xFF09070D,0xFF25111B);
        d.fill(width/2-125,22,width/2+125,23,0x66FF702E);
        d.drawCenteredTextWithShadow(textRenderer,Text.literal("PHOENIX"),width/2,30,0xFFFF8547);
        d.drawCenteredTextWithShadow(textRenderer,Text.literal("CLIENT  /  MODULE CONTROL"),width/2,43,0xFFB8AEBB);
        super.renderBackground(d,mx,my,delta);
    }
    @Override protected void init(){
        int gap=5,cw=Math.min(210,(width-24-gap)/2),left=width/2-cw-gap/2,right=left+cw+gap;
        int top=Math.max(66,height/2-142),step=21;
        addToggle(left,top,"Master HUD",PhoenixHud.isVisible(),PhoenixHud::toggle);
        addToggle(right,top,"FPS counter",PhoenixHud.isFpsEnabled(),PhoenixHud::toggleFps);
        addToggle(left,top+step,"Coordinates",PhoenixHud.isCoordinatesEnabled(),PhoenixHud::toggleCoordinates);
        addToggle(right,top+step,"Direction",PhoenixHud.isDirectionEnabled(),PhoenixHud::toggleDirection);
        addToggle(left,top+step*2,"Armor status",PhoenixHud.isArmorEnabled(),PhoenixHud::toggleArmor);
        addToggle(right,top+step*2,"Ping display",PhoenixHud.isPingEnabled(),PhoenixHud::togglePing);
        addToggle(left,top+step*3,"Movement speed",PhoenixHud.isSpeedEnabled(),PhoenixHud::toggleSpeed);
        addToggle(right,top+step*3,"Biome display",PhoenixHud.isBiomeEnabled(),PhoenixHud::toggleBiome);
        addToggle(left,top+step*4,"Dimension",PhoenixHud.isDimensionEnabled(),PhoenixHud::toggleDimension);
        addToggle(right,top+step*4,"World time",PhoenixHud.isWorldTimeEnabled(),PhoenixHud::toggleWorldTime);
        addToggle(left,top+step*5,"Local clock",PhoenixHud.isClockEnabled(),PhoenixHud::toggleClock);
        addToggle(right,top+step*5,"Attack indicator",PhoenixHud.isAttackIndicatorEnabled(),PhoenixHud::toggleAttackIndicator);
        addToggle(left,top+step*6,"Potion effects",PhoenixHud.isPotionEffectsEnabled(),PhoenixHud::togglePotionEffects);
        addToggle(right,top+step*6,"Held item counter",PhoenixHud.isItemCounterEnabled(),PhoenixHud::toggleItemCounter);
        addToggle(left,top+step*7,"Keystrokes",PhoenixHud.isKeystrokesEnabled(),PhoenixHud::toggleKeystrokes);
        addToggle(right,top+step*7,"Session timer",PhoenixHud.isSessionTimeEnabled(),PhoenixHud::toggleSessionTime);
        addToggle(left,top+step*8,"Fullbright",PhoenixHud.isFullbrightEnabled(),PhoenixHud::toggleFullbright);
        addToggle(right,top+step*8,"Waypoint HUD",PhoenixHud.isWaypointEnabled(),PhoenixHud::toggleWaypoint);
        addToggle(left,top+step*9,"Inventory overlay",PhoenixHud.isInventoryOverlayEnabled(),PhoenixHud::toggleInventoryOverlay);
        addToggle(right,top+step*9,"Chat transparency",PhoenixHud.isChatTransparencyEnabled(),PhoenixHud::toggleChatTransparency);
        addToggle(left,top+step*10,"Light level",PhoenixHud.isLightLevelEnabled(),PhoenixHud::toggleLightLevel);
        addToggle(right,top+step*10,"Nearby entities",PhoenixHud.isEntityOverlayEnabled(),PhoenixHud::toggleEntityOverlay);
        addDrawableChild(ButtonWidget.builder(Text.literal("CROSSHAIR STUDIO"),b->client.setScreen(new PhoenixCrosshairScreen(this))).dimensions(left,top+step*11,cw,19).build());
        addToggle(right,top+step*11,"Tool information",PhoenixHud.isToolInfoEnabled(),PhoenixHud::toggleToolInfo);
        addDrawableChild(ButtonWidget.builder(Text.literal("SET WAYPOINT HERE"),b->{PhoenixHud.setWaypointHere();}).dimensions(left,top+step*12,cw,20).build());
        addDrawableChild(ButtonWidget.builder(Text.literal("CLEAR WAYPOINT"),b->{PhoenixHud.clearWaypoint();}).dimensions(right,top+step*12,cw,20).build());
        addDrawableChild(ButtonWidget.builder(Text.literal("SAVE & RETURN"),b->close()).dimensions(width/2-100,top+step*13+3,200,20).build());
    }
    private void addToggle(int x,int y,String name,boolean enabled,Runnable action){
        addDrawableChild(ButtonWidget.builder(label(name,enabled),b->{action.run();b.setMessage(label(name,state(name)));})
                .dimensions(x,y,Math.min(210,(width-24)/2),19).build());
    }
    private static boolean state(String n){return switch(n){
        case "Master HUD"->PhoenixHud.isVisible();case "FPS counter"->PhoenixHud.isFpsEnabled();
        case "Coordinates"->PhoenixHud.isCoordinatesEnabled();case "Direction"->PhoenixHud.isDirectionEnabled();
        case "Armor status"->PhoenixHud.isArmorEnabled();case "Ping display"->PhoenixHud.isPingEnabled();
        case "Movement speed"->PhoenixHud.isSpeedEnabled();case "Biome display"->PhoenixHud.isBiomeEnabled();
        case "Dimension"->PhoenixHud.isDimensionEnabled();case "World time"->PhoenixHud.isWorldTimeEnabled();
        case "Local clock"->PhoenixHud.isClockEnabled();case "Attack indicator"->PhoenixHud.isAttackIndicatorEnabled();
        case "Potion effects"->PhoenixHud.isPotionEffectsEnabled();case "Held item counter"->PhoenixHud.isItemCounterEnabled();
        case "Keystrokes"->PhoenixHud.isKeystrokesEnabled();case "Session timer"->PhoenixHud.isSessionTimeEnabled();
        case "Fullbright"->PhoenixHud.isFullbrightEnabled();case "Waypoint HUD"->PhoenixHud.isWaypointEnabled();
        case "Inventory overlay"->PhoenixHud.isInventoryOverlayEnabled();case "Chat transparency"->PhoenixHud.isChatTransparencyEnabled();
        case "Light level"->PhoenixHud.isLightLevelEnabled();case "Nearby entities"->PhoenixHud.isEntityOverlayEnabled();
        case "Custom crosshair"->PhoenixHud.isCustomCrosshairEnabled();case "Tool information"->PhoenixHud.isToolInfoEnabled();
        default->false;};}
    private static Text label(String n,boolean on){return Text.literal(n+" ["+(on?"ON":"OFF")+"]");}
    @Override public void close(){if(client!=null)client.setScreen(parent);}
}
