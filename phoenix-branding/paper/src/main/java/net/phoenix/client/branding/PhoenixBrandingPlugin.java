package net.phoenix.client.branding;

import io.papermc.paper.event.player.AsyncChatEvent;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import io.papermc.paper.chat.ChatRenderer;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.plugin.messaging.PluginMessageListener;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;

import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class PhoenixBrandingPlugin extends JavaPlugin implements Listener, PluginMessageListener {
    private static final String CHANNEL = "phoenix:brand";
    private static final String PREFIX = "🐦‍🔥 ";
    private final Set<UUID> phoenixPlayers = ConcurrentHashMap.newKeySet();

    @Override public void onEnable() {
        getServer().getMessenger().registerIncomingPluginChannel(this, CHANNEL, this);
        getServer().getPluginManager().registerEvents(this, this);
        getLogger().info("Phoenix branding channel enabled. This marker is cosmetic only.");
    }

    @Override public void onDisable() {
        getServer().getMessenger().unregisterIncomingPluginChannel(this, CHANNEL, this);
        phoenixPlayers.clear();
    }

    @Override public void onPluginMessageReceived(String channel, Player player, byte[] message) {
        if (!CHANNEL.equals(channel) || message.length != 0) return;
        phoenixPlayers.add(player.getUniqueId());
        player.playerListName(Component.text(PREFIX, NamedTextColor.GOLD).append(Component.text(player.getName())));
        getLogger().fine("Phoenix client marker received from " + player.getName());
    }

    @EventHandler public void onQuit(PlayerQuitEvent event) {
        phoenixPlayers.remove(event.getPlayer().getUniqueId());
    }

    @EventHandler public void onChat(AsyncChatEvent event) {
        if (!phoenixPlayers.contains(event.getPlayer().getUniqueId())) return;
        ChatRenderer previous = event.renderer();
        event.renderer((source, sourceDisplayName, message, viewer) ->
                Component.text(PREFIX, NamedTextColor.GOLD).append(previous.render(source, sourceDisplayName, message, viewer)));
    }
}
