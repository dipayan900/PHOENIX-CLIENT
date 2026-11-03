package net.kdt.pojavlaunch;

import android.content.Context;
import net.kdt.pojavlaunch.modloaders.FabricVersion;
import net.kdt.pojavlaunch.modloaders.FabriclikeUtils;
import net.kdt.pojavlaunch.utils.DownloadUtils;
import net.kdt.pojavlaunch.value.launcherprofiles.MinecraftProfile;
import org.json.JSONObject;
import java.io.File;
import java.io.IOException;
import java.util.regex.Pattern;

/**
 * Converts a Vanilla 1.21.1-1.21.11 selection into an isolated managed Fabric
 * profile so Phoenix client entrypoints can load. The saved Vanilla profile is
 * cloned in memory and is not modified. This is Fabric-backed, not pure Vanilla.
 */
public final class PhoenixVanillaBootstrap {
    private static final Pattern SUPPORTED =
            Pattern.compile("^1\\.21\\.(?:[1-9]|1[01])$");
    private PhoenixVanillaBootstrap() {}

    public static final class Prepared {
        public final String versionId;
        public final MinecraftProfile profile;
        public final String loaderVersion;
        private Prepared(String id, MinecraftProfile p, String loader) {
            versionId = id; profile = p; loaderVersion = loader;
        }
    }

    public static boolean supports(String id) {
        return id != null && SUPPORTED.matcher(id).matches();
    }

    public static Prepared prepare(Context context, String vanillaVersion,
                                   MinecraftProfile originalProfile) throws IOException {
        if (context == null || originalProfile == null || !supports(vanillaVersion))
            throw new IllegalArgumentException("Phoenix managed bootstrap supports Minecraft 1.21.1-1.21.11.");

        FabricVersion[] available = FabriclikeUtils.FABRIC_UTILS.downloadLoaderVersions(vanillaVersion);
        if (available == null || available.length == 0)
            throw new IOException("No Fabric loader metadata is available for " + vanillaVersion);

        FabricVersion selected = null;
        for (FabricVersion candidate : available) {
            if (candidate != null && candidate.stable) { selected = candidate; break; }
        }
        if (selected == null)
            throw new IOException("No stable Fabric loader is published for " + vanillaVersion);

        String json = DownloadUtils.downloadString(
                FabriclikeUtils.FABRIC_UTILS.createJsonDownloadUrl(vanillaVersion, selected.version));
        final JSONObject metadata;
        try { metadata = new JSONObject(json); }
        catch (Exception e) { throw new IOException("Fabric returned invalid profile metadata.", e); }

        String generatedId = metadata.optString("id", "");
        if (generatedId.isEmpty() || !vanillaVersion.equals(metadata.optString("inheritsFrom", "")))
            throw new IOException("Fabric profile metadata does not match Minecraft " + vanillaVersion);

        File directory = new File(Tools.DIR_HOME_VERSION, generatedId);
        if (!directory.exists() && !directory.mkdirs())
            throw new IOException("Cannot create Phoenix runtime directory: " + directory);
        File target = new File(directory, generatedId + ".json");
        File temporary = new File(directory, generatedId + ".json.tmp");
        try {
            Tools.write(temporary.getAbsolutePath(), metadata.toString(2));
            if (target.exists() && !target.delete())
                throw new IOException("Cannot replace cached Fabric profile metadata.");
            if (!temporary.renameTo(target))
                throw new IOException("Cannot finalize Phoenix Fabric profile metadata.");
        } catch (Exception e) {
            temporary.delete();
            if (e instanceof IOException) throw (IOException)e;
            throw new IOException("Cannot save Phoenix Fabric profile metadata.", e);
        }

        MinecraftProfile isolated = Tools.GLOBAL_GSON.fromJson(
                Tools.GLOBAL_GSON.toJson(originalProfile), MinecraftProfile.class);
        isolated.lastVersionId = generatedId;
        return new Prepared(generatedId, isolated, selected.version);
    }
}
