package net.phoenix.client.branding;

import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.text.Text;

/** Phoenix in-game module control panel. */
public final class PhoenixClientScreen extends Screen {
    private final Screen parent;
    public PhoenixClientScreen(Screen parent){super(Text.literal("Phoenix Client"));this.parent=parent;}

    @Override public void renderBackground(net.minecraft.client.gui.DrawContext draw,int mouseX,int mouseY,float delta){
        draw.fillGradient(0,0,this.width,this.height,0xFF09070D,0xFF25111B);
        draw.fill(0,this.height/3,this.width,this.height/3+1,0x44FF702E);
        draw.fill(this.width/2-125,22,this.width/2+125,23,0x66FF702E);
        draw.drawTextWithShadow(this.textRenderer,Text.literal("PHOENIX"),this.width/2-37,30,0xFFFF8547);
        draw.drawTextWithShadow(this.textRenderer,Text.literal("CLIENT  /  MODULE CONTROL"),this.width/2-82,43,0xFFB8AEBB);
    }

    @Override protected void init(){
        int center=this.width/2, width=224, left=center-width/2;
        int top=Math.max(68,this.height/2-112);
        addToggle(left,top,"Master HUD",PhoenixHud.isVisible(),PhoenixHud::toggle);
        addToggle(left,top+22,"FPS counter",PhoenixHud.isFpsEnabled(),PhoenixHud::toggleFps);
        addToggle(left,top+44,"Coordinates",PhoenixHud.isCoordinatesEnabled(),PhoenixHud::toggleCoordinates);
        addToggle(left,top+66,"Direction",PhoenixHud.isDirectionEnabled(),PhoenixHud::toggleDirection);
        addToggle(left,top+88,"Armor status",PhoenixHud.isArmorEnabled(),PhoenixHud::toggleArmor);
        addToggle(left,top+110,"Ping display",PhoenixHud.isPingEnabled(),PhoenixHud::togglePing);
        addToggle(left,top+132,"Movement speed",PhoenixHud.isSpeedEnabled(),PhoenixHud::toggleSpeed);
        addToggle(left,top+154,"Biome display",PhoenixHud.isBiomeEnabled(),PhoenixHud::toggleBiome);
        addToggle(left,top+176,"Dimension display",PhoenixHud.isDimensionEnabled(),PhoenixHud::toggleDimension);
        addToggle(left,top+198,"World time",PhoenixHud.isWorldTimeEnabled(),PhoenixHud::toggleWorldTime);
        addToggle(left,top+220,"Local clock",PhoenixHud.isClockEnabled(),PhoenixHud::toggleClock);
        addDrawableChild(ButtonWidget.builder(Text.literal("Done"),b->close()).dimensions(left,top+246,width,20).build());
    }

    private void addToggle(int x,int y,String name,boolean enabled,Runnable action){
        addDrawableChild(ButtonWidget.builder(label(name,enabled),button->{action.run();button.setMessage(label(name,state(name)));})
                .dimensions(x,y,224,20).build());
    }
    private static boolean state(String n){return switch(n){
        case "Master HUD"->PhoenixHud.isVisible();case "FPS counter"->PhoenixHud.isFpsEnabled();
        case "Coordinates"->PhoenixHud.isCoordinatesEnabled();case "Direction"->PhoenixHud.isDirectionEnabled();
        case "Armor status"->PhoenixHud.isArmorEnabled();case "Ping display"->PhoenixHud.isPingEnabled();
        case "Movement speed"->PhoenixHud.isSpeedEnabled();case "Biome display"->PhoenixHud.isBiomeEnabled();
        case "Dimension display"->PhoenixHud.isDimensionEnabled();case "World time"->PhoenixHud.isWorldTimeEnabled();
        case "Local clock"->PhoenixHud.isClockEnabled();default->false;};}
    private static Text label(String n,boolean on){return Text.literal(n+"  ["+(on?"ON":"OFF")+"]");}
    @Override public void close(){if(this.client!=null)this.client.setScreen(parent);}
}
