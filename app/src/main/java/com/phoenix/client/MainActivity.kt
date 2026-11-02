package com.phoenix.client

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.json.JSONArray
import org.json.JSONObject

private val Night = Color(0xFF09090E)
private val Panel = Color(0xFF17171F)
private val Muted = Color(0xFFAAA8B2)

private data class PhoenixTheme(
    val name: String,
    val accent: Color,
    val secondary: Color,
    val description: String
)

private data class PlayerProfile(
    val id: String,
    val name: String,
    val nickname: String,
    val version: String
)

private val themes = listOf(
    PhoenixTheme("Inferno", Color(0xFFFF6A24), Color(0xFFFF3D22), "Original Phoenix fire palette"),
    PhoenixTheme("Frost", Color(0xFF54C8FF), Color(0xFF536DFF), "Cool ice-blue launcher palette"),
    PhoenixTheme("Amethyst", Color(0xFFB77BFF), Color(0xFF753DDF), "Purple cosmic launcher palette")
)
private val versions = listOf("1.21.4", "1.20.4", "1.19.4", "1.16.5", "1.12.2", "1.8.9")

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { PhoenixApp() }
    }
}

private fun loadProfiles(context: Context): List<PlayerProfile> {
    val prefs = context.getSharedPreferences("phoenix_client", Context.MODE_PRIVATE)
    val raw = prefs.getString("profiles_json", null)
    if (raw.isNullOrBlank()) {
        val nickname = prefs.getString("profile", "PhoenixPlayer") ?: "PhoenixPlayer"
        val version = prefs.getString("version", "1.21.4") ?: "1.21.4"
        return listOf(PlayerProfile("default", "Main profile", nickname, version))
    }
    return try {
        val array = JSONArray(raw)
        buildList {
            for (index in 0 until array.length()) {
                val item = array.getJSONObject(index)
                add(
                    PlayerProfile(
                        id = item.optString("id", "profile-$index"),
                        name = item.optString("name", "Player profile"),
                        nickname = item.optString("nickname", "PhoenixPlayer"),
                        version = item.optString("version", "1.21.4")
                    )
                )
            }
        }.ifEmpty { listOf(PlayerProfile("default", "Main profile", "PhoenixPlayer", "1.21.4")) }
    } catch (_: Exception) {
        listOf(PlayerProfile("default", "Main profile", "PhoenixPlayer", "1.21.4"))
    }
}

private fun encodeProfiles(profiles: List<PlayerProfile>): String {
    val array = JSONArray()
    profiles.forEach { profile ->
        array.put(
            JSONObject()
                .put("id", profile.id)
                .put("name", profile.name)
                .put("nickname", profile.nickname)
                .put("version", profile.version)
        )
    }
    return array.toString()
}

@Composable
private fun PhoenixApp() {
    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences("phoenix_client", Context.MODE_PRIVATE) }
    var page by remember { mutableStateOf("Home") }
    val profiles = remember { mutableStateListOf<PlayerProfile>().apply { addAll(loadProfiles(context)) } }
    var activeId by remember {
        mutableStateOf(prefs.getString("active_profile_id", profiles.first().id) ?: profiles.first().id)
    }
    var themeName by remember { mutableStateOf(prefs.getString("theme", "Inferno") ?: "Inferno") }
    var notice by remember { mutableStateOf("Your next adventure starts here.") }
    var showProfileDialog by remember { mutableStateOf(false) }
    var editingId by remember { mutableStateOf<String?>(null) }
    var draftName by remember { mutableStateOf("") }
    var draftNickname by remember { mutableStateOf("") }
    var draftVersion by remember { mutableStateOf(versions.first()) }
    var showDeleteId by remember { mutableStateOf<String?>(null) }

    val activeProfile = profiles.firstOrNull { it.id == activeId } ?: profiles.first()
    val theme = themes.firstOrNull { it.name == themeName } ?: themes.first()

    SideEffect {
        prefs.edit()
            .putString("profiles_json", encodeProfiles(profiles))
            .putString("active_profile_id", activeProfile.id)
            .putString("profile", activeProfile.nickname)
            .putString("version", activeProfile.version)
            .putString("theme", themeName)
            .apply()
    }

    fun saveProfile() {
        val safeName = draftName.trim().take(24)
        val safeNickname = draftNickname.filter { it.isLetterOrDigit() || it == '_' }.take(16)
        if (safeName.isBlank() || safeNickname.isBlank()) {
            notice = "Enter a profile name and a valid player nickname."
            return
        }
        val existingIndex = profiles.indexOfFirst { it.id == editingId }
        if (existingIndex >= 0) {
            profiles[existingIndex] = profiles[existingIndex].copy(
                name = safeName,
                nickname = safeNickname,
                version = draftVersion
            )
        } else {
            val created = PlayerProfile(
                id = "profile-${System.currentTimeMillis()}",
                name = safeName,
                nickname = safeNickname,
                version = draftVersion
            )
            profiles.add(created)
            activeId = created.id
        }
        showProfileDialog = false
        notice = "Profile saved on this device."
    }

    MaterialTheme(
        colorScheme = darkColorScheme(
            primary = theme.accent,
            secondary = theme.secondary,
            background = Night,
            surface = Panel
        )
    ) {
        Scaffold(
            containerColor = Night,
            bottomBar = {
                NavigationBar(containerColor = Color(0xFF121219), contentColor = theme.accent) {
                    listOf("Home", "Versions", "Profiles", "Cosmetics", "Social", "Themes", "Settings").forEach { item ->
                        NavigationBarItem(
                            selected = page == item,
                            onClick = { page = item },
                            icon = {
                                Text(
                                    when (item) {
                                        "Home" -> "⌂"
                                        "Versions" -> "▤"
                                        "Profiles" -> "◉"
                                        "Social" -> "◎"
                                        "Themes" -> "✦"
                                        else -> "⚙"
                                    },
                                    fontSize = 19.sp
                                )
                            },
                            label = { Text(item, fontSize = 10.sp) },
                            colors = NavigationBarItemDefaults.colors(
                                selectedIconColor = theme.accent,
                                selectedTextColor = theme.accent,
                                indicatorColor = theme.accent.copy(alpha = 0.15f),
                                unselectedIconColor = Muted,
                                unselectedTextColor = Muted
                            )
                        )
                    }
                }
            }
        ) { padding ->
            LazyColumn(
                modifier = Modifier.fillMaxSize().background(Night).padding(padding),
                contentPadding = PaddingValues(18.dp),
                verticalArrangement = Arrangement.spacedBy(15.dp)
            ) {
                item { Header(theme) }
                item {
                    when (page) {
                        "Home" -> Column(verticalArrangement = Arrangement.spacedBy(13.dp)) {
                            Box(
                                Modifier.fillMaxWidth()
                                    .background(
                                        Brush.linearGradient(
                                            listOf(theme.secondary.copy(alpha = 0.48f), Color(0xFF21151B), Panel)
                                        ),
                                        RoundedCornerShape(24.dp)
                                    )
                                    .padding(20.dp)
                            ) {
                                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                    Text(
                                        "RISE FROM THE ASHES",
                                        color = theme.accent,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        letterSpacing = 2.sp
                                    )
                                    Text(
                                        "Your next\nMinecraft era.",
                                        color = Color.White,
                                        fontSize = 30.sp,
                                        fontWeight = FontWeight.ExtraBold,
                                        lineHeight = 34.sp
                                    )
                                    Text("A launcher built for your next adventure.", color = Muted)
                                    Button(
                                        onClick = {
                                            val runtimeIntent =
                                                context.packageManager.getLaunchIntentForPackage("org.angelauramc.amethyst")
                                                    ?: context.packageManager.getLaunchIntentForPackage("org.angelauramc.amethyst.debug")
                                            if (runtimeIntent != null) {
                                                runtimeIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                                context.startActivity(runtimeIntent)
                                                notice = "Opened Amethyst. Phoenix has not transferred profile or version settings."
                                            } else {
                                                notice = "Amethyst is not installed. The Minecraft runtime is not bundled in Phoenix yet."
                                            }
                                        },
                                        colors = ButtonDefaults.buttonColors(containerColor = theme.accent),
                                        modifier = Modifier.fillMaxWidth().height(52.dp),
                                        shape = RoundedCornerShape(14.dp)
                                    ) {
                                        Text("▶   PLAY", color = Color.Black, fontWeight = FontWeight.Black, letterSpacing = 1.sp)
                                    }
                                }
                            }
                            InfoCard("ACTIVE PLAYER PROFILE", activeProfile.name, "Nickname: ${activeProfile.nickname}", theme.accent)
                            InfoCard("TARGET GAME VERSION", "Minecraft ${activeProfile.version}", "Saved separately for this profile · not downloaded", theme.accent)
                            InfoCard("SAVED PROFILES", profiles.size.toString(), "Switch between separate local player configurations", theme.accent)
                            InfoCard("LAUNCHER THEME", theme.name, theme.description, theme.accent)
                            Text(notice, color = Muted, fontSize = 12.sp)
                            Text(
                                "Runtime integration is still pending. PLAY only opens the separate Amethyst app when installed.",
                                color = Muted,
                                fontSize = 11.sp
                            )
                        }

                        "Versions" -> Column(verticalArrangement = Arrangement.spacedBy(11.dp)) {
                            SectionTitle(
                                "Game versions",
                                "Choose a target version for ${activeProfile.name}. This does not install game files.",
                                theme.accent
                            )
                            versions.forEach { v ->
                                SelectRow(
                                    "Minecraft $v",
                                    if (activeProfile.version == v) "SELECTED FOR THIS PROFILE" else "Tap to select",
                                    theme.accent,
                                    activeProfile.version == v
                                ) {
                                    val index = profiles.indexOfFirst { it.id == activeProfile.id }
                                    if (index >= 0) profiles[index] = profiles[index].copy(version = v)
                                    notice = "Saved Minecraft $v for ${activeProfile.name}."
                                }
                            }
                        }

                        "Profiles" -> Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            SectionTitle(
                                "Player profiles",
                                "Create separate local nicknames and target versions. No online account is connected.",
                                theme.accent
                            )
                            Button(
                                onClick = {
                                    editingId = null
                                    draftName = "New profile"
                                    draftNickname = "PhoenixPlayer"
                                    draftVersion = activeProfile.version
                                    showProfileDialog = true
                                },
                                modifier = Modifier.fillMaxWidth(),
                                colors = ButtonDefaults.buttonColors(containerColor = theme.accent),
                                shape = RoundedCornerShape(13.dp)
                            ) {
                                Text("+  CREATE PROFILE", color = Color.Black, fontWeight = FontWeight.Bold)
                            }
                            profiles.forEach { profile ->
                                Column(
                                    Modifier.fillMaxWidth()
                                        .background(Panel, RoundedCornerShape(16.dp))
                                        .padding(14.dp),
                                    verticalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Column(Modifier.weight(1f)) {
                                            Text(profile.name, color = Color.White, fontWeight = FontWeight.Bold)
                                            Text("@${profile.nickname}  ·  ${profile.version}", color = Muted, fontSize = 11.sp)
                                        }
                                        if (profile.id == activeProfile.id) {
                                            Text("ACTIVE", color = theme.accent, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                                        }
                                    }
                                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                        OutlinedButton(
                                            onClick = {
                                                activeId = profile.id
                                                notice = "Switched to ${profile.name}."
                                            },
                                            modifier = Modifier.weight(1f),
                                            border = androidx.compose.foundation.BorderStroke(1.dp, theme.accent.copy(alpha = 0.7f))
                                        ) {
                                            Text(if (profile.id == activeProfile.id) "SELECTED" else "USE PROFILE", color = theme.accent, fontSize = 10.sp)
                                        }
                                        TextButton(onClick = {
                                            editingId = profile.id
                                            draftName = profile.name
                                            draftNickname = profile.nickname
                                            draftVersion = profile.version
                                            showProfileDialog = true
                                        }) {
                                            Text("EDIT", color = Color.White, fontSize = 10.sp)
                                        }
                                        TextButton(
                                            onClick = { showDeleteId = profile.id },
                                            enabled = profiles.size > 1
                                        ) {
                                            Text("DELETE", color = if (profiles.size > 1) Color(0xFFFF7777) else Muted, fontSize = 10.sp)
                                        }
                                    }
                                }
                            }
                            Text(
                                "Profiles are local launcher presets only. Microsoft authentication and online player identity are not implemented.",
                                color = Muted,
                                fontSize = 12.sp
                            )
                        }

                        "Cosmetics" -> CosmeticsScreen(context = context, accent = theme.accent, onNotice = { notice = it })

                        "Social" -> PhoenixSocialScreen(context = context, accent = theme.accent)

                        "Themes" -> Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            SectionTitle(
                                "Launcher cosmetics",
                                "Change Phoenix's own interface look, not Minecraft in-game skins or capes.",
                                theme.accent
                            )
                            themes.forEach { option ->
                                Row(
                                    Modifier.fillMaxWidth()
                                        .background(Panel, RoundedCornerShape(16.dp))
                                        .clickable { themeName = option.name }
                                        .padding(15.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                                ) {
                                    Box(
                                        Modifier.size(42.dp)
                                            .background(
                                                Brush.linearGradient(listOf(option.accent, option.secondary)),
                                                RoundedCornerShape(13.dp)
                                            )
                                    )
                                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                                        Text(option.name, color = Color.White, fontWeight = FontWeight.Bold)
                                        Text(option.description, color = Muted, fontSize = 11.sp)
                                    }
                                    Text(
                                        if (themeName == option.name) "ACTIVE" else "APPLY",
                                        color = option.accent,
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                        }

                        else -> Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            SectionTitle("Settings", "Phoenix Client app information and local preferences.", theme.accent)
                            InfoCard("APP VERSION", "0.3.0", "Profile management update", theme.accent)
                            InfoCard("UI ENGINE", "Jetpack Compose", "Native Android interface", theme.accent)
                            InfoCard("LOCAL DATA", "On-device only", "Profile names, nicknames, versions and theme", theme.accent)
                            InfoCard("GAME ENGINE", "Not connected", "No Java runtime or Minecraft files are bundled", theme.accent)
                            Text(
                                "Phoenix Client is independent and is not affiliated with Mojang or Microsoft.",
                                color = Muted,
                                fontSize = 12.sp
                            )
                            Text(
                                "Never enter your Microsoft password into this prototype. A future sign-in must use Microsoft's official authorization flow.",
                                color = Muted,
                                fontSize = 12.sp
                            )
                        }
                    }
                }
                item {
                    Text(
                        "PHOENIX CLIENT  •  DEVELOPMENT BUILD",
                        color = Color(0xFF696773),
                        fontSize = 10.sp,
                        letterSpacing = 1.sp,
                        modifier = Modifier.fillMaxWidth().padding(top = 7.dp)
                    )
                }
            }
        }
    }

    if (showProfileDialog) {
        AlertDialog(
            onDismissRequest = { showProfileDialog = false },
            containerColor = Panel,
            title = { Text(if (editingId == null) "Create profile" else "Edit profile", color = Color.White) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedTextField(
                        value = draftName,
                        onValueChange = { draftName = it.take(24) },
                        label = { Text("Profile name") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = draftNickname,
                        onValueChange = { draftNickname = it.filter { c -> c.isLetterOrDigit() || c == '_' }.take(16) },
                        label = { Text("Player nickname") },
                        supportingText = { Text("${draftNickname.length}/16 · letters, numbers, underscore") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Text("Target version", color = Muted, fontSize = 12.sp)
                    versions.forEach { v ->
                        Row(
                            Modifier.fillMaxWidth().clickable { draftVersion = v }.padding(vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(
                                selected = draftVersion == v,
                                onClick = { draftVersion = v },
                                colors = RadioButtonDefaults.colors(selectedColor = theme.accent)
                            )
                            Text(v, color = Color.White)
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { saveProfile() }) {
                    Text("SAVE", color = theme.accent, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showProfileDialog = false }) {
                    Text("CANCEL", color = Muted)
                }
            }
        )
    }

    val deleteProfile = profiles.firstOrNull { it.id == showDeleteId }
    if (deleteProfile != null) {
        AlertDialog(
            onDismissRequest = { showDeleteId = null },
            containerColor = Panel,
            title = { Text("Delete profile?", color = Color.White) },
            text = { Text("Remove ${deleteProfile.name} from this device? This cannot be undone.", color = Muted) },
            confirmButton = {
                TextButton(onClick = {
                    profiles.removeAll { it.id == deleteProfile.id }
                    if (activeId == deleteProfile.id) activeId = profiles.first().id
                    showDeleteId = null
                    notice = "Profile deleted."
                }) {
                    Text("DELETE", color = Color(0xFFFF7777), fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteId = null }) { Text("CANCEL", color = Muted) }
            }
        )
    }
}

@Composable
private fun Header(theme: PhoenixTheme) {
    Row(
        Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Box(
                Modifier.size(44.dp)
                    .background(Brush.linearGradient(listOf(theme.accent, theme.secondary)), RoundedCornerShape(14.dp)),
                contentAlignment = Alignment.Center
            ) {
                Text("🔥", fontSize = 23.sp)
            }
            Column {
                Text("PHOENIX", color = Color.White, fontWeight = FontWeight.Black, fontSize = 20.sp, letterSpacing = 2.sp)
                Text("CLIENT", color = theme.accent, fontWeight = FontWeight.Bold, fontSize = 10.sp, letterSpacing = 4.sp)
            }
        }
        Text("v0.3.0", color = Muted, fontSize = 11.sp)
    }
}

@Composable
private fun SectionTitle(title: String, subtitle: String, accent: Color) {
    Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
        Text(title, color = Color.White, fontSize = 23.sp, fontWeight = FontWeight.Bold)
        Text(subtitle, color = Muted, fontSize = 12.sp)
    }
}

@Composable
private fun InfoCard(label: String, value: String, detail: String, accent: Color) {
    Column(
        Modifier.fillMaxWidth().background(Panel, RoundedCornerShape(18.dp)).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(5.dp)
    ) {
        Text(label, color = accent, fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.5.sp)
        Text(value, color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Bold)
        Text(detail, color = Muted, fontSize = 12.sp)
    }
}

@Composable
private fun SelectRow(title: String, detail: String, accent: Color, selected: Boolean, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().background(Panel, RoundedCornerShape(14.dp)).clickable(onClick = onClick).padding(16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Column {
            Text(title, color = Color.White, fontWeight = FontWeight.SemiBold)
            Text(detail, color = Muted, fontSize = 11.sp)
        }
        Text(if (selected) "✓" else "›", color = accent, fontSize = 22.sp)
    }
}
