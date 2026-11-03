package net.phoenix.client.branding;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.text.Text;
/** Paged Phoenix module controls; keeps controls reachable on small screens. */
public final class PhoenixClientScreen extends Screen {
 private final Screen parent; private int page=0;
 public PhoenixClientScreen(Screen parent){super(Text.literal("Phoenix Client"));this.parent=parent;}
 @Override public void renderBackground(DrawContext d,int mx,int my,float delta){d.fillGradient(0,0,width,height,0xFF09070D,0xFF25111B);d.fill(width/2-125,22,width/2+125,23,0x66FF702E);d.drawCenteredTextWithShadow(textRenderer,Text.literal("PHOENIX"),width/2,30,0xFFFF8547);d.drawCenteredTextWithShadow(textRenderer,Text.literal("CLIENT  /  "+(page==0?"HUD & INFO":page==1?"VISUAL & UTILITY":"TOOLS & PERFORMANCE")),width/2,43,0xFFB8AEBB);}
 @Override protected void init(){clearChildren();int gap=6,cw=Math.min(210,(width-24-gap)/2),left=width/2-cw-gap/2,right=left+cw+gap,top=62,step=25;
  if(page==0){
   addToggle(left,top,"Master HUD",PhoenixHud.isVisible(),PhoenixHud::toggle);addToggle(right,top,"FPS counter",PhoenixHud.isFpsEnabled(),PhoenixHud::toggleFps);
   addToggle(left,top+step,"Coordinates",PhoenixHud.isCoordinatesEnabled(),PhoenixHud::toggleCoordinates);addToggle(right,top+step,"Direction",PhoenixHud.isDirectionEnabled(),PhoenixHud::toggleDirection);
   addToggle(left,top+step*2,"Armor status",PhoenixHud.isArmorEnabled(),PhoenixHud::toggleArmor);addToggle(right,top+step*2,"Ping display",PhoenixHud.isPingEnabled(),PhoenixHud::togglePing);
   addToggle(left,top+step*3,"Movement speed",PhoenixHud.isSpeedEnabled(),PhoenixHud::toggleSpeed);addToggle(right,top+step*3,"Biome display",PhoenixHud.isBiomeEnabled(),PhoenixHud::toggleBiome);
   addToggle(left,top+step*4,"Dimension",PhoenixHud.isDimensionEnabled(),PhoenixHud::toggleDimension);addToggle(right,top+step*4,"World time",PhoenixHud.isWorldTimeEnabled(),PhoenixHud::toggleWorldTime);
   addToggle(left,top+step*5,"Local clock",PhoenixHud.isClockEnabled(),PhoenixHud::toggleClock);addToggle(right,top+step*5,"Attack indicator",PhoenixHud.isAttackIndicatorEnabled(),PhoenixHud::toggleAttackIndicator);
   addToggle(left,top+step*6,"Potion effects",PhoenixHud.isPotionEffectsEnabled(),PhoenixHud::togglePotionEffects);addToggle(right,top+step*6,"Held item counter",PhoenixHud.isItemCounterEnabled(),PhoenixHud::toggleItemCounter);
   addToggle(left,top+step*7,"Keystrokes",PhoenixHud.isKeystrokesEnabled(),PhoenixHud::toggleKeystrokes);addToggle(right,top+step*7,"Session timer",PhoenixHud.isSessionTimeEnabled(),PhoenixHud::toggleSessionTime);
  } else if(page==1){
   addToggle(left,top,"Fullbright",PhoenixHud.isFullbrightEnabled(),PhoenixHud::toggleFullbright);addToggle(right,top,"Waypoint HUD",PhoenixHud.isWaypointEnabled(),PhoenixHud::toggleWaypoint);
   addToggle(left,top+step,"Inventory overlay",PhoenixHud.isInventoryOverlayEnabled(),PhoenixHud::toggleInventoryOverlay);addToggle(right,top+step,"Chat transparency",PhoenixHud.isChatTransparencyEnabled(),PhoenixHud::toggleChatTransparency);
   addToggle(left,top+step*2,"Light level",PhoenixHud.isLightLevelEnabled(),PhoenixHud::toggleLightLevel);addToggle(right,top+step*2,"Nearby entities",PhoenixHud.isEntityOverlayEnabled(),PhoenixHud::toggleEntityOverlay);
   addToggle(left,top+step*3,"Tool information",PhoenixHud.isToolInfoEnabled(),PhoenixHud::toggleToolInfo);addToggle(right,top+step*3,"CPS counter",PhoenixHud.isCpsEnabled(),PhoenixHud::toggleCps);
   addToggle(left,top+step*4,"Toggle sprint (G)",PhoenixHud.isSprintToggled(),PhoenixHud::toggleSprint);
   addDrawableChild(ButtonWidget.builder(Text.literal("SET WAYPOINT HERE"),b->PhoenixHud.setWaypointHere()).dimensions(right,top+step*4,cw,20).build());
   addDrawableChild(ButtonWidget.builder(Text.literal("CLEAR WAYPOINT"),b->PhoenixHud.clearWaypoint()).dimensions(left,top+step*5,cw,20).build());
   addDrawableChild(ButtonWidget.builder(Text.literal("CROSSHAIR STUDIO"),b->client.setScreen(new PhoenixCrosshairScreen(this))).dimensions(right,top+step*5,cw,20).build());
   addDrawableChild(ButtonWidget.builder(Text.literal("HUD EDITOR"),b->client.setScreen(new PhoenixHudEditorScreen(this))).dimensions(left,top+step*6,cw,20).build());
  } else {
   addDrawableChild(ButtonWidget.builder(Text.literal("FPS & PERFORMANCE CONTROL"),b->client.setScreen(new PhoenixPerformanceScreen(this))).dimensions(left,top,cw*2+gap,24).build());
   addDrawableChild(ButtonWidget.builder(Text.literal("CROSSHAIR STUDIO"),b->client.setScreen(new PhoenixCrosshairScreen(this))).dimensions(left,top+step*2,cw,22).build());
   addDrawableChild(ButtonWidget.builder(Text.literal("HUD EDITOR"),b->client.setScreen(new PhoenixHudEditorScreen(this))).dimensions(right,top+step*2,cw,22).build());
   addDrawableChild(ButtonWidget.builder(Text.literal("SET WAYPOINT HERE"),b->PhoenixHud.setWaypointHere()).dimensions(left,top+step*3,cw,22).build());
   addDrawableChild(ButtonWidget.builder(Text.literal("CLEAR WAYPOINT"),b->PhoenixHud.clearWaypoint()).dimensions(right,top+step*3,cw,22).build());
  }
  int navY=height-31;addDrawableChild(ButtonWidget.builder(Text.literal("‹ PREVIOUS"),b->{page=(page+2)%3;init();}).dimensions(8,navY,100,20).build());
  addDrawableChild(ButtonWidget.builder(Text.literal("PAGE "+(page+1)+" / 3"),b->{page=(page+1)%3;init();}).dimensions(width/2-54,navY,108,20).build());
  addDrawableChild(ButtonWidget.builder(Text.literal("NEXT ›"),b->{page=(page+1)%3;init();}).dimensions(width-108,navY,100,20).build());
  addDrawableChild(ButtonWidget.builder(Text.literal("SAVE & RETURN"),b->close()).dimensions(width/2-65,navY-25,130,20).build());
 }
 private void addToggle(int x,int y,String n,boolean enabled,Runnable action){addDrawableChild(ButtonWidget.builder(label(n,enabled),b->{action.run();b.setMessage(label(n,state(n)));}).dimensions(x,y,Math.min(210,(width-24)/2),20).build());}
 private static boolean state(String n){return switch(n){
  case "Master HUD"->PhoenixHud.isVisible();case "FPS counter"->PhoenixHud.isFpsEnabled();case "Coordinates"->PhoenixHud.isCoordinatesEnabled();case "Direction"->PhoenixHud.isDirectionEnabled();
  case "Armor status"->PhoenixHud.isArmorEnabled();case "Ping display"->PhoenixHud.isPingEnabled();case "Movement speed"->PhoenixHud.isSpeedEnabled();case "Biome display"->PhoenixHud.isBiomeEnabled();
  case "Dimension"->PhoenixHud.isDimensionEnabled();case "World time"->PhoenixHud.isWorldTimeEnabled();case "Local clock"->PhoenixHud.isClockEnabled();case "Attack indicator"->PhoenixHud.isAttackIndicatorEnabled();
  case "Potion effects"->PhoenixHud.isPotionEffectsEnabled();case "Held item counter"->PhoenixHud.isItemCounterEnabled();case "Keystrokes"->PhoenixHud.isKeystrokesEnabled();case "Session timer"->PhoenixHud.isSessionTimeEnabled();
  case "Fullbright"->PhoenixHud.isFullbrightEnabled();case "Waypoint HUD"->PhoenixHud.isWaypointEnabled();case "Inventory overlay"->PhoenixHud.isInventoryOverlayEnabled();case "Chat transparency"->PhoenixHud.isChatTransparencyEnabled();
  case "Light level"->PhoenixHud.isLightLevelEnabled();case "Nearby entities"->PhoenixHud.isEntityOverlayEnabled();case "Tool information"->PhoenixHud.isToolInfoEnabled();case "CPS counter"->PhoenixHud.isCpsEnabled();case "Toggle sprint (G)"->PhoenixHud.isSprintToggled();default->false;};}
 private static Text label(String n,boolean on){return Text.literal(n+" ["+(on?"ON":"OFF")+"]");}
 @Override public void close(){if(client!=null)client.setScreen(parent);}
}