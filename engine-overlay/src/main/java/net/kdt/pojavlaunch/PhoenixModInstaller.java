package net.kdt.pojavlaunch;

import android.content.Context;
import android.widget.Toast;
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

/**
 * Installs the currently available Phoenix Fabric adapter before game launch.
 * Vanilla and other-version adapters are intentionally rejected until their
 * launcher-managed integration artifacts exist; no user profile is modified.
 */
public final class PhoenixModInstaller {
    private static final String TAG = "PhoenixModInstaller";
    private static final Pattern MC_VERSION =
            Pattern.compile("(?<![0-9])1\\.21(?:\\.\\d+)?(?![0-9])");

    private PhoenixModInstaller() {}

    private static void notify(Context context, String message) {
        if (context == null) return;
        try {
            Toast.makeText(context.getApplicationContext(), message, Toast.LENGTH_LONG).show();
        } catch (Exception ignored) {
            Log.w(TAG, "Could not display Phoenix install status.");
        }
    }

    public static void install(Context context, File gameDir, String versionId) {
        if (context == null || gameDir == null || versionId == null) {
            Log.e(TAG, "Phoenix installer received a null launch argument.");
            return;
        }

        String normalized = versionId.toLowerCase(Locale.ROOT);
        if (!normalized.contains("fabric")) {
            String message = "Phoenix automatic Vanilla integration is not available yet. This profile was not modified: " + versionId;
            Log.w(TAG, message);
            notify(context, message);
            return;
        }

        Matcher matcher = MC_VERSION.matcher(versionId);
        if (!matcher.find()) {
            String message = "Phoenix integration is not available for this profile yet. This profile was not modified: " + versionId;
            Log.e(TAG, message);
            notify(context, message);
            return;
        }

        String mcVersion = matcher.group();
        if (!"1.21.1".equals(mcVersion)) {
            String message = "Phoenix Fabric integration for Minecraft " + mcVersion + " is not available yet. This profile was not modified.";
            Log.e(TAG, message);
            notify(context, message);
            return;
        }

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
                if (jar.getEntry("fabric.mod.json") == null
                        || jar.getEntry("phoenix_branding.mixins.json") == null
                        || jar.getEntry("net/phoenix/client/branding/PhoenixBrandingClient.class") == null
                        || jar.getEntry("net/phoenix/client/branding/mixin/TitleScreenMixin.class") == null) {
                    throw new IllegalStateException("Bundled Phoenix JAR is missing required Fabric metadata or client classes.");
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

            File configDir = new File(gameDir, "config");
            if (!configDir.exists() && !configDir.mkdirs()) {
                throw new IllegalStateException("Cannot create profile config directory: " + configDir);
            }
            File marker = new File(configDir, "phoenix-client-loaded.txt");
            if (marker.exists() && !marker.delete()) {
                Log.w(TAG, "Could not clear old Phoenix load marker.");
            }
            File status = new File(configDir, "phoenix-client-install-status.txt");
            Files.writeString(status.toPath(),
                    "INSTALLED\nMinecraft: " + mcVersion + "\nProfile: " + versionId
                            + "\nJAR: " + target.getAbsolutePath() + "\n");

            Log.i(TAG, "Installed Phoenix Fabric mod for Minecraft " + mcVersion + " at " + target);
            notify(context, "Phoenix client installed for Fabric 1.21.1. Starting game...");
        } catch (Exception error) {
            if (temporary.exists()) temporary.delete();
            String message = "Phoenix client installation failed: " + error.getMessage();
            Log.e(TAG, message, error);
            notify(context, message);
        }
    }
}
