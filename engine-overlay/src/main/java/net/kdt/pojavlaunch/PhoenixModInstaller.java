package net.kdt.pojavlaunch;

import android.content.Context;
import android.util.Log;
import android.widget.Toast;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.Locale;
import java.util.jar.JarFile;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Installs the exact Phoenix adapter matching the selected Fabric-backed runtime. */
public final class PhoenixModInstaller {
    private static final String TAG = "PhoenixModInstaller";
    private static final Pattern MC_VERSION =
            Pattern.compile("(?<![0-9])1\\.21\\.(?:[1-9]|1[01])(?![0-9])");
    private PhoenixModInstaller() {}

    private static void notify(Context context, String message) {
        if (context == null) return;
        try { Toast.makeText(context.getApplicationContext(), message, Toast.LENGTH_LONG).show(); }
        catch (Exception ignored) { Log.w(TAG, "Could not display Phoenix install status."); }
    }

    public static void install(Context context, File gameDir, String versionId) {
        if (context == null || gameDir == null || versionId == null) {
            Log.e(TAG, "Phoenix installer received a null launch argument."); return;
        }
        if (!versionId.toLowerCase(Locale.ROOT).contains("fabric")) {
            String message = "Phoenix runtime was not prepared. Select Minecraft 1.21.1-1.21.11.";
            Log.e(TAG, message + " Profile: " + versionId); notify(context, message); return;
        }
        Matcher matcher = MC_VERSION.matcher(versionId);
        if (!matcher.find()) {
            String message = "Phoenix supports Minecraft 1.21.1-1.21.11 only. Profile unchanged: " + versionId;
            Log.e(TAG, message); notify(context, message); return;
        }

        String mcVersion = matcher.group();
        String fileName = "phoenix-branding-fabric-" + mcVersion + ".jar";
        File modsDir = new File(gameDir, "mods");
        File target = new File(modsDir, fileName);
        File temporary = new File(modsDir, fileName + ".tmp");
        try {
            if (!modsDir.exists() && !modsDir.mkdirs())
                throw new IllegalStateException("Cannot create profile mods directory: " + modsDir);
            try (InputStream in = context.getAssets().open("phoenix/" + fileName);
                 FileOutputStream out = new FileOutputStream(temporary)) {
                byte[] buffer = new byte[16384]; int count;
                while ((count = in.read(buffer)) != -1) out.write(buffer, 0, count);
                out.getFD().sync();
            }
            try (JarFile jar = new JarFile(temporary)) {
                if (jar.getEntry("fabric.mod.json") == null
                        || jar.getEntry("phoenix_branding.mixins.json") == null
                        || jar.getEntry("net/phoenix/client/branding/PhoenixBrandingClient.class") == null
                        || jar.getEntry("net/phoenix/client/branding/mixin/TitleScreenMixin.class") == null
                        || jar.getEntry("net/phoenix/client/branding/mixin/SplashOverlayMixin.class") == null)
                    throw new IllegalStateException("Bundled Phoenix adapter is incomplete.");
            }
            Files.move(temporary.toPath(), target.toPath(), StandardCopyOption.REPLACE_EXISTING);
            File[] old = modsDir.listFiles(f -> f.isFile()
                    && f.getName().toLowerCase(Locale.ROOT).startsWith("phoenix-branding-fabric-")
                    && f.getName().toLowerCase(Locale.ROOT).endsWith(".jar") && !f.equals(target));
            if (old != null) for (File f : old) if (!f.delete()) Log.w(TAG, "Could not remove old Phoenix adapter: " + f);

            File config = new File(gameDir, "config");
            if (!config.exists() && !config.mkdirs()) throw new IllegalStateException("Cannot create config directory.");
            File marker = new File(config, "phoenix-client-loaded.txt");
            if (marker.exists() && !marker.delete()) Log.w(TAG, "Could not clear previous Phoenix marker.");
            Files.writeString(new File(config, "phoenix-client-install-status.txt").toPath(),
                    "INSTALLED\nMinecraft: " + mcVersion + "\nProfile: " + versionId
                            + "\nJAR: " + target.getAbsolutePath() + "\n");
            Log.i(TAG, "Installed Phoenix adapter for " + mcVersion + " at " + target);
            notify(context, "Phoenix client installed for Minecraft " + mcVersion + ".");
        } catch (Exception e) {
            if (temporary.exists()) temporary.delete();
            String message = "Phoenix adapter install failed for " + mcVersion + ": " + e.getMessage();
            Log.e(TAG, message, e); notify(context, message);
        }
    }
}
