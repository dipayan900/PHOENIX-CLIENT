package net.phoenix.client.branding;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.minecraft.client.MinecraftClient;
import net.minecraft.item.ItemStack;
import net.minecraft.text.Text;
import net.minecraft.util.math.BlockPos;

import java.time.LocalTime;
import java.time.format.DateTimeFormatter;

/** Client-side Phoenix HUD modules. Each module has an independent toggle. */
public final class PhoenixHud {
    private static boolean visible = true;
    private static boolean fpsEnabled = true, coordinatesEnabled = true, directionEnabled = true;
    private static boolean armorEnabled = true, clockEnabled, pingEnabled = true;
    private static boolean speedEnabled, biomeEnabled, dimensionEnabled, worldTimeEnabled;
    private static boolean attackIndicatorEnabled, potionEffectsEnabled, itemCounterEnabled;
    private static boolean keystrokesEnabled, sessionTimeEnabled;
    private static long sessionTicks;

    private PhoenixHud() {}
    public static boolean isVisible() { return visible; }
    public static boolean isFpsEnabled() { return fpsEnabled; }
    public static boolean isCoordinatesEnabled() { return coordinatesEnabled; }
    public static boolean isDirectionEnabled() { return directionEnabled; }
    public static boolean isArmorEnabled() { return armorEnabled; }
    public static boolean isClockEnabled() { return clockEnabled; }
    public static boolean isPingEnabled() { return pingEnabled; }
    public static boolean isSpeedEnabled() { return speedEnabled; }
    public static boolean isBiomeEnabled() { return biomeEnabled; }
    public static boolean isDimensionEnabled() { return dimensionEnabled; }
    public static boolean isWorldTimeEnabled() { return worldTimeEnabled; }
    public static boolean isAttackIndicatorEnabled() { return attackIndicatorEnabled; }
    public static boolean isPotionEffectsEnabled() { return potionEffectsEnabled; }
    public static boolean isItemCounterEnabled() { return itemCounterEnabled; }
    public static boolean isKeystrokesEnabled() { return keystrokesEnabled; }
    public static boolean isSessionTimeEnabled() { return sessionTimeEnabled; }

    public static void toggle() { visible = !visible; }
    public static void toggleFps() { fpsEnabled = !fpsEnabled; }
    public static void toggleCoordinates() { coordinatesEnabled = !coordinatesEnabled; }
    public static void toggleDirection() { directionEnabled = !directionEnabled; }
    public static void toggleArmor() { armorEnabled = !armorEnabled; }
    public static void toggleClock() { clockEnabled = !clockEnabled; }
    public static void togglePing() { pingEnabled = !pingEnabled; }
    public static void toggleSpeed() { speedEnabled = !speedEnabled; }
    public static void toggleBiome() { biomeEnabled = !biomeEnabled; }
    public static void toggleDimension() { dimensionEnabled = !dimensionEnabled; }
    public static void toggleWorldTime() { worldTimeEnabled = !worldTimeEnabled; }
    public static void toggleAttackIndicator() { attackIndicatorEnabled = !attackIndicatorEnabled; }
    public static void togglePotionEffects() { potionEffectsEnabled = !potionEffectsEnabled; }
    public static void toggleItemCounter() { itemCounterEnabled = !itemCounterEnabled; }
    public static void toggleKeystrokes() { keystrokesEnabled = !keystrokesEnabled; }
    public static void toggleSessionTime() { sessionTimeEnabled = !sessionTimeEnabled; }

    public static void register() {
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            if (client.player != null && !client.isPaused()) sessionTicks++;
        });
        HudRenderCallback.EVENT.register((draw, tickCounter) -> {
            if (!visible) return;
            MinecraftClient client = MinecraftClient.getInstance();
            if (client.player == null || client.world == null) return;
            int lines = (fpsEnabled?1:0)+(coordinatesEnabled?1:0)+(directionEnabled?1:0)
                    +(armorEnabled?1:0)+(clockEnabled?1:0)+(pingEnabled?1:0)+(speedEnabled?1:0)
                    +(biomeEnabled?1:0)+(dimensionEnabled?1:0)+(worldTimeEnabled?1:0)
                    +(attackIndicatorEnabled?1:0)+(potionEffectsEnabled?1:0)+(itemCounterEnabled?1:0)
                    +(keystrokesEnabled?1:0)+(sessionTimeEnabled?1:0);
            if (lines == 0) return;
            int x=10,y=10,width=210,height=19+lines*12;
            draw.fill(x,y,x+width,y+height,0xB5100D14);
            draw.fill(x,y,x+2,y+height,0xFFFF702E);
            draw.drawTextWithShadow(client.textRenderer,Text.literal("PHOENIX  /  MODULES"),x+8,y+5,0xFFFF8A4C);
            int row=y+19;
            if(fpsEnabled){line(draw,client,"FPS",String.valueOf(client.getCurrentFps()),row);row+=12;}
            if(coordinatesEnabled){BlockPos p=client.player.getBlockPos();line(draw,client,"XYZ",p.getX()+"  "+p.getY()+"  "+p.getZ(),row);row+=12;}
            if(directionEnabled){line(draw,client,"FACING",client.player.getHorizontalFacing().getName().toUpperCase(),row);row+=12;}
            if(armorEnabled){int pieces=0,remaining=0;for(ItemStack stack:client.player.getArmorItems())if(!stack.isEmpty()){pieces++;if(stack.isDamageable())remaining+=Math.max(0,stack.getMaxDamage()-stack.getDamage());}line(draw,client,"ARMOR",pieces+" pieces · "+remaining+" durability",row);row+=12;}
            if(pingEnabled){var handler=client.getNetworkHandler();var entry=handler==null?null:handler.getPlayerListEntry(client.player.getUuid());line(draw,client,"PING",entry==null?"—":entry.getLatency()+" ms",row);row+=12;}
            if(speedEnabled){double kmh=client.player.getVelocity().horizontalLength()*20.0*3.6;line(draw,client,"SPEED",String.format(java.util.Locale.ROOT,"%.1f km/h",kmh),row);row+=12;}
            if(biomeEnabled){String biome=client.world.getBiome(client.player.getBlockPos()).getKey().map(k->k.getValue().getPath()).orElse("unknown");line(draw,client,"BIOME",biome,row);row+=12;}
            if(dimensionEnabled){line(draw,client,"DIMENSION",client.world.getRegistryKey().getValue().getPath(),row);row+=12;}
            if(worldTimeEnabled){long t=Math.floorMod(client.world.getTimeOfDay(),24000L);long h=((t+6000)/1000)%24;long m=(t%1000)*60/1000;line(draw,client,"WORLD TIME",String.format(java.util.Locale.ROOT,"%02d:%02d",h,m),row);row+=12;}
            if(clockEnabled){line(draw,client,"LOCAL",LocalTime.now().format(DateTimeFormatter.ofPattern("HH:mm:ss")),row);row+=12;}
            if(attackIndicatorEnabled){float cooldown=client.player.getAttackCooldownProgress(0.0f);line(draw,client,"ATTACK",String.format(java.util.Locale.ROOT,"%d%%",Math.round(cooldown*100)),row);row+=12;}
            if(potionEffectsEnabled){var effect=client.player.getStatusEffects().stream().findFirst().orElse(null);String value=effect==null?"None":effect.getEffectType().value().getName().getString()+" "+formatDuration(effect.getDuration());line(draw,client,"POTION",value,row);row+=12;}
            if(itemCounterEnabled){ItemStack held=client.player.getMainHandStack();int count=0;if(!held.isEmpty()){for(ItemStack stack:client.player.getInventory().main)if(ItemStack.areItemsAndComponentsEqual(stack,held))count+=stack.getCount();}line(draw,client,"HELD ITEM",held.isEmpty()?"Empty":held.getName().getString()+" ×"+count,row);row+=12;}
            if(keystrokesEnabled){var o=client.options;String keys=(o.forwardKey.isPressed()?"W":"·")+" "+(o.leftKey.isPressed()?"A":"·")+" "+(o.backKey.isPressed()?"S":"·")+" "+(o.rightKey.isPressed()?"D":"·")+"  "+(o.jumpKey.isPressed()?"JUMP":"");line(draw,client,"KEYS",keys,row);row+=12;}
            if(sessionTimeEnabled){long seconds=sessionTicks/20;line(draw,client,"SESSION",String.format(java.util.Locale.ROOT,"%02d:%02d:%02d",seconds/3600,(seconds/60)%60,seconds%60),row);}
        });
    }
    private static String formatDuration(int ticks){int seconds=Math.max(0,ticks)/20;return String.format(java.util.Locale.ROOT,"%d:%02d",seconds/60,seconds%60);}
    private static void line(net.minecraft.client.gui.DrawContext draw,MinecraftClient client,String label,String value,int y){
        draw.drawTextWithShadow(client.textRenderer,Text.literal(label),18,y,0xFFFFA06C);
        draw.drawTextWithShadow(client.textRenderer,Text.literal(value),88,y,0xFFF4EDF4);
    }
}
