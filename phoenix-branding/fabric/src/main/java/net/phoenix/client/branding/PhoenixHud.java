package net.phoenix.client.branding;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.item.ItemStack;
import net.minecraft.text.Text;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.MathHelper;
import net.minecraft.client.util.InputUtil;
import org.lwjgl.glfw.GLFW;

import java.time.LocalTime;
import java.time.format.DateTimeFormatter;

/** Client-side Phoenix modules. Settings are kept local to the client session. */
public final class PhoenixHud {
    private static boolean visible = true;
    private static boolean fpsEnabled = true, coordinatesEnabled = true, directionEnabled = true;
    private static boolean armorEnabled = true, clockEnabled, pingEnabled = true;
    private static boolean speedEnabled, biomeEnabled, dimensionEnabled, worldTimeEnabled;
    private static boolean attackIndicatorEnabled, potionEffectsEnabled, itemCounterEnabled;
    private static boolean keystrokesEnabled, sessionTimeEnabled;
    private static boolean fullbrightEnabled, waypointEnabled, inventoryOverlayEnabled, chatTransparencyEnabled;
    private static long sessionTicks;
    private static BlockPos waypoint;
    private static Identifier waypointDimension;
    private static KeyBinding zoomKey;
    private static boolean zoomActive;
    private static int previousFov = 70;
    private static double previousGamma = 1.0, previousChatOpacity = 1.0;

    private PhoenixHud() {}
    public static boolean isVisible(){return visible;}
    public static boolean isFpsEnabled(){return fpsEnabled;}
    public static boolean isCoordinatesEnabled(){return coordinatesEnabled;}
    public static boolean isDirectionEnabled(){return directionEnabled;}
    public static boolean isArmorEnabled(){return armorEnabled;}
    public static boolean isClockEnabled(){return clockEnabled;}
    public static boolean isPingEnabled(){return pingEnabled;}
    public static boolean isSpeedEnabled(){return speedEnabled;}
    public static boolean isBiomeEnabled(){return biomeEnabled;}
    public static boolean isDimensionEnabled(){return dimensionEnabled;}
    public static boolean isWorldTimeEnabled(){return worldTimeEnabled;}
    public static boolean isAttackIndicatorEnabled(){return attackIndicatorEnabled;}
    public static boolean isPotionEffectsEnabled(){return potionEffectsEnabled;}
    public static boolean isItemCounterEnabled(){return itemCounterEnabled;}
    public static boolean isKeystrokesEnabled(){return keystrokesEnabled;}
    public static boolean isSessionTimeEnabled(){return sessionTimeEnabled;}
    public static boolean isFullbrightEnabled(){return fullbrightEnabled;}
    public static boolean isWaypointEnabled(){return waypointEnabled;}
    public static boolean isInventoryOverlayEnabled(){return inventoryOverlayEnabled;}
    public static boolean isChatTransparencyEnabled(){return chatTransparencyEnabled;}
    public static boolean hasWaypoint(){return waypoint!=null;}

    public static void toggle(){visible=!visible;}
    public static void toggleFps(){fpsEnabled=!fpsEnabled;}
    public static void toggleCoordinates(){coordinatesEnabled=!coordinatesEnabled;}
    public static void toggleDirection(){directionEnabled=!directionEnabled;}
    public static void toggleArmor(){armorEnabled=!armorEnabled;}
    public static void toggleClock(){clockEnabled=!clockEnabled;}
    public static void togglePing(){pingEnabled=!pingEnabled;}
    public static void toggleSpeed(){speedEnabled=!speedEnabled;}
    public static void toggleBiome(){biomeEnabled=!biomeEnabled;}
    public static void toggleDimension(){dimensionEnabled=!dimensionEnabled;}
    public static void toggleWorldTime(){worldTimeEnabled=!worldTimeEnabled;}
    public static void toggleAttackIndicator(){attackIndicatorEnabled=!attackIndicatorEnabled;}
    public static void togglePotionEffects(){potionEffectsEnabled=!potionEffectsEnabled;}
    public static void toggleItemCounter(){itemCounterEnabled=!itemCounterEnabled;}
    public static void toggleKeystrokes(){keystrokesEnabled=!keystrokesEnabled;}
    public static void toggleSessionTime(){sessionTimeEnabled=!sessionTimeEnabled;}
    public static void toggleFullbright(){fullbrightEnabled=!fullbrightEnabled;}
    public static void toggleWaypoint(){waypointEnabled=!waypointEnabled;}
    public static void toggleInventoryOverlay(){inventoryOverlayEnabled=!inventoryOverlayEnabled;}
    public static void toggleChatTransparency(){chatTransparencyEnabled=!chatTransparencyEnabled;}

    public static void setWaypointHere(){
        MinecraftClient c=MinecraftClient.getInstance();
        if(c.player!=null&&c.world!=null){waypoint=c.player.getBlockPos().toImmutable();waypointDimension=c.world.getRegistryKey().getValue();}
    }
    public static void clearWaypoint(){waypoint=null;waypointDimension=null;}

    public static void register(){
        zoomKey=KeyBindingHelper.registerKeyBinding(new KeyBinding("key.phoenix.zoom",InputUtil.Type.KEYSYM,GLFW.GLFW_KEY_C,"category.phoenix"));
        ClientTickEvents.END_CLIENT_TICK.register(client->{
            if(client.player!=null&&!client.isPaused())sessionTicks++;
            if(client.player==null){
                if(zoomActive){client.options.getFov().setValue(previousFov);zoomActive=false;}
                return;
            }
            boolean wantsZoom=zoomKey.isPressed();
            if(wantsZoom&&!zoomActive){previousFov=client.options.getFov().getValue();zoomActive=true;}
            if(wantsZoom){client.options.getFov().setValue(30);}
            else if(zoomActive){client.options.getFov().setValue(previousFov);zoomActive=false;}
            double gamma=client.options.getGamma().getValue();
            if(fullbrightEnabled){if(gamma<15.9)previousGamma=gamma;client.options.getGamma().setValue(16.0);}
            else if(gamma>15.9){client.options.getGamma().setValue(previousGamma);}
            double opacity=client.options.getChatOpacity().getValue();
            if(chatTransparencyEnabled){if(opacity>0.56)previousChatOpacity=opacity;client.options.getChatOpacity().setValue(0.55);}
            else if(Math.abs(opacity-0.55)<0.001){client.options.getChatOpacity().setValue(previousChatOpacity);}
        });
        HudRenderCallback.EVENT.register((draw,tickCounter)->{
            if(!visible)return;
            MinecraftClient c=MinecraftClient.getInstance();
            if(c.player==null||c.world==null)return;
            int lines=(fpsEnabled?1:0)+(coordinatesEnabled?1:0)+(directionEnabled?1:0)+(armorEnabled?1:0)
                    +(clockEnabled?1:0)+(pingEnabled?1:0)+(speedEnabled?1:0)+(biomeEnabled?1:0)
                    +(dimensionEnabled?1:0)+(worldTimeEnabled?1:0)+(attackIndicatorEnabled?1:0)
                    +(potionEffectsEnabled?1:0)+(itemCounterEnabled?1:0)+(keystrokesEnabled?1:0)
                    +(sessionTimeEnabled?1:0)+(waypointEnabled&&waypoint!=null?1:0)+(inventoryOverlayEnabled?1:0);
            if(lines==0)return;
            int x=10,y=10,w=230,h=19+lines*12;
            draw.fill(x,y,x+w,y+h,0xB5100D14);draw.fill(x,y,x+2,y+h,0xFFFF702E);
            draw.drawTextWithShadow(c.textRenderer,Text.literal("PHOENIX  /  MODULES"),x+8,y+5,0xFFFF8A4C);
            int row=y+19;
            if(fpsEnabled){line(draw,c,"FPS",String.valueOf(c.getCurrentFps()),row);row+=12;}
            if(coordinatesEnabled){BlockPos p=c.player.getBlockPos();line(draw,c,"XYZ",p.getX()+"  "+p.getY()+"  "+p.getZ(),row);row+=12;}
            if(directionEnabled){line(draw,c,"FACING",c.player.getHorizontalFacing().getName().toUpperCase(),row);row+=12;}
            if(armorEnabled){int pieces=0,durability=0;for(ItemStack s:c.player.getArmorItems())if(!s.isEmpty()){pieces++;if(s.isDamageable())durability+=Math.max(0,s.getMaxDamage()-s.getDamage());}line(draw,c,"ARMOR",pieces+" pieces · "+durability+" durability",row);row+=12;}
            if(pingEnabled){var handler=c.getNetworkHandler();var e=handler==null?null:handler.getPlayerListEntry(c.player.getUuid());line(draw,c,"PING",e==null?"—":e.getLatency()+" ms",row);row+=12;}
            if(speedEnabled){double v=c.player.getVelocity().horizontalLength()*20*3.6;line(draw,c,"SPEED",String.format(java.util.Locale.ROOT,"%.1f km/h",v),row);row+=12;}
            if(biomeEnabled){String b=c.world.getBiome(c.player.getBlockPos()).getKey().map(k->k.getValue().getPath()).orElse("unknown");line(draw,c,"BIOME",b,row);row+=12;}
            if(dimensionEnabled){line(draw,c,"DIMENSION",c.world.getRegistryKey().getValue().getPath(),row);row+=12;}
            if(worldTimeEnabled){long t=Math.floorMod(c.world.getTimeOfDay(),24000L);line(draw,c,"WORLD TIME",String.format(java.util.Locale.ROOT,"%02d:%02d",((t+6000)/1000)%24,(t%1000)*60/1000),row);row+=12;}
            if(clockEnabled){line(draw,c,"LOCAL",LocalTime.now().format(DateTimeFormatter.ofPattern("HH:mm:ss")),row);row+=12;}
            if(attackIndicatorEnabled){line(draw,c,"ATTACK",Math.round(c.player.getAttackCooldownProgress(0)*100)+"%",row);row+=12;}
            if(potionEffectsEnabled){var e=c.player.getStatusEffects().stream().findFirst().orElse(null);line(draw,c,"POTION",e==null?"None":e.getEffectType().value().getName().getString()+" "+duration(e.getDuration()),row);row+=12;}
            if(itemCounterEnabled){ItemStack held=c.player.getMainHandStack();int count=0;if(!held.isEmpty())for(ItemStack s:c.player.getInventory().main)if(ItemStack.areItemsAndComponentsEqual(s,held))count+=s.getCount();line(draw,c,"HELD ITEM",held.isEmpty()?"Empty":held.getName().getString()+" ×"+count,row);row+=12;}
            if(keystrokesEnabled){var o=c.options;line(draw,c,"KEYS",(o.forwardKey.isPressed()?"W":"·")+" "+(o.leftKey.isPressed()?"A":"·")+" "+(o.backKey.isPressed()?"S":"·")+" "+(o.rightKey.isPressed()?"D":"·")+" "+(o.jumpKey.isPressed()?"JUMP":""),row);row+=12;}
            if(sessionTimeEnabled){long s=sessionTicks/20;line(draw,c,"SESSION",String.format(java.util.Locale.ROOT,"%02d:%02d:%02d",s/3600,(s/60)%60,s%60),row);row+=12;}
            if(waypointEnabled&&waypoint!=null){if(waypointDimension.equals(c.world.getRegistryKey().getValue())){double d=Math.sqrt(c.player.squaredDistanceTo(waypoint.getX()+0.5,waypoint.getY()+0.5,waypoint.getZ()+0.5));line(draw,c,"WAYPOINT",waypoint.getX()+" "+waypoint.getY()+" "+waypoint.getZ()+" · "+Math.round(d)+"m",row);}else line(draw,c,"WAYPOINT","Other dimension",row);row+=12;}
            if(inventoryOverlayEnabled){int occupied=0;for(ItemStack s:c.player.getInventory().main)if(!s.isEmpty())occupied++;line(draw,c,"INVENTORY",occupied+" / 36 slots used",row);}
        });
    }
    private static String duration(int ticks){int s=Math.max(0,ticks)/20;return String.format(java.util.Locale.ROOT,"%d:%02d",s/60,s%60);}
    private static void line(net.minecraft.client.gui.DrawContext d,MinecraftClient c,String k,String v,int y){d.drawTextWithShadow(c.textRenderer,Text.literal(k),18,y,0xFFFFA06C);d.drawTextWithShadow(c.textRenderer,Text.literal(v),96,y,0xFFF4EDF4);}
}
