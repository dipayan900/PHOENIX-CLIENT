package net.kdt.pojavlaunch;

import android.content.Context;
import android.util.Log;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.Locale;
import java.util.jar.JarFile;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Installs the matching Phoenix Fabric mod in the selected game directory before launch. */
public final class PhoenixModInstaller {
    private static final String TAG = "PhoenixModInstaller";
    private static final Pattern MC_VERSION = Pattern.compile("(?<![0-9])1\\.21(?:\\.\\d+)?(?![0-9])");

    private PhoenixModInstaller() {}

    public static void install(Context context, File gameDir, String versionId) {
        if (context == null || gameDir == null || versionId == null) return;
        if (!versionId.toLowerCase(Locale.ROOT).contains("fabric")) return;

        Matcher matcher = MC_VERSION.matcher(versionId);
        if (!matcher.find()) {
            Log.w(TAG, "Could not detect Minecraft version from selected profile: " + versionId);
            return;
        }

        String mcVersion = matcher.group();
        String assetName = "phoenix/phoenix-branding-fabric-" + mcVersion + ".jar";
        File modsDir = new File(gameDir, "mods");
        File target = new File(modsDir, "phoenix-branding-fabric-" + mcVersion + ".jar");
        File temporary = new File(modsDir, target.getName() + ".tmp");

        try {
            if (!modsDir.exists() && !modsDir.mkdirs()) {
                throw new IllegalStateException("Cannot create profile mods directory: " + modsDir);
            }

            try (InputStream input = context.getAssets().open(assetName);
                 FileOutputStream output = new FileOutputStream(temporary)) {
                byte[] buffer = new byte[16384];
                int count;
                while ((count = input.read(buffer)) != -1) output.write(buffer, 0, count);
                output.getFD().sync();
            }

            try (JarFile jar = new JarFile(temporary)) {
                if (jar.getEntry("fabric.mod.json") == null) {
                    throw new IllegalStateException("Embedded file is not a Fabric mod: " + assetName);
                }
            }

            Files.move(temporary.toPath(), target.toPath(), StandardCopyOption.REPLACE_EXISTING);

            File[] oldBuilds = modsDir.listFiles(file ->
                    file.isFile()
                    && file.getName().toLowerCase(Locale.ROOT).startsWith("phoenix-branding-fabric-")
                    && file.getName().toLowerCase(Locale.ROOT).endsWith(".jar")
                    && !file.equals(target));
            if (oldBuilds != null) {
                for (File old : oldBuilds) {
                    if (!old.delete()) Log.w(TAG, "Could not remove previous Phoenix build: " + old);
                }
            }

            File marker = new File(new File(gameDir, "config"), "phoenix-client-loaded.txt");
            if (marker.exists() && !marker.delete()) Log.w(TAG, "Could not clear old Phoenix load marker.");
            Log.i(TAG, "Installed Phoenix Fabric mod for Minecraft " + mcVersion + " at " + target);
        } catch (Exception error) {
            if (temporary.exists()) temporary.delete();
            Log.e(TAG, "Phoenix Fabric mod installation failed for " + mcVersion, error);
        }
    }
}
