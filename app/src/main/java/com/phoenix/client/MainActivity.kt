package com.phoenix.client

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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private val Ember = Color(0xFFFF6A24)
private val Flame = Color(0xFFFF3D22)
private val Night = Color(0xFF0B0B10)
private val Panel = Color(0xFF17171F)
private val Muted = Color(0xFFAAA8B2)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { PhoenixApp() }
    }
}

@Composable
private fun PhoenixApp() {
    var page by remember { mutableStateOf("Home") }
    var profile by remember { mutableStateOf("PhoenixPlayer") }
    var version by remember { mutableStateOf("1.21.4") }
    var notice by remember { mutableStateOf("Your next adventure starts here.") }

    MaterialTheme(colorScheme = darkColorScheme(
        primary = Ember, secondary = Flame, background = Night, surface = Panel
    )) {
        Scaffold(containerColor = Night, bottomBar = {
            NavigationBar(containerColor = Color(0xFF121219), contentColor = Ember) {
                listOf("Home", "Versions", "Profiles", "Settings").forEach { item ->
                    NavigationBarItem(
                        selected = page == item,
                        onClick = { page = item },
                        icon = { Text(when(item) { "Home" -> "⌂"; "Versions" -> "▤"; "Profiles" -> "◉"; else -> "⚙" }, fontSize = 20.sp) },
                        label = { Text(item) },
                        colors = NavigationBarItemDefaults.colors(selectedIconColor = Ember, selectedTextColor = Ember, indicatorColor = Color(0xFF392016), unselectedIconColor = Muted, unselectedTextColor = Muted)
                    )
                }
            }
        }) { padding ->
            LazyColumn(
                modifier = Modifier.fillMaxSize().background(Night).padding(padding),
                contentPadding = PaddingValues(20.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                item { Header() }
                item {
                    when (page) {
                        "Home" -> {
                            Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                                Box(
                                    Modifier.fillMaxWidth().background(Brush.linearGradient(listOf(Color(0xFF4A1D17), Color(0xFF21151B), Panel)), RoundedCornerShape(24.dp)).padding(20.dp)
                                ) {
                                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                        Text("RISE FROM THE ASHES", color = Ember, fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 2.sp)
                                        Text("Your next
Minecraft era.", color = Color.White, fontSize = 30.sp, fontWeight = FontWeight.ExtraBold, lineHeight = 34.sp)
                                        Text("A new world is waiting.", color = Muted)
                                        Button(onClick = { notice = "Minecraft runtime is not integrated yet. This build is the Phoenix launcher foundation." }, colors = ButtonDefaults.buttonColors(containerColor = Ember), modifier = Modifier.fillMaxWidth().height(52.dp), shape = RoundedCornerShape(14.dp)) {
                                            Text("▶   PLAY", color = Color.Black, fontWeight = FontWeight.Black, letterSpacing = 1.sp)
                                        }
                                    }
                                }
                                InfoCard("ACTIVE PROFILE", profile, "Local launcher profile")
                                InfoCard("SELECTED VERSION", "Minecraft $version", "Runtime integration pending")
                                InfoCard("CLIENT STATUS", "Phoenix foundation", "Launcher UI and build pipeline initialized")
                                Text(notice, color = Muted, fontSize = 12.sp)
                            }
                        }
                        "Versions" -> {
                            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                                SectionTitle("Game versions", "Choose a target version for a future runtime installation.")
                                listOf("1.21.4", "1.20.4", "1.19.4", "1.16.5", "1.12.2", "1.8.9").forEach { v ->
                                    SelectRow("Minecraft $v", if (version == v) "SELECTED" else "Tap to select") { version = v; notice = "Selected version $v. Game files are not downloaded in this foundation build." }
                                }
                            }
                        }
                        "Profiles" -> {
                            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                                SectionTitle("Local profiles", "This is a local launcher nickname, not an authenticated Minecraft account.")
                                OutlinedTextField(value = profile, onValueChange = { profile = it.take(16).filter { c -> c.isLetterOrDigit() || c == '_' } }, label = { Text("Player nickname") }, singleLine = true, modifier = Modifier.fillMaxWidth(), colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = Ember, focusedLabelColor = Ember, cursorColor = Ember))
                                Text("Offline profile preview: $profile", color = Muted)
                                Text("Microsoft sign-in will be added with a secure OAuth device flow in a later integration.", color = Muted, fontSize = 12.sp)
                            }
                        }
                        else -> {
                            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                                SectionTitle("Settings", "Phoenix Client foundation settings.")
                                InfoCard("THEME", "Ember / Inferno", "Original dark fire palette")
                                InfoCard("APP VERSION", "0.1.0", "Early development build")
                                InfoCard("ENGINE", "Jetpack Compose", "Native Android interface")
                                InfoCard("GAME RUNTIME", "Not connected", "No Minecraft runtime is bundled yet")
                            }
                        }
                    }
                }
                item {
                    Text("PHOENIX CLIENT  •  EARLY ACCESS FOUNDATION", color = Color(0xFF696773), fontSize = 10.sp, letterSpacing = 1.sp, modifier = Modifier.fillMaxWidth().padding(top = 8.dp))
                }
            }
        }
    }
}

@Composable
private fun Header() {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Box(Modifier.size(44.dp).background(Brush.linearGradient(listOf(Ember, Flame)), RoundedCornerShape(14.dp)), contentAlignment = Alignment.Center) {
                Text("🔥", fontSize = 23.sp)
            }
            Column {
                Text("PHOENIX", color = Color.White, fontWeight = FontWeight.Black, fontSize = 20.sp, letterSpacing = 2.sp)
                Text("CLIENT", color = Ember, fontWeight = FontWeight.Bold, fontSize = 10.sp, letterSpacing = 4.sp)
            }
        }
        Text("v0.1.0", color = Muted, fontSize = 11.sp)
    }
}

@Composable
private fun SectionTitle(title: String, subtitle: String) {
    Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
        Text(title, color = Color.White, fontSize = 23.sp, fontWeight = FontWeight.Bold)
        Text(subtitle, color = Muted, fontSize = 12.sp)
    }
}

@Composable
private fun InfoCard(label: String, value: String, detail: String) {
    Column(Modifier.fillMaxWidth().background(Panel, RoundedCornerShape(18.dp)).padding(16.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
        Text(label, color = Ember, fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.5.sp)
        Text(value, color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Bold)
        Text(detail, color = Muted, fontSize = 12.sp)
    }
}

@Composable
private fun SelectRow(title: String, detail: String, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().background(Panel, RoundedCornerShape(14.dp)).clickable(onClick = onClick).padding(16.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
        Column {
            Text(title, color = Color.White, fontWeight = FontWeight.SemiBold)
            Text(detail, color = Muted, fontSize = 11.sp)
        }
        Text("›", color = Ember, fontSize = 24.sp)
    }
}
