package net.kdt.pojavlaunch

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.Image
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import net.kdt.pojavlaunch.prefs.LauncherPreferences
import net.kdt.pojavlaunch.customcontrols.LayoutConverter
import net.kdt.pojavlaunch.value.launcherprofiles.LauncherProfiles
import net.kdt.pojavlaunch.value.launcherprofiles.MinecraftProfile
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.net.Socket
import java.util.zip.ZipFile

private val PhoenixBg = Color(0xFF0B0910)
private val PhoenixPanel = Color(0xFF19131D)
private val PhoenixMuted = Color(0xFFB8AEBB)
private val PhoenixOrange = Color(0xFFFF702E)


private data class PhoenixModResult(
    val projectId: String,
    val title: String,
    val description: String,
    val downloads: Long,
    val slug: String
)

private data class PhoenixPackResult(
    val projectId: String,
    val title: String,
    val description: String,
    val downloads: Long,
    val slug: String
)

private data class PhoenixInstalledMod(val fileName: String, val sizeBytes: Long, val enabled: Boolean)
private data class PhoenixContentFile(val fileName: String, val sizeBytes: Long, val enabled: Boolean)
private data class PhoenixReadinessItem(val title: String, val value: String, val detail: String, val healthy: Boolean)
private data class PhoenixDiagnostic(val title: String, val cause: String, val fix: String)
private data class PhoenixServer(val label: String, val host: String, val port: Int)
private data class PhoenixModCheck(val name: String, val state: String, val detail: String, val warning: Boolean)
private data class PhoenixExtension(val id: String, val name: String, val description: String)



private fun applyPhoenixFpsProfile(engineProfileId: String, preset: String): String {
    val gameDir = phoenixGameDirectory(engineProfileId).canonicalFile
    if (!gameDir.isDirectory) throw java.io.IOException("Open this Minecraft profile once before applying a performance profile.")
    val options = File(gameDir, "options.txt").canonicalFile
    if (options.parentFile != gameDir) throw java.io.IOException("Invalid Minecraft options path.")
    val backup = File(gameDir, ".phoenix-backup-options.txt")
    if (options.exists() && !backup.exists()) options.copyTo(backup, overwrite = false)
    val settings = when (preset) {
        "ULTRA" -> linkedMapOf("graphics" to "0", "renderDistance" to "4", "simulationDistance" to "3", "maxFps" to "60", "enableVsync" to "false", "clouds" to "0", "particles" to "2", "entityShadows" to "false", "biomeBlendRadius" to "0", "mipmapLevels" to "0", "entityDistanceScaling" to "0.5", "ao" to "false")
        "BALANCED" -> linkedMapOf("graphics" to "0", "renderDistance" to "8", "simulationDistance" to "5", "maxFps" to "60", "enableVsync" to "false", "clouds" to "1", "particles" to "1", "entityShadows" to "false", "biomeBlendRadius" to "1", "mipmapLevels" to "2", "entityDistanceScaling" to "0.75", "ao" to "false")
        else -> linkedMapOf("graphics" to "0", "renderDistance" to "6", "simulationDistance" to "4", "maxFps" to "60", "enableVsync" to "false", "clouds" to "0", "particles" to "2", "entityShadows" to "false", "biomeBlendRadius" to "0", "mipmapLevels" to "1", "entityDistanceScaling" to "0.5", "ao" to "false")
    }
    val oldLines = if (options.isFile) options.readLines() else emptyList()
    val output = mutableListOf<String>()
    val seen = mutableSetOf<String>()
    oldLines.forEach { line ->
        val key = line.substringBefore(':', "")
        if (key in settings.keys) {
            if (seen.add(key)) output.add(key + ":" + settings.getValue(key))
        } else output.add(line)
    }
    settings.forEach { (key, value) -> if (seen.add(key)) output.add(key + ":" + value) }
    val temp = File(gameDir, "options.txt.phoenix-tmp")
    temp.writeText(output.joinToString("\n", postfix = "\n"))
    if (options.exists() && !options.delete()) { temp.delete(); throw java.io.IOException("Could not replace options.txt. Close Minecraft and retry.") }
    if (!temp.renameTo(options)) { temp.delete(); throw java.io.IOException("Could not save Minecraft video settings.") }
    return "Applied " + preset + " profile to " + settings.size + " video settings. Original options are backed up."
}

private fun restorePhoenixOptions(engineProfileId: String): String {
    val gameDir = phoenixGameDirectory(engineProfileId).canonicalFile
    val options = File(gameDir, "options.txt")
    val backup = File(gameDir, ".phoenix-backup-options.txt")
    if (!backup.isFile) throw java.io.IOException("No Phoenix options backup exists for this profile.")
    val temp = File(gameDir, "options.txt.phoenix-restore")
    backup.copyTo(temp, overwrite = true)
    if (options.exists() && !options.delete()) { temp.delete(); throw java.io.IOException("Could not replace options.txt. Close Minecraft and retry.") }
    if (!temp.renameTo(options)) { temp.delete(); throw java.io.IOException("Could not restore the original video settings.") }
    return "Original Minecraft video settings restored from the Phoenix backup."
}

private fun importPhoenixControlMap(context: Context, uri: Uri, engineProfileId: String): String {
    val input = context.contentResolver.openInputStream(uri)
        ?: throw java.io.IOException("Phoenix could not open the selected control file.")
    val json = input.bufferedReader().use { it.readText() }
    if (json.length > 5_000_000) throw java.io.IOException("Control map is larger than the 5 MB safety limit.")
    val root = JSONObject(json)
    if (!root.has("mControlDataList")) {
        throw java.io.IOException("This file is not a supported Mojo/Amethyst control-map JSON.")
    }
    val temp = File(context.cacheDir, "phoenix-control-${UUID.randomUUID()}.json")
    temp.writeText(json)
    try {
        val converted = LayoutConverter.loadAndConvertIfNecessary(temp.absolutePath)
            ?: throw java.io.IOException("This control-map version is not supported by Phoenix.")
        val directory = File(Tools.CTRLMAP_PATH)
        if (!directory.exists() && !directory.mkdirs()) {
            throw java.io.IOException("Phoenix could not create its control-map folder.")
        }
        val destination = File(directory, "Phoenix-Mojo-${System.currentTimeMillis()}.json")
        converted.save(destination.absolutePath)
        if (LauncherProfiles.mainProfileJson == null) LauncherProfiles.load()
        val profile = LauncherProfiles.mainProfileJson.profiles[engineProfileId]
            ?: throw java.io.IOException("The active Minecraft profile could not be found.")
        profile.controlFile = destination.name
        LauncherProfiles.write()
        LauncherPreferences.PREF_DEFAULTCTRL_PATH = destination.absolutePath
        LauncherPreferences.DEFAULT_PREF.edit()
            .putString("defaultCtrl", destination.absolutePath)
            .apply()
        return destination.name
    } finally {
        temp.delete()
    }
}

private fun phoenixGameDirectory(engineProfileId: String): File {
    if (LauncherProfiles.mainProfileJson == null) LauncherProfiles.load()
    val profile = LauncherProfiles.mainProfileJson.profiles[engineProfileId]
        ?: throw java.io.IOException("Select a valid game profile first.")
    return Tools.getGameDirPath(profile)
}

private fun scanPhoenixReadiness(engineProfileId: String): List<PhoenixReadinessItem> {
    val gameDir = phoenixGameDirectory(engineProfileId)
    if (!gameDir.exists() || !gameDir.isDirectory) {
        return listOf(PhoenixReadinessItem("Game directory", "Not found", "Open the engine once and install a Minecraft version for this profile.", false))
    }
    val modsDir = File(gameDir, "mods")
    val modFiles = modsDir.listFiles()?.filter { it.isFile && (it.name.endsWith(".jar", true) || it.name.endsWith(".jar.disabled", true)) } ?: emptyList()
    val enabledMods = modFiles.count { it.name.endsWith(".jar", true) }
    val disabledMods = modFiles.size - enabledMods
    val worldsDir = File(gameDir, "saves")
    val worlds = worldsDir.listFiles()?.count { it.isDirectory && File(it, "level.dat").isFile } ?: 0
    val crashDir = File(gameDir, "crash-reports")
    val crashes = crashDir.listFiles()?.count { it.isFile && it.name.endsWith(".txt", true) } ?: 0
    val logsDir = File(gameDir, "logs")
    val latestLog = File(logsDir, "latest.log")
    val freeGb = gameDir.usableSpace / (1024L * 1024L * 1024L)
    return listOf(
        PhoenixReadinessItem("Game directory", "Available", gameDir.name, true),
        PhoenixReadinessItem("Mod files", "$enabledMods enabled · $disabledMods disabled", "Phoenix only counts JAR files here; compatibility still depends on Minecraft version and loader.", true),
        PhoenixReadinessItem("Resource packs", (File(gameDir, "resourcepacks").listFiles()?.count { it.isFile && it.name.endsWith(".zip", true) } ?: 0).toString(), "ZIP resource packs currently present in this profile.", true),
        PhoenixReadinessItem("Shader packs", (File(gameDir, "shaderpacks").listFiles()?.count { it.isFile && it.name.endsWith(".zip", true) } ?: 0).toString(), "ZIP shader packs currently present in this profile.", true),
        PhoenixReadinessItem("Saved worlds", worlds.toString(), "World folders with a valid level.dat file.", true),
        PhoenixReadinessItem("Crash reports", crashes.toString(), if (crashes == 0) "No crash reports found in the standard crash-reports folder." else "Review recent reports before the next launch.", crashes == 0),
        PhoenixReadinessItem("Latest log", if (latestLog.isFile) "Found" else "Not found", "The engine writes this file after a game session starts.", latestLog.isFile),
        PhoenixReadinessItem("Free game storage", "$freeGb GB", "Available storage on the filesystem containing this game directory.", freeGb >= 2L)
    )
}


private fun diagnosePhoenixLogs(engineProfileId: String): List<PhoenixDiagnostic> {
    val dir = phoenixGameDirectory(engineProfileId)
    val files = listOf(File(dir, "crash-reports"), File(dir, "logs")).flatMap { folder ->
        folder.listFiles()?.filter { it.isFile && (it.name.endsWith(".txt", true) || it.name == "latest.log") } ?: emptyList()
    }.sortedByDescending { it.lastModified() }.take(8)
    if (files.isEmpty()) return listOf(PhoenixDiagnostic("No diagnostic logs found", "No standard crash report or latest.log exists for this profile.", "Launch once or open the engine log viewer after a failed launch."))
    val patterns = listOf(
        Triple(Regex("(?i)outofmemoryerror|java heap space|could not reserve enough space"), "Memory exhaustion", "Lower RAM allocation, close background apps and test without heavy shaders."),
        Triple(Regex("(?i)Mixin apply failed|MixinApplyError|InjectionError"), "Mixin / mod conflict", "Check the named mod versions, loader version and Minecraft version. Disable recently added mods one at a time."),
        Triple(Regex("(?i)NoSuchMethodError|NoClassDefFoundError|ClassNotFoundException"), "Missing or incompatible class", "Update or remove the mod/library named near the first error."),
        Triple(Regex("(?i)UnsupportedClassVersionError"), "Java runtime mismatch", "Use the Java runtime required by this Minecraft version and loader."),
        Triple(Regex("(?i)GL_OUT_OF_MEMORY|OpenGL error|Failed to create framebuffer|shader compilation"), "Graphics / renderer issue", "Try another renderer, disable shaders and reduce resolution or effects."),
        Triple(Regex("(?i)Failed to download|Connection timed out|UnknownHostException|SSLHandshakeException"), "Network or download failure", "Check connectivity and retry the download."),
        Triple(Regex("(?i)ModLoadingException|LoadingFailedException|Incompatible mods"), "Mod loading failure", "Review the mod list for loader/version mismatches.")
    )
    val findings = mutableListOf<PhoenixDiagnostic>()
    files.forEach { file ->
        val content = runCatching { file.readLines().takeLast(500).joinToString("\n").takeLast(90000) }.getOrDefault("")
        patterns.forEach { pattern ->
            if (pattern.first.containsMatchIn(content) && findings.none { it.title == pattern.second }) {
                val line = content.lineSequence().firstOrNull { pattern.first.containsMatchIn(it) } ?: file.name
                findings.add(PhoenixDiagnostic(pattern.second, line.take(220) + " (source: " + file.name + ")", pattern.third))
            }
        }
    }
    if (findings.isEmpty()) findings.add(PhoenixDiagnostic("No known signature matched", "Available logs did not contain a recognised common failure pattern.", "Review the full log manually. This heuristic scanner cannot identify every crash."))
    return findings
}

private fun scanPhoenixMods(engineProfileId: String, selectedLoader: String): List<PhoenixModCheck> {
    val mods = listPhoenixInstalledMods(engineProfileId)
    if (mods.isEmpty()) return listOf(PhoenixModCheck("No mod JAR files", "Empty", "Install mods into this profile to run checks.", false))
    return mods.map { mod ->
        if (!mod.enabled) return@map PhoenixModCheck(mod.fileName, "Disabled", "This .jar.disabled file will not load as a normal mod.", false)
        val file = File(phoenixModsDirectory(engineProfileId), mod.fileName)
        val detected = runCatching {
            ZipFile(file).use { zip ->
                when {
                    zip.getEntry("fabric.mod.json") != null -> "fabric"
                    zip.getEntry("quilt.mod.json") != null -> "quilt"
                    zip.getEntry("META-INF/neoforge.mods.toml") != null -> "neoforge"
                    zip.getEntry("META-INF/mods.toml") != null -> {
                        val entry = zip.getEntry("META-INF/mods.toml")
                        val text = zip.getInputStream(entry).bufferedReader().use { it.readText().take(30000) }
                        if (text.contains("neoforge", true)) "neoforge" else "forge"
                    }
                    else -> "unknown"
                }
            }
        }.getOrDefault("unreadable")
        val mismatch = detected != "unknown" && detected != "unreadable" && detected != selectedLoader
        when {
            detected == "unreadable" -> PhoenixModCheck(mod.fileName, "Unreadable JAR", "Could not inspect this archive. Verify the download.", true)
            detected == "unknown" -> PhoenixModCheck(mod.fileName, "Unknown loader", "No common Fabric, Quilt, Forge or NeoForge marker was found.", false)
            mismatch -> PhoenixModCheck(mod.fileName, "Possible loader mismatch", "Archive marker suggests " + detected + " but the selected loader is " + selectedLoader + ". Verify project support.", true)
            else -> PhoenixModCheck(mod.fileName, "Loader marker: " + detected, "Loader marker matches. Exact Minecraft version compatibility is not guaranteed.", false)
        }
    }
}

private fun pingPhoenixServer(host: String, port: Int): Long {
    val start = System.nanoTime()
    Socket().use { it.connect(java.net.InetSocketAddress(host, port), 3500) }
    return (System.nanoTime() - start) / 1000000L
}

private fun readPhoenixServers(prefs: android.content.SharedPreferences): List<PhoenixServer> {
    val array = runCatching { JSONArray(prefs.getString("server_favourites", "[]")) }.getOrDefault(JSONArray())
    return buildList {
        for (i in 0 until array.length()) {
            val item = array.optJSONObject(i) ?: continue
            val host = item.optString("host")
            val port = item.optInt("port", 25565)
            if (host.isNotBlank() && port in 1..65535) add(PhoenixServer(item.optString("label", host), host, port))
        }
    }
}

private fun writePhoenixServers(prefs: android.content.SharedPreferences, servers: List<PhoenixServer>) {
    val array = JSONArray()
    servers.forEach { array.put(JSONObject().put("label", it.label).put("host", it.host).put("port", it.port)) }
    prefs.edit().putString("server_favourites", array.toString()).apply()
}

private fun recordPhoenixEvent(prefs: android.content.SharedPreferences, title: String, detail: String) {
    val old = runCatching { JSONArray(prefs.getString("session_timeline", "[]")) }.getOrDefault(JSONArray())
    val next = JSONArray().put(JSONObject().put("time", System.currentTimeMillis()).put("title", title).put("detail", detail))
    for (i in 0 until minOf(old.length(), 39)) next.put(old.optJSONObject(i) ?: JSONObject())
    prefs.edit().putString("session_timeline", next.toString()).apply()
}

private fun phoenixTimeline(prefs: android.content.SharedPreferences): List<Pair<String, String>> {
    val array = runCatching { JSONArray(prefs.getString("session_timeline", "[]")) }.getOrDefault(JSONArray())
    return buildList {
        for (i in 0 until array.length()) {
            val item = array.optJSONObject(i) ?: continue
            val time = item.optLong("time", 0L)
            if (time > 0L) add(java.text.DateFormat.getDateTimeInstance().format(java.util.Date(time)) to (item.optString("title") + " · " + item.optString("detail")))
        }
    }
}

private fun savePhoenixSnapshot(context: Context, profile: PhoenixSavedProfile, loader: String, ram: Int, banner: String, avatar: String, layout: String, theme: String): String {
    val dir = File(context.filesDir, "phoenix_snapshots/" + profile.id)
    if (!dir.exists() && !dir.mkdirs()) throw java.io.IOException("Could not create snapshot storage.")
    val name = "snapshot-" + System.currentTimeMillis() + ".json"
    val json = JSONObject().put("profileName", profile.name).put("nickname", profile.nickname)
        .put("version", profile.version).put("loader", loader).put("ram", ram)
        .put("banner", banner).put("avatar", avatar).put("layout", layout).put("theme", theme).put("createdAt", System.currentTimeMillis())
    File(dir, name).writeText(json.toString(2))
    return name
}

private fun listPhoenixSnapshots(context: Context, profileId: String): List<File> =
    File(context.filesDir, "phoenix_snapshots/" + profileId).listFiles()
        ?.filter { it.isFile && it.name.endsWith(".json") }?.sortedByDescending { it.lastModified() } ?: emptyList()

private fun restorePhoenixSnapshot(file: File): JSONObject {
    if (!file.isFile || !file.name.startsWith("snapshot-") || !file.name.endsWith(".json")) throw java.io.IOException("Invalid profile snapshot.")
    return JSONObject(file.readText().take(20000))
}

private fun listPhoenixContentFiles(engineProfileId: String, folderName: String): List<PhoenixContentFile> {
    if (folderName !in setOf("resourcepacks", "shaderpacks")) throw java.io.IOException("Unsupported content folder.")
    val directory = File(phoenixGameDirectory(engineProfileId), folderName)
    if (!directory.exists()) return emptyList()
    if (!directory.isDirectory) throw java.io.IOException("The $folderName path is not a folder.")
    return directory.listFiles()
        ?.filter { it.isFile && (it.name.endsWith(".zip", true) || it.name.endsWith(".zip.disabled", true)) }
        ?.sortedBy { it.name.lowercase() }
        ?.map { PhoenixContentFile(it.name, it.length(), it.name.endsWith(".zip", true)) }
        ?: emptyList()
}

private fun togglePhoenixContentFile(engineProfileId: String, folderName: String, fileName: String, enable: Boolean): String {
    if (folderName !in setOf("resourcepacks", "shaderpacks") || File(fileName).name != fileName)
        throw java.io.IOException("Invalid content selection.")
    val directory = File(phoenixGameDirectory(engineProfileId), folderName).canonicalFile
    val source = File(directory, fileName).canonicalFile
    if (source.parentFile != directory || !source.isFile) throw java.io.IOException("That content file could not be found.")
    val suffix = if (enable) ".zip.disabled" else ".zip"
    if (!source.name.endsWith(suffix, true)) throw java.io.IOException("That file is already in the requested state.")
    val destination = File(directory, if (enable) source.name.removeSuffix(".disabled") else source.name + ".disabled")
    if (destination.exists()) throw java.io.IOException("A file with the target name already exists.")
    if (!source.renameTo(destination)) throw java.io.IOException("Could not change the content state.")
    return destination.name
}

private fun listPhoenixWorlds(engineProfileId: String): List<String> {
    val saves = File(phoenixGameDirectory(engineProfileId), "saves")
    if (!saves.exists()) return emptyList()
    if (!saves.isDirectory) throw java.io.IOException("The saves path is not a folder.")
    return saves.listFiles()?.filter { it.isDirectory && File(it, "level.dat").isFile }
        ?.map { it.name }?.sortedBy { it.lowercase() } ?: emptyList()
}

private fun phoenixModsDirectory(engineProfileId: String): File {
    if (LauncherProfiles.mainProfileJson == null) LauncherProfiles.load()
    val profile = LauncherProfiles.mainProfileJson.profiles[engineProfileId]
        ?: throw java.io.IOException("Select a valid game profile before managing mods.")
    return File(Tools.getGameDirPath(profile), "mods")
}

private fun listPhoenixInstalledMods(engineProfileId: String): List<PhoenixInstalledMod> {
    val directory = phoenixModsDirectory(engineProfileId)
    if (!directory.exists()) return emptyList()
    if (!directory.isDirectory) throw java.io.IOException("The mods path is not a folder.")
    return directory.listFiles()
        ?.filter { it.isFile && (it.name.endsWith(".jar", true) || it.name.endsWith(".jar.disabled", true)) }
        ?.sortedBy { it.name.lowercase() }
        ?.map { PhoenixInstalledMod(it.name, it.length(), it.name.endsWith(".jar", true)) }
        ?: emptyList()
}

private fun togglePhoenixInstalledMod(engineProfileId: String, fileName: String, enable: Boolean): String {
    val directory = phoenixModsDirectory(engineProfileId).canonicalFile
    val source = File(directory, fileName).canonicalFile
    if (source.parentFile != directory || !source.isFile) throw java.io.IOException("That mod file could not be found.")
    val expectedSuffix = if (enable) ".jar.disabled" else ".jar"
    if (!source.name.endsWith(expectedSuffix, true)) throw java.io.IOException("The selected mod is already in that state.")
    val destinationName = if (enable) source.name.removeSuffix(".disabled") else source.name + ".disabled"
    val destination = File(directory, destinationName)
    if (destination.exists()) throw java.io.IOException("A file with the target name already exists.")
    if (!source.renameTo(destination)) throw java.io.IOException("Could not change the mod state.")
    return destination.name
}

private fun removePhoenixInstalledMod(engineProfileId: String, fileName: String) {
    if (fileName.isBlank() || File(fileName).name != fileName ||
        !(fileName.endsWith(".jar", ignoreCase = true) || fileName.endsWith(".jar.disabled", ignoreCase = true))) {
        throw java.io.IOException("Invalid mod file selection.")
    }
    val directory = phoenixModsDirectory(engineProfileId).canonicalFile
    val target = File(directory, fileName).canonicalFile
    if (target.parentFile != directory || !target.isFile || !(target.name.endsWith(".jar", true) || target.name.endsWith(".jar.disabled", true))) {
        throw java.io.IOException("That mod file could not be found.")
    }
    if (!target.delete()) throw java.io.IOException("Could not remove " + target.name + ".")
}

private fun modrinthJson(url: String): JSONObject {
    val connection = URL(url).openConnection() as HttpURLConnection
    connection.requestMethod = "GET"
    connection.connectTimeout = 15000
    connection.readTimeout = 30000
    connection.setRequestProperty("User-Agent", "PhoenixClient/1.0 (https://github.com/dipayan900/PHOENIX-CLIENT)")
    try {
        if (connection.responseCode !in 200..299) throw java.io.IOException("Modrinth returned HTTP ${connection.responseCode}")
        return JSONObject(connection.inputStream.bufferedReader().use { it.readText() })
    } finally {
        connection.disconnect()
    }
}

private fun modrinthJsonArray(url: String): JSONArray {
    val connection = URL(url).openConnection() as HttpURLConnection
    connection.requestMethod = "GET"
    connection.connectTimeout = 15000
    connection.readTimeout = 30000
    connection.setRequestProperty("User-Agent", "PhoenixClient/1.0 (https://github.com/dipayan900/PHOENIX-CLIENT)")
    try {
        if (connection.responseCode !in 200..299) throw java.io.IOException("Modrinth returned HTTP ${connection.responseCode}")
        return JSONArray(connection.inputStream.bufferedReader().use { it.readText() })
    } finally {
        connection.disconnect()
    }
}

private fun searchPhoenixMods(query: String, minecraftVersion: String, loader: String): List<PhoenixModResult> {
    val facets = org.json.JSONArray()
        .put(org.json.JSONArray().put("project_type:mod"))
        .put(org.json.JSONArray().put("versions:$minecraftVersion"))
        .put(org.json.JSONArray().put("categories:$loader"))
    val encodedFacets = URLEncoder.encode(facets.toString(), "UTF-8")
    val encodedQuery = URLEncoder.encode(query, "UTF-8")
    val data = modrinthJson("https://api.modrinth.com/v2/search?query=$encodedQuery&facets=$encodedFacets&index=downloads&limit=20")
    val hits = data.optJSONArray("hits") ?: return emptyList()
    return buildList {
        for (i in 0 until hits.length()) {
            val hit = hits.optJSONObject(i) ?: continue
            add(PhoenixModResult(
                projectId = hit.optString("project_id"),
                title = hit.optString("title", "Untitled mod"),
                description = hit.optString("description", ""),
                downloads = hit.optLong("downloads", 0),
                slug = hit.optString("slug", "")
            ))
        }
    }
}


private fun searchPhoenixPacks(query: String, minecraftVersion: String, packType: String): List<PhoenixPackResult> {
    if (packType !in setOf("resourcepack", "shader")) throw java.io.IOException("Unsupported pack category.")
    val facets = JSONArray()
        .put(JSONArray().put("project_type:$packType"))
        .put(JSONArray().put("versions:$minecraftVersion"))
    val encodedFacets = URLEncoder.encode(facets.toString(), "UTF-8")
    val encodedQuery = URLEncoder.encode(query, "UTF-8")
    val data = modrinthJson("https://api.modrinth.com/v2/search?query=$encodedQuery&facets=$encodedFacets&index=downloads&limit=20")
    val hits = data.optJSONArray("hits") ?: return emptyList()
    return buildList {
        for (i in 0 until hits.length()) {
            val hit = hits.optJSONObject(i) ?: continue
            add(PhoenixPackResult(
                projectId = hit.optString("project_id"),
                title = hit.optString("title", "Untitled pack"),
                description = hit.optString("description", ""),
                downloads = hit.optLong("downloads", 0),
                slug = hit.optString("slug", "")
            ))
        }
    }
}

private fun installPhoenixPack(pack: PhoenixPackResult, minecraftVersion: String, folderName: String, engineProfileId: String): String {
    if (folderName !in setOf("resourcepacks", "shaderpacks")) throw java.io.IOException("Unsupported pack folder.")
    if (LauncherProfiles.mainProfileJson == null) LauncherProfiles.load()
    val profile = LauncherProfiles.mainProfileJson.profiles[engineProfileId]
        ?: throw java.io.IOException("Select a valid game profile before installing packs.")
    val versions = URLEncoder.encode(JSONArray().put(minecraftVersion).toString(), "UTF-8")
    val versionArray = modrinthJsonArray("https://api.modrinth.com/v2/project/${pack.projectId}/version?game_versions=$versions")
    if (versionArray.length() == 0) throw java.io.IOException("No release is available for Minecraft $minecraftVersion.")
    val release = versionArray.getJSONObject(0)
    val files = release.optJSONArray("files") ?: throw java.io.IOException("This project has no downloadable files.")
    var chosen: JSONObject? = null
    for (i in 0 until files.length()) {
        val file = files.optJSONObject(i) ?: continue
        if (file.optBoolean("primary", false)) { chosen = file; break }
        if (chosen == null) chosen = file
    }
    val selected = chosen ?: throw java.io.IOException("This project has no downloadable files.")
    val downloadUrl = selected.optString("url")
    val rawName = selected.optString("filename", pack.slug + ".zip")
    if (!downloadUrl.startsWith("https://") || !rawName.endsWith(".zip", true))
        throw java.io.IOException("The selected release is not a valid ZIP pack.")
    val directory = File(Tools.getGameDirPath(profile), folderName)
    if (!directory.exists() && !directory.mkdirs()) throw java.io.IOException("Cannot create the $folderName folder.")
    if (!directory.isDirectory) throw java.io.IOException("The $folderName path is not a folder.")
    val safeName = rawName.replace(Regex("[^A-Za-z0-9._-]"), "_")
    var destination = File(directory, safeName)
    if (destination.exists()) destination = File(directory, safeName.removeSuffix(".zip") + "-phoenix-" + System.currentTimeMillis() + ".zip")
    val connection = URL(downloadUrl).openConnection() as HttpURLConnection
    connection.connectTimeout = 15000
    connection.readTimeout = 60000
    connection.setRequestProperty("User-Agent", "PhoenixClient/1.0 (https://github.com/dipayan900/PHOENIX-CLIENT)")
    val temporary = File(directory, destination.name + ".part")
    try {
        if (connection.responseCode !in 200..299) throw java.io.IOException("Pack download failed: HTTP ${connection.responseCode}")
        connection.inputStream.use { input -> FileOutputStream(temporary).use { output -> input.copyTo(output) } }
        if (temporary.length() <= 0L) throw java.io.IOException("The downloaded pack was empty.")
        if (!temporary.renameTo(destination)) throw java.io.IOException("Could not move the pack into $folderName.")
    } catch (error: Exception) {
        temporary.delete()
        throw error
    } finally {
        connection.disconnect()
    }
    return destination.name
}

private fun removePhoenixContentFile(engineProfileId: String, folderName: String, fileName: String) {
    if (folderName !in setOf("resourcepacks", "shaderpacks") || File(fileName).name != fileName ||
        !(fileName.endsWith(".zip", true) || fileName.endsWith(".zip.disabled", true))) {
        throw java.io.IOException("Invalid pack selection.")
    }
    val directory = File(phoenixGameDirectory(engineProfileId), folderName).canonicalFile
    val target = File(directory, fileName).canonicalFile
    if (target.parentFile != directory || !target.isFile) throw java.io.IOException("That pack file could not be found.")
    if (!target.delete()) throw java.io.IOException("Could not remove $fileName.")
}

private fun installPhoenixMod(mod: PhoenixModResult, minecraftVersion: String, loader: String, engineProfileId: String): String {
    if (LauncherProfiles.mainProfileJson == null) LauncherProfiles.load()
    val profile = LauncherProfiles.mainProfileJson.profiles[engineProfileId]
        ?: throw java.io.IOException("Select a valid game profile before installing mods.")
    val versions = URLEncoder.encode(org.json.JSONArray().put(minecraftVersion).toString(), "UTF-8")
    val loaders = URLEncoder.encode(org.json.JSONArray().put(loader).toString(), "UTF-8")
    val versionConnection = URL("https://api.modrinth.com/v2/project/${mod.projectId}/version?game_versions=$versions&loaders=$loaders")
        .openConnection() as HttpURLConnection
    versionConnection.requestMethod = "GET"
    versionConnection.connectTimeout = 15000
    versionConnection.readTimeout = 30000
    versionConnection.setRequestProperty("User-Agent", "PhoenixClient/1.0 (https://github.com/dipayan900/PHOENIX-CLIENT)")
    val versionArray = try {
        if (versionConnection.responseCode !in 200..299) throw java.io.IOException("No compatible release found for $minecraftVersion / $loader.")
        org.json.JSONArray(versionConnection.inputStream.bufferedReader().use { it.readText() })
    } finally {
        versionConnection.disconnect()
    }
    if (versionArray.length() == 0) throw java.io.IOException("No compatible release found for $minecraftVersion / $loader.")
    val release = versionArray.getJSONObject(0)
    val files = release.optJSONArray("files") ?: throw java.io.IOException("The mod has no downloadable files.")
    var chosen: JSONObject? = null
    for (i in 0 until files.length()) {
        val file = files.optJSONObject(i) ?: continue
        if (file.optBoolean("primary", false)) { chosen = file; break }
        if (chosen == null) chosen = file
    }
    val selected = chosen ?: throw java.io.IOException("The mod has no downloadable files.")
    val downloadUrl = selected.optString("url")
    val rawName = selected.optString("filename", mod.slug + ".jar")
    if (!downloadUrl.startsWith("https://") || !rawName.endsWith(".jar", true)) {
        throw java.io.IOException("The selected release is not a valid mod JAR.")
    }
    val gameDir = Tools.getGameDirPath(profile)
    val modsDir = File(gameDir, "mods")
    if (!modsDir.exists() && !modsDir.mkdirs()) throw java.io.IOException("Cannot create the mods folder.")
    val safeName = rawName.replace(Regex("[^A-Za-z0-9._-]"), "_")
    var destination = File(modsDir, safeName)
    if (destination.exists()) destination = File(modsDir, safeName.removeSuffix(".jar") + "-phoenix-" + System.currentTimeMillis() + ".jar")
    val download = URL(downloadUrl).openConnection() as HttpURLConnection
    download.connectTimeout = 15000
    download.readTimeout = 60000
    download.setRequestProperty("User-Agent", "PhoenixClient/1.0 (https://github.com/dipayan900/PHOENIX-CLIENT)")
    val temporary = File(modsDir, destination.name + ".part")
    try {
        if (download.responseCode !in 200..299) throw java.io.IOException("Mod download failed: HTTP ${download.responseCode}")
        download.inputStream.use { input ->
            FileOutputStream(temporary).use { output -> input.copyTo(output) }
        }
        if (temporary.length() <= 0L) throw java.io.IOException("The downloaded file was empty.")
        if (!temporary.renameTo(destination)) throw java.io.IOException("Could not move the downloaded mod into the mods folder.")
    } catch (error: Exception) {
        temporary.delete()
        throw error
    } finally {
        download.disconnect()
    }
    return destination.name
}

private data class PhoenixSavedProfile(
    val id: String,
    val name: String,
    val nickname: String,
    val version: String,
    val engineProfileId: String = "",
    val avatar: String = "🔥"
)

private fun installPhoenixBundledCosmeticMod(context: Context, engineProfileId: String, version: String, loader: String): String {
    if (loader != "fabric") return "Phoenix cosmetics auto-install is for Fabric profiles. Your selected loader is $loader."
    if (!version.contains("1.21.1")) return "Phoenix cosmetics add-on currently supports Minecraft Java 1.21.1 only."
    if (LauncherProfiles.mainProfileJson == null) LauncherProfiles.load()
    val profile = LauncherProfiles.mainProfileJson.profiles[engineProfileId]
        ?: throw java.io.IOException("Active Minecraft profile was not found.")
    val gameDir = Tools.getGameDirPath(profile).canonicalFile
    val modsDir = File(gameDir, "mods").canonicalFile
    if (modsDir.parentFile != gameDir) throw java.io.IOException("Invalid Minecraft mods directory.")
    if (!modsDir.exists() && !modsDir.mkdirs()) throw java.io.IOException("Could not create the Minecraft mods folder.")
    if (!modsDir.isDirectory) throw java.io.IOException("The Minecraft mods path is not a folder.")
    val target = File(modsDir, "phoenix-branding-client.jar")
    val temp = File(modsDir, "phoenix-branding-client.jar.part")
    try {
        context.assets.open("phoenix/phoenix-branding-fabric.jar").use { input ->
            FileOutputStream(temp).use { output -> input.copyTo(output) }
        }
        if (temp.length() < 1024L) throw java.io.IOException("The bundled Phoenix cosmetics add-on is incomplete.")
        if (target.exists() && !target.delete()) throw java.io.IOException("Close Minecraft before updating the Phoenix cosmetics add-on.")
        if (!temp.renameTo(target)) throw java.io.IOException("Could not install the Phoenix cosmetics add-on.")
    } catch (error: Exception) {
        temp.delete()
        throw error
    }
    return "Phoenix cosmetics add-on installed automatically for Fabric 1.21.1."
}

class PhoenixDashboardActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // TestStorageActivity has already initialized storage and unpacked engine assets.
        LauncherProfiles.load()
        setContent { PhoenixDashboard(this) }
    }
}

private fun readPhoenixProfiles(context: Context): List<PhoenixSavedProfile> {
    val prefs = context.getSharedPreferences("phoenix_client", Context.MODE_PRIVATE)
    val raw = prefs.getString("profiles_json", null)
    if (raw.isNullOrBlank()) {
        val name = prefs.getString("profile", "PhoenixPlayer") ?: "PhoenixPlayer"
        val version = prefs.getString("version", "1.21.4") ?: "1.21.4"
        return listOf(PhoenixSavedProfile(UUID.randomUUID().toString(), "Main profile", name, version))
    }
    return try {
        val array = JSONArray(raw)
        buildList {
            for (i in 0 until array.length()) {
                val item = array.getJSONObject(i)
                add(PhoenixSavedProfile(
                    id = item.optString("id", UUID.randomUUID().toString()),
                    name = item.optString("name", "Player profile"),
                    nickname = item.optString("nickname", "PhoenixPlayer"),
                    version = item.optString("version", "1.21.4"),
                    engineProfileId = item.optString("engineProfileId", ""),
                    avatar = item.optString("avatar", "🔥")
                ))
            }
        }.ifEmpty { listOf(PhoenixSavedProfile(UUID.randomUUID().toString(), "Main profile", "PhoenixPlayer", "1.21.4")) }
    } catch (_: Exception) {
        listOf(PhoenixSavedProfile(UUID.randomUUID().toString(), "Main profile", "PhoenixPlayer", "1.21.4"))
    }
}

private fun writePhoenixProfiles(context: Context, profiles: List<PhoenixSavedProfile>, active: PhoenixSavedProfile) {
    val array = JSONArray()
    profiles.forEach { p ->
        array.put(JSONObject()
            .put("id", p.id)
            .put("name", p.name)
            .put("nickname", p.nickname)
            .put("version", p.version)
            .put("engineProfileId", p.engineProfileId)
            .put("avatar", p.avatar))
    }
    context.getSharedPreferences("phoenix_client", Context.MODE_PRIVATE).edit()
        .putString("profiles_json", array.toString())
        .putString("active_profile_id", active.id)
        .putString("profile", active.nickname)
        .putString("version", active.version)
        .apply()
}

private fun connectProfilesToEngine(profiles: List<PhoenixSavedProfile>, activeId: String): List<PhoenixSavedProfile> {
    if (LauncherProfiles.mainProfileJson == null) LauncherProfiles.load()
    val engineProfiles = LauncherProfiles.mainProfileJson.profiles
    val synced = profiles.map { phoenix ->
        val engineId = phoenix.engineProfileId.takeIf { it.isNotBlank() && engineProfiles.containsKey(it) }
            ?: UUID.randomUUID().toString()
        val engineProfile = engineProfiles[engineId] ?: MinecraftProfile.createTemplate()
        engineProfile.name = phoenix.name
        engineProfile.lastVersionId = phoenix.version
        engineProfiles[engineId] = engineProfile
        phoenix.copy(engineProfileId = engineId)
    }
    val active = synced.firstOrNull { it.id == activeId } ?: synced.first()
    LauncherProfiles.write()
    LauncherPreferences.DEFAULT_PREF.edit()
        .putString(LauncherPreferences.PREF_KEY_CURRENT_PROFILE, active.engineProfileId)
        .apply()
    return synced
}

@Composable
private fun PhoenixDashboard(activity: PhoenixDashboardActivity) {
    val context = activity
    val uriHandler = LocalUriHandler.current
    val prefs = remember { context.getSharedPreferences("phoenix_client", Context.MODE_PRIVATE) }
    val profiles = remember { mutableStateListOf<PhoenixSavedProfile>().apply { addAll(readPhoenixProfiles(context)) } }
    var activeId by remember { mutableStateOf(prefs.getString("active_profile_id", profiles.first().id) ?: profiles.first().id) }
    var page by remember { mutableStateOf("Home") }
    var notice by remember { mutableStateOf("Phoenix engine ready. Sign in through the official launcher flow.") }
    val controlMapPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            try {
                val importedName = importPhoenixControlMap(context, uri, activeId)
                notice = "Imported $importedName and set it as this profile's active touch-control map. Open the game again to use it."
                recordPhoenixEvent(prefs, "Control map imported", importedName)
            } catch (error: Exception) {
                notice = error.message ?: "Phoenix could not import that control map."
            }
        }
    }
    var dialogOpen by remember { mutableStateOf(false) }
    var draftName by remember { mutableStateOf("") }
    var draftNickname by remember { mutableStateOf("") }
    var draftVersion by remember { mutableStateOf("1.21.4") }
    var draftAvatar by remember { mutableStateOf("🔥") }
    var avatarChoice by remember { mutableStateOf(prefs.getString("cosmetic_avatar", "🔥") ?: "🔥") }
    var bannerChoice by remember { mutableStateOf(prefs.getString("cosmetic_banner", "EMBER") ?: "EMBER") }
    var modQuery by remember { mutableStateOf("") }
    var modLoader by remember { mutableStateOf(prefs.getString("mod_loader_${activeId}", "fabric") ?: "fabric") }
    var modStatus by remember { mutableStateOf("Search Modrinth for mods compatible with your selected Minecraft version and loader.") }
    var modBusy by remember { mutableStateOf(false) }
    var packQuery by remember { mutableStateOf("") }
    var packType by remember { mutableStateOf("resourcepack") }
    var packStatus by remember { mutableStateOf("Search Modrinth for resource packs or shader packs compatible with this profile.") }
    var packBusy by remember { mutableStateOf(false) }
    val packResults = remember { mutableStateListOf<PhoenixPackResult>() }
    var pendingContentRemoval by remember { mutableStateOf<Pair<String, String>?>(null) }
    val totalRamMb = remember { Tools.getTotalDeviceMemory(context) }
    val freeRamMb = remember { Tools.getFreeDeviceMemory(context) }
    val maxRamMb = (totalRamMb * 0.60f).toInt().coerceAtLeast(512).coerceAtMost(6144)
    var ramAllocation by remember { mutableFloatStateOf(prefs.getInt("ram_allocation_${activeId}", LauncherPreferences.PREF_RAM_ALLOCATION).toFloat()) }
    val modResults = remember { mutableStateListOf<PhoenixModResult>() }
    val installedMods = remember { mutableStateListOf<PhoenixInstalledMod>() }
    var pendingModRemoval by remember { mutableStateOf<String?>(null) }
    val resourcePacks = remember { mutableStateListOf<PhoenixContentFile>() }
    val shaderPacks = remember { mutableStateListOf<PhoenixContentFile>() }
    val savedWorlds = remember { mutableStateListOf<String>() }
    val readinessItems = remember { mutableStateListOf<PhoenixReadinessItem>() }
    var readinessStatus by remember { mutableStateOf("Run a local scan to inspect this profile before launching.") }
    val diagnostics = remember { mutableStateListOf<PhoenixDiagnostic>() }
    val modChecks = remember { mutableStateListOf<PhoenixModCheck>() }
    val favouriteServers = remember { mutableStateListOf<PhoenixServer>().apply { addAll(readPhoenixServers(prefs)) } }
    var serverLabel by remember { mutableStateOf("") }
    var serverAddress by remember { mutableStateOf("") }
    var serverPort by remember { mutableStateOf("25565") }
    var serverStatus by remember { mutableStateOf("Save a server address, then test TCP reachability.") }
    var serverBusy by remember { mutableStateOf(false) }
    var themeChoice by remember { mutableStateOf(prefs.getString("theme_choice", "EMBER") ?: "EMBER") }
    var appearanceChoice by remember { mutableStateOf(prefs.getString("appearance_mode", "SYSTEM") ?: "SYSTEM") }
    val systemDark = isSystemInDarkTheme()
    var dashboardLayout by remember { mutableStateOf(prefs.getString("dashboard_layout", "EXPANDED") ?: "EXPANDED") }
    val snapshotFiles = remember { mutableStateListOf<File>() }
    var advancedStatus by remember { mutableStateOf("Phoenix Advanced Studio ready.") }
    var fpsPreset by remember { mutableStateOf(prefs.getString("fps_profile_${activeId}", "PERFORMANCE") ?: "PERFORMANCE") }
    var fpsStatus by remember { mutableStateOf("Choose a profile to tune Minecraft video settings for smoother gameplay.") }
    val extensionModules = remember { listOf(
        PhoenixExtension("crash_lab", "Crash Lab", "Log analysis and failure signatures"),
        PhoenixExtension("mod_radar", "Smart Mod Radar", "Loader metadata and disabled-mod checks"),
        PhoenixExtension("server_intelligence", "Server Intelligence", "Saved servers and latency tests"),
        PhoenixExtension("performance_autopilot", "Performance AutoPilot", "Device-aware recommendations"),
        PhoenixExtension("theme_studio", "Theme Studio", "Launcher appearance presets"),
        PhoenixExtension("profile_snapshots", "Profile Snapshots", "Local configuration restore points"),
        PhoenixExtension("session_timeline", "Session Timeline", "Launcher launch and activity events")
    ) }
    var toolboxStatus by remember { mutableStateOf("Manage resource packs, shader ZIPs and inspect saved worlds for the active profile.") }
    var editingId by remember { mutableStateOf<String?>(null) }
    val active = profiles.firstOrNull { it.id == activeId } ?: profiles.first()
    val studioAccent = when (themeChoice) { "FROST" -> Color(0xFF63C9FF); "VIOLET" -> Color(0xFFB38CFF); "MATRIX" -> Color(0xFF69E59A); else -> PhoenixOrange }
    val animatedAccent by animateColorAsState(targetValue = studioAccent, label = "phoenix-theme-accent")
    val useDarkAppearance = when (appearanceChoice) { "DARK" -> true; "LIGHT" -> false; else -> systemDark }
    val appearanceBg = if (useDarkAppearance) PhoenixBg else Color(0xFFF3F4F7)
    val appearancePanel = if (useDarkAppearance) PhoenixPanel else Color(0xFFFFFFFF)
    val appearanceMuted = if (useDarkAppearance) PhoenixMuted else Color(0xFF62616B)
    val appearanceText = if (useDarkAppearance) Color.White else Color(0xFF24232A)
    SideEffect { runCatching { System.setProperty("phoenix.ui.theme", if (useDarkAppearance) "dark" else "light") } }

    fun persist() {
        val synced = connectProfilesToEngine(profiles.toList(), activeId)
        profiles.clear()
        profiles.addAll(synced)
        val selected = profiles.firstOrNull { it.id == activeId } ?: profiles.first()
        writePhoenixProfiles(context, profiles, selected)
    }

    LaunchedEffect(Unit) { persist() }
    LaunchedEffect(activeId) {
        modLoader = prefs.getString("mod_loader_${activeId}", "fabric") ?: "fabric"
        ramAllocation = prefs.getInt("ram_allocation_${activeId}", LauncherPreferences.PREF_RAM_ALLOCATION).toFloat()
        fpsPreset = prefs.getString("fps_profile_${activeId}", "PERFORMANCE") ?: "PERFORMANCE"
        fpsStatus = "Performance settings loaded for " + active.name + "."
        avatarChoice = active.avatar
        prefs.edit().putString("cosmetic_avatar", active.avatar).apply()
    }
    LaunchedEffect(activeId) {
        snapshotFiles.clear()
        snapshotFiles.addAll(listPhoenixSnapshots(context, activeId))
    }
    LaunchedEffect(active.engineProfileId, page) {
        if (page == "Mods") {
            try {
                installedMods.clear()
                installedMods.addAll(listPhoenixInstalledMods(active.engineProfileId))
            } catch (error: Exception) {
                modStatus = error.message ?: "Could not read the active profile's mods folder."
            }
        }
        if (page == "Toolbox") {
            try {
                resourcePacks.clear()
                resourcePacks.addAll(listPhoenixContentFiles(active.engineProfileId, "resourcepacks"))
                shaderPacks.clear()
                shaderPacks.addAll(listPhoenixContentFiles(active.engineProfileId, "shaderpacks"))
                savedWorlds.clear()
                savedWorlds.addAll(listPhoenixWorlds(active.engineProfileId))
                toolboxStatus = "Library refreshed for ${active.name}."
            } catch (error: Exception) {
                toolboxStatus = error.message ?: "Could not read this profile's game folders."
            }
        }
    }

    MaterialTheme(colorScheme = darkColorScheme(
        primary = animatedAccent,
        secondary = animatedAccent,
        background = appearanceBg,
        surface = appearancePanel
    )) {
        MaterialTheme(colorScheme = if (useDarkAppearance) darkColorScheme(primary = animatedAccent, secondary = animatedAccent, background = appearanceBg, surface = appearancePanel, onSurface = appearanceText, onSurfaceVariant = appearanceMuted) else lightColorScheme(primary = animatedAccent, secondary = animatedAccent, background = appearanceBg, surface = appearancePanel, onSurface = appearanceText, onSurfaceVariant = appearanceMuted)) {
    Scaffold(
            containerColor = appearanceBg,
            bottomBar = {
                NavigationBar(containerColor = Color(0xFF151019)) {
                    listOf("Home", "Profiles", "Version", "Mods", "Packs", "Friends", "Settings").forEach { tab ->
                        NavigationBarItem(
                            selected = if (tab == "Packs") page == "Toolbox" else page == tab,
                            onClick = { page = if (tab == "Packs") "Toolbox" else tab },
                            icon = { Text(when(tab) { "Home" -> "⌂"; "Profiles" -> "◉"; "Version" -> "▤"; "Mods" -> "✦"; "Packs" -> "▧"; "Friends" -> "◎"; else -> "⚙" }, fontSize = 18.sp) },
                            label = { Text(if (tab == "Profiles") "Accounts" else tab, fontSize = 10.sp) }
                        )
                    }
                }
            }
        ) { padding ->
            LazyColumn(
                modifier = Modifier.fillMaxSize().background(appearanceBg).padding(padding),
                contentPadding = PaddingValues(if (dashboardLayout == "COMPACT") 12.dp else 18.dp),
                verticalArrangement = Arrangement.spacedBy(if (dashboardLayout == "COMPACT") 9.dp else 14.dp)
            ) {
                item {
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) { Image(painterResource(R.drawable.ic_phoenix_foreground), contentDescription = "Phoenix crest", modifier = Modifier.size(48.dp)); Text("PHOENIX CLIENT", color = animatedAccent, fontWeight = FontWeight.Black, letterSpacing = 2.sp, fontSize = 12.sp) }
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) { Text(avatarChoice, fontSize = 27.sp); Text("JAVA EDITION", color = appearanceText, fontSize = 26.sp, fontWeight = FontWeight.ExtraBold) }
                        Text("Your world. Your profiles. Your rules.", color = appearanceMuted, fontSize = 13.sp)
                    }
                }
                when (page) {
                    "Home" -> {
                        item {
                            Column(
                                Modifier.fillMaxWidth().background(
                                    Brush.linearGradient(listOf(animatedAccent.copy(alpha = 0.32f), when (bannerChoice) { "FROST" -> Color(0xFF173044); "OBSIDIAN" -> Color(0xFF29232D); else -> Color(0xFF542519) }, appearancePanel)),
                                    RoundedCornerShape(22.dp)
                                ).padding(20.dp),
                                verticalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                Text("RISE FROM THE ASHES", color = animatedAccent, fontWeight = FontWeight.Bold, letterSpacing = 2.sp, fontSize = 11.sp)
                                Text("Ready for your\nnext adventure?", color = appearanceText, fontSize = 29.sp, lineHeight = 33.sp, fontWeight = FontWeight.ExtraBold)
                                Text("Active profile: ${active.name}", color = appearanceMuted)
                                Text("Target version: ${active.version}", color = appearanceMuted)
                                Button(
                                    onClick = {
                                        persist()
                                        try {
                                            val cosmeticStatus = installPhoenixBundledCosmeticMod(context, active.engineProfileId, active.version, modLoader)
                                            notice = cosmeticStatus
                                        } catch (error: Exception) {
                                            notice = error.message ?: "Phoenix cosmetics add-on could not be installed."
                                        }
                                        recordPhoenixEvent(prefs, "Game launch requested", active.name + " · " + active.version)
                                        activity.startActivity(Intent(activity, LauncherActivity::class.java))
                                    },
                                    modifier = Modifier.fillMaxWidth().height(52.dp),
                                    colors = ButtonDefaults.buttonColors(containerColor = animatedAccent),
                                    shape = RoundedCornerShape(14.dp)
                                ) { Text("OPEN GAME LAUNCHER  →", color = Color.Black, fontWeight = FontWeight.Black) }
                            }
                        }
                        item { PhoenixInfo("CONNECTED ENGINE PROFILE", active.name, "Saved directly to the engine's launcher_profiles.json", animatedAccent) }
                        item { PhoenixInfo("LOCAL PHOENIX PROFILES", profiles.size.toString(), "These save launcher settings and a local nickname only. They do not sign in to Minecraft or grant game ownership.", animatedAccent) }
                        item {
                            Column(
                                Modifier.fillMaxWidth().background(appearancePanel, RoundedCornerShape(16.dp)).padding(15.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Text("MOJO CONTROL MAP IMPORT", color = animatedAccent, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                                Text("Bring a Mojo Launcher / MJLauncher JSON touch layout into Phoenix. Phoenix converts supported legacy layouts, saves a separate copy and selects it for the active profile without overwriting your existing maps.", color = appearanceMuted, fontSize = 12.sp)
                                Button(
                                    onClick = { controlMapPicker.launch(arrayOf("application/json", "text/json", "text/plain", "*/*")) },
                                    modifier = Modifier.fillMaxWidth(),
                                    colors = ButtonDefaults.buttonColors(containerColor = animatedAccent)
                                ) { Text("CHOOSE & IMPORT CONTROL MAP", color = Color.Black, fontWeight = FontWeight.Bold) }
                            }
                        }
                        item {
                            Button(
                                onClick = { page = "Profiles" },
                                modifier = Modifier.fillMaxWidth(),
                                colors = ButtonDefaults.buttonColors(containerColor = animatedAccent)
                            ) { Text("MANAGE LOCAL PROFILES", color = Color.Black, fontWeight = FontWeight.Bold) }
                        }
                        if (active.version.contains("26.3", ignoreCase = true) || active.version.contains("26.2", ignoreCase = true)) {
                            item {
                                Column(
                                    Modifier.fillMaxWidth().background(Color(0xFF241A13), RoundedCornerShape(16.dp)).padding(15.dp),
                                    verticalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Text("MALI 26.3 RENDERER COMPATIBILITY", color = Color(0xFFFFB36B), fontWeight = FontWeight.Bold, fontSize = 12.sp)
                                    Text("Your crash log shows MobileGlues failing to create the OpenGL backend. Amethyst's 26.2+ guidance for non-Adreno GPUs recommends Kopper Zink. Apply it to this profile; it takes effect on the next launch.", color = appearanceMuted, fontSize = 12.sp)
                                    Button(
                                        onClick = {
                                            try {
                                                if (LauncherProfiles.mainProfileJson == null) LauncherProfiles.load()
                                                val engineProfile = LauncherProfiles.mainProfileJson.profiles[active.engineProfileId]
                                                    ?: throw java.io.IOException("Active engine profile not found.")
                                                engineProfile.pojavRendererName = "vulkan_zink"
                                                LauncherProfiles.write()
                                                notice = "Kopper Zink selected for ${active.name}. Fully close and relaunch Minecraft 26.3 to test."
                                            } catch (error: Exception) {
                                                notice = error.message ?: "Could not update this profile's renderer."
                                            }
                                        },
                                        modifier = Modifier.fillMaxWidth(),
                                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFFB36B))
                                    ) { Text("APPLY KOPPER ZINK FIX", color = Color.Black, fontWeight = FontWeight.Bold) }
                                }
                            }
                        }
                        item {
                            PhoenixInfo(
                                "LOCAL / OFFLINE ACCOUNT",
                                "Create a local player name",
                                "Use the engine's Local Login option to create a device-only offline profile. It does not contact Microsoft or authenticate ownership. Offline profiles are for local/offline play; online-mode servers still require a valid authenticated account.",
                                animatedAccent
                            )
                        }
                        item {
                            Button(
                                onClick = { uriHandler.openUri("https://discord.gg/MnxS3g8gxA") },
                                modifier = Modifier.fillMaxWidth().height(52.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF5865F2)),
                                shape = RoundedCornerShape(14.dp)
                            ) {
                                DiscordMark()
                                Spacer(Modifier.width(10.dp))
                                Text("JOIN OUR DISCORD SERVER", color = appearanceText, fontWeight = FontWeight.Bold)
                            }
                        }
                        item {
                            Button(
                                onClick = { page = "Friends" },
                                modifier = Modifier.fillMaxWidth(),
                                colors = ButtonDefaults.buttonColors(containerColor = animatedAccent)
                            ) { Text("FRIENDS & PRIVATE CHAT  →", color = Color.Black, fontWeight = FontWeight.Bold) }
                        }
                        item {
                            OutlinedButton(
                                onClick = { page = "Credits" },
                                modifier = Modifier.fillMaxWidth()
                            ) { Text("ABOUT PHOENIX & CREATOR CREDITS", color = animatedAccent, fontWeight = FontWeight.Bold) }
                        }
                        item { Text(notice, color = appearanceMuted, fontSize = 12.sp) }
                        item {
                            Row(
                                Modifier.fillMaxWidth()
                                    .background(appearancePanel, RoundedCornerShape(18.dp))
                                    .clickable {
                                        activity.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://discord.gg/MnxS3g8gxA")))
                                    }
                                    .padding(16.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                Image(
                                    painter = painterResource(R.drawable.ic_discord),
                                    contentDescription = "Discord",
                                    modifier = Modifier.size(42.dp)
                                )
                                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                                    Text("PHOENIX COMMUNITY", color = appearanceText, fontWeight = FontWeight.Bold)
                                    Text("Join our Discord server", color = appearanceMuted, fontSize = 12.sp)
                                }
                                Text("JOIN  →", color = animatedAccent, fontSize = 12.sp, fontWeight = FontWeight.Black)
                            }
                        }
                    }
                    "Friends" -> {
                        item {
                            PhoenixSocialScreen(context = context, accent = animatedAccent)
                        }
                    }
                    "Profiles" -> {
                        item {
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                                Column(Modifier.weight(1f)) {
                                    Text("Player profiles", color = appearanceText, fontSize = 22.sp, fontWeight = FontWeight.Bold)
                                    Text("Each profile is synchronized with the engine.", color = appearanceMuted, fontSize = 12.sp)
                                }
                                Button(onClick = {
                                    editingId = null
                                    draftName = "New profile"
                                    draftNickname = "PhoenixPlayer"
                                    draftVersion = active.version
                                    draftAvatar = active.avatar
                                    dialogOpen = true
                                }, colors = ButtonDefaults.buttonColors(containerColor = animatedAccent)) { Text("+ ADD", color = Color.Black) }
                            }
                        }
                        items(profiles, key = { it.id }) { profile ->
                            Column(
                                Modifier.fillMaxWidth().background(appearancePanel, RoundedCornerShape(16.dp))
                                    .clickable { activeId = profile.id; persist(); notice = "${profile.name} is now the active engine profile." }
                                    .padding(15.dp),
                                verticalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) { Text(profile.avatar, fontSize = 20.sp); Text(profile.name, color = appearanceText, fontWeight = FontWeight.Bold) }
                                    if (profile.id == activeId) Text("ACTIVE", color = animatedAccent, fontSize = 10.sp, fontWeight = FontWeight.Black)
                                }
                                Text("@${profile.nickname}  ·  Minecraft ${profile.version}", color = appearanceMuted, fontSize = 12.sp)
                                Text("Engine profile: ${profile.engineProfileId.take(8)}…", color = appearanceMuted, fontSize = 10.sp)
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    TextButton(onClick = {
                                        editingId = profile.id
                                        draftName = profile.name
                                        draftNickname = profile.nickname
                                        draftVersion = profile.version
                                        draftAvatar = profile.avatar
                                        dialogOpen = true
                                    }) { Text("EDIT", color = animatedAccent) }
                                    if (profiles.size > 1) TextButton(onClick = {
                                        profiles.remove(profile)
                                        if (activeId == profile.id) activeId = profiles.first().id
                                        persist()
                                    }) { Text("DELETE", color = Color(0xFFFF8A80)) }
                                }
                            }
                        }
                    }
                    "Version" -> {
                        item {
                            Text("Game version", color = appearanceText, fontSize = 22.sp, fontWeight = FontWeight.Bold)
                            Text("This writes the selected version to the active engine profile. The game files still need to be installed from the game launcher.", color = appearanceMuted, fontSize = 12.sp)
                        }
                        item {
                            OutlinedTextField(
                                value = draftVersion.ifBlank { active.version },
                                onValueChange = { draftVersion = it.trim() },
                                label = { Text("Minecraft version ID") },
                                placeholder = { Text("e.g. 1.21.4 or 26.1") },
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth()
                            )
                            Button(onClick = {
                                val index = profiles.indexOfFirst { it.id == activeId }
                                if (index >= 0 && draftVersion.isNotBlank()) {
                                    profiles[index] = profiles[index].copy(version = draftVersion)
                                    persist()
                                    notice = "Version saved to the active engine profile."
                                }
                            }, modifier = Modifier.fillMaxWidth(), colors = ButtonDefaults.buttonColors(containerColor = animatedAccent)) {
                                Text("SAVE VERSION TO ENGINE PROFILE", color = Color.Black, fontWeight = FontWeight.Bold)
                            }
                        }
                        item { Text("Popular version IDs", color = appearanceMuted, fontWeight = FontWeight.Bold) }
                        items(listOf("1.21.4", "1.20.4", "1.19.4", "1.16.5", "1.12.2", "1.8.9", "latest-release", "latest-snapshot")) { version ->
                            Row(
                                Modifier.fillMaxWidth().background(appearancePanel, RoundedCornerShape(12.dp))
                                    .clickable { draftVersion = version }.padding(14.dp),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text("Minecraft $version", color = appearanceText)
                                Text("SELECT", color = animatedAccent, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                    "Mods" -> {
                        item {
                            PhoenixInfo("PHOENIX MOD HUB", "Powered by Modrinth", "Search public mods and install a compatible JAR into the active Minecraft profile's mods folder. Check each project's description and trust before installing.", animatedAccent)
                        }
                        item {
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                                    Text("INSTALLED MODS", color = animatedAccent, fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.5.sp)
                                    Text("${installedMods.size} JAR file(s) in ${active.name}", color = appearanceMuted, fontSize = 12.sp)
                                }
                                OutlinedButton(enabled = !modBusy, onClick = {
                                    try {
                                        installedMods.clear()
                                        installedMods.addAll(listPhoenixInstalledMods(active.engineProfileId))
                                        modStatus = "Installed mod list refreshed."
                                    } catch (error: Exception) {
                                        modStatus = error.message ?: "Could not refresh installed mods."
                                    }
                                }) { Text("REFRESH", color = animatedAccent) }
                            }
                        }
                        if (installedMods.isEmpty()) {
                            item { Text("No .jar mods found in this profile's mods folder yet.", color = appearanceMuted, fontSize = 12.sp) }
                        }
                        items(installedMods, key = { it.fileName }) { installed ->
                            Row(
                                Modifier.fillMaxWidth().background(appearancePanel, RoundedCornerShape(14.dp)).padding(13.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                                    Text(installed.fileName, color = appearanceText, fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                                    Text("${installed.sizeBytes / 1024} KB  ·  ${if (installed.enabled) "Enabled" else "Disabled"}", color = appearanceMuted, fontSize = 10.sp)
                                }
                                TextButton(onClick = {
                                    try {
                                        val renamed = togglePhoenixInstalledMod(active.engineProfileId, installed.fileName, !installed.enabled)
                                        installedMods.clear()
                                        installedMods.addAll(listPhoenixInstalledMods(active.engineProfileId))
                                        modStatus = "${if (installed.enabled) "Disabled" else "Enabled"} $renamed. Restart Minecraft to apply."
                                    } catch (error: Exception) {
                                        modStatus = error.message ?: "Could not change mod state."
                                    }
                                }) {
                                    Text(if (installed.enabled) "DISABLE" else "ENABLE", color = animatedAccent, fontWeight = FontWeight.Bold)
                                }
                                TextButton(onClick = { pendingModRemoval = installed.fileName }) {
                                    Text("REMOVE", color = Color(0xFFFF8A80), fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                        item {
                            OutlinedTextField(
                                value = modQuery,
                                onValueChange = { modQuery = it },
                                modifier = Modifier.fillMaxWidth(),
                                label = { Text("Search mods") },
                                placeholder = { Text("Sodium, Iris, Lithium...") },
                                singleLine = true
                            )
                        }
                        item {
                            Text("MOD LOADER", color = animatedAccent, fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.5.sp)
                            Row(horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                                listOf("fabric", "forge", "neoforge", "quilt").forEach { loader ->
                                    FilterChip(
                                        selected = modLoader == loader,
                                        onClick = {
                                            modLoader = loader
                                            prefs.edit().putString("mod_loader_${activeId}", loader).apply()
                                        },
                                        label = { Text(loader.replaceFirstChar { it.uppercase() }, fontSize = 10.sp) }
                                    )
                                }
                            }
                            val gameVersion = Regex("\\d+\\.\\d+(\\.\\d+)?").find(active.version)?.value ?: active.version
                            Text("Minecraft $gameVersion", color = appearanceMuted, fontSize = 12.sp)
                            Button(
                                enabled = !modBusy && modQuery.isNotBlank(),
                                onClick = {
                                    modBusy = true
                                    modStatus = "Searching Modrinth..."
                                    Thread {
                                        try {
                                            val version = Regex("\\d+\\.\\d+(\\.\\d+)?").find(active.version)?.value ?: active.version
                                            val found = searchPhoenixMods(modQuery.trim(), version, modLoader)
                                            activity.runOnUiThread {
                                                modResults.clear()
                                                modResults.addAll(found)
                                                modStatus = if (found.isEmpty()) "No compatible mods found. Check the version and loader filters." else "Found ${found.size} compatible projects."
                                                modBusy = false
                                            }
                                        } catch (error: Exception) {
                                            activity.runOnUiThread { modStatus = error.message ?: "Mod search failed."; modBusy = false }
                                        }
                                    }.start()
                                },
                                modifier = Modifier.fillMaxWidth(),
                                colors = ButtonDefaults.buttonColors(containerColor = animatedAccent)
                            ) { Text(if (modBusy) "SEARCHING..." else "SEARCH MODRINTH", color = Color.Black, fontWeight = FontWeight.Bold) }
                            Text(modStatus, color = appearanceMuted, fontSize = 12.sp)
                        }
                        items(modResults, key = { it.projectId }) { mod ->
                            Column(
                                Modifier.fillMaxWidth().background(appearancePanel, RoundedCornerShape(16.dp)).padding(15.dp),
                                verticalArrangement = Arrangement.spacedBy(7.dp)
                            ) {
                                Text(mod.title, color = appearanceText, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                                Text(mod.description, color = appearanceMuted, fontSize = 12.sp, maxLines = 3)
                                Text("${mod.downloads} downloads  ·  ${mod.slug}", color = animatedAccent, fontSize = 10.sp)
                                Button(
                                    enabled = !modBusy,
                                    onClick = {
                                        modBusy = true
                                        modStatus = "Checking compatible release and downloading ${mod.title}..."
                                        Thread {
                                            try {
                                                val version = Regex("\\d+\\.\\d+(\\.\\d+)?").find(active.version)?.value ?: active.version
                                                val installed = installPhoenixMod(mod, version, modLoader, active.engineProfileId)
                                                activity.runOnUiThread {
                                                    installedMods.clear()
                                                    installedMods.addAll(listPhoenixInstalledMods(active.engineProfileId))
                                                    modStatus = "Installed $installed. Restart Minecraft to load it."
                                                    modBusy = false
                                                }
                                            } catch (error: Exception) {
                                                activity.runOnUiThread { modStatus = error.message ?: "Mod installation failed."; modBusy = false }
                                            }
                                        }.start()
                                    },
                                    colors = ButtonDefaults.buttonColors(containerColor = animatedAccent)
                                ) { Text("DOWNLOAD & INSTALL", color = Color.Black, fontWeight = FontWeight.Bold) }
                            }
                        }
                        item {
                            Text("Mod compatibility depends on the installed loader and Minecraft version. Phoenix does not guarantee third-party mod safety; only install projects you trust.", color = appearanceMuted, fontSize = 11.sp)
                            TextButton(onClick = { persist(); activity.startActivity(Intent(activity, LauncherActivity::class.java)) }) {
                                Text("OPEN ENGINE MODPACK / LOADER MANAGER", color = animatedAccent)
                            }
                        }
                    }
                    "Settings" -> {
                        item {
                            PhoenixInfo("ENGINE SETTINGS", "Device & controls", "Open the original engine settings for renderer, Java runtime, controls and account management. These remain connected to the real engine.", animatedAccent)
                            Text("APP APPEARANCE", color = animatedAccent, fontWeight = FontWeight.Bold, fontSize = 11.sp, letterSpacing = 1.3.sp)
                            Row(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                                listOf("SYSTEM", "DARK", "LIGHT").forEach { mode ->
                                    FilterChip(selected = appearanceChoice == mode, onClick = {
                                        appearanceChoice = mode
                                        prefs.edit().putString("appearance_mode", mode).apply()
                                    }, label = { Text(mode, fontSize = 10.sp) })
                                }
                            }
                            Text(if (appearanceChoice == "SYSTEM") "Follows your Android light/dark setting." else "Appearance is set to " + appearanceChoice.lowercase() + ".", color = appearanceMuted, fontSize = 11.sp)
                            Button(onClick = { page = "Performance" }, modifier = Modifier.fillMaxWidth(),
                                colors = ButtonDefaults.buttonColors(containerColor = animatedAccent)) {
                                Text("OPEN PERFORMANCE TUNING", color = Color.Black, fontWeight = FontWeight.Bold)
                            }
                            Button(onClick = { page = "Toolbox" }, modifier = Modifier.fillMaxWidth(),
                                colors = ButtonDefaults.buttonColors(containerColor = appearancePanel)) {
                                Text("OPEN GAME TOOLBOX", color = animatedAccent, fontWeight = FontWeight.Bold)
                            }
                            Button(onClick = { page = "Intelligence" }, modifier = Modifier.fillMaxWidth(),
                                colors = ButtonDefaults.buttonColors(containerColor = appearancePanel)) {
                                Text("OPEN LAUNCH INTELLIGENCE", color = animatedAccent, fontWeight = FontWeight.Bold)
                            }
                            Button(onClick = { page = "Advanced" }, modifier = Modifier.fillMaxWidth(),
                                colors = ButtonDefaults.buttonColors(containerColor = appearancePanel)) {
                                Text("OPEN PHOENIX ADVANCED STUDIO", color = animatedAccent, fontWeight = FontWeight.Bold)
                            }
                            Button(onClick = { page = "Credits" }, modifier = Modifier.fillMaxWidth(),
                                colors = ButtonDefaults.buttonColors(containerColor = appearancePanel)) {
                                Text("ABOUT PHOENIX & CREATOR CREDITS", color = animatedAccent, fontWeight = FontWeight.Bold)
                            }
                            Button(onClick = { persist(); activity.startActivity(Intent(activity, LauncherActivity::class.java)) },
                                modifier = Modifier.fillMaxWidth(), colors = ButtonDefaults.buttonColors(containerColor = appearancePanel)) {
                                Text("OPEN ENGINE SETTINGS", color = animatedAccent, fontWeight = FontWeight.Bold)
                            }
                            Text("Microsoft sign-in uses the engine's official authentication screen. Phoenix does not collect or store your Microsoft password.", color = appearanceMuted, fontSize = 12.sp)
                        }
                    }
                    "Credits" -> {
                         item {
                             TextButton(onClick = { page = "Settings" }) { Text("← SETTINGS", color = animatedAccent) }
                             Column(
                                 Modifier.fillMaxWidth().background(
                                     Brush.linearGradient(listOf(Color(0xFF48251B), Color(0xFF241823), appearancePanel)),
                                     RoundedCornerShape(22.dp)
                                 ).padding(20.dp),
                                 verticalArrangement = Arrangement.spacedBy(9.dp)
                             ) {
                                 Text("PHOENIX CLIENT", color = animatedAccent, fontSize = 11.sp, fontWeight = FontWeight.Black, letterSpacing = 2.sp)
                                 Text("Built to bring Java Edition closer to mobile.", color = appearanceText, fontSize = 23.sp, fontWeight = FontWeight.ExtraBold)
                                 Text("An independent Minecraft Java launcher project focused on performance, customization and a better mobile experience.", color = appearanceMuted, fontSize = 13.sp)
                             }
                         }
                         item {
                             Column(Modifier.fillMaxWidth().background(appearancePanel, RoundedCornerShape(18.dp)).padding(17.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
                                 Text("FOUNDER & DEVELOPER", color = animatedAccent, fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.5.sp)
                                 Text("Dipayan Roy", color = appearanceText, fontSize = 25.sp, fontWeight = FontWeight.ExtraBold)
                                 Text("Developer · Community Builder · Creator", color = animatedAccent, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                                 Text("Phoenix Client is created and developed by Dipayan Roy, an independent Android-first developer building Minecraft, hosting and web projects with a focus on practical tools, community and futuristic experiences.", color = appearanceMuted, fontSize = 13.sp)
                                 Text("Project: Phoenix Client · Android launcher with an integrated Java Edition engine", color = appearanceText, fontSize = 12.sp)
                             }
                         }
                         item {
                             Column(Modifier.fillMaxWidth().background(appearancePanel, RoundedCornerShape(18.dp)).padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                 Text("DEVELOPER LINKS", color = animatedAccent, fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.3.sp)
                                 Text("GitHub · @dipayan900", color = appearanceText, fontWeight = FontWeight.SemiBold, modifier = Modifier.fillMaxWidth().clickable { uriHandler.openUri("https://github.com/dipayan900") }.padding(vertical = 5.dp))
                                 Text("Portfolio · dipayanportfolios.netlify.app", color = appearanceText, fontWeight = FontWeight.SemiBold, modifier = Modifier.fillMaxWidth().clickable { uriHandler.openUri("https://dipayanportfolios.netlify.app/") }.padding(vertical = 5.dp))
                             }
                         }
                         item {
                             Column(Modifier.fillMaxWidth().background(appearancePanel, RoundedCornerShape(18.dp)).padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                 Text("CREDITS & ACKNOWLEDGEMENTS", color = animatedAccent, fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.3.sp)
                                 Text("Built on the open-source Amethyst / PojavLauncher ecosystem. Minecraft and Java Edition are trademarks of their respective owners. Phoenix Client is an independent community project and is not an official Mojang or Microsoft product.", color = appearanceMuted, fontSize = 12.sp)
                                 Text("Thank you to the open-source maintainers, contributors, testers and Phoenix community members who make projects like this possible.", color = appearanceMuted, fontSize = 12.sp)
                             }
                         }
                     }
                     "Intelligence" -> {
                        item {
                            TextButton(onClick = { page = "Settings" }) { Text("← SETTINGS", color = animatedAccent) }
                            PhoenixInfo("PHOENIX LAUNCH INTELLIGENCE", "Pre-flight system scan", "A local, read-only health check for the active game profile. It does not modify or delete Minecraft files.", animatedAccent)
                            Button(onClick = {
                                try {
                                    readinessItems.clear()
                                    readinessItems.addAll(scanPhoenixReadiness(active.engineProfileId))
                                    val warnings = readinessItems.count { !it.healthy }
                                    readinessStatus = if (warnings == 0) "Scan complete. No basic profile warnings detected." else "Scan complete. $warnings item(s) need attention."
                                } catch (error: Exception) {
                                    readinessStatus = error.message ?: "The profile scan could not be completed."
                                }
                            }, modifier = Modifier.fillMaxWidth(), colors = ButtonDefaults.buttonColors(containerColor = animatedAccent)) {
                                Text("RUN PROFILE SCAN", color = Color.Black, fontWeight = FontWeight.Bold)
                            }
                            Text(readinessStatus, color = appearanceMuted, fontSize = 12.sp)
                        }
                        items(readinessItems, key = { it.title }) { result ->
                            Row(
                                Modifier.fillMaxWidth().background(appearancePanel, RoundedCornerShape(14.dp)).padding(14.dp),
                                horizontalArrangement = Arrangement.spacedBy(12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(if (result.healthy) "●" else "!", color = if (result.healthy) Color(0xFF7DDB9A) else Color(0xFFFFB36B), fontSize = 18.sp, fontWeight = FontWeight.Black)
                                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                                    Text(result.title, color = appearanceText, fontWeight = FontWeight.Bold)
                                    Text(result.value, color = animatedAccent, fontSize = 13.sp)
                                    Text(result.detail, color = appearanceMuted, fontSize = 11.sp)
                                }
                            }
                        }
                        item {
                            Text("This is a lightweight file-system check, not a guarantee that a modpack or Minecraft version will launch. For a crash diagnosis, inspect the engine's full log and crash report.", color = appearanceMuted, fontSize = 11.sp)
                        }
                    }
                    "Advanced" -> {
                        item {
                            TextButton(onClick = { page = "Settings" }) { Text("← SETTINGS", color = animatedAccent) }
                            PhoenixInfo("PHOENIX ADVANCED STUDIO", "Eight connected systems", "Diagnostics, compatibility checks, server tools, performance recommendations, themes, snapshots, activity history and a safe extension registry.", animatedAccent)
                            Text(advancedStatus, color = appearanceMuted, fontSize = 12.sp)
                        }
                        item {
                            PhoenixInfo("CRASH LAB", "Local log diagnostics", "Reads this profile's latest.log and recent crash reports. Findings are heuristic suggestions, not guaranteed root-cause analysis.", animatedAccent)
                            Button(onClick = {
                                try {
                                    diagnostics.clear()
                                    diagnostics.addAll(diagnosePhoenixLogs(active.engineProfileId))
                                    advancedStatus = "Crash Lab analysed the available local log files."
                                } catch (error: Exception) { advancedStatus = error.message ?: "Could not analyse local logs." }
                            }, modifier = Modifier.fillMaxWidth(), colors = ButtonDefaults.buttonColors(containerColor = animatedAccent)) {
                                Text("ANALYSE CRASH REPORTS & LOGS", color = Color.Black, fontWeight = FontWeight.Bold)
                            }
                        }
                        items(diagnostics, key = { it.title }) { finding ->
                            Column(Modifier.fillMaxWidth().background(appearancePanel, RoundedCornerShape(14.dp)).padding(14.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                                Text(finding.title, color = animatedAccent, fontWeight = FontWeight.Bold)
                                Text(finding.cause, color = appearanceText, fontSize = 12.sp)
                                Text("Suggested fix: " + finding.fix, color = appearanceMuted, fontSize = 11.sp)
                            }
                        }
                        item {
                            PhoenixInfo("SMART MOD RADAR", "Inspect installed mod JARs", "Checks enabled/disabled state and common loader metadata inside mod archives. It cannot prove full Minecraft version compatibility.", animatedAccent)
                            Button(onClick = {
                                try {
                                    modChecks.clear()
                                    modChecks.addAll(scanPhoenixMods(active.engineProfileId, modLoader))
                                    advancedStatus = "Smart Mod Radar checked " + modChecks.size + " mod file(s) against " + modLoader + "."
                                } catch (error: Exception) { advancedStatus = error.message ?: "Mod scan failed." }
                            }, modifier = Modifier.fillMaxWidth(), colors = ButtonDefaults.buttonColors(containerColor = animatedAccent)) {
                                Text("SCAN MOD COMPATIBILITY", color = Color.Black, fontWeight = FontWeight.Bold)
                            }
                        }
                        items(modChecks, key = { it.name }) { check ->
                            Column(Modifier.fillMaxWidth().background(appearancePanel, RoundedCornerShape(12.dp)).padding(13.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Text(check.name, color = appearanceText, fontWeight = FontWeight.SemiBold, fontSize = 12.sp)
                                Text(check.state, color = if (check.warning) Color(0xFFFFB36B) else animatedAccent, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                Text(check.detail, color = appearanceMuted, fontSize = 11.sp)
                            }
                        }
                        item {
                            PhoenixInfo("SERVER INTELLIGENCE", "Favourites & TCP latency", "Save Java server addresses and test whether their game port accepts a TCP connection. This is not the Minecraft server-list ping protocol.", animatedAccent)
                            OutlinedTextField(serverLabel, { serverLabel = it }, modifier = Modifier.fillMaxWidth(), label = { Text("Server name") }, singleLine = true)
                            OutlinedTextField(serverAddress, { serverAddress = it }, modifier = Modifier.fillMaxWidth(), label = { Text("Host or IP address") }, singleLine = true)
                            OutlinedTextField(serverPort, { serverPort = it }, modifier = Modifier.fillMaxWidth(), label = { Text("Port (default 25565)") }, singleLine = true)
                            Button(onClick = {
                                val host = serverAddress.trim()
                                val port = serverPort.toIntOrNull()
                                if (host.isBlank() || host.contains("/") || host.contains(" ")) serverStatus = "Enter a valid host or IP."
                                else if (port == null || port !in 1..65535) serverStatus = "Enter a port from 1 to 65535."
                                else if (favouriteServers.any { it.host.equals(host, true) && it.port == port }) serverStatus = "That server is already saved."
                                else {
                                    favouriteServers.add(PhoenixServer(serverLabel.trim().ifBlank { host }, host, port))
                                    writePhoenixServers(prefs, favouriteServers)
                                    recordPhoenixEvent(prefs, "Server saved", host + ":" + port)
                                    serverStatus = "Saved " + host + ":" + port + " to favourites."
                                    serverLabel = ""
                                }
                            }, modifier = Modifier.fillMaxWidth(), colors = ButtonDefaults.buttonColors(containerColor = animatedAccent)) {
                                Text("SAVE SERVER FAVOURITE", color = Color.Black, fontWeight = FontWeight.Bold)
                            }
                            Text(serverStatus, color = appearanceMuted, fontSize = 12.sp)
                        }
                        items(favouriteServers, key = { it.host + ":" + it.port }) { server ->
                            Column(Modifier.fillMaxWidth().background(appearancePanel, RoundedCornerShape(13.dp)).padding(13.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                                Text(server.label, color = appearanceText, fontWeight = FontWeight.Bold)
                                Text(server.host + ":" + server.port, color = appearanceMuted, fontSize = 11.sp)
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    TextButton(enabled = !serverBusy, onClick = {
                                        serverBusy = true
                                        serverStatus = "Testing " + server.host + ":" + server.port + "..."
                                        Thread {
                                            val result = runCatching { pingPhoenixServer(server.host, server.port) }
                                            activity.runOnUiThread {
                                                serverStatus = result.fold({ "TCP connection accepted in " + it + " ms. Not a full Minecraft ping." }, { "No TCP response: " + (it.message ?: "connection failed") })
                                                serverBusy = false
                                                if (result.isSuccess) recordPhoenixEvent(prefs, "Server latency test", server.host + ":" + server.port + " · " + result.getOrNull() + " ms")
                                            }
                                        }.start()
                                    }) { Text(if (serverBusy) "TESTING…" else "TEST LATENCY", color = animatedAccent) }
                                    TextButton(onClick = {
                                        favouriteServers.remove(server)
                                        writePhoenixServers(prefs, favouriteServers)
                                        serverStatus = "Removed " + server.label + " from favourites."
                                    }) { Text("REMOVE", color = Color(0xFFFF8A80)) }
                                }
                            }
                        }
                        item {
                            PhoenixInfo("PERFORMANCE AUTOPILOT", "Device-aware recommendations", "Suggests conservative memory settings based on available RAM and detected mod/shader load. It never changes settings silently.", animatedAccent)
                            val detectedMods = runCatching { listPhoenixInstalledMods(active.engineProfileId).count { it.enabled } }.getOrDefault(0)
                            val hasShaders = runCatching { listPhoenixContentFiles(active.engineProfileId, "shaderpacks").any { it.enabled } }.getOrDefault(false)
                            val recommendedRam = minOf(maxRamMb, (freeRamMb * 0.55f).toInt().coerceAtLeast(512), (totalRamMb * if (detectedMods > 30 || hasShaders) 0.42f else 0.50f).toInt()).coerceAtLeast(512)
                            Text("Device RAM: " + totalRamMb + " MB · currently free: " + freeRamMb + " MB", color = appearanceMuted, fontSize = 12.sp)
                            Text("Enabled mods: " + detectedMods + " · shaders present: " + if (hasShaders) "yes" else "no", color = appearanceMuted, fontSize = 12.sp)
                            Text("Suggested Minecraft allocation: " + recommendedRam + " MB", color = animatedAccent, fontWeight = FontWeight.Bold)
                            Button(onClick = {
                                ramAllocation = recommendedRam.toFloat()
                                prefs.edit().putInt("ram_allocation_" + activeId, recommendedRam).apply()
                                LauncherPreferences.DEFAULT_PREF.edit().putInt("allocation", recommendedRam).apply()
                                recordPhoenixEvent(prefs, "Performance recommendation applied", recommendedRam.toString() + " MB RAM allocation")
                                advancedStatus = "Applied recommended RAM allocation. You can change it again in Performance Center."
                            }, modifier = Modifier.fillMaxWidth(), colors = ButtonDefaults.buttonColors(containerColor = animatedAccent)) {
                                Text("APPLY RECOMMENDED RAM", color = Color.Black, fontWeight = FontWeight.Bold)
                            }
                        }
                        item {
                            PhoenixInfo("PHOENIX FPS & FRAME-PACING LAB", "Tune Minecraft for smoother gameplay", "These presets edit this profile's options.txt before launch. They reduce GPU/CPU workload; actual FPS depends on the device, renderer, Minecraft version, mods and world. Minecraft must be fully closed before applying.", animatedAccent)
                            Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth()) {
                                listOf("ULTRA", "PERFORMANCE", "BALANCED").forEach { preset ->
                                    FilterChip(selected = fpsPreset == preset, onClick = {
                                        fpsPreset = preset
                                        prefs.edit().putString("fps_profile_${activeId}", preset).apply()
                                    }, label = { Text(preset, fontSize = 9.sp) })
                                }
                            }
                            Text(when (fpsPreset) {
                                "ULTRA" -> "4 chunks render · 3 simulation · low particles · clouds off · 60 FPS cap"
                                "BALANCED" -> "8 chunks render · 5 simulation · reduced effects · 60 FPS cap"
                                else -> "6 chunks render · 4 simulation · low effects · 60 FPS cap"
                            }, color = appearanceMuted, fontSize = 12.sp)
                            Button(onClick = {
                                try {
                                    fpsStatus = applyPhoenixFpsProfile(active.engineProfileId, fpsPreset)
                                    recordPhoenixEvent(prefs, "FPS profile applied", fpsPreset + " · " + active.name)
                                } catch (error: Exception) { fpsStatus = error.message ?: "Could not apply Minecraft video settings." }
                            }, modifier = Modifier.fillMaxWidth(), colors = ButtonDefaults.buttonColors(containerColor = animatedAccent)) {
                                Text("APPLY FPS PROFILE", color = Color.Black, fontWeight = FontWeight.Bold)
                            }
                            OutlinedButton(onClick = {
                                try {
                                    fpsStatus = restorePhoenixOptions(active.engineProfileId)
                                    recordPhoenixEvent(prefs, "Video settings restored", active.name)
                                } catch (error: Exception) { fpsStatus = error.message ?: "Could not restore the backup." }
                            }, modifier = Modifier.fillMaxWidth()) { Text("RESTORE ORIGINAL VIDEO SETTINGS", color = animatedAccent) }
                            Text(fpsStatus, color = appearanceMuted, fontSize = 12.sp)
                            Text("MORE FPS TIPS", color = animatedAccent, fontWeight = FontWeight.Bold, fontSize = 11.sp)
                            Text("• Disable shader packs on mobile when chasing stable FPS.\n• Prefer lightweight Fabric performance mods that match your exact Minecraft version.\n• Keep render distance modest; simulation distance also affects CPU load.\n• Close background apps and leave thermal headroom; overheating can reduce performance.\n• Keep enough free storage for game files and Java runtime.\n• If a renderer fails, use a compatible renderer rather than forcing unsupported Vulkan features.", color = appearanceMuted, fontSize = 12.sp, lineHeight = 18.sp)
                        }
                        item {
                            PhoenixInfo("PHOENIX THEME STUDIO", "Personalise the dashboard", "Choose a Phoenix colour identity, banner and compact or expanded layout. Theme gradients transition with the selected preset.", animatedAccent)
                            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                listOf("EMBER", "FROST", "VIOLET", "MATRIX").forEach { theme ->
                                    FilterChip(selected = themeChoice == theme, onClick = {
                                        themeChoice = theme
                                        prefs.edit().putString("theme_choice", theme).apply()
                                        bannerChoice = if (theme == "FROST") "FROST" else if (theme == "EMBER") "EMBER" else "OBSIDIAN"
                                        prefs.edit().putString("cosmetic_banner", bannerChoice).apply()
                                    }, label = { Text(theme, fontSize = 10.sp) })
                                }
                            }
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                FilterChip(selected = dashboardLayout == "COMPACT", onClick = {
                                    dashboardLayout = "COMPACT"; prefs.edit().putString("dashboard_layout", dashboardLayout).apply()
                                }, label = { Text("COMPACT") })
                                FilterChip(selected = dashboardLayout == "EXPANDED", onClick = {
                                    dashboardLayout = "EXPANDED"; prefs.edit().putString("dashboard_layout", dashboardLayout).apply()
                                }, label = { Text("EXPANDED") })
                            }
                            Text("Active preset: " + themeChoice + " · " + dashboardLayout, color = appearanceMuted, fontSize = 12.sp)
                        }
                        item {
                            PhoenixInfo("PROFILE SNAPSHOTS", "Restore points for this profile", "Snapshots preserve Phoenix profile version, selected loader, RAM allocation, theme, banner, avatar and layout. They do not copy worlds, accounts or mod files.", animatedAccent)
                            Button(onClick = {
                                try {
                                    val name = savePhoenixSnapshot(context, active, modLoader, ramAllocation.toInt(), bannerChoice, avatarChoice, dashboardLayout, themeChoice)
                                    snapshotFiles.clear(); snapshotFiles.addAll(listPhoenixSnapshots(context, active.id))
                                    recordPhoenixEvent(prefs, "Profile snapshot saved", name + " · " + active.name)
                                    advancedStatus = "Saved profile snapshot " + name + "."
                                } catch (error: Exception) { advancedStatus = error.message ?: "Snapshot could not be saved." }
                            }, modifier = Modifier.fillMaxWidth(), colors = ButtonDefaults.buttonColors(containerColor = animatedAccent)) {
                                Text("CREATE PROFILE SNAPSHOT", color = Color.Black, fontWeight = FontWeight.Bold)
                            }
                            TextButton(onClick = { snapshotFiles.clear(); snapshotFiles.addAll(listPhoenixSnapshots(context, active.id)) }) {
                                Text("REFRESH SNAPSHOTS", color = animatedAccent)
                            }
                        }
                        items(snapshotFiles, key = { it.name }) { snapshot ->
                            Row(Modifier.fillMaxWidth().background(appearancePanel, RoundedCornerShape(12.dp)).padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                                Column(Modifier.weight(1f)) {
                                    Text(snapshot.name, color = appearanceText, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                    Text(snapshot.length().toString() + " bytes", color = appearanceMuted, fontSize = 10.sp)
                                }
                                TextButton(onClick = {
                                    try {
                                        val data = restorePhoenixSnapshot(snapshot)
                                        val version = data.optString("version", active.version)
                                        val idx = profiles.indexOfFirst { it.id == active.id }
                                        if (idx >= 0) profiles[idx] = profiles[idx].copy(version = version)
                                        modLoader = data.optString("loader", modLoader)
                                        ramAllocation = data.optInt("ram", ramAllocation.toInt()).toFloat()
                                        bannerChoice = data.optString("banner", bannerChoice)
                                        avatarChoice = data.optString("avatar", avatarChoice)
                                        dashboardLayout = data.optString("layout", dashboardLayout)
                                        themeChoice = data.optString("theme", themeChoice)
                                        prefs.edit().putString("mod_loader_" + activeId, modLoader)
                                            .putInt("ram_allocation_" + activeId, ramAllocation.toInt())
                                            .putString("cosmetic_banner", bannerChoice).putString("cosmetic_avatar", avatarChoice)
                                            .putString("dashboard_layout", dashboardLayout).putString("theme_choice", themeChoice).apply()
                                        LauncherPreferences.DEFAULT_PREF.edit().putInt("allocation", ramAllocation.toInt()).apply()
                                        persist()
                                        recordPhoenixEvent(prefs, "Profile snapshot restored", snapshot.name)
                                        advancedStatus = "Restored " + snapshot.name + ". Mod files and worlds were not changed."
                                    } catch (error: Exception) { advancedStatus = error.message ?: "Could not restore snapshot." }
                                }) { Text("RESTORE", color = animatedAccent) }
                            }
                        }
                        item {
                            PhoenixInfo("SESSION TIMELINE", "Recent launcher events", "Tracks launcher launch requests, server tests and profile snapshots. Android does not expose reliable Minecraft play duration to this dashboard.", animatedAccent)
                        }
                        items(phoenixTimeline(prefs), key = { it.first + it.second }) { event ->
                            Column(Modifier.fillMaxWidth().background(appearancePanel, RoundedCornerShape(10.dp)).padding(12.dp)) {
                                Text(event.first, color = animatedAccent, fontSize = 10.sp)
                                Text(event.second, color = appearanceText, fontSize = 12.sp)
                            }
                        }
                        item {
                            PhoenixInfo("PHOENIX EXTENSION SYSTEM", "Controlled module registry", "This foundation enables built-in Phoenix modules. It does not execute arbitrary downloaded code or load untrusted APKs.", animatedAccent)
                            extensionModules.forEach { module ->
                                val enabled = prefs.getBoolean("extension_" + module.id, true)
                                Row(Modifier.fillMaxWidth().background(appearancePanel, RoundedCornerShape(10.dp)).padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                                    Column(Modifier.weight(1f)) {
                                        Text(module.name, color = appearanceText, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                                        Text(module.description, color = appearanceMuted, fontSize = 10.sp)
                                    }
                                    Switch(checked = enabled, onCheckedChange = { prefs.edit().putBoolean("extension_" + module.id, it).apply() })
                                }
                            }
                            Text("External extension SDK, sandboxing, signing and permissions remain future work. This registry is the safe base, not third-party code execution.", color = appearanceMuted, fontSize = 11.sp)
                        }
                    }
                    "Toolbox" -> {
                        item {
                            TextButton(onClick = { page = "Settings" }) { Text("← SETTINGS", color = animatedAccent) }
                            PhoenixInfo("PHOENIX TOOLBOX", "Your game library", "Manage installed resource-pack and shader ZIP files, and inspect local worlds for the active profile. This screen never deletes worlds.", animatedAccent)
                            Text(toolboxStatus, color = appearanceMuted, fontSize = 12.sp)
                        }
                        item {
                            Column(
                                Modifier.fillMaxWidth().background(appearancePanel, RoundedCornerShape(18.dp)).padding(15.dp),
                                verticalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Text("PHOENIX PACK HUB", color = animatedAccent, fontWeight = FontWeight.Bold, letterSpacing = 1.4.sp)
                                Text("Discover and install version-matched resource packs and shader packs from Modrinth.", color = appearanceMuted, fontSize = 12.sp)
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    FilterChip(selected = packType == "resourcepack", onClick = { packType = "resourcepack"; packResults.clear() }, label = { Text("RESOURCE PACKS") })
                                    FilterChip(selected = packType == "shader", onClick = { packType = "shader"; packResults.clear() }, label = { Text("SHADERS") })
                                }
                                OutlinedTextField(
                                    value = packQuery,
                                    onValueChange = { packQuery = it },
                                    modifier = Modifier.fillMaxWidth(),
                                    singleLine = true,
                                    label = { Text(if (packType == "shader") "Search shaders" else "Search resource packs") },
                                    placeholder = { Text(if (packType == "shader") "Complementary, BSL..." else "Faithful, Fresh Animations...") }
                                )
                                val packVersion = Regex("\\d+\\.\\d+(\\.\\d+)?").find(active.version)?.value ?: active.version
                                Text("Minecraft $packVersion", color = appearanceMuted, fontSize = 11.sp)
                                Button(
                                    enabled = !packBusy && packQuery.isNotBlank(),
                                    onClick = {
                                        packBusy = true
                                        packStatus = "Searching Modrinth..."
                                        Thread {
                                            try {
                                                val found = searchPhoenixPacks(packQuery.trim(), packVersion, packType)
                                                activity.runOnUiThread {
                                                    packResults.clear()
                                                    packResults.addAll(found)
                                                    packStatus = if (found.isEmpty()) "No matching packs found for Minecraft $packVersion." else "Found ${found.size} projects."
                                                    packBusy = false
                                                }
                                            } catch (error: Exception) {
                                                activity.runOnUiThread { packStatus = error.message ?: "Pack search failed."; packBusy = false }
                                            }
                                        }.start()
                                    },
                                    modifier = Modifier.fillMaxWidth(),
                                    colors = ButtonDefaults.buttonColors(containerColor = animatedAccent)
                                ) { Text(if (packBusy) "SEARCHING..." else "SEARCH PACK HUB", color = Color.Black, fontWeight = FontWeight.Bold) }
                                Text(packStatus, color = appearanceMuted, fontSize = 11.sp)
                            }
                        }
                        items(packResults, key = { "pack-search-" + it.projectId }) { result ->
                            val selectedPackVersion = Regex("\\d+\\.\\d+(\\.\\d+)?").find(active.version)?.value ?: active.version
                            Column(
                                Modifier.fillMaxWidth().background(appearancePanel, RoundedCornerShape(15.dp)).padding(14.dp),
                                verticalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Text(result.title, color = appearanceText, fontWeight = FontWeight.Bold)
                                Text(result.description, color = appearanceMuted, fontSize = 11.sp, maxLines = 3)
                                Text("${result.downloads} downloads · ${result.slug}", color = animatedAccent, fontSize = 10.sp)
                                Button(
                                    enabled = !packBusy,
                                    onClick = {
                                        packBusy = true
                                        packStatus = "Downloading ${result.title}..."
                                        Thread {
                                            try {
                                                val folder = if (packType == "shader") "shaderpacks" else "resourcepacks"
                                                val installed = installPhoenixPack(result, selectedPackVersion, folder, active.engineProfileId)
                                                activity.runOnUiThread {
                                                    if (folder == "shaderpacks") {
                                                        shaderPacks.clear()
                                                        shaderPacks.addAll(listPhoenixContentFiles(active.engineProfileId, folder))
                                                    } else {
                                                        resourcePacks.clear()
                                                        resourcePacks.addAll(listPhoenixContentFiles(active.engineProfileId, folder))
                                                    }
                                                    packStatus = "Installed $installed. Open the in-game pack menu to enable it."
                                                    packBusy = false
                                                }
                                            } catch (error: Exception) {
                                                activity.runOnUiThread { packStatus = error.message ?: "Pack installation failed."; packBusy = false }
                                            }
                                        }.start()
                                    },
                                    colors = ButtonDefaults.buttonColors(containerColor = animatedAccent)
                                ) { Text(if (packBusy) "INSTALLING..." else "DOWNLOAD & INSTALL", color = Color.Black, fontWeight = FontWeight.Bold) }
                            }
                        }
                        item {
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                                Column(Modifier.weight(1f)) {
                                    Text("RESOURCE PACKS", color = animatedAccent, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                                    Text("${resourcePacks.size} ZIP file(s)", color = appearanceMuted, fontSize = 11.sp)
                                }
                                TextButton(onClick = {
                                    try {
                                        resourcePacks.clear()
                                        resourcePacks.addAll(listPhoenixContentFiles(active.engineProfileId, "resourcepacks"))
                                        toolboxStatus = "Resource packs refreshed."
                                    } catch (error: Exception) { toolboxStatus = error.message ?: "Refresh failed." }
                                }) { Text("REFRESH") }
                            }
                        }
                        items(resourcePacks, key = { "resource-" + it.fileName }) { pack ->
                            Row(Modifier.fillMaxWidth().background(appearancePanel, RoundedCornerShape(13.dp)).padding(13.dp),
                                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                                    Text(pack.fileName, color = appearanceText, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                                    Text("${pack.sizeBytes / 1024} KB · ${if (pack.enabled) "Enabled" else "Disabled"}", color = appearanceMuted, fontSize = 10.sp)
                                }
                                TextButton(onClick = {
                                    try {
                                        togglePhoenixContentFile(active.engineProfileId, "resourcepacks", pack.fileName, !pack.enabled)
                                        resourcePacks.clear()
                                        resourcePacks.addAll(listPhoenixContentFiles(active.engineProfileId, "resourcepacks"))
                                        toolboxStatus = "Resource pack state changed. Reopen the in-game resource-pack menu."
                                    } catch (error: Exception) { toolboxStatus = error.message ?: "Could not change pack state." }
                                }) { Text(if (pack.enabled) "DISABLE" else "ENABLE", color = animatedAccent) }
                                TextButton(onClick = { pendingContentRemoval = "resourcepacks" to pack.fileName }) {
                                    Text("REMOVE", color = Color(0xFFFF8A80))
                                }
                            }
                        }
                        item {
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                                Column(Modifier.weight(1f)) {
                                    Text("SHADER PACKS", color = animatedAccent, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                                    Text("${shaderPacks.size} ZIP file(s)", color = appearanceMuted, fontSize = 11.sp)
                                }
                                TextButton(onClick = {
                                    try {
                                        shaderPacks.clear()
                                        shaderPacks.addAll(listPhoenixContentFiles(active.engineProfileId, "shaderpacks"))
                                        toolboxStatus = "Shader packs refreshed."
                                    } catch (error: Exception) { toolboxStatus = error.message ?: "Refresh failed." }
                                }) { Text("REFRESH") }
                            }
                        }
                        items(shaderPacks, key = { "shader-" + it.fileName }) { pack ->
                            Row(Modifier.fillMaxWidth().background(appearancePanel, RoundedCornerShape(13.dp)).padding(13.dp),
                                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                                    Text(pack.fileName, color = appearanceText, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                                    Text("${pack.sizeBytes / 1024} KB · ${if (pack.enabled) "Enabled" else "Disabled"}", color = appearanceMuted, fontSize = 10.sp)
                                }
                                TextButton(onClick = {
                                    try {
                                        togglePhoenixContentFile(active.engineProfileId, "shaderpacks", pack.fileName, !pack.enabled)
                                        shaderPacks.clear()
                                        shaderPacks.addAll(listPhoenixContentFiles(active.engineProfileId, "shaderpacks"))
                                        toolboxStatus = "Shader pack state changed. Reopen shader settings in-game."
                                    } catch (error: Exception) { toolboxStatus = error.message ?: "Could not change shader state." }
                                }) { Text(if (pack.enabled) "DISABLE" else "ENABLE", color = animatedAccent) }
                                TextButton(onClick = { pendingContentRemoval = "shaderpacks" to pack.fileName }) {
                                    Text("REMOVE", color = Color(0xFFFF8A80))
                                }
                            }
                        }
                        item {
                            PhoenixInfo("SAVED WORLDS", savedWorlds.size.toString(), "World folders with a valid level.dat file in this profile's saves directory. World files are shown read-only here.", animatedAccent)
                        }
                        items(savedWorlds, key = { "world-" + it }) { world ->
                            Row(Modifier.fillMaxWidth().background(appearancePanel, RoundedCornerShape(12.dp)).padding(14.dp),
                                verticalAlignment = Alignment.CenterVertically) {
                                Text("🌍", fontSize = 19.sp)
                                Spacer(Modifier.width(10.dp))
                                Text(world, color = appearanceText, fontWeight = FontWeight.SemiBold)
                            }
                        }
                        item {
                            Text("Pack Hub downloads ZIP releases matched to the selected Minecraft version. Installed packs can be enabled, disabled or removed here; reopen the in-game menu after changes.", color = appearanceMuted, fontSize = 11.sp)
                        }
                    }
                    "Performance" -> {
                        item {
                            TextButton(onClick = { page = "Settings" }) { Text("← SETTINGS", color = animatedAccent) }
                            PhoenixInfo("PERFORMANCE CENTER", "Memory tuning", "Set Minecraft's RAM allocation. Keep memory available for Android and other apps to reduce crashes.", animatedAccent)
                        }
                        item {
                            Text("DEVICE MEMORY", color = animatedAccent, fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.5.sp)
                            Text("$totalRamMb MB total  ·  $freeRamMb MB currently available", color = appearanceMuted, fontSize = 12.sp)
                            Text("Minecraft allocation: ${ramAllocation.toInt()} MB", color = appearanceText, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                            Slider(
                                value = ramAllocation.coerceIn(256f, maxRamMb.toFloat()),
                                onValueChange = { ramAllocation = it },
                                valueRange = 256f..maxRamMb.toFloat()
                            )
                            Text("Recommended maximum: $maxRamMb MB (60% of device RAM, capped at 6 GB).", color = appearanceMuted, fontSize = 11.sp)
                        }
                        item {
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                listOf("LOW" to 768, "BALANCED" to 1536, "HIGH" to 3072).forEach { (label, amount) ->
                                    OutlinedButton(
                                        onClick = { ramAllocation = amount.coerceAtMost(maxRamMb).coerceAtLeast(256).toFloat() },
                                        modifier = Modifier.weight(1f)
                                    ) { Text(label, fontSize = 10.sp) }
                                }
                            }
                            Button(
                                onClick = {
                                    val selected = ramAllocation.toInt().coerceIn(256, maxRamMb)
                                    LauncherPreferences.DEFAULT_PREF.edit().putInt("allocation", selected).apply()
                                    prefs.edit().putInt("ram_allocation_${activeId}", selected).apply()
                                    notice = "Minecraft memory allocation saved for ${active.name}: $selected MB."
                                },
                                modifier = Modifier.fillMaxWidth(),
                                colors = ButtonDefaults.buttonColors(containerColor = animatedAccent)
                            ) { Text("SAVE PERFORMANCE SETTINGS", color = Color.Black, fontWeight = FontWeight.Bold) }
                            Text("These settings affect the next game launch. Renderer and Java runtime choices are available in the engine settings.", color = appearanceMuted, fontSize = 11.sp)
                        }
                    }
                }
            }
        }
    }


    if (pendingContentRemoval != null) {
        val selected = pendingContentRemoval!!
        AlertDialog(
            onDismissRequest = { pendingContentRemoval = null },
            title = { Text("Remove installed pack?") },
            text = { Text("Remove ${selected.second} from this profile's ${selected.first} folder? This only deletes the selected ZIP file.") },
            confirmButton = {
                TextButton(onClick = {
                    try {
                        removePhoenixContentFile(active.engineProfileId, selected.first, selected.second)
                        if (selected.first == "shaderpacks") {
                            shaderPacks.removeAll { it.fileName == selected.second }
                        } else {
                            resourcePacks.removeAll { it.fileName == selected.second }
                        }
                        toolboxStatus = "Removed ${selected.second}."
                    } catch (error: Exception) {
                        toolboxStatus = error.message ?: "Could not remove the pack."
                    }
                    pendingContentRemoval = null
                }) { Text("REMOVE", color = Color(0xFFFF8A80)) }
            },
            dismissButton = { TextButton(onClick = { pendingContentRemoval = null }) { Text("CANCEL") } },
            containerColor = appearancePanel,
            titleContentColor = appearanceText,
            textContentColor = appearanceMuted
        )
    }

    if (pendingModRemoval != null) {
        val fileName = pendingModRemoval!!
        AlertDialog(
            onDismissRequest = { pendingModRemoval = null },
            title = { Text("Remove installed mod?") },
            text = { Text("Remove $fileName from the active profile's mods folder? This only deletes this selected JAR file. Minecraft must be restarted for the change to take effect.") },
            confirmButton = {
                TextButton(onClick = {
                    try {
                        removePhoenixInstalledMod(active.engineProfileId, fileName)
                        installedMods.removeAll { it.fileName == fileName }
                        modStatus = "Removed $fileName. Restart Minecraft to apply the change."
                    } catch (error: Exception) {
                        modStatus = error.message ?: "Could not remove the mod."
                    }
                    pendingModRemoval = null
                }) { Text("REMOVE", color = Color(0xFFFF8A80)) }
            },
            dismissButton = { TextButton(onClick = { pendingModRemoval = null }) { Text("CANCEL") } },
            containerColor = appearancePanel,
            titleContentColor = appearanceText,
            textContentColor = appearanceMuted
        )
    }

    if (dialogOpen) {
        AlertDialog(
            onDismissRequest = { dialogOpen = false },
            title = { Text(if (editingId == null) "Create Phoenix profile" else "Edit profile") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(draftName, { draftName = it }, label = { Text("Profile name") }, singleLine = true)
                    OutlinedTextField(draftNickname, { draftNickname = it }, label = { Text("Local username") }, supportingText = { Text("Up to 16 letters, numbers or underscores. Stored on this device only.") }, singleLine = true)
                    Text("CHOOSE IDENTITY EMBLEM", color = animatedAccent, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                    Row(horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                        listOf("🔥", "🐉", "⚡", "🌌", "🛡️", "🦅", "☄️", "🌋").forEach { emblem ->
                            Text(
                                emblem,
                                modifier = Modifier.background(if (draftAvatar == emblem) Color(0xFF382019) else appearancePanel, RoundedCornerShape(10.dp))
                                    .clickable { draftAvatar = emblem }.padding(7.dp),
                                fontSize = 20.sp
                            )
                        }
                    }
                    OutlinedTextField(draftVersion, { draftVersion = it }, label = { Text("Minecraft version ID") }, singleLine = true)
                    Text("Local identity only — official Minecraft sign-in is still required for authenticated online play.", color = appearanceMuted, fontSize = 11.sp)
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    val safeName = draftName.trim().take(32)
                    val safeNick = draftNickname.trim().filter { it.isLetterOrDigit() || it == '_' }.take(16)
                    if (safeName.isNotBlank() && safeNick.isNotBlank() && draftVersion.isNotBlank()) {
                        val index = profiles.indexOfFirst { it.id == editingId }
                        if (index >= 0) profiles[index] = profiles[index].copy(name = safeName, nickname = safeNick, version = draftVersion.trim(), avatar = draftAvatar)
                        else {
                            val created = PhoenixSavedProfile(UUID.randomUUID().toString(), safeName, safeNick, draftVersion.trim(), avatar = draftAvatar)
                            profiles.add(created)
                            activeId = created.id
                        }
                        persist()
                        avatarChoice = draftAvatar
                        prefs.edit().putString("cosmetic_avatar", draftAvatar).apply()
                        notice = "Phoenix local identity saved."
                        dialogOpen = false
                    }
                }) { Text("SAVE", color = animatedAccent) }
            },
            dismissButton = { TextButton(onClick = { dialogOpen = false }) { Text("CANCEL") } },
            containerColor = appearancePanel,
            titleContentColor = appearanceText,
            textContentColor = appearanceMuted
        )
    }
    }
}

@Composable
private fun PhoenixInfo(title: String, value: String, detail: String, accent: Color) {
    Column(
        Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surface, RoundedCornerShape(16.dp)).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(5.dp)
    ) {
        Text(title, color = accent, fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.5.sp)
        Text(value, color = MaterialTheme.colorScheme.onSurface, fontSize = 20.sp, fontWeight = FontWeight.Bold)
        Text(detail, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp)
    }
}


@Composable
private fun DiscordMark() {
    Canvas(Modifier.size(24.dp)) {
        val w = size.width
        val h = size.height
        val body = Path().apply {
            moveTo(w * 0.22f, h * 0.22f)
            cubicTo(w * 0.39f, h * 0.12f, w * 0.61f, h * 0.12f, w * 0.78f, h * 0.22f)
            cubicTo(w * 0.91f, h * 0.42f, w * 0.96f, h * 0.67f, w * 0.88f, h * 0.82f)
            cubicTo(w * 0.78f, h * 0.91f, w * 0.69f, h * 0.93f, w * 0.62f, h * 0.88f)
            lineTo(w * 0.57f, h * 0.81f)
            lineTo(w * 0.43f, h * 0.81f)
            lineTo(w * 0.38f, h * 0.88f)
            cubicTo(w * 0.29f, h * 0.93f, w * 0.20f, h * 0.91f, w * 0.12f, h * 0.82f)
            cubicTo(w * 0.04f, h * 0.67f, w * 0.09f, h * 0.42f, w * 0.22f, h * 0.22f)
            close()
        }
        drawPath(body, Color.White)
        drawCircle(Color(0xFF5865F2), radius = w * 0.055f, center = androidx.compose.ui.geometry.Offset(w * 0.38f, h * 0.53f))
        drawCircle(Color(0xFF5865F2), radius = w * 0.055f, center = androidx.compose.ui.geometry.Offset(w * 0.62f, h * 0.53f))
        val smile = Path().apply {
            moveTo(w * 0.35f, h * 0.68f)
            cubicTo(w * 0.44f, h * 0.75f, w * 0.56f, h * 0.75f, w * 0.65f, h * 0.68f)
        }
        drawPath(smile, Color(0xFF5865F2), style = Stroke(width = w * 0.045f))
    }
}
