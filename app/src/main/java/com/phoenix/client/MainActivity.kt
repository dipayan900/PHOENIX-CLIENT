package com.phoenix.client

import android.content.Context
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

private val Night = Color(0xFF0B0B10)
private val Panel = Color(0xFF17171F)
private val Muted = Color(0xFFAAA8B2)
private data class PhoenixTheme(val name: String, val accent: Color, val secondary: Color, val description: String)
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

@Composable
private fun PhoenixApp() {
    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences("phoenix_client", Context.MODE_PRIVATE) }
    var page by remember { mutableStateOf("Home") }
    var profile by remember { mutableStateOf(prefs.getString("profile", "PhoenixPlayer") ?: "PhoenixPlayer") }
    var version by remember { mutableStateOf(prefs.getString("version", "1.21.4") ?: "1.21.4") }
    var themeName by remember { mutableStateOf(prefs.getString("theme", "Inferno") ?: "Inferno") }
    var notice by remember { mutableStateOf("Your next adventure starts here.") }
    val theme = themes.firstOrNull { it.name == themeName } ?: themes.first()

    SideEffect {
        prefs.edit()
            .putString("profile", profile)
            .putString("version", version)
            .putString("theme", themeName)
            .apply()
    }

    MaterialTheme(colorScheme = darkColorScheme(
        primary = theme.accent, secondary = theme.secondary, background = Night, surface = Panel
    )) {
        Scaffold(containerColor = Night, bottomBar = {
            NavigationBar(containerColor = Color(0xFF121219), contentColor = theme.accent) {
                listOf("Home", "Versions", "Profiles", "Themes", "Settings").forEach { item ->
                    NavigationBarItem(
                        selected = page == item,
                        onClick = { page = item },
                        icon = { Text(when(item) { "Home" -> "⌂"; "Versions" -> "▤"; "Profiles" -> "◉"; "Themes" -> "✦"; else -> "⚙" }, fontSize = 19.sp) },
                        label = { Text(item, fontSize = 10.sp) },
                        colors = NavigationBarItemDefaults.colors(selectedIconColor = theme.accent, selectedTextColor = theme.accent, indicatorColor = theme.accent.copy(alpha = 0.15f), unselectedIconColor = Muted, unselectedTextColor = Muted)
                    )
                }
            }
        }) { padding ->
            LazyColumn(
                modifier = Modifier.fillMaxSize().background(Night).padding(padding),
                contentPadding = PaddingValues(18.dp),
                verticalArrangement = Arrangement.spacedBy(15.dp)
            ) {
                item { Header(theme) }
                item {
                    when (page) {
                        "Home" -> Column(verticalArrangement = Arrangement.spacedBy(13.dp)) {
                            Box(Modifier.fillMaxWidth().background(Brush.linearGradient(listOf(theme.secondary.copy(alpha = 0.42f), Color(0xFF21151B), Panel)), RoundedCornerShape(24.dp)).padding(20.dp)) {
                                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                    Text("RISE FROM THE ASHES", color = theme.accent, fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 2.sp)
                                    Text("Your next\nMinecraft era.", color = Color.White, fontSize = 30.sp, fontWeight = FontWeight.ExtraBold, lineHeight = 34.sp)
                                    Text("A launcher built for your next adventure.", color = Muted)
                                    Button(onClick = { notice = "Game launch is not available yet: the Java runtime, game files and account flow have not been integrated." }, colors = ButtonDefaults.buttonColors(containerColor = theme.accent), modifier = Modifier.fillMaxWidth().height(52.dp), shape = RoundedCornerShape(14.dp)) {
                                        Text("▶   PLAY", color = Color.Black, fontWeight = FontWeight.Black, letterSpacing = 1.sp)
                                    }
                                }
                            }
                            InfoCard("ACTIVE LOCAL PROFILE", profile.ifBlank { "No nickname set" }, "Saved on this device · not a Microsoft sign-in", theme.accent)
                            InfoCard("SELECTED GAME VERSION", "Minecraft $version", "Selection is saved locally · game files not installed", theme.accent)
                            InfoCard("LAUNCHER THEME", theme.name, theme.description, theme.accent)
                            InfoCard("BUILD STATUS", "Launcher foundation", "Minecraft runtime integration is still pending", theme.accent)
                            Text(notice, color = Muted, fontSize = 12.sp)
                        }
                        "Versions" -> Column(verticalArrangement = Arrangement.spacedBy(11.dp)) {
                            SectionTitle("Game versions", "Choose a target version. This does not download or install game files.", theme.accent)
                            versions.forEach { v ->
                                SelectRow("Minecraft $v", if (version == v) "SELECTED" else "Tap to select", theme.accent, version == v) {
                                    version = v
                                    notice = "Saved target version $v."
                                }
                            }
                        }
                        "Profiles" -> Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            SectionTitle("Local player profile", "Your nickname is stored only on this device. It is not account authentication.", theme.accent)
                            OutlinedTextField(
                                value = profile,
                                onValueChange = { input -> profile = input.filter { it.isLetterOrDigit() || it == '_' }.take(16) },
                                label = { Text("Player nickname") },
                                supportingText = { Text("${profile.length}/16 · letters, numbers and underscore") },
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth(),
                                colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = theme.accent, focusedLabelColor = theme.accent, cursorColor = theme.accent)
                            )
                            InfoCard("PROFILE PREVIEW", profile.ifBlank { "PhoenixPlayer" }, "Local offline launcher identity", theme.accent)
                            Text("Secure Microsoft account sign-in is not implemented yet. Never enter your Microsoft password into this prototype.", color = Muted, fontSize = 12.sp)
                        }
                        "Themes" -> Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            SectionTitle("Launcher cosmetics", "Change Phoenix's own interface look. These are not Minecraft in-game skins or capes.", theme.accent)
                            themes.forEach { option ->
                                Row(
                                    Modifier.fillMaxWidth().background(Panel, RoundedCornerShape(16.dp)).clickable { themeName = option.name }.padding(15.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                                ) {
                                    Box(Modifier.size(42.dp).background(Brush.linearGradient(listOf(option.accent, option.secondary)), RoundedCornerShape(13.dp)))
                                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                                        Text(option.name, color = Color.White, fontWeight = FontWeight.Bold)
                                        Text(option.description, color = Muted, fontSize = 11.sp)
                                    }
                                    Text(if (themeName == option.name) "ACTIVE" else "APPLY", color = option.accent, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                                }
                            }
                            Text("The selected theme is saved automatically and remains after closing the app.", color = Muted, fontSize = 12.sp)
                        }
                        else -> Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            SectionTitle("Settings", "Phoenix Client app information and local preferences.", theme.accent)
                            InfoCard("APP VERSION", "0.2.0", "Early development build", theme.accent)
                            InfoCard("UI ENGINE", "Jetpack Compose", "Native Android interface", theme.accent)
                            InfoCard("PREFERENCES", "Saved locally", "Nickname, target version and theme", theme.accent)
                            InfoCard("GAME ENGINE", "Not connected", "No Java runtime or Minecraft files are bundled", theme.accent)
                            Text("Phoenix Client is independent and is not affiliated with Mojang or Microsoft.", color = Muted, fontSize = 12.sp)
                        }
                    }
                }
                item { Text("PHOENIX CLIENT  •  DEVELOPMENT BUILD", color = Color(0xFF696773), fontSize = 10.sp, letterSpacing = 1.sp, modifier = Modifier.fillMaxWidth().padding(top = 7.dp)) }
            }
        }
    }
}

@Composable
private fun Header(theme: PhoenixTheme) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Box(Modifier.size(44.dp).background(Brush.linearGradient(listOf(theme.accent, theme.secondary)), RoundedCornerShape(14.dp)), contentAlignment = Alignment.Center) {
                Text("🔥", fontSize = 23.sp)
            }
            Column {
                Text("PHOENIX", color = Color.White, fontWeight = FontWeight.Black, fontSize = 20.sp, letterSpacing = 2.sp)
                Text("CLIENT", color = theme.accent, fontWeight = FontWeight.Bold, fontSize = 10.sp, letterSpacing = 4.sp)
            }
        }
        Text("v0.2.0", color = Muted, fontSize = 11.sp)
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
    Column(Modifier.fillMaxWidth().background(Panel, RoundedCornerShape(18.dp)).padding(16.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
        Text(label, color = accent, fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.5.sp)
        Text(value, color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Bold)
        Text(detail, color = Muted, fontSize = 12.sp)
    }
}

@Composable
private fun SelectRow(title: String, detail: String, accent: Color, selected: Boolean, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().background(Panel, RoundedCornerShape(14.dp)).clickable(onClick = onClick).padding(16.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
        Column {
            Text(title, color = Color.White, fontWeight = FontWeight.SemiBold)
            Text(detail, color = Muted, fontSize = 11.sp)
        }
        Text(if (selected) "✓" else "›", color = accent, fontSize = 22.sp)
    }
}
