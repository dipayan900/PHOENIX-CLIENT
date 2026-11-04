package net.phoenix.client.branding;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.loader.api.FabricLoader;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.item.ItemStack;
import net.minecraft.particle.ParticleTypes;
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
    private static String uiTheme = "SYSTEM";
    private static boolean registered;
    private static boolean fpsEnabled = true, coordinatesEnabled = true, directionEnabled = true;
    private static boolean armorEnabled = true, clockEnabled, pingEnabled = true;
    private static boolean speedEnabled, biomeEnabled, dimensionEnabled, worldTimeEnabled;
    private static boolean attackIndicatorEnabled, potionEffectsEnabled, itemCounterEnabled;
    private static boolean keystrokesEnabled, sessionTimeEnabled;
    private static boolean fullbrightEnabled, waypointEnabled, inventoryOverlayEnabled, chatTransparencyEnabled;
    private static boolean lightLevelEnabled, entityOverlayEnabled, customCrosshairEnabled, toolInfoEnabled;
    private static boolean healthIndicatorEnabled = true;
    private static long sessionTicks;
    private static BlockPos waypoint;
    private static Identifier waypointDimension;
    private static KeyBinding zoomKey, sprintToggleKey, menuKey;
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
    private static boolean particlesEnabled = true;
    private static int particleStyle = 0;
    private static final Map<String,Float> observedHealth = new HashMap<>();
    private static String positionKey(String key){return key.toLowerCase(java.util.Locale.ROOT).replaceAll("[^a-z0-9_-]","_");}
    private static int elementIndex(String key){for(int i=0;i<ELEMENT_KEYS.length;i++)if(ELEMENT_KEYS[i].equals(key))return i;return 0;}
    public static String[] getElementKeys(){return ELEMENT_KEYS.clone();}
    public static int getElementX(String key){int[] p=elementPositions.get(positionKey(key));return p==null?10+(elementIndex(key)/12)*160:p[0];}
    public static int getElementY(String key){int[] p=elementPositions.get(positionKey(key));return p==null?10+(elementIndex(key)%12)*22:p[1];}
    public static void setElementPosition(String key,int x,int y){elementPositions.put(positionKey(key),new int[]{Math.max(0,x),Math.max(0,y)});}
    public static void resetElementPositions(){elementPositions.clear();saveConfig();}
    private static Path configFile(){return FabricLoader.getInstance().getConfigDir().resolve("phoenix-client.properties");}
    public static int getHudX(){return hudX;} public static int getHudY(){return hudY;}
    public static void setHudPosition(int x,int y){hudX=Math.max(0,x);hudY=Math.max(0,y);saveConfig();}
    private static void loadConfig() {
        try {
            Path path = configFile();
            if (!Files.exists(path)) return;
            Properties values = new Properties();
            try (InputStream in = Files.newInputStream(path)) { values.load(in); }
            hudX = Math.max(0, Integer.parseInt(values.getProperty("hud.x", "10")));
            hudY = Math.max(0, Integer.parseInt(values.getProperty("hud.y", "10")));
            crosshairStyle = Math.max(0, Math.min(4, Integer.parseInt(values.getProperty("crosshair.style", "0"))));
            crosshairColor = (int) Long.parseLong(values.getProperty("crosshair.color", "4294937141"));
            crosshairSize = Math.max(3, Math.min(18, Integer.parseInt(values.getProperty("crosshair.size", "7"))));
            crosshairGap = Math.max(0, Math.min(10, Integer.parseInt(values.getProperty("crosshair.gap", "3"))));
            crosshairThickness = Math.max(1, Math.min(4, Integer.parseInt(values.getProperty("crosshair.thickness", "1"))));
            crosshairDot = Boolean.parseBoolean(values.getProperty("crosshair.dot", "false"));
            customCrosshairEnabled = Boolean.parseBoolean(values.getProperty("crosshair.enabled", "false"));
            uiTheme = values.getProperty("ui.theme", uiTheme).toUpperCase(java.util.Locale.ROOT);
            visible = setting(values, "hud.visible", visible);
            fpsEnabled = setting(values, "hud.fps", fpsEnabled);
            coordinatesEnabled = setting(values, "hud.coordinates", coordinatesEnabled);
            directionEnabled = setting(values, "hud.direction", directionEnabled);
            armorEnabled = setting(values, "hud.armor", armorEnabled);
            clockEnabled = setting(values, "hud.clock", clockEnabled);
            pingEnabled = setting(values, "hud.ping", pingEnabled);
            speedEnabled = setting(values, "hud.speed", speedEnabled);
            biomeEnabled = setting(values, "hud.biome", biomeEnabled);
            dimensionEnabled = setting(values, "hud.dimension", dimensionEnabled);
            worldTimeEnabled = setting(values, "hud.world_time", worldTimeEnabled);
            attackIndicatorEnabled = setting(values, "hud.attack", attackIndicatorEnabled);
            potionEffectsEnabled = setting(values, "hud.potions", potionEffectsEnabled);
            itemCounterEnabled = setting(values, "hud.item_counter", itemCounterEnabled);
            keystrokesEnabled = setting(values, "hud.keystrokes", keystrokesEnabled);
            sessionTimeEnabled = setting(values, "hud.session", sessionTimeEnabled);
            fullbrightEnabled = setting(values, "hud.fullbright", fullbrightEnabled);
            waypointEnabled = setting(values, "hud.waypoint", waypointEnabled);
            inventoryOverlayEnabled = setting(values, "hud.inventory", inventoryOverlayEnabled);
            chatTransparencyEnabled = setting(values, "hud.chat_transparency", chatTransparencyEnabled);
            lightLevelEnabled = setting(values, "hud.light_level", lightLevelEnabled);
            entityOverlayEnabled = setting(values, "hud.nearby_entities", entityOverlayEnabled);
            healthIndicatorEnabled = setting(values, "hud.health_indicators", healthIndicatorEnabled);
            particlesEnabled = setting(values, "particles.enabled", particlesEnabled);
            particleStyle = Math.max(0, Math.min(3, Integer.parseInt(values.getProperty("particles.style", "0"))));
            toolInfoEnabled = setting(values, "hud.tool_info", toolInfoEnabled);
            cpsEnabled = setting(values, "hud.cps", cpsEnabled);
            sprintToggled = setting(values, "module.toggle_sprint", sprintToggled);
            for (String key : values.stringPropertyNames()) {
                if (key.startsWith("element.") && key.endsWith(".x")) {
                    String id = key.substring(8, key.length() - 2);
                    try {
                        int x = Integer.parseInt(values.getProperty(key));
                        int y = Integer.parseInt(values.getProperty("element." + id + ".y", "10"));
                        elementPositions.put(id, new int[]{Math.max(0, x), Math.max(0, y)});
                    } catch (NumberFormatException ignored) { }
                }
            }
        } catch (Exception ignored) { }
    }

    private static boolean setting(Properties values, String key, boolean fallback) {
        return Boolean.parseBoolean(values.getProperty(key, String.valueOf(fallback)));
    }

    public static void saveConfig() {
        try {
            Path path = configFile();
            Files.createDirectories(path.getParent());
            Properties values = new Properties();
            values.setProperty("hud.x", String.valueOf(hudX));
            values.setProperty("hud.y", String.valueOf(hudY));
            values.setProperty("crosshair.style", String.valueOf(crosshairStyle));
            values.setProperty("crosshair.color", Long.toString(Integer.toUnsignedLong(crosshairColor)));
            values.setProperty("crosshair.size", String.valueOf(crosshairSize));
            values.setProperty("crosshair.gap", String.valueOf(crosshairGap));
            values.setProperty("crosshair.thickness", String.valueOf(crosshairThickness));
            values.setProperty("crosshair.dot", String.valueOf(crosshairDot));
            values.setProperty("crosshair.enabled", String.valueOf(customCrosshairEnabled));
            values.setProperty("ui.theme", uiTheme);
            values.setProperty("hud.visible", String.valueOf(visible));
            values.setProperty("hud.fps", String.valueOf(fpsEnabled));
            values.setProperty("hud.coordinates", String.valueOf(coordinatesEnabled));
            values.setProperty("hud.direction", String.valueOf(directionEnabled));
            values.setProperty("hud.armor", String.valueOf(armorEnabled));
            values.setProperty("hud.clock", String.valueOf(clockEnabled));
            values.setProperty("hud.ping", String.valueOf(pingEnabled));
            values.setProperty("hud.speed", String.valueOf(speedEnabled));
            values.setProperty("hud.biome", String.valueOf(biomeEnabled));
            values.setProperty("hud.dimension", String.valueOf(dimensionEnabled));
            values.setProperty("hud.world_time", String.valueOf(worldTimeEnabled));
            values.setProperty("hud.attack", String.valueOf(attackIndicatorEnabled));
            values.setProperty("hud.potions", String.valueOf(potionEffectsEnabled));
            values.setProperty("hud.item_counter", String.valueOf(itemCounterEnabled));
            values.setProperty("hud.keystrokes", String.valueOf(keystrokesEnabled));
            values.setProperty("hud.session", String.valueOf(sessionTimeEnabled));
            values.setProperty("hud.fullbright", String.valueOf(fullbrightEnabled));
            values.setProperty("hud.waypoint", String.valueOf(waypointEnabled));
            values.setProperty("hud.inventory", String.valueOf(inventoryOverlayEnabled));
            values.setProperty("hud.chat_transparency", String.valueOf(chatTransparencyEnabled));
            values.setProperty("hud.light_level", String.valueOf(lightLevelEnabled));
            values.setProperty("hud.nearby_entities", String.valueOf(entityOverlayEnabled));
            values.setProperty("hud.health_indicators", String.valueOf(healthIndicatorEnabled));
            values.setProperty("particles.enabled", String.valueOf(particlesEnabled));
            values.setProperty("particles.style", String.valueOf(particleStyle));
            values.setProperty("hud.tool_info", String.valueOf(toolInfoEnabled));
            values.setProperty("hud.cps", String.valueOf(cpsEnabled));
            values.setProperty("module.toggle_sprint", String.valueOf(sprintToggled));
            for (var entry : elementPositions.entrySet()) {
                values.setProperty("element." + entry.getKey() + ".x", String.valueOf(entry.getValue()[0]));
                values.setProperty("element." + entry.getKey() + ".y", String.valueOf(entry.getValue()[1]));
            }
            try (OutputStream out = Files.newOutputStream(path)) {
                values.store(out, "Phoenix Client settings");
            }
        } catch (Exception ignored) { }
    }

    private PhoenixHud() {}
    public static String getUiTheme(){return uiTheme;}
    public static boolean isLightUiTheme(){return "LIGHT".equals(uiTheme)||("SYSTEM".equals(uiTheme)&&"light".equalsIgnoreCase(System.getProperty("phoenix.ui.theme","dark")));}
    public static void cycleUiTheme(){uiTheme=switch(uiTheme){case "SYSTEM"->"DARK";case "DARK"->"LIGHT";default->"SYSTEM";};saveConfig();}
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
    public static boolean isHealthIndicatorEnabled(){return healthIndicatorEnabled;}
    public static void toggleHealthIndicator(){healthIndicatorEnabled=!healthIndicatorEnabled;saveConfig();}
    public static boolean isParticlesEnabled(){return particlesEnabled;}
    public static void toggleParticles(){particlesEnabled=!particlesEnabled;saveConfig();}
    public static int getParticleStyle(){return particleStyle;}
    public static void setParticleStyle(int style){particleStyle=Math.max(0,Math.min(3,style));saveConfig();}
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

    public static void toggle(){visible=!visible;saveConfig();}
    public static void toggleFps(){fpsEnabled=!fpsEnabled;saveConfig();}
    public static void toggleCoordinates(){coordinatesEnabled=!coordinatesEnabled;saveConfig();}
    public static void toggleDirection(){directionEnabled=!directionEnabled;saveConfig();}
    public static void toggleArmor(){armorEnabled=!armorEnabled;saveConfig();}
    public static void toggleClock(){clockEnabled=!clockEnabled;saveConfig();}
    public static void togglePing(){pingEnabled=!pingEnabled;saveConfig();}
    public static void toggleSpeed(){speedEnabled=!speedEnabled;saveConfig();}
    public static void toggleBiome(){biomeEnabled=!biomeEnabled;saveConfig();}
    public static void toggleDimension(){dimensionEnabled=!dimensionEnabled;saveConfig();}
    public static void toggleWorldTime(){worldTimeEnabled=!worldTimeEnabled;saveConfig();}
    public static void toggleAttackIndicator(){attackIndicatorEnabled=!attackIndicatorEnabled;saveConfig();}
    public static void togglePotionEffects(){potionEffectsEnabled=!potionEffectsEnabled;saveConfig();}
    public static void toggleItemCounter(){itemCounterEnabled=!itemCounterEnabled;saveConfig();}
    public static void toggleKeystrokes(){keystrokesEnabled=!keystrokesEnabled;saveConfig();}
    public static void toggleSessionTime(){sessionTimeEnabled=!sessionTimeEnabled;saveConfig();}
    public static void toggleFullbright(){fullbrightEnabled=!fullbrightEnabled;saveConfig();}
    public static void toggleWaypoint(){waypointEnabled=!waypointEnabled;saveConfig();}
    public static void toggleInventoryOverlay(){inventoryOverlayEnabled=!inventoryOverlayEnabled;saveConfig();}
    public static void toggleChatTransparency(){chatTransparencyEnabled=!chatTransparencyEnabled;saveConfig();}
    public static void toggleLightLevel(){lightLevelEnabled=!lightLevelEnabled;saveConfig();}
    public static void toggleEntityOverlay(){entityOverlayEnabled=!entityOverlayEnabled;saveConfig();}
    public static void toggleCustomCrosshair(){customCrosshairEnabled=!customCrosshairEnabled;saveConfig();}
    public static void toggleToolInfo(){toolInfoEnabled=!toolInfoEnabled;saveConfig();}
    public static void toggleCps(){cpsEnabled=!cpsEnabled;saveConfig();}
    public static void toggleSprint(){sprintToggled=!sprintToggled;saveConfig();}
    public static void applyPreset(String preset){
        visible=true;
        if("PVP".equals(preset)){
            fpsEnabled=true;coordinatesEnabled=true;directionEnabled=true;armorEnabled=true;pingEnabled=true;
            keystrokesEnabled=true;cpsEnabled=true;attackIndicatorEnabled=true;potionEffectsEnabled=true;
            speedEnabled=false;biomeEnabled=false;dimensionEnabled=false;worldTimeEnabled=false;clockEnabled=false;
            sessionTimeEnabled=false;inventoryOverlayEnabled=false;lightLevelEnabled=false;entityOverlayEnabled=false;
            toolInfoEnabled=false;itemCounterEnabled=false;
        }else if("PERFORMANCE".equals(preset)){
            fpsEnabled=true;coordinatesEnabled=false;directionEnabled=false;armorEnabled=false;pingEnabled=false;
            keystrokesEnabled=false;cpsEnabled=false;attackIndicatorEnabled=false;potionEffectsEnabled=false;
            speedEnabled=false;biomeEnabled=false;dimensionEnabled=false;worldTimeEnabled=false;clockEnabled=false;
            sessionTimeEnabled=false;inventoryOverlayEnabled=false;lightLevelEnabled=false;entityOverlayEnabled=false;
            toolInfoEnabled=false;itemCounterEnabled=false;
        }else{
            fpsEnabled=true;coordinatesEnabled=true;directionEnabled=true;armorEnabled=true;pingEnabled=true;
            speedEnabled=false;biomeEnabled=false;dimensionEnabled=false;worldTimeEnabled=false;clockEnabled=false;
            attackIndicatorEnabled=false;potionEffectsEnabled=false;itemCounterEnabled=false;keystrokesEnabled=false;
            sessionTimeEnabled=false;inventoryOverlayEnabled=false;lightLevelEnabled=false;entityOverlayEnabled=false;
            toolInfoEnabled=false;cpsEnabled=true;
        }
        saveConfig();
    }

    public static void setWaypointHere(){
        MinecraftClient c=MinecraftClient.getInstance();
        if(c.player!=null&&c.world!=null){waypoint=c.player.getBlockPos().toImmutable();waypointDimension=c.world.getRegistryKey().getValue();}
    }
    public static void clearWaypoint(){waypoint=null;waypointDimension=null;}

    public static void register(){
        if (registered) return;
        registered = true;
        loadConfig();
        zoomKey=KeyBindingHelper.registerKeyBinding(new KeyBinding("key.phoenix.zoom",InputUtil.Type.KEYSYM,GLFW.GLFW_KEY_C,"category.phoenix"));
        sprintToggleKey=KeyBindingHelper.registerKeyBinding(new KeyBinding("key.phoenix.toggle_sprint",InputUtil.Type.KEYSYM,GLFW.GLFW_KEY_G,"category.phoenix"));
        menuKey=KeyBindingHelper.registerKeyBinding(new KeyBinding("key.phoenix.menu",InputUtil.Type.KEYSYM,GLFW.GLFW_KEY_RIGHT_SHIFT,"category.phoenix"));
        ClientTickEvents.END_CLIENT_TICK.register(client->{
            if(client.player!=null&&!client.isPaused())sessionTicks++;
            while(sprintToggleKey.wasPressed()){sprintToggled=!sprintToggled;saveConfig();}
            while(menuKey.wasPressed()){if(client.player!=null)client.setScreen(new PhoenixClientScreen(null));}
            if(client.player!=null&&sprintToggled)client.player.setSprinting(client.options.forwardKey.isPressed()&&!client.player.isSneaking());
            long now=System.currentTimeMillis();
            if(client.getWindow()!=null){boolean left=org.lwjgl.glfw.GLFW.glfwGetMouseButton(client.getWindow().getHandle(),org.lwjgl.glfw.GLFW.GLFW_MOUSE_BUTTON_LEFT)==org.lwjgl.glfw.GLFW.GLFW_PRESS;boolean right=org.lwjgl.glfw.GLFW.glfwGetMouseButton(client.getWindow().getHandle(),org.lwjgl.glfw.GLFW.GLFW_MOUSE_BUTTON_RIGHT)==org.lwjgl.glfw.GLFW.GLFW_PRESS;if((left&&!lastLeftDown)||(right&&!lastRightDown))clickTimes.addLast(now);lastLeftDown=left;lastRightDown=right;}
            while(!clickTimes.isEmpty()&&now-clickTimes.peekFirst()>1000)clickTimes.removeFirst();
            if(client.player==null){
                if(zoomActive){client.options.getFov().setValue(previousFov);zoomActive=false;}
                return;
            }
            updateHitParticles(client);
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
            if(directionEnabled){line(draw,c,"FACING",c.player.getHorizontalFacing().asString().toUpperCase(java.util.Locale.ROOT),row);row+=12;}
            if(armorEnabled){renderArmorWidget(draw,c);row+=42;}
            if(pingEnabled){var handler=c.getNetworkHandler();var e=handler==null?null:handler.getPlayerListEntry(c.player.getUuid());line(draw,c,"PING",e==null?"—":e.getLatency()+" ms",row);row+=12;}
            if(speedEnabled){double v=c.player.getVelocity().horizontalLength()*20*3.6;line(draw,c,"SPEED",String.format(java.util.Locale.ROOT,"%.1f km/h",v),row);row+=12;}
            if(biomeEnabled){String b=c.world.getBiome(c.player.getBlockPos()).getKey().map(k->k.getValue().getPath()).orElse("unknown");line(draw,c,"BIOME",b,row);row+=12;}
            if(dimensionEnabled){line(draw,c,"DIMENSION",c.world.getRegistryKey().getValue().getPath(),row);row+=12;}
            if(worldTimeEnabled){long t=Math.floorMod(c.world.getTimeOfDay(),24000L);line(draw,c,"WORLD TIME",String.format(java.util.Locale.ROOT,"%02d:%02d",((t+6000)/1000)%24,(t%1000)*60/1000),row);row+=12;}
            if(clockEnabled){line(draw,c,"LOCAL",LocalTime.now().format(DateTimeFormatter.ofPattern("HH:mm:ss")),row);row+=12;}
            if(attackIndicatorEnabled){line(draw,c,"ATTACK",Math.round(c.player.getAttackCooldownProgress(0)*100)+"%",row);row+=12;}
            if(potionEffectsEnabled){var e=c.player.getStatusEffects().stream().findFirst().orElse(null);line(draw,c,"POTION",e==null?"None":e.getEffectType().value().getName().getString()+" "+duration(e.getDuration()),row);row+=12;}
            if(itemCounterEnabled){ItemStack held=c.player.getMainHandStack();int count=0;if(!held.isEmpty())for(int slot=0;slot<36;slot++){ItemStack stack=c.player.getInventory().getStack(slot);if(ItemStack.areItemsAndComponentsEqual(stack,held))count+=stack.getCount();}line(draw,c,"HELD ITEM",held.isEmpty()?"Empty":held.getName().getString()+" ×"+count,row);row+=12;}
            if(keystrokesEnabled){renderKeystrokesWidget(draw,c);row+=58;}
            if(sessionTimeEnabled){long s=sessionTicks/20;line(draw,c,"SESSION",String.format(java.util.Locale.ROOT,"%02d:%02d:%02d",s/3600,(s/60)%60,s%60),row);row+=12;}
            if(waypointEnabled&&waypoint!=null){if(waypointDimension.equals(c.world.getRegistryKey().getValue())){double d=Math.sqrt(c.player.squaredDistanceTo(waypoint.getX()+0.5,waypoint.getY()+0.5,waypoint.getZ()+0.5));line(draw,c,"WAYPOINT",waypoint.getX()+" "+waypoint.getY()+" "+waypoint.getZ()+" · "+Math.round(d)+"m",row);}else line(draw,c,"WAYPOINT","Other dimension",row);row+=12;}
            if(inventoryOverlayEnabled){renderInventoryWidget(draw,c);row+=84;}
            if(lightLevelEnabled){int light=c.world.getLightLevel(c.player.getBlockPos());line(draw,c,"LIGHT LEVEL",light+" / 15",row);row+=12;}
            if(entityOverlayEnabled){int shown=0;for(var entity:c.world.getEntitiesByClass(net.minecraft.entity.LivingEntity.class,c.player.getBoundingBox().expand(12),e->e!=c.player&&!e.isRemoved())){if(shown++>=3)break;double dist=Math.sqrt(c.player.squaredDistanceTo(entity));line(draw,c,"NEARBY "+shown,entity.getName().getString()+" · "+Math.round(dist)+"m",row);row+=12;}}
            if(toolInfoEnabled){renderToolWidget(draw,c);row+=38;}
            if(cpsEnabled){line(draw,c,"CPS",String.valueOf(clickTimes.size()),row);row+=12;}
            if(customCrosshairEnabled)renderCrosshair(draw,c.getWindow().getScaledWidth()/2,c.getWindow().getScaledHeight()/2);
        });
    }
    private static int widgetPanelColor(){return isLightUiTheme()?0xEFFFFFFF:0xD9100D16;}
    private static int widgetBorderColor(){return isLightUiTheme()?0xFFB94D1B:0xFFFF702E;}
    private static int widgetTextColor(){return isLightUiTheme()?0xFF29232B:0xFFF8F1F7;}
    private static int durabilityColor(float ratio){return ratio>0.6f?0xFF55D68B:ratio>0.25f?0xFFFFC04D:0xFFFF5B65;}
    private static void widgetPanel(net.minecraft.client.gui.DrawContext d,int x,int y,int w,int h,String title){
        d.fill(x,y,x+w,y+h,widgetPanelColor());
        d.fill(x,y,x+w,y+2,widgetBorderColor());
        d.drawTextWithShadow(MinecraftClient.getInstance().textRenderer,Text.literal(title),x+5,y+5,widgetBorderColor());
    }
    private static void renderArmorWidget(net.minecraft.client.gui.DrawContext d,MinecraftClient c){
        int x=getElementX("ARMOR"),y=getElementY("ARMOR");
        widgetPanel(d,x,y,112,39,"ARMOR");
        EquipmentSlot[] slots={EquipmentSlot.HEAD,EquipmentSlot.CHEST,EquipmentSlot.LEGS,EquipmentSlot.FEET};
        for(int i=0;i<slots.length;i++){
            ItemStack stack=c.player.getEquippedStack(slots[i]);int sx=x+5+i*27,sy=y+17;
            d.fill(sx-1,sy-1,sx+17,sy+17,0x553A303F);
            if(!stack.isEmpty()){
                d.drawItem(stack,sx,sy);
                if(stack.isDamageable()){
                    float ratio=(float)(stack.getMaxDamage()-stack.getDamage())/(float)stack.getMaxDamage();
                    d.fill(sx,sy+17,sx+16,sy+19,0xFF302A32);
                    d.fill(sx,sy+17,sx+Math.max(1,Math.round(16*ratio)),sy+19,durabilityColor(ratio));
                }
            }
        }
    }
    private static void renderKeystrokesWidget(net.minecraft.client.gui.DrawContext d,MinecraftClient c){
        int x=getElementX("KEYS"),y=getElementY("KEYS");widgetPanel(d,x,y,94,55,"KEYSTROKES");
        drawKey(d,c,"W",c.options.forwardKey.isPressed(),x+37,y+17);
        drawKey(d,c,"A",c.options.leftKey.isPressed(),x+17,y+35);
        drawKey(d,c,"S",c.options.backKey.isPressed(),x+37,y+35);
        drawKey(d,c,"D",c.options.rightKey.isPressed(),x+57,y+35);
        d.fill(x+17,y+49,x+77,y+52,c.options.jumpKey.isPressed()?widgetBorderColor():0xFF554B59);
    }
    private static void drawKey(net.minecraft.client.gui.DrawContext d,MinecraftClient c,String key,boolean pressed,int x,int y){
        int bg=pressed?0xFF69351F:(isLightUiTheme()?0xFFE4DDE5:0xFF302735);
        d.fill(x,y,x+18,y+16,bg);
        d.fill(x,y,x+18,y+1,pressed?0xFFFF9A5B:0xFF655968);
        int tw=c.textRenderer.getWidth(key);
        d.drawTextWithShadow(c.textRenderer,Text.literal(key),x+(18-tw)/2,y+4,widgetTextColor());
    }
    private static void renderInventoryWidget(net.minecraft.client.gui.DrawContext d,MinecraftClient c){
        int x=getElementX("INVENTORY"),y=getElementY("INVENTORY");widgetPanel(d,x,y,182,82,"INVENTORY");
        for(int row=0;row<4;row++){
            for(int col=0;col<9;col++){
                int slot=row==3?col:9+row*9+col;
                int sx=x+5+col*19,sy=y+15+row*16;
                boolean selected=row==3&&col==c.player.getInventory().selectedSlot;
                d.fill(sx,sy,sx+18,sy+16,selected?0xFF70402A:0x99534A58);
                ItemStack stack=c.player.getInventory().getStack(slot);
                if(!stack.isEmpty())d.drawItem(stack,sx+1,sy);
            }
        }
    }

    private static void renderToolWidget(net.minecraft.client.gui.DrawContext d,MinecraftClient c){
        int x=getElementX("TOOL INFO"),y=getElementY("TOOL INFO");ItemStack stack=c.player.getMainHandStack();
        widgetPanel(d,x,y,132,36,"HELD ITEM");
        if(stack.isEmpty()){d.drawTextWithShadow(c.textRenderer,Text.literal("Empty hand"),x+7,y+19,widgetTextColor());return;}
        d.drawItem(stack,x+6,y+17);
        String name=stack.getName().getString();if(name.length()>13)name=name.substring(0,12)+"…";
        d.drawTextWithShadow(c.textRenderer,Text.literal(name),x+27,y+18,widgetTextColor());
        if(stack.isDamageable()){
            float ratio=(float)(stack.getMaxDamage()-stack.getDamage())/(float)stack.getMaxDamage();
            d.fill(x+27,y+29,x+124,y+32,0xFF302A32);
            d.fill(x+27,y+29,x+27+Math.max(1,Math.round(97*ratio)),y+32,durabilityColor(ratio));
        }
    }

    private static void updateHitParticles(MinecraftClient client){
        if(!particlesEnabled||client.player==null||client.world==null)return;
        var nearby=client.world.getEntitiesByClass(net.minecraft.entity.LivingEntity.class,
                client.player.getBoundingBox().expand(12.0),e->e!=client.player&&!e.isRemoved());
        for(net.minecraft.entity.LivingEntity entity:nearby){
            String id=entity.getUuidAsString();
            float health=entity.getHealth();
            Float previous=observedHealth.put(id,health);
            if(previous!=null&&health<previous){
                net.minecraft.particle.ParticleEffect effect=switch(particleStyle){
                    case 1 -> ParticleTypes.HEART;
                    case 2 -> ParticleTypes.END_ROD;
                    case 3 -> ParticleTypes.FLAME;
                    default -> ParticleTypes.CRIT;
                };
                for(int i=0;i<5;i++){
                    double ox=(Math.random()-0.5)*0.65,oz=(Math.random()-0.5)*0.65;
                    double vx=(Math.random()-0.5)*0.08,vz=(Math.random()-0.5)*0.08;
                    client.world.addParticle(effect,entity.getX()+ox,entity.getY()+entity.getHeight()*0.65,
                            entity.getZ()+oz,vx,0.035+Math.random()*0.06,vz);
                }
            }
        }
        if(observedHealth.size()>512)observedHealth.clear();
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
        boolean light=isLightUiTheme();
        int panel=light?0xDFFFFFFF:0xB5100D14;
        int accent=light?0xFFB94D1B:0xFFFF702E;
        int label=light?0xFF8F3B18:0xFFFFA06C;
        int value=light?0xFF25232A:0xFFF4EDF4;
        d.fill(x,y,x+w,y+17,panel);d.fill(x,y,x+2,y+17,accent);
        d.drawTextWithShadow(c.textRenderer,Text.literal(k),x+7,y+4,label);
        d.drawTextWithShadow(c.textRenderer,Text.literal(v),x+Math.min(90,Math.max(48,labelWidth+13)),y+4,value);
    }
}
