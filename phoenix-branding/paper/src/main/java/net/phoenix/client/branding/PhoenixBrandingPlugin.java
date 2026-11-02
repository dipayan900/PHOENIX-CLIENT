package net.phoenix.client.branding;

import io.papermc.paper.event.player.AsyncChatEvent;
import io.papermc.paper.chat.ChatRenderer;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.plugin.messaging.PluginMessageListener;

import java.io.File;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.net.InetSocketAddress;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.HexFormat;
import java.util.concurrent.Executors;
import java.nio.charset.StandardCharsets;
import java.sql.*;
import java.time.LocalDate;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public final class PhoenixBrandingPlugin extends JavaPlugin
        implements Listener, PluginMessageListener, CommandExecutor {
    private static final String BRAND_CHANNEL = "phoenix:brand";
    private static final String LOADOUT_CHANNEL = "phoenix:loadout";
    private static final String PREFIX = "🐦‍🔥 ";
    private static final Map<String, Cosmetic> CATALOG = new LinkedHashMap<>();
    private final Set<UUID> phoenixPlayers = ConcurrentHashMap.newKeySet();
    private Connection database;
    private HttpServer apiServer;
    private final SecureRandom secureRandom = new SecureRandom();

    private record Cosmetic(String slot, int price, boolean emote) {}
    private record Loadout(String cape, String wings, String head, String back, String emote) {}

    static {
        add("ember_cape","cape",0,false); add("frost_cape","cape",250,false);
        add("nebula_cape","cape",650,false); add("royal_cape","cape",1200,false);
        add("celestial_wings","wings",700,false); add("inferno_wings","wings",1100,false);
        add("pixel_glider","wings",350,false); add("dragon_wings","wings",1500,false);
        add("void_wings","wings",850,false); add("frost_wings","wings",500,false);
        add("mechanical_wings","wings",900,false); add("fairy_wings","wings",450,false);
        add("bat_wings","wings",200,false); add("phoenix_crown","head",500,false);
        add("starlight_halo","head",300,false); add("cyber_visor","head",100,false);
        add("wizard_hat","head",150,false); add("cat_ears","head",120,false);
        add("horns","head",300,false); add("samurai_helmet","head",700,false);
        add("explorer_pack","back",150,false); add("backpack_jet","back",750,false);
        add("backpack_turtle","back",150,false); add("shoulder_dragon","back",950,false);
        add("shoulder_cat","back",350,false); add("shoulder_owl","back",600,false);
        add("aurora_cape","cape",800,false); add("royal_guard_cape","cape",400,false);
        add("shadow_cape","cape",700,false); add("ocean_cape","cape",350,false);
        add("galaxy_cape","cape",1400,false); add("crimson_cape","cape",650,false);
        add("moss_cape","cape",100,false);
        add("crown_ice","head",350,false); add("astronaut_helmet","head",650,false);
        add("fox_mask","head",300,false); add("oni_mask","head",550,false);
        add("pixel_shades","head",100,false); add("gold_monocle","head",250,false);
        add("respirator","head",350,false); add("halo_solar","head",600,false);
        add("halo_lunar","head",350,false); add("earrings_star","head",100,false);
        add("scarf_red","head",120,false);
 add("dragon_tail","back",500,false);
        add("fox_tail","back",300,false); add("robot_arm","back",650,false);
        add("crystal_bracers","back",300,false);
        add("wave","emote",0,true); add("victory_pose","emote",250,true);
        add("pixel_dance","emote",450,true); add("salute","emote",100,true);
        add("clap","emote",100,true); add("laugh","emote",200,true);
        add("shrug","emote",100,true); add("bow","emote",220,true);
        add("spin","emote",400,true); add("moonwalk","emote",450,true);
        add("guitar","emote",280,true); add("sit","emote",100,true);
        add("meditate","emote",420,true); add("dance2","emote",480,true);
    }
    private static void add(String id, String slot, int price, boolean emote) {
        CATALOG.put(id, new Cosmetic(slot, price, emote));
    }

    @Override public void onEnable() {
        saveDefaultConfig();
        try {
            if (!getDataFolder().exists() && !getDataFolder().mkdirs()) throw new SQLException("Cannot create plugin data directory");
            database = DriverManager.getConnection("jdbc:sqlite:" + new File(getDataFolder(), "cosmetics.db").getAbsolutePath());
            try (Statement s = database.createStatement()) {
                s.executeUpdate("PRAGMA journal_mode=WAL");
                s.executeUpdate("CREATE TABLE IF NOT EXISTS wallets(uuid TEXT PRIMARY KEY, coins INTEGER NOT NULL DEFAULT 100, last_daily TEXT)");
                s.executeUpdate("CREATE TABLE IF NOT EXISTS owned(uuid TEXT NOT NULL, cosmetic_id TEXT NOT NULL, PRIMARY KEY(uuid, cosmetic_id))");
                s.executeUpdate("CREATE TABLE IF NOT EXISTS loadouts(uuid TEXT PRIMARY KEY, cape TEXT NOT NULL DEFAULT '', wings TEXT NOT NULL DEFAULT '', head TEXT NOT NULL DEFAULT '', back TEXT NOT NULL DEFAULT '', emote TEXT NOT NULL DEFAULT '')");
                s.executeUpdate("CREATE TABLE IF NOT EXISTS api_tokens(uuid TEXT PRIMARY KEY, token_hash TEXT NOT NULL)");
            }
        } catch (SQLException e) {
            getLogger().severe("Cosmetics database could not start: " + e.getMessage());
            getServer().getPluginManager().disablePlugin(this);
            return;
        }
        getServer().getMessenger().registerIncomingPluginChannel(this, BRAND_CHANNEL, this);
        getServer().getMessenger().registerOutgoingPluginChannel(this, LOADOUT_CHANNEL);
        getServer().getPluginManager().registerEvents(this, this);
        Objects.requireNonNull(getCommand("phoenix"), "phoenix command missing from plugin.yml").setExecutor(this);
        startApiServer();
        for (Player online : Bukkit.getOnlinePlayers()) applyBranding(online);
        getLogger().info("Phoenix cosmetics database ready. Owner privileges are verified against configured Minecraft UUID.");
    }

    @Override public void onDisable() {
        getServer().getMessenger().unregisterIncomingPluginChannel(this, BRAND_CHANNEL, this);
        getServer().getMessenger().unregisterOutgoingPluginChannel(this, LOADOUT_CHANNEL);
        phoenixPlayers.clear();
        if (apiServer != null) apiServer.stop(0);
        try { if (database != null && !database.isClosed()) database.close(); }
        catch (SQLException e) { getLogger().warning("Could not close cosmetics database: " + e.getMessage()); }
    }

    private synchronized void ensurePlayer(UUID id) throws SQLException {
        try (PreparedStatement p = database.prepareStatement("INSERT OR IGNORE INTO wallets(uuid,coins) VALUES(?,100)")) {
            p.setString(1,id.toString()); p.executeUpdate();
        }
        try (PreparedStatement p = database.prepareStatement("INSERT OR IGNORE INTO owned(uuid,cosmetic_id) VALUES(?, 'wave')")) {
            p.setString(1,id.toString()); p.executeUpdate();
        }
        try (PreparedStatement p = database.prepareStatement("INSERT OR IGNORE INTO loadouts(uuid) VALUES(?)")) {
            p.setString(1,id.toString()); p.executeUpdate();
        }
    }

    private boolean isOwner(UUID id) {
        String configured = getConfig().getString("owner-uuid", "").trim();
        return !configured.isEmpty() && configured.equalsIgnoreCase(id.toString());
    }

    private synchronized int balance(UUID id) throws SQLException {
        ensurePlayer(id);
        try (PreparedStatement p=database.prepareStatement("SELECT coins FROM wallets WHERE uuid=?")) {
            p.setString(1,id.toString()); try(ResultSet r=p.executeQuery()){return r.next()?r.getInt(1):0;}
        }
    }

    private synchronized boolean owns(UUID id, String cosmetic) throws SQLException {
        if (isOwner(id)) return true;
        try (PreparedStatement p=database.prepareStatement("SELECT 1 FROM owned WHERE uuid=? AND cosmetic_id=?")) {
            p.setString(1,id.toString()); p.setString(2,cosmetic);
            try(ResultSet r=p.executeQuery()){return r.next();}
        }
    }

    private synchronized Loadout loadout(UUID id) throws SQLException {
        ensurePlayer(id);
        try (PreparedStatement p=database.prepareStatement("SELECT cape,wings,head,back,emote FROM loadouts WHERE uuid=?")) {
            p.setString(1,id.toString());
            try(ResultSet r=p.executeQuery()){return r.next()?new Loadout(r.getString(1),r.getString(2),r.getString(3),r.getString(4),r.getString(5)):new Loadout("","","","","");}
        }
    }

    private synchronized void broadcastLoadouts() {
        for (Player viewer : Bukkit.getOnlinePlayers()) {
            // Only send the custom S2C channel to clients that registered the Phoenix protocol.
            if (!phoenixPlayers.contains(viewer.getUniqueId())) continue;
            for (Player subject : Bukkit.getOnlinePlayers()) {
                try {
                    Loadout l=loadout(subject.getUniqueId());
                    String packet=subject.getUniqueId()+";"+l.cape()+";"+l.wings()+";"+l.head()+";"+l.back()+";"+l.emote();
                    byte[] body=packet.getBytes(StandardCharsets.UTF_8);
                    // Minecraft STRING fields are VarInt-length-prefixed UTF-8, matching PacketCodecs.STRING.
                    java.io.ByteArrayOutputStream encoded=new java.io.ByteArrayOutputStream(body.length+5);
                    int remaining=body.length;
                    while((remaining & 0xFFFFFF80)!=0){encoded.write((remaining & 0x7F)|0x80);remaining >>>=7;}
                    encoded.write(remaining);
                    encoded.writeBytes(body);
                    viewer.sendPluginMessage(this,LOADOUT_CHANNEL,encoded.toByteArray());
                } catch(SQLException e) { getLogger().warning("Could not sync cosmetics for "+subject.getUniqueId()); }
            }
        }
    }


    private static String sha256(String value) throws Exception {
        byte[] digest = MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8));
        return HexFormat.of().formatHex(digest);
    }

    private void startApiServer() {
        if (!getConfig().getBoolean("api-enabled", true)) return;
        int port = getConfig().getInt("api-port", 8765);
        try {
            apiServer = HttpServer.create(new InetSocketAddress(getConfig().getString("api-bind", "0.0.0.0"), port), 32);
            apiServer.createContext("/api/inventory", e -> handleApi(e, "inventory"));
            apiServer.createContext("/api/equip", e -> handleApi(e, "equip"));
            apiServer.createContext("/api/buy", e -> handleApi(e, "buy"));
            apiServer.createContext("/api/emote", e -> handleApi(e, "emote"));
            apiServer.createContext("/api/unequip", e -> handleApi(e, "unequip"));
            apiServer.setExecutor(Executors.newCachedThreadPool(r -> { Thread t = new Thread(r, "Phoenix-Launcher-API"); t.setDaemon(true); return t; }));
            apiServer.start();
            getLogger().warning("Phoenix launcher API listening on port " + port + ". HTTP is unencrypted: use trusted LAN only and never port-forward this API.");
        } catch (Exception e) {
            getLogger().severe("Could not start launcher sync API: " + e.getMessage());
        }
    }

    private synchronized UUID authenticateApi(HttpExchange exchange) throws Exception {
        String header = exchange.getRequestHeaders().getFirst("Authorization");
        if (header == null || !header.startsWith("Bearer ")) return null;
        String hash = sha256(header.substring(7).trim());
        try (PreparedStatement q = database.prepareStatement("SELECT uuid FROM api_tokens WHERE token_hash=?")) {
            q.setString(1, hash);
            try (ResultSet rs = q.executeQuery()) { return rs.next() ? UUID.fromString(rs.getString(1)) : null; }
        }
    }

    private void reply(HttpExchange exchange, int status, String body) throws java.io.IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "text/plain; charset=utf-8");
        exchange.getResponseHeaders().set("Cache-Control", "no-store");
        exchange.sendResponseHeaders(status, bytes.length);
        try (var out = exchange.getResponseBody()) { out.write(bytes); }
    }

    private String readBody(HttpExchange exchange) throws java.io.IOException {
        byte[] bytes = exchange.getRequestBody().readNBytes(256);
        if (bytes.length >= 256) throw new java.io.IOException("Request too large");
        return new String(bytes, StandardCharsets.UTF_8).trim();
    }

    private void handleApi(HttpExchange exchange, String action) throws java.io.IOException {
        try {
            UUID player = authenticateApi(exchange);
            if (player == null) { reply(exchange, 401, "Unauthorized. Run /phoenix link in game."); return; }
            if (action.equals("inventory")) {
                if (!exchange.getRequestMethod().equals("GET")) { reply(exchange, 405, "Use GET."); return; }
                ensurePlayer(player);
                Loadout l = loadout(player);
                List<String> ids = new ArrayList<>();
                try (PreparedStatement q = database.prepareStatement("SELECT cosmetic_id FROM owned WHERE uuid=? ORDER BY cosmetic_id")) {
                    q.setString(1, player.toString());
                    try (ResultSet rs = q.executeQuery()) { while (rs.next()) ids.add(rs.getString(1)); }
                }
                String response = "uuid=" + player + "\ncoins=" + balance(player) + "\nowner=" + isOwner(player)
                    + "\ncatalog=" + String.join(",", CATALOG.keySet()) + "\nowned=" + String.join(",", ids) + "\ncape=" + l.cape() + "\nwings=" + l.wings()
                    + "\nhead=" + l.head() + "\nback=" + l.back() + "\nemote=" + l.emote();
                reply(exchange, 200, response);
                return;
            }
            if (!exchange.getRequestMethod().equals("POST")) { reply(exchange, 405, "Use POST."); return; }
            String body = readBody(exchange);
            if (action.equals("buy")) {
                String id = body.replaceFirst("^id=", "").toLowerCase(Locale.ROOT);
                Cosmetic c = CATALOG.get(id);
                if (c == null || !id.matches("[a-z0-9_]{1,48}")) { reply(exchange, 400, "Invalid cosmetic id."); return; }
                if (owns(player, id)) { reply(exchange, 200, "Already owned."); return; }
                synchronized (this) {
                    database.setAutoCommit(false);
                    try {
                        int coins = balance(player);
                        if (coins < c.price()) { database.rollback(); reply(exchange, 402, "Not enough server coins."); return; }
                        try (PreparedStatement u = database.prepareStatement("UPDATE wallets SET coins=coins-? WHERE uuid=?")) {
                            u.setInt(1, c.price()); u.setString(2, player.toString()); u.executeUpdate();
                        }
                        try (PreparedStatement i = database.prepareStatement("INSERT OR IGNORE INTO owned(uuid,cosmetic_id) VALUES(?,?)")) {
                            i.setString(1, player.toString()); i.setString(2, id); i.executeUpdate();
                        }
                        database.commit();
                    } catch (Exception e) { database.rollback(); throw e; }
                    finally { database.setAutoCommit(true); }
                }
                reply(exchange, 200, "Unlocked " + id);
                return;
            }
            if (action.equals("emote")) {
                String id = body.replaceFirst("^id=", "").toLowerCase(Locale.ROOT);
                Cosmetic c = CATALOG.get(id);
                if (c == null || !c.emote() || !id.matches("[a-z0-9_]{1,48}")) { reply(exchange, 400, "Invalid emote id."); return; }
                if (!owns(player, id)) { reply(exchange, 403, "Emote is not owned by this account."); return; }
                synchronized (this) {
                    try (PreparedStatement u = database.prepareStatement("UPDATE loadouts SET emote=? WHERE uuid=?")) {
                        u.setString(1, id); u.setString(2, player.toString()); u.executeUpdate();
                    }
                }
                Bukkit.getScheduler().runTask(this, this::broadcastLoadouts);
                reply(exchange, 200, "Playing emote " + id);
                return;
            }
            if (action.equals("equip")) {
                String id = body.replaceFirst("^id=", "").toLowerCase(Locale.ROOT);
                Cosmetic c = CATALOG.get(id);
                if (c == null || c.emote() || !id.matches("[a-z0-9_]{1,48}")) { reply(exchange, 400, "Invalid cosmetic id."); return; }
                if (!owns(player, id)) { reply(exchange, 403, "Cosmetic is not owned by this account."); return; }
                synchronized (this) {
                    try (PreparedStatement u = database.prepareStatement("UPDATE loadouts SET " + c.slot() + "=? WHERE uuid=?")) {
                        u.setString(1, id); u.setString(2, player.toString()); u.executeUpdate();
                    }
                }
                Bukkit.getScheduler().runTask(this, this::broadcastLoadouts);
                reply(exchange, 200, "Equipped " + id);
                return;
            }
            if (action.equals("unequip")) {
                String slot = body.replaceFirst("^slot=", "").toLowerCase(Locale.ROOT);
                if (!Set.of("cape", "wings", "head", "back", "emote").contains(slot)) { reply(exchange, 400, "Invalid slot."); return; }
                synchronized (this) {
                    try (PreparedStatement u = database.prepareStatement("UPDATE loadouts SET " + slot + "='' WHERE uuid=?")) {
                        u.setString(1, player.toString()); u.executeUpdate();
                    }
                }
                Bukkit.getScheduler().runTask(this, this::broadcastLoadouts);
                reply(exchange, 200, "Unequipped " + slot);
                return;
            }
            reply(exchange, 404, "Unknown endpoint.");
        } catch (Exception e) {
            getLogger().warning("Launcher API request failed: " + e.getMessage());
            try { reply(exchange, 500, "Server error."); } catch (Exception ignored) { }
        } finally { exchange.close(); }
    }

    private void createLauncherLink(Player player) throws SQLException {
        byte[] random = new byte[32];
        secureRandom.nextBytes(random);
        String token = Base64.getUrlEncoder().withoutPadding().encodeToString(random);
        String tokenHash;
        try { tokenHash = sha256(token); }
        catch (Exception e) { throw new SQLException("SHA-256 is unavailable", e); }
        synchronized (this) {
            try (PreparedStatement p = database.prepareStatement("INSERT OR REPLACE INTO api_tokens(uuid,token_hash) VALUES(?,?)")) {
                p.setString(1, player.getUniqueId().toString());
                p.setString(2, tokenHash);
                p.executeUpdate();
            }
        }
        player.sendMessage(Component.text("Phoenix launcher link token (keep private):", NamedTextColor.GREEN));
        player.sendMessage(Component.text(token, NamedTextColor.GOLD));
        player.sendMessage(Component.text("Enter this in Phoenix Cosmetics. /phoenix unlink revokes it. HTTP API is for trusted LAN only.", NamedTextColor.YELLOW));
    }

    private void applyBranding(Player player) {
        boolean enabled = getConfig().getBoolean("branding-enabled", true);
        if (enabled) {
            player.playerListName(Component.text(PREFIX, NamedTextColor.GOLD).append(Component.text(player.getName())));
            player.customName(Component.text(PREFIX, NamedTextColor.GOLD).append(Component.text(player.getName())));
            player.setCustomNameVisible(true);
        } else {
            player.playerListName(Component.text(player.getName()));
            player.customName(null);
            player.setCustomNameVisible(false);
        }
    }

    @Override public void onPluginMessageReceived(String channel, Player player, byte[] message) {
        if (!BRAND_CHANNEL.equals(channel) || message.length != 0) return;
        phoenixPlayers.add(player.getUniqueId());
        applyBranding(player);
    }

    @EventHandler public void onJoin(PlayerJoinEvent event) {
        Player p=event.getPlayer();
        try { ensurePlayer(p.getUniqueId()); }
        catch(SQLException e){p.sendMessage(Component.text("Phoenix cosmetics database is unavailable.",NamedTextColor.RED));return;}
        applyBranding(p);
        Bukkit.getScheduler().runTaskLater(this,this::broadcastLoadouts,20L);
    }

    @EventHandler public void onQuit(PlayerQuitEvent event) { phoenixPlayers.remove(event.getPlayer().getUniqueId()); }

    @EventHandler public void onChat(AsyncChatEvent event) {
        if (!getConfig().getBoolean("branding-enabled", true)) return;
        ChatRenderer previous=event.renderer();
        event.renderer((source,display,message,viewer)->Component.text(PREFIX,NamedTextColor.GOLD)
                .append(previous.render(source,display,message,viewer)));
    }

    @Override public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) { sender.sendMessage("Run this command in-game."); return true; }
        try {
            ensurePlayer(player.getUniqueId());
            if (args.length > 0 && args[0].equalsIgnoreCase("branding")) {
                if (!sender.hasPermission("phoenix.admin")) { sender.sendMessage(Component.text("You need phoenix.admin permission.", NamedTextColor.RED)); return true; }
                if (args.length < 2) { sender.sendMessage("Usage: /phoenix branding <on|off|toggle|status>"); return true; }
                String mode = args[1].toLowerCase(Locale.ROOT);
                boolean enabled = getConfig().getBoolean("branding-enabled", true);
                if (mode.equals("on")) enabled = true;
                else if (mode.equals("off")) enabled = false;
                else if (mode.equals("toggle")) enabled = !enabled;
                else if (!mode.equals("status")) { sender.sendMessage("Use on, off, toggle or status."); return true; }
                getConfig().set("branding-enabled", enabled);
                saveConfig();
                for (Player online : Bukkit.getOnlinePlayers()) applyBranding(online);
                sender.sendMessage(Component.text("Phoenix in-world and tab branding: " + (enabled ? "ON" : "OFF"), NamedTextColor.GOLD));
                return true;
            }
            if (args.length > 0 && args[0].equalsIgnoreCase("link")) {
                createLauncherLink(player);
                return true;
            }
            if (args.length > 0 && args[0].equalsIgnoreCase("unlink")) {
                try (PreparedStatement q = database.prepareStatement("DELETE FROM api_tokens WHERE uuid=?")) {
                    q.setString(1, player.getUniqueId().toString());
                    q.executeUpdate();
                }
                player.sendMessage(Component.text("Launcher token revoked.", NamedTextColor.YELLOW));
                return true;
            }
            if (args.length == 0 || args[0].equalsIgnoreCase("help")) {
                player.sendMessage(Component.text("Phoenix · /phoenix balance | claim | shop | buy <id> | equip <id> | unequip <slot> | wardrobe | emote <id> | link | unlink | branding <on|off|toggle>",NamedTextColor.GOLD));
                return true;
            }
            switch(args[0].toLowerCase(Locale.ROOT)) {
                case "balance" -> player.sendMessage(Component.text("Phoenix Coins: "+balance(player.getUniqueId()),NamedTextColor.GOLD));
                case "claim" -> claimDaily(player);
                case "shop" -> {
                    player.sendMessage(Component.text("Phoenix Cosmetic Catalogue:",NamedTextColor.GOLD));
                    CATALOG.forEach((id,c)->player.sendMessage(Component.text(id+" · "+c.slot()+" · "+(c.price()==0?"FREE":c.price()+" coins"))));
                }
                case "buy" -> buy(player,args);
                case "equip" -> equip(player,args);
                case "unequip" -> unequip(player,args);
                case "wardrobe" -> wardrobe(player);
                case "emote" -> playEmote(player,args);
                case "owner" -> player.sendMessage(Component.text(isOwner(player.getUniqueId())?"Verified Phoenix owner entitlement.":"No owner entitlement on this server.",isOwner(player.getUniqueId())?NamedTextColor.GREEN:NamedTextColor.RED));
                default -> player.sendMessage(Component.text("Unknown command. Use /phoenix help.",NamedTextColor.RED));
            }
        } catch(SQLException e) {
            player.sendMessage(Component.text("Cosmetics database error. Ask the server operator to check the plugin log.",NamedTextColor.RED));
            getLogger().warning("Database error for "+player.getUniqueId()+": "+e.getMessage());
        }
        return true;
    }

    private void claimDaily(Player p) throws SQLException {
        synchronized(this) {
            String today=LocalDate.now().toString();
            try(PreparedStatement q=database.prepareStatement("SELECT last_daily FROM wallets WHERE uuid=?")) {
                q.setString(1,p.getUniqueId().toString());
                try(ResultSet r=q.executeQuery()) {
                    if(r.next() && today.equals(r.getString(1))) { p.sendMessage(Component.text("Daily coins already claimed.",NamedTextColor.YELLOW)); return; }
                }
            }
            try(PreparedStatement u=database.prepareStatement("UPDATE wallets SET coins=coins+100,last_daily=? WHERE uuid=?")) {
                u.setString(1,today);u.setString(2,p.getUniqueId().toString());u.executeUpdate();
            }
        }
        p.sendMessage(Component.text("100 free Phoenix Coins added. Balance: "+balance(p.getUniqueId()),NamedTextColor.GREEN));
    }

    private void buy(Player p,String[] args) throws SQLException {
        if(args.length<2){p.sendMessage("Usage: /phoenix buy <cosmetic-id>");return;}
        String id=args[1].toLowerCase(Locale.ROOT); Cosmetic c=CATALOG.get(id);
        if(c==null){p.sendMessage(Component.text("Unknown cosmetic. Use /phoenix shop.",NamedTextColor.RED));return;}
        if(owns(p.getUniqueId(),id)){p.sendMessage(Component.text("Already owned.",NamedTextColor.YELLOW));return;}
        synchronized(this) {
            database.setAutoCommit(false);
            try {
                int coins=balance(p.getUniqueId());
                if(coins<c.price()){database.rollback();p.sendMessage(Component.text("Not enough coins. Use /phoenix claim for the daily free reward.",NamedTextColor.RED));return;}
                try(PreparedStatement u=database.prepareStatement("UPDATE wallets SET coins=coins-? WHERE uuid=?")){u.setInt(1,c.price());u.setString(2,p.getUniqueId().toString());u.executeUpdate();}
                try(PreparedStatement i=database.prepareStatement("INSERT OR IGNORE INTO owned(uuid,cosmetic_id) VALUES(?,?)")){i.setString(1,p.getUniqueId().toString());i.setString(2,id);i.executeUpdate();}
                database.commit();
            } catch(SQLException e){database.rollback();throw e;}
            finally{database.setAutoCommit(true);}
        }
        p.sendMessage(Component.text("Unlocked "+id+". Balance: "+balance(p.getUniqueId()),NamedTextColor.GREEN));
    }

    private void equip(Player p,String[] args) throws SQLException {
        if(args.length<2){p.sendMessage("Usage: /phoenix equip <cosmetic-id>");return;}
        String id=args[1].toLowerCase(Locale.ROOT);Cosmetic c=CATALOG.get(id);
        if(c==null || c.emote()){p.sendMessage(Component.text("That is not an equip slot cosmetic.",NamedTextColor.RED));return;}
        if(!owns(p.getUniqueId(),id)){p.sendMessage(Component.text("You do not own that cosmetic. Use /phoenix buy "+id,NamedTextColor.RED));return;}
        String column=c.slot();
        try(PreparedStatement u=database.prepareStatement("UPDATE loadouts SET "+column+"=? WHERE uuid=?")){u.setString(1,id);u.setString(2,p.getUniqueId().toString());u.executeUpdate();}
        p.sendMessage(Component.text("Equipped "+id+". Other Phoenix clients on this server will receive your loadout.",NamedTextColor.GREEN));
        broadcastLoadouts();
    }

    private void unequip(Player p,String[] args) throws SQLException {
        if(args.length<2){p.sendMessage("Usage: /phoenix unequip <cape|wings|head|back|emote>");return;}
        String slot=args[1].toLowerCase(Locale.ROOT);
        if(!Set.of("cape","wings","head","back","emote").contains(slot)){p.sendMessage(Component.text("Unknown slot.",NamedTextColor.RED));return;}
        try(PreparedStatement u=database.prepareStatement("UPDATE loadouts SET "+slot+"='' WHERE uuid=?")){u.setString(1,p.getUniqueId().toString());u.executeUpdate();}
        broadcastLoadouts();p.sendMessage(Component.text("Cleared "+slot+" slot.",NamedTextColor.GREEN));
    }

    private void wardrobe(Player p) throws SQLException {
        p.sendMessage(Component.text("Your Phoenix wardrobe:",NamedTextColor.GOLD));
        try(PreparedStatement q=database.prepareStatement("SELECT cosmetic_id FROM owned WHERE uuid=? ORDER BY cosmetic_id")) {
            q.setString(1,p.getUniqueId().toString());
            try(ResultSet r=q.executeQuery()){while(r.next())p.sendMessage(Component.text("• "+r.getString(1)));}
        }
        if(isOwner(p.getUniqueId()))p.sendMessage(Component.text("Owner entitlement: all catalogue cosmetics unlocked.",NamedTextColor.GREEN));
    }

    private void playEmote(Player p,String[] args) throws SQLException {
        if(args.length>=2 && args[1].equalsIgnoreCase("stop")) {
            try(PreparedStatement u=database.prepareStatement("UPDATE loadouts SET emote='' WHERE uuid=?")) {
                u.setString(1,p.getUniqueId().toString()); u.executeUpdate();
            }
            broadcastLoadouts();
            p.sendMessage(Component.text("Emote stopped.",NamedTextColor.GREEN));
            return;
        }
        if(args.length<2){p.sendMessage("Usage: /phoenix emote <emote-id|stop>");return;}
        String id=args[1].toLowerCase(Locale.ROOT);Cosmetic c=CATALOG.get(id);
        if(c==null || !c.emote()){p.sendMessage(Component.text("Unknown emote.",NamedTextColor.RED));return;}
        if(!owns(p.getUniqueId(),id)){p.sendMessage(Component.text("You do not own that emote.",NamedTextColor.RED));return;}
        try(PreparedStatement u=database.prepareStatement("UPDATE loadouts SET emote=? WHERE uuid=?")){u.setString(1,id);u.setString(2,p.getUniqueId().toString());u.executeUpdate();}
        broadcastLoadouts();p.sendMessage(Component.text("Emote request sent to compatible Phoenix clients: "+id,NamedTextColor.GREEN));
    }
}
