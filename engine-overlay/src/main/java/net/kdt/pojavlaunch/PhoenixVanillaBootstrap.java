package net.kdt.pojavlaunch;

import android.content.Context;
import net.kdt.pojavlaunch.modloaders.FabricVersion;
import net.kdt.pojavlaunch.modloaders.FabriclikeUtils;
import net.kdt.pojavlaunch.utils.DownloadUtils;
import net.kdt.pojavlaunch.value.launcherprofiles.MinecraftProfile;
import org.json.JSONArray;
import org.json.JSONObject;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.util.Locale;
import java.util.regex.Pattern;

/**
 * Creates an isolated Fabric-backed Phoenix runtime from a selected Vanilla
 * profile. The saved Vanilla profile is cloned in memory and never overwritten.
 */
public final class PhoenixVanillaBootstrap {
    private static final String TAG = "PhoenixVanillaBootstrap";
    private static final Pattern SUPPORTED =
            Pattern.compile("^1\\.21\\.(?:[1-9]|1[01])$");
    private PhoenixVanillaBootstrap() {}

    public static final class Prepared {
        public final String versionId;
        public final MinecraftProfile profile;
        public final String loaderVersion;
        private Prepared(String id, MinecraftProfile profile, String loader) {
            this.versionId = id;
            this.profile = profile;
            this.loaderVersion = loader;
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
            Files.move(temporary.toPath(), target.toPath(), StandardCopyOption.REPLACE_EXISTING);
        } catch (Exception e) {
            temporary.delete();
            if (e instanceof IOException) throw (IOException)e;
            throw new IOException("Cannot save Phoenix Fabric profile metadata.", e);
        }

        // Fabric adds libraries that are not part of the vanilla manifest.
        // Fetch those before launch so the engine can construct a complete classpath.
        downloadFabricLibraries(metadata);

        MinecraftProfile isolated = Tools.GLOBAL_GSON.fromJson(
                Tools.GLOBAL_GSON.toJson(originalProfile), MinecraftProfile.class);
        isolated.lastVersionId = generatedId;
        return new Prepared(generatedId, isolated, selected.version);
    }

    private static void downloadFabricLibraries(JSONObject metadata) throws IOException {
        JSONArray libraries = metadata.optJSONArray("libraries");
        if (libraries == null) return;
        File libraryRoot = new File(Tools.DIR_HOME_LIBRARY).getCanonicalFile();
        if (!libraryRoot.exists() && !libraryRoot.mkdirs())
            throw new IOException("Cannot create Minecraft libraries directory.");

        for (int i = 0; i < libraries.length(); i++) {
            JSONObject library = libraries.optJSONObject(i);
            if (library == null) continue;
            JSONObject downloads = library.optJSONObject("downloads");
            JSONObject artifact = downloads == null ? null : downloads.optJSONObject("artifact");
            if (artifact == null) continue;

            String relativePath = artifact.optString("path", "");
            String source = artifact.optString("url", "");
            if (relativePath.isEmpty() || source.isEmpty()) continue;

            File destination = new File(libraryRoot, relativePath).getCanonicalFile();
            if (!destination.toPath().startsWith(libraryRoot.toPath()))
                throw new IOException("Rejected invalid Fabric library path.");
            String expectedSha1 = artifact.optString("sha1", "");
            long expectedSize = artifact.optLong("size", -1L);
            if (destination.isFile()) {
                boolean valid = expectedSha1.isEmpty()
                        ? (expectedSize < 0 || destination.length() == expectedSize)
                        : expectedSha1.equalsIgnoreCase(sha1(destination));
                if (valid) continue;
            }

            File parent = destination.getParentFile();
            if (parent == null || (!parent.exists() && !parent.mkdirs()))
                throw new IOException("Cannot create library folder for " + relativePath);
            File temporaryFile = new File(destination.getAbsolutePath() + ".phoenix.tmp");
            HttpURLConnection connection = (HttpURLConnection) new URL(source).openConnection();
            connection.setConnectTimeout(15000);
            connection.setReadTimeout(30000);
            connection.setInstanceFollowRedirects(true);
            try {
                connection.connect();
                if (connection.getResponseCode() < 200 || connection.getResponseCode() >= 300)
                    throw new IOException("Fabric library download failed: HTTP " + connection.getResponseCode());
                try (InputStream in = connection.getInputStream();
                     FileOutputStream out = new FileOutputStream(temporaryFile)) {
                    byte[] buffer = new byte[16384];
                    int count;
                    while ((count = in.read(buffer)) != -1) out.write(buffer, 0, count);
                    out.getFD().sync();
                }
                if (expectedSize >= 0 && temporaryFile.length() != expectedSize)
                    throw new IOException("Fabric library size check failed: " + relativePath);
                if (!expectedSha1.isEmpty() && !expectedSha1.equalsIgnoreCase(sha1(temporaryFile)))
                    throw new IOException("Fabric library checksum failed: " + relativePath);
                Files.move(temporaryFile.toPath(), destination.toPath(), StandardCopyOption.REPLACE_EXISTING);
            } catch (Exception error) {
                temporaryFile.delete();
                if (error instanceof IOException) throw (IOException) error;
                throw new IOException("Could not install Fabric library " + relativePath, error);
            } finally {
                connection.disconnect();
            }
        }
    }

    private static String sha1(File file) throws IOException {
        try (InputStream in = Files.newInputStream(file.toPath())) {
            MessageDigest digest = MessageDigest.getInstance("SHA-1");
            byte[] buffer = new byte[16384];
            int count;
            while ((count = in.read(buffer)) != -1) digest.update(buffer, 0, count);
            StringBuilder result = new StringBuilder();
            for (byte value : digest.digest()) result.append(String.format(Locale.ROOT, "%02x", value & 0xff));
            return result.toString();
        } catch (Exception error) {
            if (error instanceof IOException) throw (IOException) error;
            throw new IOException("Could not verify Fabric library checksum.", error);
        }
    }
}
