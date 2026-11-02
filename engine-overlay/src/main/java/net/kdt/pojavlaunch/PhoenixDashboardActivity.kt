package net.kdt.pojavlaunch

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.Image
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
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

private data class PhoenixInstalledMod(val fileName: String, val sizeBytes: Long, val enabled: Boolean)
private data class PhoenixContentFile(val fileName: String, val sizeBytes: Long, val enabled: Boolean)
private data class PhoenixReadinessItem(val title: String, val value: String, val detail: String, val healthy: Boolean)
private data class PhoenixDiagnostic(val title: String, val cause: String, val fix: String)
private data class PhoenixServer(val label: String, val host: String, val port: Int)
private data class PhoenixModCheck(val name: String, val state: String, val detail: String, val warning: Boolean)
private data class PhoenixExtension(val id: String, val name: String, val description: String)


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
    val engineProfileId: String = ""
)

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
                    engineProfileId = item.optString("engineProfileId", "")
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
            .put("engineProfileId", p.engineProfileId))
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
    var dialogOpen by remember { mutableStateOf(false) }
    var draftName by remember { mutableStateOf("") }
    var draftNickname by remember { mutableStateOf("") }
    var draftVersion by remember { mutableStateOf("1.21.4") }
    var avatarChoice by remember { mutableStateOf(prefs.getString("cosmetic_avatar", "🔥") ?: "🔥") }
    var bannerChoice by remember { mutableStateOf(prefs.getString("cosmetic_banner", "EMBER") ?: "EMBER") }
    var modQuery by remember { mutableStateOf("") }
    var modLoader by remember { mutableStateOf(prefs.getString("mod_loader_${activeId}", "fabric") ?: "fabric") }
    var modStatus by remember { mutableStateOf("Search Modrinth for mods compatible with your selected Minecraft version and loader.") }
    var modBusy by remember { mutableStateOf(false) }
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
    var dashboardLayout by remember { mutableStateOf(prefs.getString("dashboard_layout", "EXPANDED") ?: "EXPANDED") }
    val snapshotFiles = remember { mutableStateListOf<File>() }
    var advancedStatus by remember { mutableStateOf("Phoenix Advanced Studio ready.") }
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
    var notice by remember { mutableStateOf("Phoenix engine ready. Sign in through the official launcher flow.") }
    val active = profiles.firstOrNull { it.id == activeId } ?: profiles.first()
    val studioAccent = when (themeChoice) { "FROST" -> Color(0xFF63C9FF); "VIOLET" -> Color(0xFFB38CFF); "MATRIX" -> Color(0xFF69E59A); else -> PhoenixOrange }
    val animatedAccent by animateColorAsState(targetValue = studioAccent, label = "phoenix-theme-accent")

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
        background = PhoenixBg,
        surface = PhoenixPanel
    )) {
        Scaffold(
            containerColor = PhoenixBg,
            bottomBar = {
                NavigationBar(containerColor = Color(0xFF151019)) {
                    listOf("Home", "Profiles", "Version", "Mods", "Cosmetics", "Settings").forEach { tab ->
                        NavigationBarItem(
                            selected = page == tab,
                            onClick = { page = tab },
                            icon = { Text(when(tab) { "Home" -> "⌂"; "Profiles" -> "◉"; "Version" -> "▤"; "Mods" -> "✦"; "Cosmetics" -> "✧"; else -> "⚙" }, fontSize = 18.sp) },
                            label = { Text(tab, fontSize = 10.sp) }
                        )
                    }
                }
            }
        ) { padding ->
            LazyColumn(
                modifier = Modifier.fillMaxSize().background(PhoenixBg).padding(padding),
                contentPadding = PaddingValues(if (dashboardLayout == "COMPACT") 12.dp else 18.dp),
                verticalArrangement = Arrangement.spacedBy(if (dashboardLayout == "COMPACT") 9.dp else 14.dp)
            ) {
                item {
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text("PHOENIX CLIENT", color = animatedAccent, fontWeight = FontWeight.Black, letterSpacing = 3.sp, fontSize = 12.sp)
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) { Text(avatarChoice, fontSize = 27.sp); Text("JAVA EDITION", color = Color.White, fontSize = 26.sp, fontWeight = FontWeight.ExtraBold) }
                        Text("Your world. Your profiles. Your rules.", color = PhoenixMuted, fontSize = 13.sp)
                    }
                }
                when (page) {
                    "Home" -> {
                        item {
                            Column(
                                Modifier.fillMaxWidth().background(
                                    Brush.linearGradient(listOf(animatedAccent.copy(alpha = 0.32f), when (bannerChoice) { "FROST" -> Color(0xFF173044); "OBSIDIAN" -> Color(0xFF29232D); else -> Color(0xFF542519) }, PhoenixPanel)),
                                    RoundedCornerShape(22.dp)
                                ).padding(20.dp),
                                verticalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                Text("RISE FROM THE ASHES", color = animatedAccent, fontWeight = FontWeight.Bold, letterSpacing = 2.sp, fontSize = 11.sp)
                                Text("Ready for your\nnext adventure?", color = Color.White, fontSize = 29.sp, lineHeight = 33.sp, fontWeight = FontWeight.ExtraBold)
                                Text("Active profile: ${active.name}", color = PhoenixMuted)
                                Text("Target version: ${active.version}", color = PhoenixMuted)
                                Button(
                                    onClick = {
                                        persist()
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
                        item { PhoenixInfo("LOCAL PHOENIX PROFILES", profiles.size.toString(), "These save launcher settings and nicknames; they are not Minecraft authentication accounts.", animatedAccent) }
                        item {
                            PhoenixInfo(
                                "GAME ACCOUNT REQUIRED",
                                "Microsoft account",
                                "Launching Minecraft Java Edition requires a Microsoft account that owns the game. Phoenix cannot create an account or grant game ownership.",
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
                                Text("JOIN OUR DISCORD SERVER", color = Color.White, fontWeight = FontWeight.Bold)
                            }
                        }
                        item { Text(notice, color = PhoenixMuted, fontSize = 12.sp) }
                        item {
                            Row(
                                Modifier.fillMaxWidth()
                                    .background(PhoenixPanel, RoundedCornerShape(18.dp))
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
                                    Text("PHOENIX COMMUNITY", color = Color.White, fontWeight = FontWeight.Bold)
                                    Text("Join our Discord server", color = PhoenixMuted, fontSize = 12.sp)
                                }
                                Text("JOIN  →", color = animatedAccent, fontSize = 12.sp, fontWeight = FontWeight.Black)
                            }
                        }
                    }
                    "Profiles" -> {
                        item {
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                                Column(Modifier.weight(1f)) {
                                    Text("Player profiles", color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.Bold)
                                    Text("Each profile is synchronized with the engine.", color = PhoenixMuted, fontSize = 12.sp)
                                }
                                Button(onClick = {
                                    editingId = null
                                    draftName = "New profile"
                                    draftNickname = "PhoenixPlayer"
                                    draftVersion = active.version
                                    dialogOpen = true
                                }, colors = ButtonDefaults.buttonColors(containerColor = animatedAccent)) { Text("+ ADD", color = Color.Black) }
                            }
                        }
                        items(profiles, key = { it.id }) { profile ->
                            Column(
                                Modifier.fillMaxWidth().background(PhoenixPanel, RoundedCornerShape(16.dp))
                                    .clickable { activeId = profile.id; persist(); notice = "${profile.name} is now the active engine profile." }
                                    .padding(15.dp),
                                verticalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) { Text(avatarChoice, fontSize = 20.sp); Text(profile.name, color = Color.White, fontWeight = FontWeight.Bold) }
                                    if (profile.id == activeId) Text("ACTIVE", color = animatedAccent, fontSize = 10.sp, fontWeight = FontWeight.Black)
                                }
                                Text("@${profile.nickname}  ·  Minecraft ${profile.version}", color = PhoenixMuted, fontSize = 12.sp)
                                Text("Engine profile: ${profile.engineProfileId.take(8)}…", color = PhoenixMuted, fontSize = 10.sp)
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    TextButton(onClick = {
                                        editingId = profile.id
                                        draftName = profile.name
                                        draftNickname = profile.nickname
                                        draftVersion = profile.version
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
                    "Cosmetics" -> {
                        item {
                            PhoenixInfo("PHOENIX COSMETICS", "Make your profile yours", "These are launcher profile cosmetics only. They do not change Minecraft account skins or server-side cosmetics.", animatedAccent)
                        }
                        item {
                            Text("PROFILE EMBLEM", color = animatedAccent, fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.5.sp)
                            Text("Choose a saved Phoenix identity emblem.", color = PhoenixMuted, fontSize = 12.sp)
                        }
                        items(listOf("🔥", "🐉", "⚡", "🌌", "🛡️", "🦅", "☄️", "🌋")) { emblem ->
                            Row(
                                Modifier.fillMaxWidth()
                                    .background(if (avatarChoice == emblem) Color(0xFF382019) else PhoenixPanel, RoundedCornerShape(12.dp))
                                    .clickable {
                                        avatarChoice = emblem
                                        prefs.edit().putString("cosmetic_avatar", emblem).apply()
                                        notice = "Phoenix profile emblem updated."
                                    }.padding(14.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                    Text(emblem, fontSize = 24.sp)
                                    Text("Phoenix emblem", color = Color.White)
                                }
                                if (avatarChoice == emblem) Text("EQUIPPED", color = animatedAccent, fontSize = 10.sp, fontWeight = FontWeight.Black)
                            }
                        }
                        item {
                            Text("PROFILE BANNER", color = animatedAccent, fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.5.sp)
                            Text("Choose the look of your Phoenix dashboard banner.", color = PhoenixMuted, fontSize = 12.sp)
                        }
                        items(listOf("EMBER", "OBSIDIAN", "FROST")) { banner ->
                            val selected = bannerChoice == banner
                            val colors = when (banner) {
                                "FROST" -> listOf(Color(0xFF173044), Color(0xFF18202E), PhoenixPanel)
                                "OBSIDIAN" -> listOf(Color(0xFF29232D), Color(0xFF17131B), PhoenixPanel)
                                else -> listOf(Color(0xFF542519), Color(0xFF21151F), PhoenixPanel)
                            }
                            Row(
                                Modifier.fillMaxWidth()
                                    .background(Brush.linearGradient(colors), RoundedCornerShape(14.dp))
                                    .clickable {
                                        bannerChoice = banner
                                        prefs.edit().putString("cosmetic_banner", banner).apply()
                                        notice = "$banner banner equipped."
                                    }.padding(16.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(banner, color = Color.White, fontWeight = FontWeight.Bold)
                                if (selected) Text("EQUIPPED", color = Color.White, fontSize = 10.sp, fontWeight = FontWeight.Black)
                            }
                        }
                    }
                    "Version" -> {
                        item {
                            Text("Game version", color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.Bold)
                            Text("This writes the selected version to the active engine profile. The game files still need to be installed from the game launcher.", color = PhoenixMuted, fontSize = 12.sp)
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
                        item { Text("Popular version IDs", color = PhoenixMuted, fontWeight = FontWeight.Bold) }
                        items(listOf("1.21.4", "1.20.4", "1.19.4", "1.16.5", "1.12.2", "1.8.9", "latest-release", "latest-snapshot")) { version ->
                            Row(
                                Modifier.fillMaxWidth().background(PhoenixPanel, RoundedCornerShape(12.dp))
                                    .clickable { draftVersion = version }.padding(14.dp),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text("Minecraft $version", color = Color.White)
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
                                    Text("${installedMods.size} JAR file(s) in ${active.name}", color = PhoenixMuted, fontSize = 12.sp)
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
                            item { Text("No .jar mods found in this profile's mods folder yet.", color = PhoenixMuted, fontSize = 12.sp) }
                        }
                        items(installedMods, key = { it.fileName }) { installed ->
                            Row(
                                Modifier.fillMaxWidth().background(PhoenixPanel, RoundedCornerShape(14.dp)).padding(13.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                                    Text(installed.fileName, color = Color.White, fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                                    Text("${installed.sizeBytes / 1024} KB  ·  ${if (installed.enabled) "Enabled" else "Disabled"}", color = PhoenixMuted, fontSize = 10.sp)
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
                            Text("Minecraft $gameVersion", color = PhoenixMuted, fontSize = 12.sp)
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
                            Text(modStatus, color = PhoenixMuted, fontSize = 12.sp)
                        }
                        items(modResults, key = { it.projectId }) { mod ->
                            Column(
                                Modifier.fillMaxWidth().background(PhoenixPanel, RoundedCornerShape(16.dp)).padding(15.dp),
                                verticalArrangement = Arrangement.spacedBy(7.dp)
                            ) {
                                Text(mod.title, color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                                Text(mod.description, color = PhoenixMuted, fontSize = 12.sp, maxLines = 3)
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
                            Text("Mod compatibility depends on the installed loader and Minecraft version. Phoenix does not guarantee third-party mod safety; only install projects you trust.", color = PhoenixMuted, fontSize = 11.sp)
                            TextButton(onClick = { persist(); activity.startActivity(Intent(activity, LauncherActivity::class.java)) }) {
                                Text("OPEN ENGINE MODPACK / LOADER MANAGER", color = animatedAccent)
                            }
                        }
                    }
                    "Settings" -> {
                        item {
                            PhoenixInfo("ENGINE SETTINGS", "Device & controls", "Open the original engine settings for renderer, Java runtime, controls and account management. These remain connected to the real engine.", animatedAccent)
                            Button(onClick = { page = "Performance" }, modifier = Modifier.fillMaxWidth(),
                                colors = ButtonDefaults.buttonColors(containerColor = animatedAccent)) {
                                Text("OPEN PERFORMANCE TUNING", color = Color.Black, fontWeight = FontWeight.Bold)
                            }
                            Button(onClick = { page = "Toolbox" }, modifier = Modifier.fillMaxWidth(),
                                colors = ButtonDefaults.buttonColors(containerColor = PhoenixPanel)) {
                                Text("OPEN GAME TOOLBOX", color = animatedAccent, fontWeight = FontWeight.Bold)
                            }
                            Button(onClick = { page = "Intelligence" }, modifier = Modifier.fillMaxWidth(),
                                colors = ButtonDefaults.buttonColors(containerColor = PhoenixPanel)) {
                                Text("OPEN LAUNCH INTELLIGENCE", color = animatedAccent, fontWeight = FontWeight.Bold)
                            }
                            Button(onClick = { page = "Advanced" }, modifier = Modifier.fillMaxWidth(),
                                colors = ButtonDefaults.buttonColors(containerColor = PhoenixPanel)) {
                                Text("OPEN PHOENIX ADVANCED STUDIO", color = animatedAccent, fontWeight = FontWeight.Bold)
                            }
                            Button(onClick = { persist(); activity.startActivity(Intent(activity, LauncherActivity::class.java)) },
                                modifier = Modifier.fillMaxWidth(), colors = ButtonDefaults.buttonColors(containerColor = PhoenixPanel)) {
                                Text("OPEN ENGINE SETTINGS", color = animatedAccent, fontWeight = FontWeight.Bold)
                            }
                            Text("Microsoft sign-in uses the engine's official authentication screen. Phoenix does not collect or store your Microsoft password.", color = PhoenixMuted, fontSize = 12.sp)
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
                            Text(readinessStatus, color = PhoenixMuted, fontSize = 12.sp)
                        }
                        items(readinessItems, key = { it.title }) { result ->
                            Row(
                                Modifier.fillMaxWidth().background(PhoenixPanel, RoundedCornerShape(14.dp)).padding(14.dp),
                                horizontalArrangement = Arrangement.spacedBy(12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(if (result.healthy) "●" else "!", color = if (result.healthy) Color(0xFF7DDB9A) else Color(0xFFFFB36B), fontSize = 18.sp, fontWeight = FontWeight.Black)
                                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                                    Text(result.title, color = Color.White, fontWeight = FontWeight.Bold)
                                    Text(result.value, color = animatedAccent, fontSize = 13.sp)
                                    Text(result.detail, color = PhoenixMuted, fontSize = 11.sp)
                                }
                            }
                        }
                        item {
                            Text("This is a lightweight file-system check, not a guarantee that a modpack or Minecraft version will launch. For a crash diagnosis, inspect the engine's full log and crash report.", color = PhoenixMuted, fontSize = 11.sp)
                        }
                    }
                    "Advanced" -> {
                        item {
                            TextButton(onClick = { page = "Settings" }) { Text("← SETTINGS", color = animatedAccent) }
                            PhoenixInfo("PHOENIX ADVANCED STUDIO", "Eight connected systems", "Diagnostics, compatibility checks, server tools, performance recommendations, themes, snapshots, activity history and a safe extension registry.", animatedAccent)
                            Text(advancedStatus, color = PhoenixMuted, fontSize = 12.sp)
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
                            Column(Modifier.fillMaxWidth().background(PhoenixPanel, RoundedCornerShape(14.dp)).padding(14.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                                Text(finding.title, color = animatedAccent, fontWeight = FontWeight.Bold)
                                Text(finding.cause, color = Color.White, fontSize = 12.sp)
                                Text("Suggested fix: " + finding.fix, color = PhoenixMuted, fontSize = 11.sp)
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
                            Column(Modifier.fillMaxWidth().background(PhoenixPanel, RoundedCornerShape(12.dp)).padding(13.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Text(check.name, color = Color.White, fontWeight = FontWeight.SemiBold, fontSize = 12.sp)
                                Text(check.state, color = if (check.warning) Color(0xFFFFB36B) else animatedAccent, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                Text(check.detail, color = PhoenixMuted, fontSize = 11.sp)
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
                            Text(serverStatus, color = PhoenixMuted, fontSize = 12.sp)
                        }
                        items(favouriteServers, key = { it.host + ":" + it.port }) { server ->
                            Column(Modifier.fillMaxWidth().background(PhoenixPanel, RoundedCornerShape(13.dp)).padding(13.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                                Text(server.label, color = Color.White, fontWeight = FontWeight.Bold)
                                Text(server.host + ":" + server.port, color = PhoenixMuted, fontSize = 11.sp)
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
                            Text("Device RAM: " + totalRamMb + " MB · currently free: " + freeRamMb + " MB", color = PhoenixMuted, fontSize = 12.sp)
                            Text("Enabled mods: " + detectedMods + " · shaders present: " + if (hasShaders) "yes" else "no", color = PhoenixMuted, fontSize = 12.sp)
                            Text("Suggested Minecraft allocation: " + recommendedRam + " MB", color = animatedAccent, fontWeight = FontWeight.Bold)
                            Button(onClick = {
                                ramAllocation = recommendedRam.toFloat()
                                prefs.edit().putInt("ram_allocation_" + activeId, recommendedRam).apply()
                                LauncherPreferences.DEFAULT_PREF.edit().putInt(LauncherPreferences.PREF_RAM_ALLOCATION, recommendedRam).apply()
                                recordPhoenixEvent(prefs, "Performance recommendation applied", recommendedRam.toString() + " MB RAM allocation")
                                advancedStatus = "Applied recommended RAM allocation. You can change it again in Performance Center."
                            }, modifier = Modifier.fillMaxWidth(), colors = ButtonDefaults.buttonColors(containerColor = animatedAccent)) {
                                Text("APPLY RECOMMENDED RAM", color = Color.Black, fontWeight = FontWeight.Bold)
                            }
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
                            Text("Active preset: " + themeChoice + " · " + dashboardLayout, color = PhoenixMuted, fontSize = 12.sp)
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
                            Row(Modifier.fillMaxWidth().background(PhoenixPanel, RoundedCornerShape(12.dp)).padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                                Column(Modifier.weight(1f)) {
                                    Text(snapshot.name, color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                    Text(snapshot.length().toString() + " bytes", color = PhoenixMuted, fontSize = 10.sp)
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
                                        LauncherPreferences.DEFAULT_PREF.edit().putInt(LauncherPreferences.PREF_RAM_ALLOCATION, ramAllocation.toInt()).apply()
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
                            Column(Modifier.fillMaxWidth().background(PhoenixPanel, RoundedCornerShape(10.dp)).padding(12.dp)) {
                                Text(event.first, color = animatedAccent, fontSize = 10.sp)
                                Text(event.second, color = Color.White, fontSize = 12.sp)
                            }
                        }
                        item {
                            PhoenixInfo("PHOENIX EXTENSION SYSTEM", "Controlled module registry", "This foundation enables built-in Phoenix modules. It does not execute arbitrary downloaded code or load untrusted APKs.", animatedAccent)
                            extensionModules.forEach { module ->
                                val enabled = prefs.getBoolean("extension_" + module.id, true)
                                Row(Modifier.fillMaxWidth().background(PhoenixPanel, RoundedCornerShape(10.dp)).padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                                    Column(Modifier.weight(1f)) {
                                        Text(module.name, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                                        Text(module.description, color = PhoenixMuted, fontSize = 10.sp)
                                    }
                                    Switch(checked = enabled, onCheckedChange = { prefs.edit().putBoolean("extension_" + module.id, it).apply() })
                                }
                            }
                            Text("External extension SDK, sandboxing, signing and permissions remain future work. This registry is the safe base, not third-party code execution.", color = PhoenixMuted, fontSize = 11.sp)
                        }
                    }
                    "Toolbox" -> {
                        item {
                            TextButton(onClick = { page = "Settings" }) { Text("← SETTINGS", color = animatedAccent) }
                            PhoenixInfo("PHOENIX TOOLBOX", "Your game library", "Manage installed resource-pack and shader ZIP files, and inspect local worlds for the active profile. This screen never deletes worlds.", animatedAccent)
                            Text(toolboxStatus, color = PhoenixMuted, fontSize = 12.sp)
                        }
                        item {
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                                Column(Modifier.weight(1f)) {
                                    Text("RESOURCE PACKS", color = animatedAccent, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                                    Text("${resourcePacks.size} ZIP file(s)", color = PhoenixMuted, fontSize = 11.sp)
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
                            Row(Modifier.fillMaxWidth().background(PhoenixPanel, RoundedCornerShape(13.dp)).padding(13.dp),
                                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                                    Text(pack.fileName, color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                                    Text("${pack.sizeBytes / 1024} KB · ${if (pack.enabled) "Enabled" else "Disabled"}", color = PhoenixMuted, fontSize = 10.sp)
                                }
                                TextButton(onClick = {
                                    try {
                                        togglePhoenixContentFile(active.engineProfileId, "resourcepacks", pack.fileName, !pack.enabled)
                                        resourcePacks.clear()
                                        resourcePacks.addAll(listPhoenixContentFiles(active.engineProfileId, "resourcepacks"))
                                        toolboxStatus = "Resource pack state changed. Reopen the in-game resource-pack menu."
                                    } catch (error: Exception) { toolboxStatus = error.message ?: "Could not change pack state." }
                                }) { Text(if (pack.enabled) "DISABLE" else "ENABLE", color = animatedAccent) }
                            }
                        }
                        item {
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                                Column(Modifier.weight(1f)) {
                                    Text("SHADER PACKS", color = animatedAccent, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                                    Text("${shaderPacks.size} ZIP file(s)", color = PhoenixMuted, fontSize = 11.sp)
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
                            Row(Modifier.fillMaxWidth().background(PhoenixPanel, RoundedCornerShape(13.dp)).padding(13.dp),
                                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                                    Text(pack.fileName, color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                                    Text("${pack.sizeBytes / 1024} KB · ${if (pack.enabled) "Enabled" else "Disabled"}", color = PhoenixMuted, fontSize = 10.sp)
                                }
                                TextButton(onClick = {
                                    try {
                                        togglePhoenixContentFile(active.engineProfileId, "shaderpacks", pack.fileName, !pack.enabled)
                                        shaderPacks.clear()
                                        shaderPacks.addAll(listPhoenixContentFiles(active.engineProfileId, "shaderpacks"))
                                        toolboxStatus = "Shader pack state changed. Reopen shader settings in-game."
                                    } catch (error: Exception) { toolboxStatus = error.message ?: "Could not change shader state." }
                                }) { Text(if (pack.enabled) "DISABLE" else "ENABLE", color = animatedAccent) }
                            }
                        }
                        item {
                            PhoenixInfo("SAVED WORLDS", savedWorlds.size.toString(), "World folders with a valid level.dat file in this profile's saves directory. World files are shown read-only here.", animatedAccent)
                        }
                        items(savedWorlds, key = { "world-" + it }) { world ->
                            Row(Modifier.fillMaxWidth().background(PhoenixPanel, RoundedCornerShape(12.dp)).padding(14.dp),
                                verticalAlignment = Alignment.CenterVertically) {
                                Text("🌍", fontSize = 19.sp)
                                Spacer(Modifier.width(10.dp))
                                Text(world, color = Color.White, fontWeight = FontWeight.SemiBold)
                            }
                        }
                        item {
                            Text("Pack toggles rename the selected ZIP to a .disabled extension. No files are downloaded or deleted by this toolbox.", color = PhoenixMuted, fontSize = 11.sp)
                        }
                    }
                    "Performance" -> {
                        item {
                            TextButton(onClick = { page = "Settings" }) { Text("← SETTINGS", color = animatedAccent) }
                            PhoenixInfo("PERFORMANCE CENTER", "Memory tuning", "Set Minecraft's RAM allocation. Keep memory available for Android and other apps to reduce crashes.", animatedAccent)
                        }
                        item {
                            Text("DEVICE MEMORY", color = animatedAccent, fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.5.sp)
                            Text("$totalRamMb MB total  ·  $freeRamMb MB currently available", color = PhoenixMuted, fontSize = 12.sp)
                            Text("Minecraft allocation: ${ramAllocation.toInt()} MB", color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                            Slider(
                                value = ramAllocation.coerceIn(256f, maxRamMb.toFloat()),
                                onValueChange = { ramAllocation = it },
                                valueRange = 256f..maxRamMb.toFloat()
                            )
                            Text("Recommended maximum: $maxRamMb MB (60% of device RAM, capped at 6 GB).", color = PhoenixMuted, fontSize = 11.sp)
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
                                    LauncherPreferences.PREF_RAM_ALLOCATION = selected
                                    LauncherPreferences.DEFAULT_PREF.edit().putInt("allocation", selected).apply()
                                    prefs.edit().putInt("ram_allocation_${activeId}", selected).apply()
                                    notice = "Minecraft memory allocation saved for ${active.name}: $selected MB."
                                },
                                modifier = Modifier.fillMaxWidth(),
                                colors = ButtonDefaults.buttonColors(containerColor = animatedAccent)
                            ) { Text("SAVE PERFORMANCE SETTINGS", color = Color.Black, fontWeight = FontWeight.Bold) }
                            Text("These settings affect the next game launch. Renderer and Java runtime choices are available in the engine settings.", color = PhoenixMuted, fontSize = 11.sp)
                        }
                    }
                }
            }
        }
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
            containerColor = PhoenixPanel,
            titleContentColor = Color.White,
            textContentColor = PhoenixMuted
        )
    }

    if (dialogOpen) {
        AlertDialog(
            onDismissRequest = { dialogOpen = false },
            title = { Text(if (editingId == null) "Create Phoenix profile" else "Edit profile") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(draftName, { draftName = it }, label = { Text("Profile name") }, singleLine = true)
                    OutlinedTextField(draftNickname, { draftNickname = it }, label = { Text("Local nickname label") }, singleLine = true)
                    OutlinedTextField(draftVersion, { draftVersion = it }, label = { Text("Minecraft version ID") }, singleLine = true)
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    val safeName = draftName.trim().take(32)
                    val safeNick = draftNickname.trim().filter { it.isLetterOrDigit() || it == '_' }.take(16)
                    if (safeName.isNotBlank() && safeNick.isNotBlank() && draftVersion.isNotBlank()) {
                        val index = profiles.indexOfFirst { it.id == editingId }
                        if (index >= 0) profiles[index] = profiles[index].copy(name = safeName, nickname = safeNick, version = draftVersion.trim())
                        else {
                            val created = PhoenixSavedProfile(UUID.randomUUID().toString(), safeName, safeNick, draftVersion.trim())
                            profiles.add(created)
                            activeId = created.id
                        }
                        persist()
                        notice = "Phoenix profile saved and synchronized with the engine."
                        dialogOpen = false
                    }
                }) { Text("SAVE", color = animatedAccent) }
            },
            dismissButton = { TextButton(onClick = { dialogOpen = false }) { Text("CANCEL") } },
            containerColor = PhoenixPanel,
            titleContentColor = Color.White,
            textContentColor = PhoenixMuted
        )
    }
}

@Composable
private fun PhoenixInfo(title: String, value: String, detail: String, accent: Color) {
    Column(
        Modifier.fillMaxWidth().background(PhoenixPanel, RoundedCornerShape(16.dp)).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(5.dp)
    ) {
        Text(title, color = accent, fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.5.sp)
        Text(value, color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Bold)
        Text(detail, color = PhoenixMuted, fontSize = 12.sp)
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
