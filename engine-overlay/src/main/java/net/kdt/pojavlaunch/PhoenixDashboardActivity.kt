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

private val PhoenixBg = Color(0xFF0B0910)
private val PhoenixPanel = Color(0xFF19131D)
private val PhoenixMuted = Color(0xFFB8AEBB)
private val PhoenixOrange = Color(0xFFFF702E)

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
                            PhoenixInfo("MODS & MODPACKS", "Engine-powered", "Open the game launcher to import modpacks and manage Fabric / Forge installations.", PhoenixOrange)
                            Button(onClick = { persist(); activity.startActivity(Intent(activity, LauncherActivity::class.java)) },
                                modifier = Modifier.fillMaxWidth(), colors = ButtonDefaults.buttonColors(containerColor = PhoenixOrange)) {
                                Text("OPEN MOD & VERSION MANAGER", color = Color.Black, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                    else -> {
                        item {
                            PhoenixInfo("ENGINE SETTINGS", "Device & controls", "Renderer, Java runtime, memory, touch controls and account settings remain managed by the integrated engine.", PhoenixOrange)
                            Button(onClick = { persist(); activity.startActivity(Intent(activity, LauncherActivity::class.java)) },
                                modifier = Modifier.fillMaxWidth(), colors = ButtonDefaults.buttonColors(containerColor = PhoenixOrange)) {
                                Text("OPEN ENGINE SETTINGS", color = Color.Black, fontWeight = FontWeight.Bold)
                            }
                            Text("Microsoft sign-in uses the engine's official authentication screen. Phoenix does not collect or store your Microsoft password.", color = PhoenixMuted, fontSize = 12.sp)
                        }
                    }
                }
            }
        }
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
