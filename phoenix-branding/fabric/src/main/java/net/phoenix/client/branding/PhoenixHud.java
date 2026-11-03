package net.phoenix.client.branding;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.loader.api.FabricLoader;
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

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;
import java.io.InputStream;
import java.io.OutputStream;
import java.time.LocalTime;
import java.util.ArrayDeque;
import java.util.HashMap;
import java.util.Map;
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
    private static boolean lightLevelEnabled, entityOverlayEnabled, customCrosshairEnabled, toolInfoEnabled;
    private static long sessionTicks;
    private static BlockPos waypoint;
    private static Identifier waypointDimension;
    private static KeyBinding zoomKey, sprintToggleKey;
    private static boolean sprintToggled, cpsEnabled=true, lastLeftDown, lastRightDown;
    private static final ArrayDeque<Long> clickTimes = new ArrayDeque<>();
    private static boolean zoomActive;
    private static int previousFov = 70;
    private static double previousGamma = 1.0, previousChatOpacity = 1.0;
    private static int crosshairStyle=0, crosshairColor=0xFFFF7A35, crosshairSize=7, crosshairGap=3, crosshairThickness=1;
    private static boolean crosshairDot=false;
    private static int hudX=10,hudY=10;
    private static final String[] ELEMENT_KEYS={"FPS","XYZ","FACING","ARMOR","PING","SPEED","BIOME","DIMENSION","WORLD TIME","LOCAL","ATTACK","POTION","HELD ITEM","KEYS","SESSION","WAYPOINT","INVENTORY","LIGHT LEVEL","NEARBY 1","NEARBY 2","NEARBY 3","TOOL INFO","CPS"};
    private static final Map<String,int[]> elementPositions=new HashMap<>();
    private static String positionKey(String key){return key.toLowerCase(java.util.Locale.ROOT).replaceAll("[^a-z0-9_-]","_");}
    private static int elementIndex(String key){for(int i=0;i<ELEMENT_KEYS.length;i++)if(ELEMENT_KEYS[i].equals(key))return i;return 0;}
    public static String[] getElementKeys(){return ELEMENT_KEYS.clone();}
    public static int getElementX(String key){int[] p=elementPositions.get(positionKey(key));return p==null?10+(elementIndex(key)/12)*160:p[0];}
    public static int getElementY(String key){int[] p=elementPositions.get(positionKey(key));return p==null?10+(elementIndex(key)%12)*22:p[1];}
    public static void setElementPosition(String key,int x,int y){elementPositions.put(positionKey(key),new int[]{Math.max(0,x),Math.max(0,y)});saveConfig();}
    public static void resetElementPositions(){elementPositions.clear();saveConfig();}
    private static Path configFile(){return FabricLoader.getInstance().getConfigDir().resolve("phoenix-client.properties");}
    public static int getHudX(){return hudX;} public static int getHudY(){return hudY;}
    public static void setHudPosition(int x,int y){hudX=Math.max(0,x);hudY=Math.max(0,y);saveConfig();}
    private static void loadConfig(){try{Path p=configFile();if(Files.exists(p)){Properties v=new Properties();try(InputStream in=Files.newInputStream(p)){v.load(in);}hudX=Math.max(0,Integer.parseInt(v.getProperty("hud.x","10")));hudY=Math.max(0,Integer.parseInt(v.getProperty("hud.y","10")));crosshairStyle=Math.max(0,Math.min(4,Integer.parseInt(v.getProperty("crosshair.style","0"))));crosshairColor=(int)Long.parseLong(v.getProperty("crosshair.color","4294937141"));crosshairSize=Math.max(3,Math.min(18,Integer.parseInt(v.getProperty("crosshair.size","7"))));crosshairGap=Math.max(0,Math.min(10,Integer.parseInt(v.getProperty("crosshair.gap","3"))));crosshairThickness=Math.max(1,Math.min(4,Integer.parseInt(v.getProperty("crosshair.thickness","1"))));crosshairDot=Boolean.parseBoolean(v.getProperty("crosshair.dot","false"));customCrosshairEnabled=Boolean.parseBoolean(v.getProperty("crosshair.enabled","false"));
        for(String k:v.stringPropertyNames())if(k.startsWith("element.")&&k.endsWith(".x")){String id=k.substring(8,k.length()-2);try{int ex=Integer.parseInt(v.getProperty(k));int ey=Integer.parseInt(v.getProperty("element."+id+".y","10"));elementPositions.put(id,new int[]{Math.max(0,ex),Math.max(0,ey)});}catch(Exception ignored){}}
        }}catch(Exception ignored){}}
    public static void saveConfig(){try{Path p=configFile();Files.createDirectories(p.getParent());Properties v=new Properties();v.setProperty("hud.x",String.valueOf(hudX));v.setProperty("hud.y",String.valueOf(hudY));v.setProperty("crosshair.style",String.valueOf(crosshairStyle));v.setProperty("crosshair.color",Long.toString(Integer.toUnsignedLong(crosshairColor)));v.setProperty("crosshair.size",String.valueOf(crosshairSize));v.setProperty("crosshair.gap",String.valueOf(crosshairGap));v.setProperty("crosshair.thickness",String.valueOf(crosshairThickness));v.setProperty("crosshair.dot",String.valueOf(crosshairDot));v.setProperty("crosshair.enabled",String.valueOf(customCrosshairEnabled));
        for(var e:elementPositions.entrySet()){v.setProperty("element."+e.getKey()+".x",String.valueOf(e.getValue()[0]));v.setProperty("element."+e.getKey()+".y",String.valueOf(e.getValue()[1]));}
        try(OutputStream out=Files.newOutputStream(p)){v.store(out,"Phoenix Client settings");}}catch(Exception ignored){}}

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
    public static boolean isLightLevelEnabled(){return lightLevelEnabled;}
    public static boolean isEntityOverlayEnabled(){return entityOverlayEnabled;}
    public static boolean isCustomCrosshairEnabled(){return customCrosshairEnabled;}
    public static boolean isToolInfoEnabled(){return toolInfoEnabled;}
    public static boolean isCpsEnabled(){return cpsEnabled;}
    public static boolean isSprintToggled(){return sprintToggled;}
    public static int getCrosshairStyle(){return crosshairStyle;}
    public static int getCrosshairColor(){return crosshairColor;}
    public static int getCrosshairSize(){return crosshairSize;}
    public static int getCrosshairGap(){return crosshairGap;}
    public static int getCrosshairThickness(){return crosshairThickness;}
    public static boolean hasCrosshairDot(){return crosshairDot;}
    public static void cycleCrosshairStyle(){crosshairStyle=(crosshairStyle+1)%5;saveConfig();}
    public static void cycleCrosshairColor(){int[] colors={0xFFFF7A35,0xFF55D6FF,0xFF8DFF76,0xFFFF5C9A,0xFFFFFFFF,0xFFFFE45C};int next=0;for(int i=0;i<colors.length;i++)if(colors[i]==crosshairColor)next=(i+1)%colors.length;crosshairColor=colors[next];saveConfig();}
    public static void adjustCrosshairSize(int delta){crosshairSize=Math.max(3,Math.min(18,crosshairSize+delta));saveConfig();}
    public static void adjustCrosshairGap(int delta){crosshairGap=Math.max(0,Math.min(10,crosshairGap+delta));saveConfig();}
    public static void adjustCrosshairThickness(int delta){crosshairThickness=Math.max(1,Math.min(4,crosshairThickness+delta));saveConfig();}
    public static void toggleCrosshairDot(){crosshairDot=!crosshairDot;saveConfig();}

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
    public static void toggleLightLevel(){lightLevelEnabled=!lightLevelEnabled;}
    public static void toggleEntityOverlay(){entityOverlayEnabled=!entityOverlayEnabled;}
    public static void toggleCustomCrosshair(){customCrosshairEnabled=!customCrosshairEnabled;saveConfig();}
    public static void toggleToolInfo(){toolInfoEnabled=!toolInfoEnabled;}
    public static void toggleCps(){cpsEnabled=!cpsEnabled;}
    public static void toggleSprint(){sprintToggled=!sprintToggled;}

    public static void setWaypointHere(){
        MinecraftClient c=MinecraftClient.getInstance();
        if(c.player!=null&&c.world!=null){waypoint=c.player.getBlockPos().toImmutable();waypointDimension=c.world.getRegistryKey().getValue();}
    }
    public static void clearWaypoint(){waypoint=null;waypointDimension=null;}

    public static void register(){
        loadConfig();
        zoomKey=KeyBindingHelper.registerKeyBinding(new KeyBinding("key.phoenix.zoom",InputUtil.Type.KEYSYM,GLFW.GLFW_KEY_C,"category.phoenix"));
        sprintToggleKey=KeyBindingHelper.registerKeyBinding(new KeyBinding("key.phoenix.toggle_sprint",InputUtil.Type.KEYSYM,GLFW.GLFW_KEY_G,"category.phoenix"));
        ClientTickEvents.END_CLIENT_TICK.register(client->{
            if(client.player!=null&&!client.isPaused())sessionTicks++;
            while(sprintToggleKey.wasPressed())sprintToggled=!sprintToggled;
            if(client.player!=null&&sprintToggled)client.player.setSprinting(client.options.forwardKey.isPressed()&&!client.player.isSneaking());
            long now=System.currentTimeMillis();
            if(client.getWindow()!=null){boolean left=org.lwjgl.glfw.GLFW.glfwGetMouseButton(client.getWindow().getHandle(),org.lwjgl.glfw.GLFW.GLFW_MOUSE_BUTTON_LEFT)==org.lwjgl.glfw.GLFW.GLFW_PRESS;boolean right=org.lwjgl.glfw.GLFW.glfwGetMouseButton(client.getWindow().getHandle(),org.lwjgl.glfw.GLFW.GLFW_MOUSE_BUTTON_RIGHT)==org.lwjgl.glfw.GLFW.GLFW_PRESS;if((left&&!lastLeftDown)||(right&&!lastRightDown))clickTimes.addLast(now);lastLeftDown=left;lastRightDown=right;}
            while(!clickTimes.isEmpty()&&now-clickTimes.peekFirst()>1000)clickTimes.removeFirst();
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
                    +(sessionTimeEnabled?1:0)+(waypointEnabled&&waypoint!=null?1:0)+(inventoryOverlayEnabled?1:0)
                    +(lightLevelEnabled?1:0)+(entityOverlayEnabled?3:0)+(toolInfoEnabled?1:0)+(cpsEnabled?1:0);
            if(lines==0&&!customCrosshairEnabled)return;
            int row=hudY;
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
            if(inventoryOverlayEnabled){int occupied=0;for(ItemStack s:c.player.getInventory().main)if(!s.isEmpty())occupied++;line(draw,c,"INVENTORY",occupied+" / 36 slots used",row);row+=12;}
            if(lightLevelEnabled){int light=c.world.getLightLevel(c.player.getBlockPos());line(draw,c,"LIGHT LEVEL",light+" / 15",row);row+=12;}
            if(entityOverlayEnabled){int shown=0;for(var entity:c.world.getEntitiesByClass(net.minecraft.entity.LivingEntity.class,c.player.getBoundingBox().expand(12),e->e!=c.player&&!e.isRemoved())){if(shown++>=3)break;double dist=Math.sqrt(c.player.squaredDistanceTo(entity));line(draw,c,"NEARBY "+shown,entity.getName().getString()+" · "+Math.round(dist)+"m",row);row+=12;}}
            if(toolInfoEnabled){ItemStack held=c.player.getMainHandStack();String v=held.isEmpty()?"No tool held":(held.isDamageable()?Math.max(0,held.getMaxDamage()-held.getDamage())+" durability":held.getName().getString());line(draw,c,"TOOL INFO",v,row);row+=12;}
            if(cpsEnabled){line(draw,c,"CPS",String.valueOf(clickTimes.size()),row);row+=12;}
            if(customCrosshairEnabled)renderCrosshair(draw,c.getWindow().getScaledWidth()/2,c.getWindow().getScaledHeight()/2);
        });
    }
    public static void renderCrosshair(net.minecraft.client.gui.DrawContext d,int cx,int cy){
        int s=crosshairSize,g=crosshairGap,t=crosshairThickness,col=crosshairColor;
        switch(crosshairStyle){
            case 1 -> {d.fill(cx-s,cy-t/2,cx+s+1,cy+(t+1)/2,col);d.fill(cx-t/2,cy-s,cx+(t+1)/2,cy+s+1,col);}
            case 2 -> {d.fill(cx-s,cy-s,cx+s+1,cy-s+t,col);d.fill(cx-s,cy+s-t+1,cx+s+1,cy+s+1,col);d.fill(cx-s,cy-s,cx-s+t,cy+s+1,col);d.fill(cx+s-t+1,cy-s,cx+s+1,cy+s+1,col);}
            case 3 -> {d.fill(cx-s,cy-g-t,cx+s+1,cy-g,col);d.fill(cx-s,cy+g+1,cx+s+1,cy+g+t+1,col);}
            case 4 -> {d.fill(cx-s,cy-t/2,cx+s+1,cy+(t+1)/2,col);d.fill(cx-t/2,cy-s,cx+(t+1)/2,cy+s+1,col);d.fill(cx-s,cy-s,cx-s+2,cy-s+2,col);d.fill(cx+s-1,cy-s,cx+s+1,cy-s+2,col);d.fill(cx-s,cy+s-1,cx-s+2,cy+s+1,col);d.fill(cx+s-1,cy+s-1,cx+s+1,cy+s+1,col);}
            default -> {d.fill(cx-s,cy-g-t,cx-s+t,cy-g,col);d.fill(cx+g,cy-s,cx+g+t,cy-s+t,col);d.fill(cx+s-t+1,cy+g,cx+s+1,cy+g+t,col);d.fill(cx-g-t,cy+s-t+1,cx-g,cy+s+1,col);}
        }
        if(crosshairDot)d.fill(cx-1,cy-1,cx+2,cy+2,col);
    }
    private static String duration(int ticks){int s=Math.max(0,ticks)/20;return String.format(java.util.Locale.ROOT,"%d:%02d",s/60,s%60);}
    private static void line(net.minecraft.client.gui.DrawContext d,MinecraftClient c,String k,String v,int ignoredY){
        int x=getElementX(k),y=getElementY(k),labelWidth=c.textRenderer.getWidth(k),valueWidth=c.textRenderer.getWidth(v);
        int w=Math.min(260,Math.max(112,labelWidth+valueWidth+20));
        d.fill(x,y,x+w,y+17,0xB5100D14);d.fill(x,y,x+2,y+17,0xFFFF702E);
        d.drawTextWithShadow(c.textRenderer,Text.literal(k),x+7,y+4,0xFFFFA06C);
        d.drawTextWithShadow(c.textRenderer,Text.literal(v),x+Math.min(90,Math.max(48,labelWidth+13)),y+4,0xFFF4EDF4);
    }
}
