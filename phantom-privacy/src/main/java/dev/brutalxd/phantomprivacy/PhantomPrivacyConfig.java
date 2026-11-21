package dev.brutalxd.phantomprivacy;

import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Properties;
import java.util.Set;
import java.util.stream.Collectors;

public final class PhantomPrivacyConfig {
    private static final Path FILE = FabricLoader.getInstance().getConfigDir().resolve("phantom-privacy.properties");
    private static final Set<String> HIDDEN = new LinkedHashSet<>();

    private PhantomPrivacyConfig() {}

    public static synchronized void load() {
        HIDDEN.clear();
        HIDDEN.add("phantomprivacy");
        if (!Files.exists(FILE)) return;
        Properties properties = new Properties();
        try (Reader reader = Files.newBufferedReader(FILE)) {
            properties.load(reader);
            String value = properties.getProperty("hidden-mod-ids", "");
            Arrays.stream(value.split(","))
                    .map(String::trim)
                    .filter(id -> !id.isEmpty())
                    .map(id -> id.toLowerCase(Locale.ROOT))
                    .forEach(HIDDEN::add);
        } catch (IOException exception) {
            System.err.println("[Phantom Privacy] Could not read config: " + exception.getMessage());
        }
    }

    public static synchronized Set<String> getHiddenIds() {
        return new LinkedHashSet<>(HIDDEN);
    }

    public static synchronized boolean isHidden(String modId) {
        return modId != null && HIDDEN.contains(modId.toLowerCase(Locale.ROOT));
    }

    public static synchronized void setHiddenIds(String commaSeparatedIds) {
        Set<String> ids = Arrays.stream(commaSeparatedIds.split(","))
                .map(String::trim)
                .filter(id -> !id.isEmpty())
                .map(id -> id.toLowerCase(Locale.ROOT))
                .collect(Collectors.toCollection(LinkedHashSet::new));
        setHiddenIds(ids);
    }

    public static synchronized void setHiddenIds(Set<String> ids) {
        HIDDEN.clear();
        HIDDEN.add("phantomprivacy");
        ids.stream()
                .filter(id -> id != null && !id.isBlank())
                .map(id -> id.trim().toLowerCase(Locale.ROOT))
                .forEach(HIDDEN::add);
        save();
    }

    private static void save() {
        Properties properties = new Properties();
        properties.setProperty("hidden-mod-ids", HIDDEN.stream()
                .filter(id -> !id.equals("phantomprivacy"))
                .sorted()
                .collect(Collectors.joining(",")));
        try {
            Files.createDirectories(FILE.getParent());
            try (Writer writer = Files.newBufferedWriter(FILE)) {
                properties.store(writer, "Phantom Privacy - Mod Menu display filter");
            }
        } catch (IOException exception) {
            System.err.println("[Phantom Privacy] Could not save config: " + exception.getMessage());
        }
    }
}
