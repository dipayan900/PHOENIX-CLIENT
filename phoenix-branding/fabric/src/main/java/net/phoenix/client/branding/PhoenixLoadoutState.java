package net.phoenix.client.branding;

import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/** Validated, server-supplied cosmetic state shared by the client renderer. */
public final class PhoenixLoadoutState {
    private static final Set<String> ALLOWED = Set.of(
            "ember_cape","frost_cape","nebula_cape","royal_cape","celestial_wings",
            "inferno_wings","pixel_glider","dragon_wings","void_wings","frost_wings",
            "mechanical_wings","fairy_wings","bat_wings","phoenix_crown","starlight_halo",
            "cyber_visor","wizard_hat","cat_ears","horns","samurai_helmet","explorer_pack",
            "backpack_jet","backpack_turtle","shoulder_dragon","shoulder_cat","shoulder_owl",
            "aurora_cape","royal_guard_cape","shadow_cape","ocean_cape","galaxy_cape","crimson_cape","moss_cape",
            "crown_ice","astronaut_helmet","fox_mask","oni_mask","pixel_shades","gold_monocle","respirator",
            "halo_solar","halo_lunar","earrings_star","scarf_red","sword_sheath","shield_royal","shield_void",
            "dragon_tail","fox_tail","robot_arm","crystal_bracers",
            "wave","victory_pose","pixel_dance","salute","clap","laugh","shrug","bow",
            "spin","moonwalk","guitar","sit","meditate","dance2");
    private static final Map<UUID, Loadout> LOADOUTS = new ConcurrentHashMap<>();

    public record Loadout(String cape, String wings, String head, String back, String emote) {}
    private PhoenixLoadoutState() {}

    public static void accept(String packet) {
        if (packet == null || packet.length() > 512) return;
        String[] p = packet.split(";", -1);
        if (p.length != 6) return;
        try {
            UUID player = UUID.fromString(p[0]);
            LOADOUTS.put(player, new Loadout(valid(p[1]), valid(p[2]), valid(p[3]), valid(p[4]), valid(p[5])));
        } catch (IllegalArgumentException ignored) {
            // Malformed server data is ignored; it never becomes a file path or command.
        }
    }

    private static String valid(String id) { return id.isEmpty() || ALLOWED.contains(id) ? id : ""; }
    public static Loadout get(UUID player) { return LOADOUTS.get(player); }
    public static void clear() { LOADOUTS.clear(); }
}
