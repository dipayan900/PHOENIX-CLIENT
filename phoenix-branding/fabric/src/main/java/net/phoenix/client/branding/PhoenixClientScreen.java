package net.phoenix.client.branding;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.text.Text;

/** Phoenix module control panel. */
public final class PhoenixClientScreen extends Screen {
    private final Screen parent;
    public PhoenixClientScreen(Screen parent){super(Text.literal("Phoenix Client"));this.parent=parent;}

    @Override public void renderBackground(DrawContext draw,int mouseX,int mouseY,float delta){
        draw.fillGradient(0,0,width,height,0xFF09070D,0xFF25111B);
        draw.fill(0,height/3,width,height/3+1,0x44FF702E);
        draw.fill(width/2-125,22,width/2+125,23,0x66FF702E);
        draw.drawCenteredTextWithShadow(textRenderer,Text.literal("PHOENIX"),width/2,30,0xFFFF8547);
        draw.drawCenteredTextWithShadow(textRenderer,Text.literal("CLIENT  /  MODULE CONTROL"),width/2,43,0xFFB8AEBB);
        super.renderBackground(draw,mouseX,mouseY,delta);
    }

    @Override protected void init(){
        int gap=6, colWidth=Math.min(210,(width-28-gap)/2), left=width/2-colWidth-gap/2;
        int right=left+colWidth+gap, top=height/2-104, step=23;
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
        addDrawableChild(ButtonWidget.builder(Text.literal("SAVE & RETURN"),b->close())
                .dimensions(width/2-110,top+step*8+5,220,20).build());
    }

    private void addToggle(int x,int y,String name,boolean enabled,Runnable action){
        addDrawableChild(ButtonWidget.builder(label(name,enabled),button->{action.run();button.setMessage(label(name,state(name)));})
                .dimensions(x,y,Math.min(210,(width-28)/2),20).build());
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
        default->false;};}
    private static Text label(String n,boolean on){return Text.literal(n+"  ["+(on?"ON":"OFF")+"]");}
    @Override public void close(){if(client!=null)client.setScreen(parent);}
}
