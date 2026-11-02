package net.kdt.pojavlaunch

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
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

private fun phoenixGameDirectory(engineProfileId: String): File {
    if (LauncherProfiles.mainProfileJson == null) LauncherProfiles.load()
    val profile = LauncherProfiles.mainProfileJson.profiles[engineProfileId]
        ?: throw java.io.IOException("Select a valid game profile first.")
    return File(Tools.getGameDirPath(profile))
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
    var toolboxStatus by remember { mutableStateOf("Manage resource packs, shader ZIPs and inspect saved worlds for the active profile.") }
    var editingId by remember { mutableStateOf<String?>(null) }
    var notice by remember { mutableStateOf("Phoenix engine ready. Sign in through the official launcher flow.") }
    val active = profiles.firstOrNull { it.id == activeId } ?: profiles.first()

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
        primary = PhoenixOrange,
        secondary = Color(0xFFFFA36F),
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
                contentPadding = PaddingValues(18.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                item {
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text("PHOENIX CLIENT", color = PhoenixOrange, fontWeight = FontWeight.Black, letterSpacing = 3.sp, fontSize = 12.sp)
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) { Text(avatarChoice, fontSize = 27.sp); Text("JAVA EDITION", color = Color.White, fontSize = 26.sp, fontWeight = FontWeight.ExtraBold) }
                        Text("Your world. Your profiles. Your rules.", color = PhoenixMuted, fontSize = 13.sp)
                    }
                }
                when (page) {
                    "Home" -> {
                        item {
                            Column(
                                Modifier.fillMaxWidth().background(
                                    Brush.linearGradient(when (bannerChoice) { "FROST" -> listOf(Color(0xFF173044), Color(0xFF18202E), PhoenixPanel); "OBSIDIAN" -> listOf(Color(0xFF29232D), Color(0xFF17131B), PhoenixPanel); else -> listOf(Color(0xFF542519), Color(0xFF21151F), PhoenixPanel) }),
                                    RoundedCornerShape(22.dp)
                                ).padding(20.dp),
                                verticalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                Text("RISE FROM THE ASHES", color = PhoenixOrange, fontWeight = FontWeight.Bold, letterSpacing = 2.sp, fontSize = 11.sp)
                                Text("Ready for your\nnext adventure?", color = Color.White, fontSize = 29.sp, lineHeight = 33.sp, fontWeight = FontWeight.ExtraBold)
                                Text("Active profile: ${active.name}", color = PhoenixMuted)
                                Text("Target version: ${active.version}", color = PhoenixMuted)
                                Button(
                                    onClick = {
                                        persist()
                                        activity.startActivity(Intent(activity, LauncherActivity::class.java))
                                    },
                                    modifier = Modifier.fillMaxWidth().height(52.dp),
                                    colors = ButtonDefaults.buttonColors(containerColor = PhoenixOrange),
                                    shape = RoundedCornerShape(14.dp)
                                ) { Text("OPEN GAME LAUNCHER  →", color = Color.Black, fontWeight = FontWeight.Black) }
                            }
                        }
                        item { PhoenixInfo("CONNECTED ENGINE PROFILE", active.name, "Saved directly to the engine's launcher_profiles.json", PhoenixOrange) }
                        item { PhoenixInfo("LOCAL PHOENIX PROFILES", profiles.size.toString(), "These save launcher settings and nicknames; they are not Minecraft authentication accounts.", PhoenixOrange) }
                        item {
                            PhoenixInfo(
                                "GAME ACCOUNT REQUIRED",
                                "Microsoft account",
                                "Launching Minecraft Java Edition requires a Microsoft account that owns the game. Phoenix cannot create an account or grant game ownership.",
                                PhoenixOrange
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
                                Text("JOIN  →", color = PhoenixOrange, fontSize = 12.sp, fontWeight = FontWeight.Black)
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
                                }, colors = ButtonDefaults.buttonColors(containerColor = PhoenixOrange)) { Text("+ ADD", color = Color.Black) }
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
                                    if (profile.id == activeId) Text("ACTIVE", color = PhoenixOrange, fontSize = 10.sp, fontWeight = FontWeight.Black)
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
                                    }) { Text("EDIT", color = PhoenixOrange) }
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
                            PhoenixInfo("PHOENIX COSMETICS", "Make your profile yours", "These are launcher profile cosmetics only. They do not change Minecraft account skins or server-side cosmetics.", PhoenixOrange)
                        }
                        item {
                            Text("PROFILE EMBLEM", color = PhoenixOrange, fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.5.sp)
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
                                if (avatarChoice == emblem) Text("EQUIPPED", color = PhoenixOrange, fontSize = 10.sp, fontWeight = FontWeight.Black)
                            }
                        }
                        item {
                            Text("PROFILE BANNER", color = PhoenixOrange, fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.5.sp)
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
                            }, modifier = Modifier.fillMaxWidth(), colors = ButtonDefaults.buttonColors(containerColor = PhoenixOrange)) {
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
                                Text("SELECT", color = PhoenixOrange, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                    "Mods" -> {
                        item {
                            PhoenixInfo("PHOENIX MOD HUB", "Powered by Modrinth", "Search public mods and install a compatible JAR into the active Minecraft profile's mods folder. Check each project's description and trust before installing.", PhoenixOrange)
                        }
                        item {
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                                    Text("INSTALLED MODS", color = PhoenixOrange, fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.5.sp)
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
                                }) { Text("REFRESH", color = PhoenixOrange) }
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
                                    Text(if (installed.enabled) "DISABLE" else "ENABLE", color = PhoenixOrange, fontWeight = FontWeight.Bold)
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
                            Text("MOD LOADER", color = PhoenixOrange, fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.5.sp)
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
                                colors = ButtonDefaults.buttonColors(containerColor = PhoenixOrange)
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
                                Text("${mod.downloads} downloads  ·  ${mod.slug}", color = PhoenixOrange, fontSize = 10.sp)
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
                                    colors = ButtonDefaults.buttonColors(containerColor = PhoenixOrange)
                                ) { Text("DOWNLOAD & INSTALL", color = Color.Black, fontWeight = FontWeight.Bold) }
                            }
                        }
                        item {
                            Text("Mod compatibility depends on the installed loader and Minecraft version. Phoenix does not guarantee third-party mod safety; only install projects you trust.", color = PhoenixMuted, fontSize = 11.sp)
                            TextButton(onClick = { persist(); activity.startActivity(Intent(activity, LauncherActivity::class.java)) }) {
                                Text("OPEN ENGINE MODPACK / LOADER MANAGER", color = PhoenixOrange)
                            }
                        }
                    }
                    "Settings" -> {
                        item {
                            PhoenixInfo("ENGINE SETTINGS", "Device & controls", "Open the original engine settings for renderer, Java runtime, controls and account management. These remain connected to the real engine.", PhoenixOrange)
                            Button(onClick = { page = "Performance" }, modifier = Modifier.fillMaxWidth(),
                                colors = ButtonDefaults.buttonColors(containerColor = PhoenixOrange)) {
                                Text("OPEN PERFORMANCE TUNING", color = Color.Black, fontWeight = FontWeight.Bold)
                            }
                            Button(onClick = { page = "Toolbox" }, modifier = Modifier.fillMaxWidth(),
                                colors = ButtonDefaults.buttonColors(containerColor = PhoenixPanel)) {
                                Text("OPEN GAME TOOLBOX", color = PhoenixOrange, fontWeight = FontWeight.Bold)
                            }
                            Button(onClick = { persist(); activity.startActivity(Intent(activity, LauncherActivity::class.java)) },
                                modifier = Modifier.fillMaxWidth(), colors = ButtonDefaults.buttonColors(containerColor = PhoenixPanel)) {
                                Text("OPEN ENGINE SETTINGS", color = PhoenixOrange, fontWeight = FontWeight.Bold)
                            }
                            Text("Microsoft sign-in uses the engine's official authentication screen. Phoenix does not collect or store your Microsoft password.", color = PhoenixMuted, fontSize = 12.sp)
                        }
                    }
                    "Toolbox" -> {
                        item {
                            TextButton(onClick = { page = "Settings" }) { Text("← SETTINGS", color = PhoenixOrange) }
                            PhoenixInfo("PHOENIX TOOLBOX", "Your game library", "Manage installed resource-pack and shader ZIP files, and inspect local worlds for the active profile. This screen never deletes worlds.", PhoenixOrange)
                            Text(toolboxStatus, color = PhoenixMuted, fontSize = 12.sp)
                        }
                        item {
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                                Column(Modifier.weight(1f)) {
                                    Text("RESOURCE PACKS", color = PhoenixOrange, fontWeight = FontWeight.Bold, fontSize = 12.sp)
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
                                }) { Text(if (pack.enabled) "DISABLE" else "ENABLE", color = PhoenixOrange) }
                            }
                        }
                        item {
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                                Column(Modifier.weight(1f)) {
                                    Text("SHADER PACKS", color = PhoenixOrange, fontWeight = FontWeight.Bold, fontSize = 12.sp)
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
                                }) { Text(if (pack.enabled) "DISABLE" else "ENABLE", color = PhoenixOrange) }
                            }
                        }
                        item {
                            PhoenixInfo("SAVED WORLDS", savedWorlds.size.toString(), "World folders with a valid level.dat file in this profile's saves directory. World files are shown read-only here.", PhoenixOrange)
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
                            TextButton(onClick = { page = "Settings" }) { Text("← SETTINGS", color = PhoenixOrange) }
                            PhoenixInfo("PERFORMANCE CENTER", "Memory tuning", "Set Minecraft's RAM allocation. Keep memory available for Android and other apps to reduce crashes.", PhoenixOrange)
                        }
                        item {
                            Text("DEVICE MEMORY", color = PhoenixOrange, fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.5.sp)
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
                                colors = ButtonDefaults.buttonColors(containerColor = PhoenixOrange)
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
                }) { Text("SAVE", color = PhoenixOrange) }
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
